package com.spacewaltz.decrees.economy;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.spacewaltz.decrees.council.Messenger;
import com.spacewaltz.decrees.council.CouncilPortfolios;
import net.minecraft.command.argument.GameProfileArgumentType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Economy command handlers.
 *
 * Player-facing:
 *   /money
 *   /money balance
 *   /money pay <player> <gold> <silver> <copper>
 *
 * Admin-facing (/economy, with /moneyadmin as a legacy alias):
 *   /economy help
 *   /economy grant <player> <gold> <silver> <copper>
 *   /economy seize <player> <gold> <silver> <copper>
 *   /economy treasury
 *   /economy log [count]
 *   /economy reload
 */
public final class EconomyCommands {

    private EconomyCommands() {
    }

    // ---------------------------------------------------------------------
    // Registration entrypoints
    // ---------------------------------------------------------------------

    /**
     * Registers player-facing /money commands.
     */
    public static void registerMoneyCommands(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(
                CommandManager.literal("money")
                        // /money -> balance
                        .executes(ctx -> showBalance(ctx.getSource()))
                        // /money balance
                        .then(CommandManager.literal("balance")
                                .executes(ctx -> showBalance(ctx.getSource())))
                        // /money pay <player> <gold> <silver> <copper>
                        .then(CommandManager.literal("pay")
                                .then(CommandManager.argument("target", GameProfileArgumentType.gameProfile())
                                        .then(CommandManager.argument("gold", IntegerArgumentType.integer(0))
                                                .then(CommandManager.argument("silver", IntegerArgumentType.integer(0))
                                                        .then(CommandManager.argument("copper", IntegerArgumentType.integer(0))
                                                                .executes(ctx -> pay(
                                                                        ctx,
                                                                        IntegerArgumentType.getInteger(ctx, "gold"),
                                                                        IntegerArgumentType.getInteger(ctx, "silver"),
                                                                        IntegerArgumentType.getInteger(ctx, "copper")
                                                                ))
                                                        )
                                                )
                                        )
                                )
                        )
                        // /money help
                        .then(CommandManager.literal("help")
                                .executes(ctx -> showPlayerHelp(ctx.getSource())))
        );
    }

    /**
     * Registers admin-facing commands under /economy and legacy alias /moneyadmin.
     */
    public static void registerMoneyAdminCommands(CommandDispatcher<ServerCommandSource> dispatcher) {
        // Root admin tree under /economy
        LiteralCommandNode<ServerCommandSource> economyNode = dispatcher.register(
                CommandManager.literal("economy")
                        .requires(src -> CouncilPortfolios.hasPortfolio(
                                src,
                                CouncilPortfolios.Portfolio.ECONOMY_ADMIN
                        ))
                        // /economy help
                        .then(CommandManager.literal("help")
                                .executes(ctx -> showAdminHelp(ctx.getSource())))
                        // /economy grant <player> <gold> <silver> <copper>
                        .then(CommandManager.literal("grant")
                                .then(CommandManager.argument("target", GameProfileArgumentType.gameProfile())
                                        .then(CommandManager.argument("gold", IntegerArgumentType.integer(0))
                                                .then(CommandManager.argument("silver", IntegerArgumentType.integer(0))
                                                        .then(CommandManager.argument("copper", IntegerArgumentType.integer(0))
                                                                .executes(ctx -> grant(
                                                                        ctx,
                                                                        IntegerArgumentType.getInteger(ctx, "gold"),
                                                                        IntegerArgumentType.getInteger(ctx, "silver"),
                                                                        IntegerArgumentType.getInteger(ctx, "copper")
                                                                ))
                                                        )
                                                )
                                        )
                                )
                        )
                        // /economy seize <player> <gold> <silver> <copper>
                        .then(CommandManager.literal("seize")
                                .then(CommandManager.argument("target", GameProfileArgumentType.gameProfile())
                                        .then(CommandManager.argument("gold", IntegerArgumentType.integer(0))
                                                .then(CommandManager.argument("silver", IntegerArgumentType.integer(0))
                                                        .then(CommandManager.argument("copper", IntegerArgumentType.integer(0))
                                                                .executes(ctx -> seize(
                                                                        ctx,
                                                                        IntegerArgumentType.getInteger(ctx, "gold"),
                                                                        IntegerArgumentType.getInteger(ctx, "silver"),
                                                                        IntegerArgumentType.getInteger(ctx, "copper")
                                                                ))
                                                        )
                                                )
                                        )
                                )
                        )
                        // /economy treasury
                        .then(CommandManager.literal("treasury")
                                .executes(ctx -> showTreasury(ctx.getSource())))
                        // /economy log [count]
                        .then(CommandManager.literal("log")
                                .executes(ctx -> showLog(ctx.getSource(), 10))
                                .then(CommandManager.argument("count", IntegerArgumentType.integer(1, 100))
                                        .executes(ctx -> showLog(
                                                ctx.getSource(),
                                                IntegerArgumentType.getInteger(ctx, "count")
                                        ))
                                )
                        )
                        // /economy reload
                        .then(CommandManager.literal("reload")
                                .executes(ctx -> reloadEconomy(ctx.getSource())))
        );

        // Legacy admin alias: /moneyadmin -> /economy
        dispatcher.register(
                CommandManager.literal("moneyadmin")
                        .requires(src -> CouncilPortfolios.hasPortfolio(
                                src,
                                CouncilPortfolios.Portfolio.ECONOMY_ADMIN
                        ))
                        .redirect(economyNode)
        );
    }

