package com.hollandsmp.staffsession.runtime;

import com.hollandsmp.staffsessionapi.model.Investigation;
import com.hollandsmp.staffsessionapi.model.InvestigationStatus;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class RuntimeInvestigationCache {
    private final Map<UUID, RuntimeInvestigation> byStaffer = new ConcurrentHashMap<UUID, RuntimeInvestigation>();
    private final Map<UUID, RuntimeInvestigation> byTarget = new ConcurrentHashMap<UUID, RuntimeInvestigation>();

    public void install(Investigation investigation) {
        if (investigation == null || investigation.getInvestigationId() == null || investigation.getStatus() != InvestigationStatus.ACTIVE) {
            return;
        }
        RuntimeInvestigation runtime = new RuntimeInvestigation(
            investigation.getInvestigationId(),
            investigation.getStaffer(),
            investigation.getTarget(),
            investigation.getType(),
            investigation.getStatus(),
            investigation.getWorldName(),
            investigation.getMinX(),
            investigation.getMinY(),
            investigation.getMinZ(),
            investigation.getMaxX(),
            investigation.getMaxY(),
            investigation.getMaxZ()
        );
        byStaffer.put(investigation.getStaffer(), runtime);
        if (investigation.getTarget() != null) {
            byTarget.put(investigation.getTarget(), runtime);
        }
    }

    public void removeByStaffer(UUID staffer) {
        RuntimeInvestigation removed = byStaffer.remove(staffer);
        if (removed != null && removed.getTarget() != null) {
            byTarget.remove(removed.getTarget());
        }
    }

    public void removeByTarget(UUID target) {
        RuntimeInvestigation removed = byTarget.remove(target);
        if (removed != null && removed.getStaffer() != null) {
            byStaffer.remove(removed.getStaffer());
        }
    }

    public RuntimeInvestigation getByStaffer(UUID staffer) {
        return byStaffer.get(staffer);
    }

    public RuntimeInvestigation getByTarget(UUID target) {
        return byTarget.get(target);
    }

    public void clear() {
        byStaffer.clear();
        byTarget.clear();
    }
}
