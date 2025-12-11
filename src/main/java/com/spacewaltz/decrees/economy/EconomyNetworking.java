package com.spacewaltz.decrees.economy;

import com.spacewaltz.decrees.DecreesOfTheSix;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Registers economy networking and handles snapshot requests for the G-key UI.
 *
 * Server side responsibilities:
 *  - Register C2S/S2C payload types.
 *  - Listen for {@link EconomySnapshotRequestC2SPayload} from a client.
 *  - Build a lightweight snapshot (current balance + recent transactions) for that player.
 *  - Send it back as {@link EconomySnapshotS2CPayload}.
 */
public final class EconomyNetworking {

    private static boolean INITIALIZED = false;

    private EconomyNetworking() {
    }

    /**
     * Called once from {@link com.spacewaltz.decrees.DecreesOfTheSix#onInitialize()}.
     */
    public static void init() {
        if (INITIALIZED) {
            // Guard against accidental double-registration during dev reloads.
            DecreesOfTheSix.LOGGER.warn("EconomyNetworking.init() called more than once; ignoring.");
            return;
        }
        INITIALIZED = true;

        // Register payload codecs.
        PayloadTypeRegistry.playC2S().register(
                EconomySnapshotRequestC2SPayload.ID,
                EconomySnapshotRequestC2SPayload.CODEC
        );
        PayloadTypeRegistry.playS2C().register(
                EconomySnapshotS2CPayload.ID,
                EconomySnapshotS2CPayload.CODEC
        );

        // Handle snapshot requests from clients.
        ServerPlayNetworking.registerGlobalReceiver(
                EconomySnapshotRequestC2SPayload.ID,
                (payload, context) -> {
                    ServerPlayerEntity player = context.player();
                    MinecraftServer server = context.server();

                    if (player == null || server == null) {
                        return;
                    }

                    // Always hop back to the main server thread.
                    server.execute(() -> sendSnapshotTo(player));
                }
        );

        DecreesOfTheSix.LOGGER.info("Economy networking initialized");
    }

    /**
     * Builds and sends a snapshot of the requesting player's economy state.
     */
    private static void sendSnapshotTo(ServerPlayerEntity player) {
        if (player == null || player.getServer() == null) {
            return;
        }

        EconomyConfigData config = EconomyConfig.get();
        EconomyStore store = EconomyStore.get();

        // Make sure the player actually has an account.
        EconomyAccount account = EconomyService.getOrCreatePlayerAccount(player.getUuid());
        int balanceCopper = EconomyService.getBalanceCopper(account);

        // Build the last N relevant transactions for that account.
        List<String> recentLines = buildLedgerLinesForAccount(player, account.id, 10, config, store);

        EconomySnapshotS2CPayload payload =
                new EconomySnapshotS2CPayload(balanceCopper, recentLines);

        ServerPlayNetworking.send(player, payload);
    }

    /**
     * Collects up to {@code maxEntries} most recent transactions that involve the given account.
     */
    private static List<String> buildLedgerLinesForAccount(ServerPlayerEntity viewer,
                                                           String accountId,
                                                           int maxEntries,
                                                           EconomyConfigData cfg,
                                                           EconomyStore store) {
        if (store == null || store.transactions == null || store.transactions.isEmpty() || accountId == null) {
            return Collections.singletonList("No ledger entries yet.");
        }

        List<EconomyTransaction> all = store.transactions;
        List<String> result = new ArrayList<>();

        // Walk from newest to oldest.
        for (int i = all.size() - 1; i >= 0 && result.size() < maxEntries; i--) {
            EconomyTransaction tx = all.get(i);
            if (tx == null) continue;

            boolean involves =
                    accountId.equals(tx.fromAccountId) ||
                            accountId.equals(tx.toAccountId);

            if (!involves) continue;

            String line = formatTransactionForViewer(viewer, tx, accountId, cfg, store);
            if (line != null && !line.isBlank()) {
                result.add(line);
            }
        }

        if (result.isEmpty()) {
            return Collections.singletonList("No ledger entries yet.");
        }

        return result;
    }

