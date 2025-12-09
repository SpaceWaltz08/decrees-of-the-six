package com.spacewaltz.decrees.council;

import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;

public class CouncilUtil {

    public static SeatDefinition getSeatFor(ServerPlayerEntity player) {
        return CouncilConfig.findSeatByHolder(player.getUuid());
    }

    /**
     * For use in command .requires(...) – true only for players that hold a seat.
     */
    public static boolean isCouncilPlayer(ServerCommandSource src) {
        if (src.getEntity() instanceof ServerPlayerEntity player) {
            return getSeatFor(player) != null;
        }
        return false;
    }
}
