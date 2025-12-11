package com.spacewaltz.decrees.council;

import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Bridges council seats to "portfolios" (economy admin, guilds admin, etc.).
 *
 * Concept:
 *  - Each portfolio is assigned to one seat ID in council.json.
 *  - Whoever holds that seat automatically has the corresponding powers.
 *  - Server owners can still grant / override via Fabric Permissions or op.
 */
public final class CouncilPortfolios {

    private CouncilPortfolios() {
    }

    public enum Portfolio {
        ECONOMY_ADMIN,
        GUILDS_ADMIN
        // Add more later if you want: WAR_ADMIN, LAW_ADMIN, etc.
    }

    /**
     * For use in command .requires(...).
     *
     * Rules:
     *  - If the source is the player who holds the configured seat for that
     *    portfolio -> true.
     *  - Otherwise fall back to Fabric Permissions / vanilla op.
     */
    public static boolean hasPortfolio(ServerCommandSource src, Portfolio portfolio) {
        if (src == null) {
            return false;
        }

        // Console / command blocks: permission/op only.
        if (!(src.getEntity() instanceof ServerPlayerEntity player)) {
            return CouncilPermissions.has(src, permissionKey(portfolio), 3);
        }

        // Player: find which seat they currently hold.
        SeatDefinition seat = CouncilUtil.getSeatFor(player);
        if (seat != null && seat.id != null) {
            String requiredSeatId = seatIdForPortfolio(CouncilConfig.get(), portfolio);
            if (requiredSeatId != null && !requiredSeatId.isBlank()
                    && seat.id.equalsIgnoreCase(requiredSeatId)) {
                return true;
            }
        }

        // Fallback: allow via Fabric Permissions / op if configured.
        return CouncilPermissions.has(src, permissionKey(portfolio), 3);
    }

    /**
     * Optional helper if you want a friendly, in-lore error message
     * inside subcommand handlers.
     */
    public static boolean ensurePortfolio(ServerCommandSource src, Portfolio portfolio, String action) {
        if (hasPortfolio(src, portfolio)) {
            return true;
        }

        CouncilConfigData cfg = CouncilConfig.get();
        String seatId = seatIdForPortfolio(cfg, portfolio);

        if (seatId == null || seatId.isBlank()) {
            Messenger.error(src,
                    "This action requires a council portfolio, but no seat is configured for it. " +
                            "Ask an admin to set the appropriate seat in council.json.");
            return false;
        }

        SeatDefinition seat = CouncilConfig.findSeat(seatId);
        String seatName;
        if (seat != null && seat.displayName != null && !seat.displayName.isBlank()) {
            seatName = seat.displayName;
        } else {
            seatName = "the seat \"" + seatId + "\"";
        }

        Messenger.error(src, "Only " + seatName + " may " + action + ".");
        return false;
    }

    // --- internal helpers ---

    private static String seatIdForPortfolio(CouncilConfigData cfg, Portfolio portfolio) {
        if (cfg == null) return null;

        return switch (portfolio) {
            case ECONOMY_ADMIN -> cfg.economyAdminSeatId;
            case GUILDS_ADMIN  -> cfg.guildsAdminSeatId;
        };
    }

    private static String permissionKey(Portfolio portfolio) {
        return switch (portfolio) {
            case ECONOMY_ADMIN -> "decrees.economy.admin";
            case GUILDS_ADMIN  -> "decrees.guilds.admin";
        };
    }
}