    /**
     * Creates a human-readable, one-line summary of a transaction from the viewer's perspective.
     *
     * Examples:
     *  "+1G 0S 0C Scales from Spacewaltz08 (payment)"
     *  "-0G 5S 0C Scales to Guild Treasury (seizure)"
     */
    private static String formatTransactionForViewer(ServerPlayerEntity viewer,
                                                     EconomyTransaction tx,
                                                     String viewerAccountId,
                                                     EconomyConfigData cfg,
                                                     EconomyStore store) {
        String currencyName = (cfg.currencyName == null || cfg.currencyName.isBlank())
                ? "Coins"
                : cfg.currencyName;

        int amount = Math.max(0, tx.amountCopper);

        boolean isSender = viewerAccountId != null && viewerAccountId.equals(tx.fromAccountId);
        boolean isReceiver = viewerAccountId != null && viewerAccountId.equals(tx.toAccountId);

        String amountText = formatAmountGSC(amount, currencyName, cfg);

        String signedAmount;
        if (isReceiver && !isSender) {
            signedAmount = "+" + amountText;
        } else if (isSender && !isReceiver) {
            signedAmount = "-" + amountText;
        } else {
            // Fallback – viewer is not directly involved or it's a self-transfer.
            signedAmount = amountText;
        }

        String otherParty = resolveOtherPartyLabel(viewer, store, tx, viewerAccountId);

        String typeLabel;
        switch (tx.type) {
            case PLAYER_PAYMENT -> typeLabel = "payment";
            case ADMIN_MINT -> typeLabel = "grant";
            case ADMIN_BURN -> typeLabel = "burn";
            case ADMIN_SEIZURE -> typeLabel = "seizure";
            default -> typeLabel = tx.type.name().toLowerCase(Locale.ROOT);
        }

        String preposition;
        if (isSender && !isReceiver) {
            preposition = "to";
        } else if (isReceiver && !isSender) {
            preposition = "from";
        } else {
            preposition = "with";
        }

        return signedAmount + " " + preposition + " " + otherParty + " (" + typeLabel + ")";
    }

    /**
     * Resolves the "other side" of a transaction relative to the viewer's account.
     */
    private static String resolveOtherPartyLabel(ServerPlayerEntity viewer,
                                                 EconomyStore store,
                                                 EconomyTransaction tx,
                                                 String viewerAccountId) {
        if (store == null || store.accounts == null) {
            return "System";
        }

        String fromId = tx.fromAccountId;
        String toId = tx.toAccountId;

        String otherId;
        if (viewerAccountId != null && viewerAccountId.equals(fromId)) {
            otherId = toId;
        } else if (viewerAccountId != null && viewerAccountId.equals(toId)) {
            otherId = fromId;
        } else {
            // Viewer isn't strictly one side – pick "to" if present, otherwise "from".
            otherId = toId != null ? toId : fromId;
        }

        if (otherId == null) {
            // System side of mint/burn/seizure operations.
            return switch (tx.type) {
                case ADMIN_MINT -> "System (grant)";
                case ADMIN_BURN -> "System (burn)";
                case ADMIN_SEIZURE -> "Treasury";
                default -> "System";
            };
        }

        EconomyAccount other = store.accounts.get(otherId);
        if (other == null) {
            return shortenId(otherId);
        }

        // Player account: try to resolve to an in-game name.
        if (other.type == AccountType.PLAYER && other.ownerId != null) {
            try {
                UUID uuid = UUID.fromString(other.ownerId);
                if (viewer != null && viewer.getServer() != null) {
                    ServerPlayerEntity otherPlayer =
                            viewer.getServer().getPlayerManager().getPlayer(uuid);
                    if (otherPlayer != null) {
                        return otherPlayer.getName().getString();
                    }
                }
            } catch (IllegalArgumentException ignored) {
                // ownerId was not a UUID – fall through to generic label.
            }

            // Fallback: short form of the UUID string.
            return shortenId(other.ownerId);
        }

        // Treasury account, if you have a dedicated type.
        if (other.type == AccountType.TREASURY) {
            return "Treasury";
        }

        // System account: use its ownerId as human label if present.
        if (other.type == AccountType.SYSTEM && other.ownerId != null && !other.ownerId.isBlank()) {
            return other.ownerId;
        }

        return shortenId(otherId);
    }

    private static String shortenId(String raw) {
        if (raw == null) return "?";
        if (raw.length() <= 8) return raw;
        return raw.substring(0, 8);
    }

    // ---------------------------------------------------------------------
    // Gold/Silver/Copper helpers for UI formatting
    // ---------------------------------------------------------------------

    private static String formatAmountGSC(int totalCopper,
                                          String currencyName,
                                          EconomyConfigData cfg) {
        GscAmount gsc = splitToGSC(Math.max(0, totalCopper), cfg);
        return gsc.gold + "G " + gsc.silver + "S " + gsc.copper + "C " + currencyName;
    }

    private static GscAmount splitToGSC(int totalCopper, EconomyConfigData cfg) {
        int cps = Math.max(1, cfg.copperPerSilver);
        int spg = Math.max(1, cfg.silverPerGold);

        int copperPerGold = cps * spg;

        int gold = totalCopper / copperPerGold;
        int remainder = totalCopper % copperPerGold;

        int silver = remainder / cps;
        int copper = remainder % cps;

        return new GscAmount(gold, silver, copper);
    }

    private record GscAmount(int gold, int silver, int copper) {
    }
}