    // ---------------------------------------------------------------------
    // Player commands
    // ---------------------------------------------------------------------

    private static int showBalance(ServerCommandSource src) {
        if (!EconomyConfig.get().enabled) {
            Messenger.error(src, "The economy system is currently disabled.");
            return 0;
        }

        ServerPlayerEntity player;
        try {
            player = src.getPlayer();
        } catch (Exception e) {
            Messenger.error(src, "Only in-game players can use /money.");
            return 0;
        }

        EconomyAccount account = EconomyService.getOrCreatePlayerAccount(player.getUuid());
        int balanceCopper = EconomyService.getBalanceCopper(account);

        String amount = formatAmountGSCWithName(balanceCopper);
        Messenger.info(src, "Your balance: " + amount);
        return 1;
    }

    private static int pay(CommandContext<ServerCommandSource> ctx,
                           int gold,
                           int silver,
                           int copper) {
        ServerCommandSource src = ctx.getSource();

        if (!EconomyConfig.get().enabled) {
            Messenger.error(src, "The economy system is currently disabled.");
            return 0;
        }

        ServerPlayerEntity sender;
        try {
            sender = src.getPlayer();
        } catch (Exception e) {
            Messenger.error(src, "Only in-game players can use /money pay.");
            return 0;
        }

        if (gold < 0 || silver < 0 || copper < 0) {
            Messenger.error(src, "Amounts cannot be negative.");
            return 0;
        }

        int amountCopper = toCopperFromGSC(gold, silver, copper);
        if (amountCopper <= 0) {
            Messenger.error(src, "Amount must be greater than zero.");
            return 0;
        }

        Collection<GameProfile> targets;
        try {
            targets = GameProfileArgumentType.getProfileArgument(ctx, "target");
        } catch (Exception e) {
            Messenger.error(src, "Invalid target player.");
            return 0;
        }

        if (targets.isEmpty()) {
            Messenger.error(src, "No matching player found.");
            return 0;
        }

        GameProfile targetProfile = targets.iterator().next();
        if (targetProfile.getId() == null) {
            Messenger.error(src, "Target player has no UUID.");
            return 0;
        }

        EconomyAccount from = EconomyService.getOrCreatePlayerAccount(sender.getUuid());
        EconomyAccount to = EconomyService.getOrCreatePlayerAccount(targetProfile.getId());

        if (!EconomyService.canTransfer(from, amountCopper)) {
            Messenger.error(src, "Insufficient funds.");
            return 0;
        }

        EconomyService.transfer(
                from,
                to,
                amountCopper,
                TransactionType.PLAYER_PAYMENT,
                "Player payment via /money pay."
        );

        String amountLabel = formatAmountGSCWithName(amountCopper);
        Messenger.info(src, "Paid " + amountLabel + " to " + targetProfile.getName() + ".");
        return 1;
    }

    private static int showPlayerHelp(ServerCommandSource src) {
        Messenger.info(src, "Money commands:");
        Messenger.info(src, "  /money                - show your balance");
        Messenger.info(src, "  /money balance        - show your balance");
        Messenger.info(src, "  /money pay <player> <G> <S> <C>  - pay another player in Gold/Silver/Copper.");
        return 1;
    }

    // ---------------------------------------------------------------------
    // Admin commands
    // ---------------------------------------------------------------------

    private static int showAdminHelp(ServerCommandSource src) {
        Messenger.info(src, "Economy admin commands (/economy):");
        Messenger.info(src, "  /economy help                          - show this help.");
        Messenger.info(src, "  /economy grant <player> <G> <S> <C>    - grant currency to a player.");
        Messenger.info(src, "  /economy seize <player> <G> <S> <C>    - seize currency from a player.");
        Messenger.info(src, "  /economy treasury                      - show treasury balance.");
        Messenger.info(src, "  /economy log [count]                   - show recent transactions.");
        Messenger.info(src, "  /economy reload                        - reload economy config & store.");
        Messenger.info(src, "Legacy alias: /moneyadmin ...");
        return 1;
    }

