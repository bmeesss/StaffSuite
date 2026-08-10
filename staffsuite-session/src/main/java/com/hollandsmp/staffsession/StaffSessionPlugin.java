package com.hollandsmp.staffsession;

import com.hollandsmp.staffsession.api.StaffSessionImpl;
import org.bukkit.plugin.java.JavaPlugin;

public final class StaffSessionPlugin extends JavaPlugin {
    private StaffSessionImpl staffSession;

    @Override
    public void onEnable() {
        this.staffSession = StaffSessionImpl.create(this);
    }

    @Override
    public void onDisable() {
        if (this.staffSession != null) {
            this.staffSession.shutdown();
        }
    }

    public StaffSessionImpl getStaffSession() {
        return staffSession;
    }
}
