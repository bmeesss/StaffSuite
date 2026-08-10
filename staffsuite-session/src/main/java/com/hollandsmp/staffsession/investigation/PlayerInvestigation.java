package com.hollandsmp.staffsession.investigation;

import com.hollandsmp.staffsession.runtime.RuntimeInvestigation;
import com.hollandsmp.staffsession.runtime.RuntimeInvestigationCache;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;

public final class PlayerInvestigation implements Listener {
    private final JavaPlugin plugin;
    private final RuntimeInvestigationCache runtimeCache;
    private final LeashSystem leashSystem;
    private final TeleportAuthorization teleportAuthorization;

    public PlayerInvestigation(JavaPlugin plugin, RuntimeInvestigationCache runtimeCache, LeashSystem leashSystem, TeleportAuthorization teleportAuthorization) {
        this.plugin = plugin;
        this.runtimeCache = runtimeCache;
        this.leashSystem = leashSystem;
        this.teleportAuthorization = teleportAuthorization;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        reconcile(event.getPlayer().getUniqueId());
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
        reconcile(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        reconcile(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onTeleport(PlayerTeleportEvent event) {
        reconcile(event.getPlayer().getUniqueId());
    }

    public void shutdown() {
        runtimeCache.clear();
    }

    private void reconcile(UUID playerId) {
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
}
