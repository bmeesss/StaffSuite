package com.hollandsmp.staffsuite;

import com.hollandsmp.staffsession.api.StaffSessionImpl;
import org.bukkit.plugin.java.JavaPlugin;

public final class StaffSuitePlugin extends JavaPlugin {
    private StaffSessionImpl staffSession;

    @Override
    public void onEnable() {
        this.staffSession = StaffSessionImpl.create(this);
        getLogger().info("StaffSuite enabled.");
    }

    @Override
    public void onDisable() {
        if (this.staffSession != null) {
            this.staffSession.shutdown();
            this.staffSession = null;
        }
        getLogger().info("StaffSuite disabled.");
    }

    public StaffSessionImpl getStaffSession() {
        return staffSession;
    }
}
