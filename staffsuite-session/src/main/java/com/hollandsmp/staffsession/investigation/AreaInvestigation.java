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
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;

public final class AreaInvestigation implements Listener {
    private final JavaPlugin plugin;
    private final RuntimeInvestigationCache runtimeCache;
    private final LeashSystem leashSystem;
    private final TeleportAuthorization teleportAuthorization;

    public AreaInvestigation(JavaPlugin plugin, RuntimeInvestigationCache runtimeCache, LeashSystem leashSystem, TeleportAuthorization teleportAuthorization) {
        this.plugin = plugin;
        this.runtimeCache = runtimeCache;
        this.leashSystem = leashSystem;
        this.teleportAuthorization = teleportAuthorization;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        RuntimeInvestigation active = runtimeCache.getByStaffer(player.getUniqueId());
        if (active == null || active.getWorldName() == null) {
            return;
        }
        Location to = event.getTo();
        if (to == null || to.getWorld() == null) {
            return;
        }
        if (!to.getWorld().getName().equals(active.getWorldName()) || !insideBounds(active, to)) {
            event.setCancelled(true);
            final Location center = center(active);
            if (center != null) {
                Bukkit.getScheduler().runTask(plugin, new Runnable() {
                    @Override
                    public void run() {
                        if (player.isOnline()) {
                            player.teleport(center);
                        }
                    }
                });
            }
        }
    }

    public void shutdown() {
        runtimeCache.clear();
    }

    private boolean insideBounds(RuntimeInvestigation investigation, Location location) {
        return location.getX() >= investigation.getMinX() && location.getX() <= investigation.getMaxX()
            && location.getY() >= investigation.getMinY() && location.getY() <= investigation.getMaxY()
            && location.getZ() >= investigation.getMinZ() && location.getZ() <= investigation.getMaxZ();
    }

    private Location center(RuntimeInvestigation investigation) {
        org.bukkit.World world = Bukkit.getWorld(investigation.getWorldName());
        if (world == null) {
            return null;
        }
        return new Location(world,
            (investigation.getMinX() + investigation.getMaxX()) / 2.0D,
            (investigation.getMinY() + investigation.getMaxY()) / 2.0D,
            (investigation.getMinZ() + investigation.getMaxZ()) / 2.0D);
    }
}
