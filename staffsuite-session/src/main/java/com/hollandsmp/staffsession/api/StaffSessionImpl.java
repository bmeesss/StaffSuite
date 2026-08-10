package com.hollandsmp.staffsession.api;

import com.hollandsmp.staffsession.core.PolicyEngine;
import com.hollandsmp.staffsession.core.SessionController;
import com.hollandsmp.staffsession.db.StaffSessionDatabase;
import com.hollandsmp.staffsession.integrity.CrashRecovery;
import com.hollandsmp.staffsession.investigation.AreaBoundaryProvider;
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
import org.bukkit.Location;
import org.bukkit.entity.Player;
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
    private final AreaBoundaryProvider areaBoundaryProvider;
    private volatile boolean available;

    private StaffSessionImpl(JavaPlugin plugin) {
        this.plugin = plugin;
        this.database = new StaffSessionDatabase(plugin);
        this.policyEngine = new PolicyEngine();
        this.runtimeCache = new RuntimeInvestigationCache();
        this.sessionController = new SessionController(database, policyEngine);
        this.teleportAuthorization = new TeleportAuthorization(plugin, runtimeCache);
        this.leashSystem = new LeashSystem(plugin, runtimeCache, teleportAuthorization);
        this.areaBoundaryProvider = new DefaultAreaBoundaryProvider();
        this.playerInvestigation = new PlayerInvestigation(plugin, database, runtimeCache, leashSystem, teleportAuthorization);
        this.areaInvestigation = new AreaInvestigation(plugin, runtimeCache, leashSystem, teleportAuthorization, areaBoundaryProvider);
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
            rebuildRuntimeCacheFromDatabase();
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
        return captureRuntimeSnapshot(staffer, target, type).thenCompose(runtimeBounds ->
            sessionController.startInvestigation(staffer, target, type, reportId, runtimeBounds).thenApply(result -> {
                if (result != null && result.isSuccessful() && result.getInvestigation() != null) {
                    final Investigation investigation = result.getInvestigation();
                    Bukkit.getScheduler().runTask(plugin, new Runnable() {
                        @Override
                        public void run() {
                            runtimeCache.install(investigation);
                            if (investigation.getTarget() != null) {
                                teleportAuthorization.revokeTeleportAuthorization(investigation.getTarget());
                            }
                        }
                    });
                }
                return result;
            })
        );
    }

    @Override
    public CompletableFuture<InvestigationResult> endInvestigation(UUID staffer) {
        if (!isAvailable()) {
            return CompletableFuture.completedFuture(InvestigationResult.failure(FailureReason.SESSION_UNAVAILABLE));
        }
        if (staffer == null) {
            return CompletableFuture.completedFuture(InvestigationResult.failure(FailureReason.INVALID_STATE));
        }
        return sessionController.endInvestigation(staffer).thenApply(result -> {
            if (result != null && result.isSuccessful() && result.getInvestigation() != null) {
                final Investigation investigation = result.getInvestigation();
                Bukkit.getScheduler().runTask(plugin, new Runnable() {
                    @Override
                    public void run() {
                        runtimeCache.removeByStaffer(investigation.getStaffer());
                        if (investigation.getTarget() != null) {
                            runtimeCache.removeByTarget(investigation.getTarget());
                            teleportAuthorization.revokeTeleportAuthorization(investigation.getTarget());
                        }
                    }
                });
            }
            return result;
        });
    }

    @Override
    public boolean isStafferInSession(UUID staffer) {
        if (!isAvailable() || staffer == null) {
            return false;
        }
        return runtimeCache.getByStaffer(staffer) != null;
    }

    @Override
    public boolean isPlayerBeingInvestigated(UUID target) {
        if (!isAvailable() || target == null) {
            return false;
        }
        return runtimeCache.getByTarget(target) != null;
    }

    @Override
    public Optional<Investigation> getActiveInvestigation(UUID staffer) {
        if (!isAvailable() || staffer == null) {
            return Optional.empty();
        }
        com.hollandsmp.staffsession.runtime.RuntimeInvestigation cached = runtimeCache.getByStaffer(staffer);
        if (cached == null) {
            return Optional.empty();
        }
        return Optional.of(new Investigation(
            cached.getInvestigationId(),
            cached.getStaffer(),
            cached.getTarget(),
            cached.getType(),
            cached.getStatus(),
            null,
            0L,
            null,
            cached.getWorldName(),
            cached.getMinX(),
            cached.getMinY(),
            cached.getMinZ(),
            cached.getMaxX(),
            cached.getMaxY(),
            cached.getMaxZ()
        ));
    }

    public RuntimeInvestigationCache getRuntimeCache() {
        return runtimeCache;
    }

    public TeleportAuthorization getTeleportAuthorization() {
        return teleportAuthorization;
    }

    public AreaBoundaryProvider getAreaBoundaryProvider() {
        return areaBoundaryProvider;
    }

    private CompletableFuture<Investigation> captureRuntimeSnapshot(final UUID staffer, final UUID target, final InvestigationType type) {
        final CompletableFuture<Investigation> future = new CompletableFuture<Investigation>();
        Bukkit.getScheduler().runTask(plugin, new Runnable() {
            @Override
            public void run() {
                Player stafferPlayer = Bukkit.getPlayer(staffer);
                if (stafferPlayer == null) {
                    future.complete(null);
                    return;
                }
                Location anchor;
                if (type == InvestigationType.PLAYER) {
                    Player targetPlayer = target == null ? null : Bukkit.getPlayer(target);
                    if (targetPlayer == null) {
                        future.complete(null);
                        return;
                    }
                    anchor = targetPlayer.getLocation();
                } else {
                    anchor = stafferPlayer.getLocation();
                }
                if (anchor == null || anchor.getWorld() == null) {
                    future.complete(null);
                    return;
                }
                Investigation base = new Investigation(null, staffer, target, type, null, null, 0L, null);
                future.complete(areaBoundaryProvider.createBoundarySnapshot(base, anchor));
            }
        });
        return future;
    }

    private void rebuildRuntimeCacheFromDatabase() {
        for (Investigation investigation : database.loadActiveInvestigations()) {
            runtimeCache.install(investigation);
        }
    }
}
