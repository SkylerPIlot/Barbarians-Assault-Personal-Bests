package com.BaPB;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.inject.Inject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

@Slf4j
public class BaPBService
{
    private static final String TOKEN_ISSUER_URL   = "https://osrs-ba-api-7f97e40f532b.herokuapp.com/api/v1/tokens/public/";
    private static final String SUBMIT_RUN_URL   = "https://osrs-ba-api-7f97e40f532b.herokuapp.com/api/v1/rounds/";

    private static final String SIGNING_SECRET = "ba-4-all";
    private String cachedToken = null;
    private Instant cachedTokenExpiry = null;

    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final OkHttpClient http;
    private final Gson gson;
    private final BaPBConfig config;

    @Inject
    public BaPBService(OkHttpClient http, Gson gson, BaPBConfig config)
    {
        this.http = http;
        this.gson = gson;
        this.config = config;
    }

    public void shutdown()
    {
        executor.shutdown();
    }

    private boolean isTokenValid()
    {
        if (cachedToken == null || cachedToken.isEmpty()) return false;
        if (cachedTokenExpiry == null ) return false;

        // Expire 30 seconds early to avoid edge cases
        Instant bufferExpiry = cachedTokenExpiry.minusSeconds(30);
        return Instant.now().isBefore(bufferExpiry);
    }

    private void fetchToken(String currentPlayer) throws Exception
    {
        String timestamp = String.valueOf(Instant.now().toEpochMilli());
        String nonce = UUID.randomUUID().toString();
        String signature = generateHmacSha256(SIGNING_SECRET, timestamp + nonce);

        // Create JSON body
        Map<String, String> bodyMap = new HashMap<>();
        bodyMap.put("description", currentPlayer);
        String jsonBody = gson.toJson(bodyMap);

        RequestBody body = RequestBody.create(JSON, jsonBody);

        Request req = new Request.Builder()
                .url(TOKEN_ISSUER_URL)
                .header("X-Timestamp", timestamp)
                .header("X-Nonce", nonce)
                .header("X-Signature", signature)
                .post(body)
                .build();

        try (Response resp = http.newCall(req).execute())
        {
            if (!resp.isSuccessful() || resp.body() == null)
                throw new RuntimeException("Failed to fetch API token: " + resp.code() + " " + resp.message());


            String rawResponse = resp.body().string();  // read raw response

            log.debug("Raw API response: {}", rawResponse);

            TokenWrapper wrapper = gson.fromJson(rawResponse, TokenWrapper.class);
            if (wrapper == null || wrapper.data == null || wrapper.data.token == null || wrapper.data.token.isEmpty())
                throw new RuntimeException("Token issuer returned no 'token' key");

            cachedToken = wrapper.data.token;

            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSS");
            LocalDateTime ldt = LocalDateTime.parse(wrapper.data.expiresAt, formatter);
            cachedTokenExpiry = ldt.toInstant(ZoneOffset.UTC);
        }
    }