    private static int grant(CommandContext<ServerCommandSource> ctx,
                             int gold,
                             int silver,
                             int copper) {
        ServerCommandSource src = ctx.getSource();

        if (!EconomyConfig.get().enabled) {
            Messenger.error(src, "The economy system is currently disabled.");
            return 0;
        }

        if (gold < 0 || silver < 0 || copper < 0) {
            Messenger.error(src, "Amounts cannot be negative.");
            return 0;
        }

        Collection<GameProfile> targets;
        try {
            targets = GameProfileArgumentType.getProfileArgument(ctx, "target");
        } catch (Exception e) {
            Messenger.error(src, "Invalid target player.");
            return 0;
        }

        if (targets.isEmpty()) {
            Messenger.error(src, "No matching player found.");
            return 0;
        }

        GameProfile targetProfile = targets.iterator().next();
        if (targetProfile.getId() == null) {
            Messenger.error(src, "Target player has no UUID.");
            return 0;
        }

        int amountCopper = toCopperFromGSC(gold, silver, copper);
        if (amountCopper <= 0) {
            Messenger.error(src, "Amount must be greater than zero.");
            return 0;
        }

        EconomyAccount targetAccount =
                EconomyService.getOrCreatePlayerAccount(targetProfile.getId());

        EconomyService.mint(
                targetAccount,
                amountCopper,
                "Admin grant via /economy grant."
        );

        String amountLabel = formatAmountGSCWithName(amountCopper);
        Messenger.info(src, "Granted " + amountLabel + " to " + targetProfile.getName() + ".");
        return 1;
    }

    private static int seize(CommandContext<ServerCommandSource> ctx,
                             int gold,
                             int silver,
                             int copper) {
        ServerCommandSource src = ctx.getSource();

        if (!EconomyConfig.get().enabled) {
            Messenger.error(src, "The economy system is currently disabled.");
            return 0;
        }

        if (gold < 0 || silver < 0 || copper < 0) {
            Messenger.error(src, "Amounts cannot be negative.");
            return 0;
        }

        Collection<GameProfile> targets;
        try {
            targets = GameProfileArgumentType.getProfileArgument(ctx, "target");
        } catch (Exception e) {
            Messenger.error(src, "Invalid target player.");
            return 0;
        }

        if (targets.isEmpty()) {
            Messenger.error(src, "No matching player found.");
            return 0;
        }

        GameProfile targetProfile = targets.iterator().next();
        if (targetProfile.getId() == null) {
            Messenger.error(src, "Target player has no UUID.");
            return 0;
        }

        int amountCopper = toCopperFromGSC(gold, silver, copper);
        if (amountCopper <= 0) {
            Messenger.error(src, "Amount must be greater than zero.");
            return 0;
        }

        EconomyAccount targetAccount =
                EconomyService.getOrCreatePlayerAccount(targetProfile.getId());

        if (!EconomyService.canTransfer(targetAccount, amountCopper)) {
            Messenger.error(src, "Target does not have enough funds to seize that amount.");
            return 0;
        }

        EconomyService.seizeToTreasury(
                targetAccount,
                amountCopper,
                "Admin seizure via /economy seize."
        );

        String amountLabel = formatAmountGSCWithName(amountCopper);
        Messenger.info(src, "Seized " + amountLabel + " from " + targetProfile.getName() + " to the treasury.");
        return 1;
    }

    private static int showTreasury(ServerCommandSource src) {
        if (!EconomyConfig.get().enabled) {
            Messenger.error(src, "The economy system is currently disabled.");
            return 0;
        }

        EconomyAccount treasury = EconomyService.getTreasuryAccount();
        if (treasury == null) {
            Messenger.error(src, "Treasury account not found.");
            return 0;
        }

        int balanceCopper = EconomyService.getBalanceCopper(treasury);
        String amount = formatAmountGSCWithName(balanceCopper);
        Messenger.info(src, "Treasury balance: " + amount + ".");
        return 1;
    }

    private static int reloadEconomy(ServerCommandSource src) {
        if (!CouncilPortfolios.ensurePortfolio(
                src,
                CouncilPortfolios.Portfolio.ECONOMY_ADMIN,
                "reload the economy configuration"
        )) {
            return 0;
        }

        // If your EconomyConfig/EconomyStore.load(...) do not take a server,
        // change these to EconomyConfig.load() and EconomyStore.load().
        EconomyConfig.load();
        EconomyStore.load();

        Messenger.info(src, "Economy configuration & store reloaded from disk.");
        return 1;
    }

