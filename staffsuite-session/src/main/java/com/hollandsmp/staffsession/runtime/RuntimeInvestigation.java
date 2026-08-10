package com.hollandsmp.staffsession.runtime;

import com.hollandsmp.staffsessionapi.model.InvestigationStatus;
import com.hollandsmp.staffsessionapi.model.InvestigationType;

import java.util.UUID;

public final class RuntimeInvestigation {
    private final String investigationId;
    private final UUID staffer;
    private final UUID target;
    private final InvestigationType type;
    private final InvestigationStatus status;
    private final String worldName;
    private final Double minX;
    private final Double minY;
    private final Double minZ;
    private final Double maxX;
    private final Double maxY;
    private final Double maxZ;

    public RuntimeInvestigation(String investigationId, UUID staffer, UUID target, InvestigationType type, InvestigationStatus status,
                                String worldName, Double minX, Double minY, Double minZ, Double maxX, Double maxY, Double maxZ) {
        this.investigationId = investigationId;
        this.staffer = staffer;
        this.target = target;
        this.type = type;
        this.status = status;
        this.worldName = worldName;
        this.minX = minX;
        this.minY = minY;
        this.minZ = minZ;
        this.maxX = maxX;
        this.maxY = maxY;
        this.maxZ = maxZ;
    }

    public String getInvestigationId() { return investigationId; }
    public UUID getStaffer() { return staffer; }
    public UUID getTarget() { return target; }
    public InvestigationType getType() { return type; }
    public InvestigationStatus getStatus() { return status; }
    public String getWorldName() { return worldName; }
    public Double getMinX() { return minX; }
    public Double getMinY() { return minY; }
    public Double getMinZ() { return minZ; }
    public Double getMaxX() { return maxX; }
    public Double getMaxY() { return maxY; }
    public Double getMaxZ() { return maxZ; }
}
