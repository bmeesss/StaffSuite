package com.hollandsmp.staffsessionapi.model;

public final class InvestigationResult {
    private final boolean successful;

    public InvestigationResult(boolean successful) {
        this.successful = successful;
    }

    public boolean isSuccessful() {
        return successful;
    }
}
