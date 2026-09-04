package com.BaPB;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

/**
 * Holds GameTimers for:
 * 1. Individual wave timers
 * 2. Queue/Lobby (QS) timers
 * 3. Global round timer
 * Provides methods to start/stop/reset and tick all timers.
 */
@Slf4j
public class Timers
{
    @Setter
    @Getter
    public static class WaveData {
        private final GameTimer waveTimer;
        private final GameTimer qsTimer;
        private boolean goodPremove;
        private int lobbyCount;
        private int waveAttemptCount;
        private int qsAttemptCount;
        private Lobby.RelativePoint relativePoint;
        private Double rangerDeathTime;
        private Double fighterDeathTime;
        private Double runnerDeathTime;
        private Double healerDeathTime;
        private Double queenSpawnTime;
        private Double resetWaveTime;
        private Integer resetQsTime;

        public WaveData() {
            this.waveTimer = new GameTimer();
            this.qsTimer = new GameTimer();
            this.goodPremove = false;
            this.lobbyCount = 0;
            this.waveAttemptCount = 0;
            this.qsAttemptCount = 0;
            this.resetWaveTime = 0.0;
            this.resetQsTime = 0;
        }

        private WaveData(WaveData source) {
            this.waveTimer = source.waveTimer.copy();
            this.qsTimer = source.qsTimer.copy();
            this.goodPremove = source.goodPremove;
            this.lobbyCount = source.lobbyCount;
            this.waveAttemptCount = source.waveAttemptCount;
            this.qsAttemptCount = source.qsAttemptCount;
            this.relativePoint = source.relativePoint;
            this.rangerDeathTime = source.rangerDeathTime;
            this.fighterDeathTime = source.fighterDeathTime;
            this.runnerDeathTime = source.runnerDeathTime;
            this.healerDeathTime = source.healerDeathTime;
            this.queenSpawnTime = source.queenSpawnTime;
            this.resetWaveTime = source.resetWaveTime;
            this.resetQsTime = source.resetQsTime;
        }

        public void onGameTick() {
            waveTimer.onGameTick();
            qsTimer.onGameTick();
        }

        public void stopTimers() {
            waveTimer.stop();
            qsTimer.stop();
        }

        public void incrementLobbyCount() {
            this.lobbyCount++;
        }

        public void addResetWaveTime(double waveSeconds) {
            if (this.resetWaveTime == null) {
                this.resetWaveTime = 0.0;
            }
            this.resetWaveTime += waveSeconds;
        }

        public void addResetQsTime(int qsTicks) {
            if (this.resetQsTime == null) {
                this.resetQsTime = 0;
            }
            this.resetQsTime += qsTicks;
        }
    }

    // Per-wave meta data, wave # -> meta data
    @Getter
    private final Map<Integer, WaveData> waveData = new HashMap<>();

    // Global round timer
    @Getter
    private final GameTimer roundTimer = new GameTimer();

    public int lastWave = 0; // 0 = not in a wave
    public int lastLobby = 0;  // 0 = not in a lobby

    /* -------------------- Wave Timer Methods -------------------- **/

    public GameTimer getWaveTimer(int waveNumber)
    {
        return waveData
                .computeIfAbsent(waveNumber, k -> new WaveData())
                .getWaveTimer();
    }

    public void startWave(int waveNumber)
    {
        if (waveNumber > 0) {
            WaveData data = waveData.computeIfAbsent(waveNumber, k -> new WaveData());
            if (data.waveAttemptCount > 0) {
                data.addResetWaveTime(data.getWaveTimer().getElapsedSeconds(false, false));
                data.getWaveTimer().clear();
            }
            data.waveAttemptCount++;
            data.getWaveTimer().start();
        }
    }

    public void stopWave(int waveNumber)
    {
        WaveData data = waveData.get(waveNumber);
        if (data != null) data.getWaveTimer().stop();
    }

    /* -------------------- QS Timer Methods -------------------- **/

    public GameTimer getQSTimer(int waveNumber) {
        return waveData
                .computeIfAbsent(waveNumber, k -> new WaveData())
                .getQsTimer();
    }

    public void startQS(int waveNumber) {
        if (waveNumber > 0) {
            WaveData data = waveData.computeIfAbsent(waveNumber, k -> new WaveData());
            if (data.qsAttemptCount > 0) {
                data.addResetQsTime(data.getQsTimer().roundTicks);
                data.getQsTimer().clear();
            }
            data.qsAttemptCount++;
            data.getQsTimer().start();
        }
    }

    public void stopQS(int waveNumber)
    {
        WaveData data = waveData.get(waveNumber);
        if (data != null) data.getQsTimer().stop();
    }

    /* -------------------- Round Timer Methods -------------------- **/

    public void startRound()
    {
        roundTimer.start();
    }

