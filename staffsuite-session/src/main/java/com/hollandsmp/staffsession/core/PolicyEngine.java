package com.hollandsmp.staffsession.core;

import com.hollandsmp.staffsession.db.StaffSessionDatabase;
import com.hollandsmp.staffsessionapi.model.FailureReason;
import com.hollandsmp.staffsessionapi.model.InvestigationType;

import java.util.UUID;

public final class PolicyEngine {
    public PolicyDecision evaluateStart(UUID staffer, UUID target, InvestigationType type, StaffSessionDatabase database) {
        if (staffer == null) {
            return PolicyDecision.denied(FailureReason.INVALID_STATE);
        }
        if (type == null) {
            return PolicyDecision.denied(FailureReason.INVALID_INVESTIGATION_TYPE);
        }
        if (type == InvestigationType.PLAYER && target == null) {
            return PolicyDecision.denied(FailureReason.INVALID_TARGET);
        }
        if (type == InvestigationType.PLAYER && staffer.equals(target)) {
            return PolicyDecision.denied(FailureReason.INVALID_TARGET);
        }
        if (database.isStafferInSession(staffer)) {
            return PolicyDecision.denied(FailureReason.STAFFER_ALREADY_IN_SESSION);
        }
        if (type == InvestigationType.PLAYER && target != null && database.isPlayerBeingInvestigated(target)) {
            return PolicyDecision.denied(FailureReason.TARGET_ALREADY_INVESTIGATED);
        }
        return PolicyDecision.allowed();
    }

    public static final class PolicyDecision {
        private final boolean allowed;
        private final FailureReason failureReason;

        private PolicyDecision(boolean allowed, FailureReason failureReason) {
            this.allowed = allowed;
            this.failureReason = failureReason;
        }

        public static PolicyDecision allowed() {
            return new PolicyDecision(true, null);
        }

        public static PolicyDecision denied(FailureReason failureReason) {
            return new PolicyDecision(false, failureReason);
        }

        public boolean isAllowed() { return allowed; }
        public FailureReason getFailureReason() { return failureReason; }
    }
}
