package com.hollandsmp.staffsession.core;

import java.util.UUID;

public final class TransactionContext {
    private final String transactionId;
    private final UUID playerUuid;
    private final String sessionId;
    private final String transactionType;
    private final long startedAt;
    private final long expiresAt;
    private final String expectedTransitions;
    private final String currentExpectedTransition;
    private final String status;

    public TransactionContext(String transactionId, UUID playerUuid, String sessionId, String transactionType, long startedAt, long expiresAt, String expectedTransitions, String currentExpectedTransition, String status) {
        this.transactionId = transactionId;
        this.playerUuid = playerUuid;
        this.sessionId = sessionId;
        this.transactionType = transactionType;
        this.startedAt = startedAt;
        this.expiresAt = expiresAt;
        this.expectedTransitions = expectedTransitions;
        this.currentExpectedTransition = currentExpectedTransition;
        this.status = status;
    }
}
