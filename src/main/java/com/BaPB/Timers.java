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
        private Lobby.RelativePoint relativePoint;

        public WaveData() {
            this.waveTimer = new GameTimer();
            this.qsTimer = new GameTimer();
            this.goodPremove = false;
            this.lobbyCount = 0;
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
        if (waveNumber > 0) getWaveTimer(waveNumber).start();
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
        if (waveNumber > 0) getQSTimer(waveNumber).start();
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
            startQS(currentLobby);

            if (relPoint != null)
            {
                WaveData data = waveData.computeIfAbsent(currentLobby, k -> new WaveData());
                data.setRelativePoint(relPoint);
            }

            // Detect resets
            WaveData data = waveData.get(currentLobby);
            if (data != null) data.incrementLobbyCount();

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

            log.debug("Wave {}: waveTimer={}s, qsTimer={}s, goodPremove={}, lobbyCount={}, relativePoint={}",
                    waveNumber,
                    data.getWaveTimer().getElapsedSeconds(true),
                    data.getQsTimer().getElapsedSeconds(true),
                    data.isGoodPremove(),
                    data.getLobbyCount(),
                    relPointStr
            );
        }
        log.debug("-------------------------");
    }
}
