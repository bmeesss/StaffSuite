package com.hollandsmp.staffsession.investigation;

import com.hollandsmp.staffsession.runtime.RuntimeInvestigationCache;
import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class TeleportAuthorization implements Listener {
    private final JavaPlugin plugin;
    private final RuntimeInvestigationCache runtimeCache;
    private final Map<UUID, Authorization> authorizations = new ConcurrentHashMap<UUID, Authorization>();

    public TeleportAuthorization(JavaPlugin plugin, RuntimeInvestigationCache runtimeCache) {
        this.plugin = plugin;
        this.runtimeCache = runtimeCache;
    }

    public void authorizeTeleport(UUID playerId, String investigationId, Location destination, long expiresAt) {
        authorizations.put(playerId, new Authorization(investigationId, destination, expiresAt));
    }

    public boolean isTeleportAuthorized(UUID playerId, String investigationId, Location destination) {
        Authorization authorization = authorizations.get(playerId);
        return authorization != null && authorization.isValid(investigationId, destination);
    }

    public void consumeTeleportAuthorization(UUID playerId) {
        authorizations.remove(playerId);
    }

    public void revokeTeleportAuthorization(UUID playerId) {
        authorizations.remove(playerId);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        String investigationId = null;
        if (runtimeCache.getByStaffer(playerId) != null) {
            investigationId = runtimeCache.getByStaffer(playerId).getInvestigationId();
        } else if (runtimeCache.getByTarget(playerId) != null) {
            investigationId = runtimeCache.getByTarget(playerId).getInvestigationId();
        }
        Authorization authorization = authorizations.get(playerId);
        if (authorization != null && authorization.isValid(investigationId, event.getTo())) {
            return;
        }
        if (investigationId != null) {
            event.setCancelled(true);
        }
    }

    public void shutdown() {
        authorizations.clear();
    }

    private static final class Authorization {
        private final String investigationId;
        private final Location destination;
        private final long expiresAt;

        private Authorization(String investigationId, Location destination, long expiresAt) {
            this.investigationId = investigationId;
            this.destination = destination == null ? null : destination.clone();
            this.expiresAt = expiresAt;
        }

        private boolean isValid(String investigationId, Location location) {
            if (location == null || destination == null || location.getWorld() == null || destination.getWorld() == null) {
                return false;
            }
            return System.currentTimeMillis() <= expiresAt
                && this.investigationId != null
                && this.investigationId.equals(investigationId)
                && location.getWorld().getName().equals(destination.getWorld().getName())
                && location.distanceSquared(destination) <= 0.25D;
        }
    }
}
