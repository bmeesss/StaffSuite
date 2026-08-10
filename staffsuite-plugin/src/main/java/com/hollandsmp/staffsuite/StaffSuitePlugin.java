package com.hollandsmp.staffsuite;

import org.bukkit.plugin.java.JavaPlugin;

public final class StaffSuitePlugin extends JavaPlugin {
    @Override
    public void onEnable() {
        getLogger().info("StaffSuite Phase 1 bootstrap enabled.");
    }

    @Override
    public void onDisable() {
        getLogger().info("StaffSuite Phase 1 bootstrap disabled.");
    }
}
