package com.hollandsmp.staffsession.investigation;

import java.util.UUID;

public final class PlayerInvestigation {
    private final UUID staffer;
    private final UUID target;
    private final String investigationId;

    public PlayerInvestigation(UUID staffer, UUID target, String investigationId) {
        this.staffer = staffer;
        this.target = target;
        this.investigationId = investigationId;
    }

    public UUID getStaffer() { return staffer; }
    public UUID getTarget() { return target; }
    public String getInvestigationId() { return investigationId; }
}