    private void submitRunToAPI(
            Map<String, String> currentTeam,
            String roundFormat,
            Timers timers,
            boolean scroller,
            String submittedBy,
            String userUuid
    ) throws IOException
    {
        // Prepare players
        List<PlayerEntry> players = new ArrayList<>();
        for (Map.Entry<String, String> e : currentTeam.entrySet())
        {
            String uuid = (userUuid != null && e.getKey().equals(submittedBy)) ? userUuid : null;
            players.add(new PlayerEntry(e.getKey(), e.getValue(), uuid));
        }

        // Prepare wave data
        List<WaveEntry> waveData = new ArrayList<>();
        for (Map.Entry<Integer, Timers.WaveData> entry : timers.getWaveData().entrySet()) {
            int waveNumber = entry.getKey();
            Timers.WaveData data = entry.getValue();

            double waveTime = 0;
            int qsTime = 0;
            boolean goodPremove = false;
            boolean reset = false;

            if (data != null) {
                waveTime = data.getWaveTimer().getElapsedSeconds(scroller, false); // true = isLeader/scroller
                qsTime = data.getQsTimer().roundTicks;
                goodPremove = data.isGoodPremove();
                reset = data.getLobbyCount() > 1;
            }

            waveData.add(new WaveEntry(waveNumber, waveTime, qsTime, goodPremove, reset));
        }

        // Prepare round time
        double roundTime = timers.getRoundTimer().getElapsedSeconds(scroller);

        SubmitPayload payload = new SubmitPayload(
                roundFormat,
                roundTime,
                submittedBy,
                scroller,
                players,
                waveData
        );

        RequestBody body = RequestBody.create(JSON, gson.toJson(payload));
        Request req = new Request.Builder()
                .url(SUBMIT_RUN_URL)
                .addHeader("Authorization", "Bearer " + cachedToken)
                .post(body)
                .build();

        try (Response resp = http.newCall(req).execute())
        {
            if (resp.isSuccessful())
            {
                log.info("Successfully submitted run to API");
            }
            else
            {
                log.warn("API submission failed: {} {}", resp.code(), resp.message());
            }
        }
    }

    public void handleRoundEnd(
            Map<String, String> currentTeam,
            String roundFormat,
            Timers timers,
            boolean scroller,
            String submittedBy
    )
    {
        if (!config.SubmitRuns() || roundFormat == null)
        {
            log.debug("SubmitRuns is disabled or roundFormat is null. Skipping round submission.");
            return;
        }

        String userUuid = config.uuid_key();

        executor.execute(() -> {
            try {
                // Validate token
                if (!isTokenValid()) {
                    log.info("API token invalid or expired. Fetching a new one...");
                    fetchToken(submittedBy);
                }

                submitRunToAPI(currentTeam, roundFormat, timers, scroller, submittedBy, userUuid);

            } catch (Exception e) {
                log.warn("Failed during token check or run submission", e);
            }
        });
    }

    private String generateHmacSha256(String key, String data) throws Exception
    {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] rawHmac = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(rawHmac);
    }

    private static class TokenWrapper {
        String status;
        TokenResponse data;
        Object error;
    }

    private static class TokenResponse
    {
        @SerializedName("token")
        String token;

        @SerializedName("expires_at")
        String expiresAt;
    }

    private static class WaveEntry {
        @SerializedName("wave_number")
        final int waveNumber;

        @SerializedName("wave_time")
        final double waveTime;

        @SerializedName("qs_time")
        final int qsTime;

        @SerializedName("good_premove")
        final boolean goodPremove;

        @SerializedName("reset")
        final Boolean reset;

        WaveEntry(int waveNumber, double waveTime, int qsTime, boolean goodPremove, boolean reset) {
            this.waveNumber = waveNumber;
            this.waveTime = waveTime;
            this.qsTime = qsTime;
            this.goodPremove = goodPremove;
            this.reset = reset;
        }
    }

    private static class PlayerEntry
    {
        @SerializedName("character_name")
        final String characterName;

        @SerializedName("role")
        final String role;

        @SerializedName("uuid_key")
        final String uuidKey; // optional

        PlayerEntry(String characterName, String role, String uuidKey)
        {
            this.characterName = characterName;
            this.role = role;
            this.uuidKey = uuidKey;
        }
    }

    private static class SubmitPayload
    {
        @SerializedName("format")
        final String format;
        @SerializedName("round_time")
        final double roundTime;
        @SerializedName("scroller")
        final boolean scroller;
        @SerializedName("submitted_by")
        final String submittedBy;
        @SerializedName("players")
        final List<PlayerEntry> players;
        @SerializedName("wave_data")
        final List<WaveEntry> waveData;

        SubmitPayload(String format, double roundTime, String submittedBy, boolean scroller, List<PlayerEntry> players, List<WaveEntry> waveData)
        {
            this.format = format;
            this.roundTime = roundTime;
            this.submittedBy = submittedBy;
            this.scroller = scroller;
            this.players = players;
            this.waveData = waveData;
        }
    }
}
