package com.hollandsmp.staffsessionapi.model;

public final class InvestigationResult {
    private final boolean successful;
    private final FailureReason failureReason;
    private final Investigation investigation;

    private InvestigationResult(boolean successful, FailureReason failureReason, Investigation investigation) {
        this.successful = successful;
        this.failureReason = failureReason;
        this.investigation = investigation;
    }

    public static InvestigationResult success(Investigation investigation) {
        return new InvestigationResult(true, null, investigation);
    }

    public static InvestigationResult failure(FailureReason failureReason) {
        return new InvestigationResult(false, failureReason, null);
    }

    public boolean isSuccessful() { return successful; }
    public FailureReason getFailureReason() { return failureReason; }
    public Investigation getInvestigation() { return investigation; }
}