    private static int showLog(ServerCommandSource src, int count) {
        if (!EconomyConfig.get().enabled) {
            Messenger.error(src, "The economy system is currently disabled.");
            return 0;
        }

        if (count <= 0) {
            count = 10;
        }

        EconomyStore store = EconomyStore.get();
        if (store == null || store.transactions == null || store.transactions.isEmpty()) {
            Messenger.info(src, "No transactions recorded yet.");
            return 1;
        }

        List<EconomyTransaction> list = store.transactions;
        int size = list.size();
        int start = Math.max(0, size - count);

        Messenger.info(src, "Last " + (size - start) + " economy transaction(s):");

        for (int i = start; i < size; i++) {
            EconomyTransaction tx = list.get(i);
            if (tx == null) continue;

            String line = formatLogLine(tx);
            Messenger.line(src, "  " + line);
        }

        return 1;
    }

    private static String formatLogLine(EconomyTransaction tx) {
        EconomyConfigData cfg = EconomyConfig.get();
        EconomyStore store = EconomyStore.get();

        String currencyName = (cfg.currencyName == null || cfg.currencyName.isBlank())
                ? "Coins"
                : cfg.currencyName;

        String amount = formatAmountGSC(tx.amountCopper);
        String amountWithName = amount + " " + currencyName;

        String fromLabel = describeAccount(store, tx.fromAccountId);
        String toLabel = describeAccount(store, tx.toAccountId);

        String typeLabel = tx.type.name();

        StringBuilder sb = new StringBuilder();
        sb.append("[#").append(tx.id).append("] ")
                .append(amountWithName)
                .append(": ")
                .append(fromLabel)
                .append(" -> ")
                .append(toLabel)
                .append(" (").append(typeLabel).append(")");

        // If your EconomyTransaction uses 'description' instead of 'note',
        // change tx.note to tx.description below.
        if (tx.description != null && !tx.description.isBlank()) {
            sb.append(" - ").append(tx.description);
        }

        return sb.toString();
    }

    private static String describeAccount(EconomyStore store, String accountId) {
        if (accountId == null) {
            return "System";
        }

        EconomyAccount acc = store.accounts.get(accountId);
        if (acc == null) {
            return shortenId(accountId);
        }

        if (acc.type == AccountType.PLAYER && acc.ownerId != null) {
            try {
                UUID uuid = UUID.fromString(acc.ownerId);
                return "Player(" + uuid.toString().substring(0, 8) + ")";
            } catch (IllegalArgumentException ignored) {
                return "Player(" + shortenId(acc.ownerId) + ")";
            }
        }

        if (acc.type == AccountType.SYSTEM && acc.ownerId != null && !acc.ownerId.isBlank()) {
            return acc.ownerId;
        }

        if (acc.type == AccountType.TREASURY) {
            return "Treasury";
        }

        return shortenId(accountId);
    }

    private static String shortenId(String raw) {
        if (raw == null) return "?";
        if (raw.length() <= 8) return raw;
        return raw.substring(0, 8);
    }

    // ---------------------------------------------------------------------
    // Currency helpers (Gold/Silver/Copper)
    // ---------------------------------------------------------------------

    /**
     * Converts a Gold/Silver/Copper triple to raw copper units using the current config.
     */
    private static int toCopperFromGSC(int gold, int silver, int copper) {
        EconomyConfigData cfg = EconomyConfig.get();
        int cps = Math.max(1, cfg.copperPerSilver);
        int spg = Math.max(1, cfg.silverPerGold);

        long copperPerGold = (long) cps * (long) spg;

        long total =
                (long) gold * copperPerGold +
                        (long) silver * cps +
                        (long) copper;

        if (total > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        if (total < 0) {
            return 0;
        }
        return (int) total;
    }

    /**
     * Splits a raw copper amount into Gold/Silver/Copper based on config.
     */
    private static GscAmount splitToGSC(int totalCopper) {
        EconomyConfigData cfg = EconomyConfig.get();
        int cps = Math.max(1, cfg.copperPerSilver);
        int spg = Math.max(1, cfg.silverPerGold);

        int copperPerGold = cps * spg;

        int gold = totalCopper / copperPerGold;
        int remainder = totalCopper % copperPerGold;

        int silver = remainder / cps;
        int copper = remainder % cps;

        return new GscAmount(gold, silver, copper);
    }

    private static String getCurrencyName() {
        EconomyConfigData cfg = EconomyConfig.get();
        String name = cfg.currencyName;
        if (name == null || name.isBlank()) {
            name = "Coins";
        }
        return name;
    }

    private static String formatAmountGSC(int totalCopper) {
        GscAmount gsc = splitToGSC(Math.max(0, totalCopper));
        return gsc.gold + "G " + gsc.silver + "S " + gsc.copper + "C";
    }

    private static String formatAmountGSCWithName(int totalCopper) {
        return formatAmountGSC(totalCopper) + " " + getCurrencyName();
    }

    // Simple holder for a Gold/Silver/Copper triple.
    private record GscAmount(int gold, int silver, int copper) {
    }
}
