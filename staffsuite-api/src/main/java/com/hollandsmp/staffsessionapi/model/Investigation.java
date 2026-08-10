package com.hollandsmp.staffsessionapi.model;

public interface Investigation {
    String getId();

    InvestigationType getType();

    InvestigationStatus getStatus();
}
