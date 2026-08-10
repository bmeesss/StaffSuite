package com.hollandsmp.staffsession.api;

import com.hollandsmp.staffsession.core.PolicyEngine;
import com.hollandsmp.staffsession.core.SessionController;
import com.hollandsmp.staffsession.db.StaffSessionDatabase;
import com.hollandsmp.staffsession.integrity.CrashRecovery;
import com.hollandsmp.staffsession.investigation.AreaInvestigation;
import com.hollandsmp.staffsession.investigation.DefaultAreaBoundaryProvider;
import com.hollandsmp.staffsession.investigation.LeashSystem;
import com.hollandsmp.staffsession.investigation.PlayerInvestigation;
import com.hollandsmp.staffsession.investigation.TeleportAuthorization;
import com.hollandsmp.staffsession.runtime.RuntimeInvestigationCache;
import com.hollandsmp.staffsessionapi.StaffSessionAPI;
import com.hollandsmp.staffsessionapi.model.FailureReason;
import com.hollandsmp.staffsessionapi.model.Investigation;
import com.hollandsmp.staffsessionapi.model.InvestigationResult;
import com.hollandsmp.staffsessionapi.model.InvestigationType;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class StaffSessionImpl implements StaffSessionAPI {
    private final JavaPlugin plugin;
    private final StaffSessionDatabase database;
    private final SessionController sessionController;
    private final PolicyEngine policyEngine;
    private final RuntimeInvestigationCache runtimeCache;
    private final LeashSystem leashSystem;
    private final TeleportAuthorization teleportAuthorization;
    private final PlayerInvestigation playerInvestigation;
    private final AreaInvestigation areaInvestigation;
    private final DefaultAreaBoundaryProvider areaBoundaryProvider;
    private volatile boolean available;

    private StaffSessionImpl(JavaPlugin plugin) {
        this.plugin = plugin;
        this.database = new StaffSessionDatabase(plugin);
        this.policyEngine = new PolicyEngine();
        this.runtimeCache = new RuntimeInvestigationCache();
        this.sessionController = new SessionController(database, policyEngine);
        this.teleportAuthorization = new TeleportAuthorization(plugin, runtimeCache);
        this.leashSystem = new LeashSystem(plugin, runtimeCache, teleportAuthorization);
        this.playerInvestigation = new PlayerInvestigation(plugin, runtimeCache, leashSystem, teleportAuthorization);
        this.areaBoundaryProvider = new DefaultAreaBoundaryProvider();
        this.areaInvestigation = new AreaInvestigation(plugin, runtimeCache, leashSystem, teleportAuthorization);
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
            runtimeCache.clear();
            Bukkit.getPluginManager().registerEvents(teleportAuthorization, plugin);
            Bukkit.getPluginManager().registerEvents(leashSystem, plugin);
            Bukkit.getPluginManager().registerEvents(playerInvestigation, plugin);
            Bukkit.getPluginManager().registerEvents(areaInvestigation, plugin);
            available = true;
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to initialize StaffSession: " + e.getMessage());
            available = false;
        }
    }

    public void shutdown() {
        available = false;
        org.bukkit.event.HandlerList.unregisterAll(teleportAuthorization);
        org.bukkit.event.HandlerList.unregisterAll(leashSystem);
        org.bukkit.event.HandlerList.unregisterAll(playerInvestigation);
        org.bukkit.event.HandlerList.unregisterAll(areaInvestigation);
        sessionController.shutdown();
        teleportAuthorization.shutdown();
        leashSystem.shutdown();
        playerInvestigation.shutdown();
        areaInvestigation.shutdown();
        runtimeCache.clear();
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
        if (staffer == null || type == null) {
            return CompletableFuture.completedFuture(InvestigationResult.failure(FailureReason.INVALID_STATE));
        }
        return sessionController.startInvestigation(staffer, target, type, reportId, null).whenComplete((result, throwable) -> {
            if (throwable == null && result != null && result.isSuccessful() && result.getInvestigation() != null) {
                Bukkit.getScheduler().runTask(plugin, new Runnable() {
                    @Override
                    public void run() {
                        runtimeCache.install(result.getInvestigation());
                    }
                });
            }
        });
    }

    @Override
    public CompletableFuture<InvestigationResult> endInvestigation(UUID staffer) {
        if (!isAvailable()) {
            return CompletableFuture.completedFuture(InvestigationResult.failure(FailureReason.SESSION_UNAVAILABLE));
        }
        if (staffer == null) {
            return CompletableFuture.completedFuture(InvestigationResult.failure(FailureReason.INVALID_STATE));
        }
        return sessionController.endInvestigation(staffer);
    }

    @Override
    public boolean isStafferInSession(UUID staffer) {
        return isAvailable() && staffer != null && database.isStafferInSession(staffer);
    }

    @Override
    public boolean isPlayerBeingInvestigated(UUID target) {
        return isAvailable() && target != null && database.isPlayerBeingInvestigated(target);
    }

    @Override
    public Optional<Investigation> getActiveInvestigation(UUID staffer) {
        if (!isAvailable()) {
            return Optional.empty();
        }
        if (staffer == null) {
            return Optional.empty();
        }
        return database.getActiveInvestigation(staffer);
    }
}
