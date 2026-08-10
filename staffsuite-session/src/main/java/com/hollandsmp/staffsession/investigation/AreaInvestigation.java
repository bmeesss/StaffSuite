package com.hollandsmp.staffsession.investigation;

import java.util.UUID;

public final class AreaInvestigation {
    private final UUID staffer;
    private final String worldName;
    private final double minX;
    private final double minY;
    private final double minZ;
    private final double maxX;
    private final double maxY;
    private final double maxZ;

    public AreaInvestigation(UUID staffer, String worldName, double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        this.staffer = staffer;
        this.worldName = worldName;
        this.minX = minX;
        this.minY = minY;
        this.minZ = minZ;
        this.maxX = maxX;
        this.maxY = maxY;
        this.maxZ = maxZ;
    }

    public UUID getStaffer() { return staffer; }
    public String getWorldName() { return worldName; }
    public double getMinX() { return minX; }
    public double getMinY() { return minY; }
    public double getMinZ() { return minZ; }
    public double getMaxX() { return maxX; }
    public double getMaxY() { return maxY; }
    public double getMaxZ() { return maxZ; }
}
