package com.hollandsmp.staffsession.investigation;

import com.hollandsmp.staffsession.db.StaffSessionDatabase;
import com.hollandsmp.staffsession.runtime.RuntimeInvestigation;
import com.hollandsmp.staffsession.runtime.RuntimeInvestigationCache;
import com.hollandsmp.staffsessionapi.model.Investigation;
import com.hollandsmp.staffsessionapi.model.InvestigationStatus;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.UUID;

public final class PlayerInvestigation implements Listener {
    private final JavaPlugin plugin;
    private final StaffSessionDatabase database;
    private final RuntimeInvestigationCache runtimeCache;
    private final LeashSystem leashSystem;
    private final TeleportAuthorization teleportAuthorization;

    public PlayerInvestigation(JavaPlugin plugin, StaffSessionDatabase database, RuntimeInvestigationCache runtimeCache, LeashSystem leashSystem, TeleportAuthorization teleportAuthorization) {
        this.plugin = plugin;
        this.database = database;
        this.runtimeCache = runtimeCache;
        this.leashSystem = leashSystem;
        this.teleportAuthorization = teleportAuthorization;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        rebuildFromDatabase(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        teleportAuthorization.revokeTeleportAuthorization(playerId);
        runtimeCache.removeByStaffer(playerId);
        runtimeCache.removeByTarget(playerId);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        reconcileFromCache(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        reconcileFromCache(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onTeleport(PlayerTeleportEvent event) {
        reconcileFromCache(event.getPlayer().getUniqueId());
    }

    public void shutdown() {
        runtimeCache.clear();
    }

    private void reconcileFromCache(UUID playerId) {
        RuntimeInvestigation investigation = runtimeCache.getByStaffer(playerId);
        if (investigation == null) {
            investigation = runtimeCache.getByTarget(playerId);
        }
        if (investigation == null) {
            teleportAuthorization.revokeTeleportAuthorization(playerId);
            return;
        }
        if (investigation.getStatus() != com.hollandsmp.staffsessionapi.model.InvestigationStatus.ACTIVE) {
            runtimeCache.removeByStaffer(investigation.getStaffer());
            runtimeCache.removeByTarget(investigation.getTarget());
            teleportAuthorization.revokeTeleportAuthorization(playerId);
        }
    }

    private void rebuildFromDatabase(final UUID playerId) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, new Runnable() {
            @Override
            public void run() {
                final List<Investigation> activeInvestigations = database.loadActiveInvestigations();
                Bukkit.getScheduler().runTask(plugin, new Runnable() {
                    @Override
                    public void run() {
                        runtimeCache.removeByStaffer(playerId);
                        runtimeCache.removeByTarget(playerId);
                        teleportAuthorization.revokeTeleportAuthorization(playerId);
                        for (Investigation investigation : activeInvestigations) {
                            if (investigation.getStatus() == InvestigationStatus.ACTIVE
                                && (playerId.equals(investigation.getStaffer()) || playerId.equals(investigation.getTarget()))) {
                                runtimeCache.install(investigation);
                            }
                        }
                    }
                });
            }
        });
    }
}
