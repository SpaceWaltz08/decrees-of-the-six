package com.spacewaltz.decrees.client.economy;

import com.spacewaltz.decrees.economy.EconomySnapshotS2CPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/**
 * Client-side wiring for economy packets.
 */
public final class EconomyClientNetworking {

    private EconomyClientNetworking() {
    }

    public static void init() {
        ClientPlayNetworking.registerGlobalReceiver(
                EconomySnapshotS2CPayload.ID,
                (payload, context) -> ClientEconomyState.handleSnapshot(payload)
        );
    }
}
