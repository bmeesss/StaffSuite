package com.hollandsmp.staffsession.core;

import com.hollandsmp.staffsession.db.StaffSessionDatabase;
import com.hollandsmp.staffsessionapi.model.Investigation;
import com.hollandsmp.staffsessionapi.model.FailureReason;
import com.hollandsmp.staffsessionapi.model.InvestigationResult;
import com.hollandsmp.staffsessionapi.model.InvestigationType;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class SessionController {
    private final StaffSessionDatabase database;
    private final PolicyEngine policyEngine;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public SessionController(StaffSessionDatabase database, PolicyEngine policyEngine) {
        this.database = database;
        this.policyEngine = policyEngine;
    }

    public CompletableFuture<InvestigationResult> startInvestigation(final UUID staffer, final UUID target, final InvestigationType type, final String reportId, final Investigation runtimeBounds) {
        return CompletableFuture.supplyAsync(() -> {
            PolicyEngine.PolicyDecision decision = policyEngine.evaluateStart(staffer, target, type, database);
            if (!decision.isAllowed()) {
                return InvestigationResult.failure(decision.getFailureReason());
            }
            if (runtimeBounds != null && runtimeBounds.getWorldName() != null) {
                return database.startInvestigation(staffer, target, type, reportId,
                    runtimeBounds.getWorldName(),
                    runtimeBounds.getMinX(), runtimeBounds.getMinY(), runtimeBounds.getMinZ(),
                    runtimeBounds.getMaxX(), runtimeBounds.getMaxY(), runtimeBounds.getMaxZ());
            }
            return database.startInvestigation(staffer, target, type, reportId);
        }, executor);
    }

    public CompletableFuture<InvestigationResult> endInvestigation(final UUID staffer) {
        return CompletableFuture.supplyAsync(() -> database.endInvestigation(staffer), executor);
    }

    public void shutdown() {
        executor.shutdownNow();
    }
}
