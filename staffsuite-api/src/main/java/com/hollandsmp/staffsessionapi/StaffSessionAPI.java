package com.hollandsmp.staffsessionapi;

import com.hollandsmp.staffsessionapi.model.Investigation;
import com.hollandsmp.staffsessionapi.model.InvestigationResult;
import com.hollandsmp.staffsessionapi.model.InvestigationType;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface StaffSessionAPI {

    boolean isAvailable();

    CompletableFuture<InvestigationResult> startInvestigation(
        UUID staffer,
        UUID target,
        InvestigationType type,
        String reportId
    );

    CompletableFuture<InvestigationResult> endInvestigation(UUID staffer);

    boolean isStafferInSession(UUID staffer);

    boolean isPlayerBeingInvestigated(UUID target);

    Optional<Investigation> getActiveInvestigation(UUID staffer);
}
