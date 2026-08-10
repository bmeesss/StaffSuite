package com.hollandsmp.staffsession.investigation;

import com.hollandsmp.staffsession.runtime.RuntimeInvestigation;
import com.hollandsmp.staffsession.runtime.RuntimeInvestigationCache;
import com.hollandsmp.staffsessionapi.model.InvestigationType;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;

public final class LeashSystem implements Listener {
    private final JavaPlugin plugin;
    private final RuntimeInvestigationCache runtimeCache;
    private final TeleportAuthorization teleportAuthorization;

    public LeashSystem(JavaPlugin plugin, RuntimeInvestigationCache runtimeCache, TeleportAuthorization teleportAuthorization) {
        this.plugin = plugin;
        this.runtimeCache = runtimeCache;
        this.teleportAuthorization = teleportAuthorization;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (sameBlock(event.getFrom(), event.getTo())) {
            return;
        }
        enforce(event.getPlayer(), event.getFrom(), event.getTo(), event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        RuntimeInvestigation active = runtimeCache.getByStaffer(event.getPlayer().getUniqueId());
        String investigationId = active == null ? null : active.getInvestigationId();
        if (teleportAuthorization.isTeleportAuthorized(event.getPlayer().getUniqueId(), investigationId, event.getTo())) {
            teleportAuthorization.consumeTeleportAuthorization(event.getPlayer().getUniqueId());
            return;
        }
        enforce(event.getPlayer(), event.getFrom(), event.getTo(), event);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        teleportAuthorization.revokeTeleportAuthorization(event.getPlayer().getUniqueId());
        runtimeCache.removeByTarget(event.getPlayer().getUniqueId());
        runtimeCache.removeByStaffer(event.getPlayer().getUniqueId());
    }

    public void shutdown() {
        runtimeCache.clear();
        teleportAuthorization.shutdown();
    }

    private void enforce(Player player, Location from, Location to, org.bukkit.event.Cancellable cancellable) {
        UUID playerId = player.getUniqueId();
        RuntimeInvestigation active = runtimeCache.getByTarget(playerId);
        if (active == null) {
            return;
        }
        if (active.getType() == InvestigationType.PLAYER && to != null && !insideBounds(active, to)) {
            cancellable.setCancelled(true);
                teleportAuthorization.revokeTeleportAuthorization(playerId);
            Bukkit.getScheduler().runTask(plugin, new Runnable() {
                @Override
                public void run() {
                    Location origin = boundaryCenter(active);
                    if (origin != null && player.isOnline()) {
                        player.teleport(origin);
                    }
                }
            });
        }
    }

    private boolean insideBounds(RuntimeInvestigation investigation, Location location) {
        if (location == null || location.getWorld() == null || investigation.getWorldName() == null) {
            return true;
        }
        if (!location.getWorld().getName().equals(investigation.getWorldName())) {
            return false;
        }
        double x = location.getX();
        double y = location.getY();
        double z = location.getZ();
        return x >= investigation.getMinX() && x <= investigation.getMaxX()
            && y >= investigation.getMinY() && y <= investigation.getMaxY()
            && z >= investigation.getMinZ() && z <= investigation.getMaxZ();
    }

    private Location boundaryCenter(RuntimeInvestigation investigation) {
        if (investigation.getWorldName() == null) {
            return null;
        }
        org.bukkit.World world = Bukkit.getWorld(investigation.getWorldName());
        if (world == null) {
            return null;
        }
        double x = (investigation.getMinX() + investigation.getMaxX()) / 2.0D;
        double y = (investigation.getMinY() + investigation.getMaxY()) / 2.0D;
        double z = (investigation.getMinZ() + investigation.getMaxZ()) / 2.0D;
        return new Location(world, x, y, z);
    }

    private boolean sameBlock(Location from, Location to) {
        if (from == null || to == null) {
            return false;
        }
        return from.getBlockX() == to.getBlockX() && from.getBlockY() == to.getBlockY() && from.getBlockZ() == to.getBlockZ();
    }
}
