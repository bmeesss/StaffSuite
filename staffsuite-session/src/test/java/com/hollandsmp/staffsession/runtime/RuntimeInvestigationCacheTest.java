package com.hollandsmp.staffsession.runtime;

import com.hollandsmp.staffsessionapi.model.Investigation;
import com.hollandsmp.staffsessionapi.model.InvestigationStatus;
import com.hollandsmp.staffsessionapi.model.InvestigationType;
import org.junit.Assert;
import org.junit.Test;

import java.util.UUID;

public class RuntimeInvestigationCacheTest {
    @Test
    public void activeInvestigationEntersAndLeavesCache() {
        RuntimeInvestigationCache cache = new RuntimeInvestigationCache();
        Investigation active = new Investigation(UUID.randomUUID().toString(), UUID.randomUUID(), UUID.randomUUID(),
            InvestigationType.PLAYER, InvestigationStatus.ACTIVE, "r", System.currentTimeMillis(), null, "world", 0.0, 0.0, 0.0, 1.0, 1.0, 1.0);
        cache.install(active);
        Assert.assertNotNull(cache.getByStaffer(active.getStaffer()));
        Assert.assertNotNull(cache.getByTarget(active.getTarget()));
        cache.removeByStaffer(active.getStaffer());
        Assert.assertNull(cache.getByStaffer(active.getStaffer()));
        Assert.assertNull(cache.getByTarget(active.getTarget()));
    }

    @Test
    public void clearRemovesAllEntries() {
        RuntimeInvestigationCache cache = new RuntimeInvestigationCache();
        Investigation active = new Investigation(UUID.randomUUID().toString(), UUID.randomUUID(), UUID.randomUUID(),
            InvestigationType.PLAYER, InvestigationStatus.ACTIVE, "r", System.currentTimeMillis(), null, "world", 0.0, 0.0, 0.0, 1.0, 1.0, 1.0);
        cache.install(active);
        cache.clear();
        Assert.assertNull(cache.getByStaffer(active.getStaffer()));
        Assert.assertNull(cache.getByTarget(active.getTarget()));
    }
}
