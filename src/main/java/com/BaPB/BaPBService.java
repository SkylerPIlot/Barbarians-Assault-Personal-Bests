package com.BaPB;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
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
import java.util.concurrent.ScheduledExecutorService;

@Slf4j
public class BaPBService
{
    private static final String PLAYER_PBS_URL = "https://api.osrs-ba.com/api/v1/players/pbs/";
    private static final String TOKEN_ISSUER_URL   = "https://api.osrs-ba.com/api/v1/tokens/public/";
    private static final String SUBMIT_RUN_URL   = "https://api.osrs-ba.com/api/v1/rounds/";

    private static final String SIGNING_SECRET = "ba-4-all";
    private String cachedToken = null;
    private Instant cachedTokenExpiry = null;

    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    private final OkHttpClient http;
    private final Gson gson;
    private final BaPBConfig config;

    @Inject
    private ScheduledExecutorService executor;

    @Inject
    public BaPBService(OkHttpClient http, Gson gson, BaPBConfig config)
    {
        this.http = http;
        this.gson = gson;
        this.config = config;
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
            String userUuid,
            String worldRegion
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
            Double resetWaveTime = 0.0;
            Integer resetQsTime = 0;
            Lobby.RelativePoint rp = null;
            Double rangerDeathTime = null;
            Double fighterDeathTime = null;
            Double runnerDeathTime = null;
            Double healerDeathTime = null;
            Double queenSpawnTime = null;

            if (data != null) {
                waveTime = data.getWaveTimer().getElapsedSeconds(scroller, false); // true = isLeader/scroller
                qsTime = data.getQsTimer().roundTicks;
                goodPremove = data.isGoodPremove();
                reset = data.getLobbyCount() > 1;
                resetWaveTime = data.getResetWaveTime();
                resetQsTime = data.getResetQsTime();
                rp = data.getRelativePoint();
                rangerDeathTime = data.getRangerDeathTime();
                fighterDeathTime = data.getFighterDeathTime();
                runnerDeathTime = data.getRunnerDeathTime();
                healerDeathTime = data.getHealerDeathTime();
                queenSpawnTime = data.getQueenSpawnTime();

            }

            waveData.add(new WaveEntry(waveNumber, waveTime, qsTime, goodPremove, reset, resetWaveTime, resetQsTime, rp, rangerDeathTime, fighterDeathTime, runnerDeathTime, healerDeathTime, queenSpawnTime));
        }

        // Prepare round time
        double roundTime = timers.getRoundTimer().getElapsedSeconds(scroller);

        SubmitPayload payload = new SubmitPayload(
                roundFormat,
                roundTime,
                submittedBy,
                scroller,
                worldRegion,
                players,
                waveData
        );

