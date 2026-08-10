package com.hollandsmp.staffsession.integrity;

import com.hollandsmp.staffsessionapi.model.Investigation;
import com.hollandsmp.staffsessionapi.model.InvestigationStatus;
import com.hollandsmp.staffsessionapi.model.InvestigationType;

public final class CorruptedProtection {
    public boolean isCorrupted(Investigation investigation) {
        if (investigation == null) {
            return true;
        }
        if (investigation.getInvestigationId() == null || investigation.getInvestigationId().trim().isEmpty()) {
            return true;
        }
        if (investigation.getStaffer() == null) {
            return true;
        }
        if (investigation.getType() == null || investigation.getStatus() == null) {
            return true;
        }
        if (investigation.getStartedAt() <= 0) {
            return true;
        }
        if (investigation.getType() == InvestigationType.PLAYER && investigation.getTarget() == null) {
            return true;
        }
        return investigation.getStatus() != InvestigationStatus.ACTIVE
            && investigation.getStatus() != InvestigationStatus.ENDED
            && investigation.getStatus() != InvestigationStatus.CRASHED_RECOVERED
            && investigation.getStatus() != InvestigationStatus.CORRUPTED;
    }
}
