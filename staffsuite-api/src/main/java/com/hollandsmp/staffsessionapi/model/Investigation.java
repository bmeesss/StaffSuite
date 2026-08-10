package com.hollandsmp.staffsessionapi.model;

import java.util.UUID;

public final class Investigation {
    private final String investigationId;
    private final UUID staffer;
    private final UUID target;
    private final InvestigationType type;
    private final InvestigationStatus status;
    private final String sourceReportId;
    private final long startedAt;
    private final Long endedAt;

    public Investigation(String investigationId, UUID staffer, UUID target, InvestigationType type,
                         InvestigationStatus status, String sourceReportId, long startedAt, Long endedAt) {
        this.investigationId = investigationId;
        this.staffer = staffer;
        this.target = target;
        this.type = type;
        this.status = status;
        this.sourceReportId = sourceReportId;
        this.startedAt = startedAt;
        this.endedAt = endedAt;
    }

    public String getInvestigationId() { return investigationId; }
    public UUID getStaffer() { return staffer; }
    public UUID getTarget() { return target; }
    public InvestigationType getType() { return type; }
    public InvestigationStatus getStatus() { return status; }
    public String getSourceReportId() { return sourceReportId; }
    public long getStartedAt() { return startedAt; }
    public Long getEndedAt() { return endedAt; }
}
