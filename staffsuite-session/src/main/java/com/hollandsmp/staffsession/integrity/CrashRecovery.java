package com.hollandsmp.staffsession.integrity;

import com.hollandsmp.staffsession.db.StaffSessionDatabase;

public final class CrashRecovery {
    private final StaffSessionDatabase database;

    public CrashRecovery(StaffSessionDatabase database) {
        this.database = database;
    }

    public void recoverStaleInvestigations() {
        database.recoverActiveInvestigations();
    }
}
