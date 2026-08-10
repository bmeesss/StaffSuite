package com.hollandsmp.staffsession.investigation;

import com.hollandsmp.staffsession.runtime.RuntimeInvestigationCache;
import org.bukkit.Location;
import org.bukkit.World;
import org.junit.Assert;
import org.junit.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.UUID;

public class TeleportAuthorizationTest {
    @Test
    public void authorizationIsScopedAndConsumed() {
        RuntimeInvestigationCache cache = new RuntimeInvestigationCache();
        TeleportAuthorization authorization = new TeleportAuthorization(null, cache);
        UUID playerId = UUID.randomUUID();
        UUID otherPlayerId = UUID.randomUUID();
        World world = worldProxy("world");
        Location destination = new Location(world, 0.0, 64.0, 0.0);

        authorization.authorizeTeleport(playerId, "inv-1", destination, System.currentTimeMillis() + 5000L);
        Assert.assertTrue(authorization.isTeleportAuthorized(playerId, "inv-1", destination));
        Assert.assertFalse(authorization.isTeleportAuthorized(otherPlayerId, "inv-1", destination));
        Assert.assertFalse(authorization.isTeleportAuthorized(playerId, "inv-2", destination));
        authorization.consumeTeleportAuthorization(playerId);
        Assert.assertFalse(authorization.isTeleportAuthorized(playerId, "inv-1", destination));
    }

    @Test
    public void revokeClearsAuthorization() {
        RuntimeInvestigationCache cache = new RuntimeInvestigationCache();
        TeleportAuthorization authorization = new TeleportAuthorization(null, cache);
        UUID playerId = UUID.randomUUID();
        Location destination = new Location(worldProxy("world"), 0.0, 64.0, 0.0);
        authorization.authorizeTeleport(playerId, "inv-1", destination, System.currentTimeMillis() + 5000L);
        authorization.revokeTeleportAuthorization(playerId);
        Assert.assertFalse(authorization.isTeleportAuthorized(playerId, "inv-1", destination));
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
