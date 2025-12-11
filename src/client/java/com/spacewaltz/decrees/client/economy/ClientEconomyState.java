package com.spacewaltz.decrees.client.economy;

import com.spacewaltz.decrees.economy.EconomySnapshotRequestC2SPayload;
import com.spacewaltz.decrees.economy.EconomySnapshotS2CPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;

import java.util.Collections;
import java.util.List;

/**
 * Client-side cache of the economy snapshot for the G-key UI:
 * - current balance in raw copper
 * - last N formatted ledger lines
 */
public final class ClientEconomyState {

    private static long balanceCopper = 0L;
    private static List<String> recentLines = Collections.emptyList();

    private ClientEconomyState() {
    }

    /**
     * Ask the server for a fresh snapshot of our account
     * (balance + recent transactions).
     */
    public static void requestSnapshot() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getNetworkHandler() == null || client.player == null) {
            return;
        }

        // New Fabric custom payload API: just send the payload instance.
        ClientPlayNetworking.send(new EconomySnapshotRequestC2SPayload());
    }

    /**
     * Called by EconomyClientNetworking when the server sends us
     * an EconomySnapshotS2CPayload.
     */
    public static void handleSnapshot(EconomySnapshotS2CPayload payload) {
        if (payload == null) {
            return;
        }

        // balanceCopper in the payload is an int; store as long on the client
        // (Future-proof and matches the G/S/C splitting we do in the UI).
        balanceCopper = Math.max(0L, payload.balanceCopper());

        List<String> lines = payload.recentLines();
        if (lines == null || lines.isEmpty()) {
            recentLines = Collections.emptyList();
        } else {
            // Make an unmodifiable copy for safety.
            recentLines = List.copyOf(lines);
        }
    }

    /**
     * Raw copper balance (client-side cache).
     */
    public static long getBalanceCopper() {
        return balanceCopper;
    }

    /**
     * Last formatted ledger lines (already human-readable).
     */
    public static List<String> getRecentLines() {
        return recentLines;
    }
}
