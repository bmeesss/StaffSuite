package com.hollandsmp.staffsession.api;

import com.hollandsmp.staffsession.core.PolicyEngine;
import com.hollandsmp.staffsession.core.SessionController;
import com.hollandsmp.staffsession.db.StaffSessionDatabase;
import com.hollandsmp.staffsession.integrity.CrashRecovery;
import com.hollandsmp.staffsessionapi.StaffSessionAPI;
import com.hollandsmp.staffsessionapi.model.FailureReason;
import com.hollandsmp.staffsessionapi.model.Investigation;
import com.hollandsmp.staffsessionapi.model.InvestigationResult;
import com.hollandsmp.staffsessionapi.model.InvestigationType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class StaffSessionImpl implements StaffSessionAPI {
    private final JavaPlugin plugin;
    private final StaffSessionDatabase database;
    private final SessionController sessionController;
    private final PolicyEngine policyEngine;
    private volatile boolean available;

    private StaffSessionImpl(JavaPlugin plugin) {
        this.plugin = plugin;
        this.database = new StaffSessionDatabase(plugin);
        this.policyEngine = new PolicyEngine();
        this.sessionController = new SessionController(database, policyEngine);
    }

    public static StaffSessionImpl create(JavaPlugin plugin) {
        StaffSessionImpl impl = new StaffSessionImpl(plugin);
        impl.initialize();
        return impl;
    }

    private void initialize() {
        try {
            database.initialize();
            new CrashRecovery(database).recoverStaleInvestigations();
            available = true;
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to initialize StaffSession: " + e.getMessage());
            available = false;
        }
    }

    public void shutdown() {
        available = false;
        sessionController.shutdown();
        database.close();
    }

    @Override
    public boolean isAvailable() {
        return available && database.isAvailable();
    }

    @Override
    public CompletableFuture<InvestigationResult> startInvestigation(UUID staffer, UUID target, InvestigationType type, String reportId) {
        if (!isAvailable()) {
            return CompletableFuture.completedFuture(InvestigationResult.failure(FailureReason.SESSION_UNAVAILABLE));
        }
        return sessionController.startInvestigation(staffer, target, type, reportId);
    }

    @Override
    public CompletableFuture<InvestigationResult> endInvestigation(UUID staffer) {
        if (!isAvailable()) {
            return CompletableFuture.completedFuture(InvestigationResult.failure(FailureReason.SESSION_UNAVAILABLE));
        }
        return sessionController.endInvestigation(staffer);
    }

    @Override
    public boolean isStafferInSession(UUID staffer) {
        return isAvailable() && database.isStafferInSession(staffer);
    }

    @Override
    public boolean isPlayerBeingInvestigated(UUID target) {
        return isAvailable() && database.isPlayerBeingInvestigated(target);
    }

    @Override
    public Optional<Investigation> getActiveInvestigation(UUID staffer) {
        if (!isAvailable()) {
            return Optional.empty();
        }
        return database.getActiveInvestigation(staffer);
    }
}
