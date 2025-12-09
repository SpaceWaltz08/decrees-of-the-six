package com.spacewaltz.decrees.council;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;

public final class CouncilSounds {

    private CouncilSounds() {
    }

    public static void playCeremonySoundToAll(MinecraftServer server) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            playCeremonySoundForPlayer(player);
        }
    }

    private static void playCeremonySoundForPlayer(ServerPlayerEntity player) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }

        String id = CouncilConfig.get().ceremonySound;
        if (id == null || id.isBlank()) {
            id = "minecraft:ui.toast.toast_complete";
            // or your preferred fallback
        }

        String cmd = "playsound " + id + " master @s";

        // ❗ Make the command source silent so no chat message appears
        ServerCommandSource silentSource = player.getCommandSource().withSilent();

        server.getCommandManager().executeWithPrefix(silentSource, cmd);
    }
}