        RequestBody body = RequestBody.create(JSON, gson.toJson(payload));
        log.debug("Submitting body: {}", gson.toJson(payload));
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
            String submittedBy,
            String worldRegion
    )
    {
        if (!config.SubmitRuns() || roundFormat == null || currentTeam == null || currentTeam.isEmpty())
        {
            log.debug("SubmitRuns is disabled, roundFormat is null, or team data is missing. Skipping round submission.");
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

                submitRunToAPI(currentTeam, roundFormat, timers, scroller, submittedBy, userUuid, worldRegion);

            } catch (Exception e) {
                log.warn("Failed during token check or run submission", e);
            }
        });
    }

    public boolean supportsPersonalBest(String personalBestType)
    {
        return getPersonalBestTarget(personalBestType) != null;
    }

    public double fetchPersonalBest(String player, String personalBestType) throws IOException
    {
        PersonalBestTarget target = getPersonalBestTarget(personalBestType);
        if (target == null)
        {
            return 0.0;
        }

        HttpUrl url = Objects.requireNonNull(HttpUrl.parse(PLAYER_PBS_URL)).newBuilder()
                .addQueryParameter("name", player)
                .build();
        if (target.format != null && target.role != null)
        {
            url = url.newBuilder()
                    .addQueryParameter("format", target.format)
                    .addQueryParameter("role", target.role)
                    .build();
        }
        Request request = new Request.Builder().url(url).get().build();

        try (Response response = http.newCall(request).execute())
        {
            if (!response.isSuccessful() || response.body() == null)
            {
                throw new IOException("Personal best API lookup failed with HTTP " + response.code());
            }

            try
            {
                JsonObject wrapper = gson.fromJson(response.body().charStream(), JsonObject.class);
                JsonObject data = getObject(wrapper, "data");
                if (target.format == null)
                {
                    double personalBest = findQuickestPersonalBest(data);
                    if (personalBest <= 0)
                    {
                        log.warn("Personal best API returned no PBs for {}", player);
                    }
                    return personalBest;
                }

                JsonObject format = getObject(data, target.format);
                JsonObject role = getObject(format, target.role);
                JsonElement pbTime = role == null ? null : role.get("pb_time");
                return pbTime == null || pbTime.isJsonNull() ? 0.0 : pbTime.getAsDouble();
            }
            catch (JsonParseException | IllegalStateException | NumberFormatException ex)
            {
                throw new IOException("Unable to parse personal best API response", ex);
            }
        }
    }

    private static double findQuickestPersonalBest(JsonElement element)
    {
        if (element == null || !element.isJsonObject())
        {
            return 0.0;
        }

        double quickest = Double.POSITIVE_INFINITY;
        JsonObject object = element.getAsJsonObject();
        JsonElement pbTime = object.get("pb_time");
        if (pbTime != null && !pbTime.isJsonNull())
        {
            double value = pbTime.getAsDouble();
            if (value > 0)
            {
                quickest = value;
            }
        }

        for (Map.Entry<String, JsonElement> entry : object.entrySet())
        {
            double nested = findQuickestPersonalBest(entry.getValue());
            if (nested > 0 && nested < quickest)
            {
                quickest = nested;
            }
        }

        return Double.isInfinite(quickest) ? 0.0 : quickest;
    }

    private static JsonObject getObject(JsonObject parent, String member)
    {
        if (parent == null)
        {
            return null;
        }

        JsonElement element = parent.get(member);
        return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
    }

    private static PersonalBestTarget getPersonalBestTarget(String personalBestType)
    {
        switch (personalBestType)
        {
            case "Barbarian Assault": return new PersonalBestTarget(null, null);
            case "Main Attacker": return new PersonalBestTarget("five_man", "main_attacker");
            case "Attacker": return new PersonalBestTarget("five_man", "2nd_attacker");
            case "Healer": return new PersonalBestTarget("five_man", "healer");
            case "Collector": return new PersonalBestTarget("five_man", "collector");
            case "Defender": return new PersonalBestTarget("five_man", "defender");
            case "Leech Attacker": return new PersonalBestTarget("leech", "attacker");
            case "Leech Healer": return new PersonalBestTarget("leech", "healer");
            case "Leech Collector": return new PersonalBestTarget("leech", "collector");
            case "Leech Defender": return new PersonalBestTarget("leech", "defender");
            case "DH Attacker": return new PersonalBestTarget("duo_heal", "attacker");
            case "DH 2nd Healer": return new PersonalBestTarget("duo_heal", "2nd_healer");
            case "DH Main Healer": return new PersonalBestTarget("duo_heal", "main_healer");
            case "DH Collector": return new PersonalBestTarget("duo_heal", "collector");
            case "DH Defender": return new PersonalBestTarget("duo_heal", "defender");
            default: return null;
        }
    }

    private static class PersonalBestTarget
    {
        private final String format;
        private final String role;

        private PersonalBestTarget(String format, String role)
        {
            this.format = format;
            this.role = role;
        }
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

        @SerializedName("reset_wave_time")
        final Double resetWaveTime;

        @SerializedName("reset_qs_time")
        final Integer resetQsTime;

        @SerializedName("good_premove")
        final boolean goodPremove;

        @SerializedName("reset")
        final Boolean reset;

        @SerializedName("x_qs_spawn")
        final Integer xSpawn;

        @SerializedName("y_qs_spawn")
        final Integer ySpawn;

        @SerializedName("ranger_death_time")
        final Double rangerDeathTime;

        @SerializedName("fighter_death_time")
        final Double fighterDeathTime;

        @SerializedName("runner_death_time")
        final Double runnerDeathTime;

        @SerializedName("healer_death_time")
        final Double healerDeathTime;

        @SerializedName("queen_spawn_time")
        final Double queenSpawnTime;

        WaveEntry(int waveNumber, double waveTime, int qsTime, boolean goodPremove, boolean reset, Double resetWaveTime, Integer resetQsTime, Lobby.RelativePoint relativePoint, Double rangerDeathTime, Double fighterDeathTime, Double runnerDeathTime, Double healerDeathTime, Double queenSpawnTime) {
            this.waveNumber = waveNumber;
            this.waveTime = waveTime;
            this.qsTime = qsTime;
            this.resetWaveTime = resetWaveTime;
            this.resetQsTime = resetQsTime;
            this.goodPremove = goodPremove;
            this.reset = reset;
            this.rangerDeathTime = rangerDeathTime;
            this.fighterDeathTime = fighterDeathTime;
            this.runnerDeathTime = runnerDeathTime;
            this.healerDeathTime = healerDeathTime;
            this.queenSpawnTime = queenSpawnTime;

            if (relativePoint != null) {
                this.xSpawn = relativePoint.getX();
                this.ySpawn = relativePoint.getY();
            } else {
                this.xSpawn = null;
                this.ySpawn = null;
            }
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
        @SerializedName("world_region")
        final String worldRegion;
        @SerializedName("players")
        final List<PlayerEntry> players;
        @SerializedName("wave_data")
        final List<WaveEntry> waveData;

        SubmitPayload(String format, double roundTime, String submittedBy, boolean scroller, String worldRegion, List<PlayerEntry> players, List<WaveEntry> waveData)
        {
            this.format = format;
            this.roundTime = roundTime;
            this.submittedBy = submittedBy;
            this.scroller = scroller;
            this.worldRegion = worldRegion;
            this.players = players;
            this.waveData = waveData;
        }
    }
}
