package com.spacewaltz.decrees.client.guild;

import com.spacewaltz.decrees.guilds.GuildSnapshotS2CPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/**
 * Client-side wiring for guild packets.
 */
public final class GuildClientNetworking {

    private GuildClientNetworking() {
    }

    public static void init() {
        ClientPlayNetworking.registerGlobalReceiver(
                GuildSnapshotS2CPayload.ID,
                (payload, context) -> ClientGuildState.handleSnapshot(payload)
        );
    }
}
