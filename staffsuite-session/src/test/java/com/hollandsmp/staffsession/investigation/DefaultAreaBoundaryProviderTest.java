package com.hollandsmp.staffsession.investigation;

import com.hollandsmp.staffsessionapi.model.Investigation;
import com.hollandsmp.staffsessionapi.model.InvestigationStatus;
import com.hollandsmp.staffsessionapi.model.InvestigationType;
import org.bukkit.Location;
import org.bukkit.World;
import org.junit.Assert;
import org.junit.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.UUID;

public class DefaultAreaBoundaryProviderTest {
    @Test
    public void createsDeterministicTemporaryBounds() {
        DefaultAreaBoundaryProvider provider = new DefaultAreaBoundaryProvider();
        Investigation base = new Investigation(UUID.randomUUID().toString(), UUID.randomUUID(), null, InvestigationType.AREA,
            InvestigationStatus.ACTIVE, null, System.currentTimeMillis(), null);
        Location anchor = new Location(worldProxy("spawn"), 10.0D, 64.0D, -4.0D);

        Investigation boundary = provider.createBoundarySnapshot(base, anchor);

        Assert.assertEquals("spawn", boundary.getWorldName());
        Assert.assertEquals(-14.0D, boundary.getMinX(), 0.0001D);
        Assert.assertEquals(40.0D, boundary.getMinY(), 0.0001D);
        Assert.assertEquals(-28.0D, boundary.getMinZ(), 0.0001D);
        Assert.assertEquals(34.0D, boundary.getMaxX(), 0.0001D);
        Assert.assertEquals(88.0D, boundary.getMaxY(), 0.0001D);
        Assert.assertEquals(20.0D, boundary.getMaxZ(), 0.0001D);
    }

    private World worldProxy(final String name) {
        return (World) Proxy.newProxyInstance(
            World.class.getClassLoader(),
            new Class[]{World.class},
            new InvocationHandler() {
                @Override
                public Object invoke(Object proxy, Method method, Object[] args) {
                    if ("getName".equals(method.getName())) {
                        return name;
                    }
                    Class<?> returnType = method.getReturnType();
                    if (returnType.equals(boolean.class)) {
                        return false;
                    }
                    if (returnType.equals(int.class) || returnType.equals(short.class) || returnType.equals(byte.class)) {
                        return 0;
                    }
                    if (returnType.equals(long.class)) {
                        return 0L;
                    }
                    if (returnType.equals(float.class)) {
                        return 0f;
                    }
                    if (returnType.equals(double.class)) {
                        return 0d;
                    }
                    return null;
                }
            }
        );
    }
}