    public double getRoundSeconds(boolean isLeader)
    {
        return roundTimer.getElapsedSeconds(isLeader);
    }

    /* -------------------- NPC Death Methods -------------------- **/

    public void setRangerDeath(int waveNumber, double time)
    {
        WaveData data = waveData.get(waveNumber);
        if (data != null) data.setRangerDeathTime(time);
    }

    public void setFighterDeath(int waveNumber, double time)
    {
        WaveData data = waveData.get(waveNumber);
        if (data != null) data.setFighterDeathTime(time);
    }

    public void setRunnerDeath(int waveNumber, double time)
    {
        WaveData data = waveData.get(waveNumber);
        if (data != null) data.setRunnerDeathTime(time);
    }

    public void setHealerDeath(int waveNumber, double time)
    {
        WaveData data = waveData.get(waveNumber);
        if (data != null) data.setHealerDeathTime(time);
    }

    public void setQueenSpawnTime(double time)
    {
        WaveData data = waveData.get(10); // Queen only W10
        if (data != null) data.setQueenSpawnTime(time);
    }

    /* -------------------- Tick Update -------------------- **/

    /**
     * Call once per game tick to update all timers
     */
    public void onGameTick() {
        roundTimer.onGameTick();
        waveData.values().forEach(WaveData::onGameTick);
    }

    /* -------------------- Reset Methods -------------------- **/

    /**
     * Resets all timers: round, waves, and QS
     */
    public void resetAll() {
        roundTimer.clear();
        waveData.clear();
        lastWave = 0;
        lastLobby = 0;
    }

    public Timers copy()
    {
        Timers copy = new Timers();
        copy.roundTimer.clear();
        copy.roundTimer.roundTicks = roundTimer.roundTicks;
        for (Map.Entry<Integer, WaveData> entry : waveData.entrySet())
        {
            copy.waveData.put(entry.getKey(), new WaveData(entry.getValue()));
        }
        copy.lastWave = lastWave;
        copy.lastLobby = lastLobby;
        return copy;
    }

    /**
     * Stops the round timer, all wave timers, and all QS timers.
     */
    public void stopAll()
    {
        // Stop round
        roundTimer.stop();

        // Stop all per-wave timers
        waveData.values().forEach(WaveData::stopTimers);
    }

    /* -------------------- Core flow methods -------------------- */

    /**
     * Update timers based on the detected wave/lobby state.
     */
    public void updateState(int currentWave, int currentLobby, boolean goodPremove, Lobby.RelativePoint relPoint)
    {
        // --- Transition: Lobby → Wave ---
        if (lastWave == 0 && currentWave > 0)
        {
            startWave(currentWave);
            stopQS(lastLobby);

            // Set premove for this wave (which produce the next lobby premove)
            if (currentWave + 1 <= 10) {
                WaveData data = waveData.computeIfAbsent(currentWave + 1, k -> new WaveData());
                data.setGoodPremove(goodPremove);
            }
        }

        // --- Transition: Wave → Lobby ---
        else if (lastWave > 0 && currentWave == 0)
        {
            stopWave(lastWave);

            // A zero lobby means the player is between locations or has left BA.
            // Do not create a wave-data entry because only lobby IDs 1-10 are valid.
            if (currentLobby > 0)
            {
                WaveData currentLobbyData = waveData.computeIfAbsent(currentLobby, k -> new WaveData());

                if (relPoint != null)
                {
                    currentLobbyData.setRelativePoint(relPoint);
                }

                currentLobbyData.incrementLobbyCount();
                startQS(currentLobby);
            }

        }

        // Save latest state
        lastWave = currentWave;
        lastLobby = currentLobby;
    }

    public void logWaveData()
    {
        if (waveData.isEmpty())
        {
            log.debug("No wave data available.");
            return;
        }

        log.debug("----- WaveData Dump -----");
        for (Map.Entry<Integer, WaveData> entry : waveData.entrySet())
        {
            int waveNumber = entry.getKey();
            WaveData data = entry.getValue();

            String relPointStr = (data.getRelativePoint() != null)
                    ? "(" + data.getRelativePoint().getX() + "," + data.getRelativePoint().getY() + ")"
                    : "null";

            log.debug("Wave {}: waveTimer={}s, qsTimer={}s, goodPremove={}, lobbyCount={}, fighterTime={}, rangerTime={}, runnerTime={}, healerTime={}, relativePoint={}",
                    waveNumber,
                    data.getWaveTimer().getElapsedSeconds(true),
                    data.getQsTimer().getElapsedSeconds(true),
                    data.isGoodPremove(),
                    data.getLobbyCount(),
                    data.getFighterDeathTime(),
                    data.getRangerDeathTime(),
                    data.getRunnerDeathTime(),
                    data.getHealerDeathTime(),
                    relPointStr
            );
        }
        log.debug("-------------------------");
    }
}
