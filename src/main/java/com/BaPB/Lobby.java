package com.BaPB;

import lombok.Getter;
import net.runelite.api.coords.WorldPoint;

import java.util.HashMap;
import java.util.Map;

/**
 * Maps a WorldPoint to a lobby ID (int).
 */
public class Lobby
{
    public static class LobbyRegion
    {
        private final int xMin, xMax, yMin, yMax;

        public LobbyRegion(int xMin, int yMin)
        {
            this.xMin = xMin;
            this.yMin = yMin;
            this.xMax = xMin + 7; // 8x8 lobbies
            this.yMax = yMin + 7; // 8x8 lobbies
        }

        public boolean contains(WorldPoint point)
        {
            return point.getX() >= xMin && point.getX() <= xMax
                    && point.getY() >= yMin && point.getY() <= yMax;
        }
    }

    @Getter
    public static class RelativePoint
    {
        private final int x;
        private final int y;

        public RelativePoint(int x, int y)
        {
            this.x = x;
            this.y = y;
        }

        @Override
        public String toString()
        {
            return "(" + x + "," + y + ")";
        }
    }

    // Key = lobbyId, Value = region
    private final Map<Integer, LobbyRegion> regions = new HashMap<>();

    public Lobby()
    {
        regions.put(1, new LobbyRegion(2576, 5291));
        regions.put(2, new LobbyRegion(2584, 5291));
        regions.put(3, new LobbyRegion(2595, 5291));
        regions.put(4, new LobbyRegion(2603, 5291));
        regions.put(5, new LobbyRegion(2576, 5281));
        regions.put(6, new LobbyRegion(2584, 5281));
        regions.put(7, new LobbyRegion(2595, 5281));
        regions.put(8, new LobbyRegion(2603, 5281));
        regions.put(9, new LobbyRegion(2576, 5271));
        regions.put(10, new LobbyRegion(2584, 5271));
    }

    /**
     * Returns the lobby ID containing this point, or 0 if none.
     */
    public int getLobbyId(WorldPoint point)
    {
        for (Map.Entry<Integer, LobbyRegion> entry : regions.entrySet())
        {
            if (entry.getValue().contains(point))
            {
                return entry.getKey();
            }
        }
        return 0;
    }

    /**
     * Returns all LobbyRegion objects
     */
    public Iterable<LobbyRegion> getAllRegions()
    {
        return regions.values();
    }

    /**
     * Returns the relative coordinates (x, y) within the given lobby.
     * Returns null if the lobbyId is invalid.
     */
    public RelativePoint getRelativeCoordinates(WorldPoint point, int lobbyId)
    {
        LobbyRegion region = regions.get(lobbyId);
        if (region == null)
        {
            return null;
        }

        int relativeX = point.getX() - region.xMin;
        int relativeY = point.getY() - region.yMin;

        return new RelativePoint(relativeX, relativeY);
    }
}
