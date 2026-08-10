package com.hollandsmp.staffsession.investigation;

import java.util.UUID;

public final class TeleportAuthorization {
    private final String authorizationId;
    private final String investigationId;
    private final UUID staffer;
    private final UUID target;
    private final long expiresAt;
    private boolean consumed;

    public TeleportAuthorization(String authorizationId, String investigationId, UUID staffer, UUID target, long expiresAt) {
        this.authorizationId = authorizationId;
        this.investigationId = investigationId;
        this.staffer = staffer;
        this.target = target;
        this.expiresAt = expiresAt;
    }

    public String getAuthorizationId() { return authorizationId; }
    public String getInvestigationId() { return investigationId; }
    public UUID getStaffer() { return staffer; }
    public UUID getTarget() { return target; }
    public long getExpiresAt() { return expiresAt; }
    public boolean isConsumed() { return consumed; }

    public boolean isValidFor(UUID staffer, UUID target, String investigationId, long now) {
        return !consumed
            && this.staffer != null
            && this.staffer.equals(staffer)
            && this.target != null
            && this.target.equals(target)
            && this.investigationId != null
            && this.investigationId.equals(investigationId)
            && now <= expiresAt;
    }

    public void consume() {
        this.consumed = true;
    }
}
