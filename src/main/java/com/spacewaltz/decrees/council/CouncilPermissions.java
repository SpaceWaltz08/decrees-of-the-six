package com.spacewaltz.decrees.council;

import net.minecraft.server.command.ServerCommandSource;

import java.lang.reflect.Method;

/**
 * Tiny wrapper around Fabric Permissions API (if present),
 * with a safe fallback to vanilla op levels.
 */
public final class CouncilPermissions {

    private static final boolean FABRIC_PERMISSIONS_PRESENT;
    private static final Method FABRIC_CHECK_METHOD;

    static {
        boolean present = false;
        Method method = null;

        try {
            // We only touch this class via reflection, so the mod is optional.
            Class<?> clazz = Class.forName("me.lucko.fabric.api.permissions.v0.Permissions");
            method = clazz.getMethod("check", ServerCommandSource.class, String.class, int.class);
            present = true;
        } catch (Throwable t) {
            present = false;
            method = null;
        }

        FABRIC_PERMISSIONS_PRESENT = present;
        FABRIC_CHECK_METHOD = method;
    }

    private CouncilPermissions() {
    }

    /**
     * @param src           command source
     * @param permissionKey e.g. "decrees.admin"
     * @param defaultLevel  fallback vanilla op level (0-4)
     */
    public static boolean has(ServerCommandSource src, String permissionKey, int defaultLevel) {
        if (FABRIC_PERMISSIONS_PRESENT && FABRIC_CHECK_METHOD != null) {
            try {
                Object res = FABRIC_CHECK_METHOD.invoke(null, src, permissionKey, defaultLevel);
                if (res instanceof Boolean b) {
                    return b;
                }
            } catch (Throwable ignored) {
                // fall through to op level
            }
        }

        // Fallback: normal vanilla op level
        return src.hasPermissionLevel(defaultLevel);
    }
}
