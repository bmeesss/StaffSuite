package com.hollandsmp.staffsession.integrity;

import java.util.UUID;

public final class SecurityAudit {
    public enum EventType {
        INVESTIGATION_STARTED,
        INVESTIGATION_ENDED,
        INVESTIGATION_RECOVERED,
        INVESTIGATION_CORRUPTED,
        AUTHORIZATION_DENIED,
        PERMISSION_DENIED,
        TARGET_LOCK_REJECTED,
        STAFFER_LOCK_REJECTED,
        INVALID_STATE_DETECTED,
        DATABASE_FAILURE
    }

    public static final class AuditRecord {
        private final String auditId;
        private final String investigationId;
        private final UUID staffer;
        private final UUID target;
        private final EventType eventType;
        private final String reason;
        private final long createdAt;

        public AuditRecord(String auditId, String investigationId, UUID staffer, UUID target, EventType eventType, String reason, long createdAt) {
            this.auditId = auditId;
            this.investigationId = investigationId;
            this.staffer = staffer;
            this.target = target;
            this.eventType = eventType;
            this.reason = reason;
            this.createdAt = createdAt;
        }

        public String getAuditId() { return auditId; }
        public String getInvestigationId() { return investigationId; }
        public UUID getStaffer() { return staffer; }
        public UUID getTarget() { return target; }
        public EventType getEventType() { return eventType; }
        public String getReason() { return reason; }
        public long getCreatedAt() { return createdAt; }
    }
}
