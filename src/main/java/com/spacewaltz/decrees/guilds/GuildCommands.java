package com.spacewaltz.decrees.guilds;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.spacewaltz.decrees.council.CouncilUtil;
import com.spacewaltz.decrees.council.Messenger;
import com.spacewaltz.decrees.council.SeatDefinition;
import com.spacewaltz.decrees.economy.EconomyAccount;
import com.spacewaltz.decrees.economy.EconomyConfig;
import com.spacewaltz.decrees.economy.EconomyConfigData;
import com.spacewaltz.decrees.economy.EconomyService;
import com.spacewaltz.decrees.economy.TransactionType;
import net.minecraft.command.argument.GameProfileArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collection;
import java.util.Locale;
import java.util.UUID;

/**
 * /guild and /vox guild command trees.
 */
public final class GuildCommands {

    private GuildCommands() {
    }

    // ---------------------------------------------------------------------
    // Registration
    // ---------------------------------------------------------------------

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(
                CommandManager.literal("guild")
                        // /guild help
                        .then(CommandManager.literal("help")
                                .executes(ctx -> showHelp(ctx.getSource())))
                        // /guild create <name>
                        .then(CommandManager.literal("create")
                                .then(CommandManager.argument("name", StringArgumentType.greedyString())
                                        .executes(ctx -> createGuild(ctx,
                                                StringArgumentType.getString(ctx, "name")))))
                        // /guild info [name]
                        .then(CommandManager.literal("info")
                                .executes(ctx -> infoSelf(ctx.getSource()))
                                .then(CommandManager.argument("name", StringArgumentType.greedyString())
                                        .executes(ctx -> infoByName(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "name")))))
                        // /guild list
                        .then(CommandManager.literal("list")
                                .executes(ctx -> listGuilds(ctx.getSource())))
                        // /guild invite <player>
                        .then(CommandManager.literal("invite")
                                .then(CommandManager.argument("target", GameProfileArgumentType.gameProfile())
                                        .executes(GuildCommands::invite)))
                        // /guild accept
                        .then(CommandManager.literal("accept")
                                .executes(GuildCommands::accept))
                        // /guild deny
                        .then(CommandManager.literal("deny")
                                .executes(GuildCommands::deny))
                        // /guild leave
                        .then(CommandManager.literal("leave")
                                .executes(GuildCommands::leave))
                        // /guild kick <player>
                        .then(CommandManager.literal("kick")
                                .then(CommandManager.argument("target", GameProfileArgumentType.gameProfile())
                                        .executes(GuildCommands::kick)))
                        // /guild balance
                        .then(CommandManager.literal("balance")
                                .executes(GuildCommands::balance))
                        // /guild deposit <gold> <silver> <copper>
                        .then(CommandManager.literal("deposit")
                                .then(CommandManager.argument("gold", IntegerArgumentType.integer(0))
                                        .then(CommandManager.argument("silver", IntegerArgumentType.integer(0))
                                                .then(CommandManager.argument("copper", IntegerArgumentType.integer(0))
                                                        .executes(ctx -> deposit(
                                                                ctx,
                                                                IntegerArgumentType.getInteger(ctx, "gold"),
                                                                IntegerArgumentType.getInteger(ctx, "silver"),
                                                                IntegerArgumentType.getInteger(ctx, "copper")))))))
                        // /guild withdraw <gold> <silver> <copper>
                        .then(CommandManager.literal("withdraw")
                                .then(CommandManager.argument("gold", IntegerArgumentType.integer(0))
                                        .then(CommandManager.argument("silver", IntegerArgumentType.integer(0))
                                                .then(CommandManager.argument("copper", IntegerArgumentType.integer(0))
                                                        .executes(ctx -> withdraw(
                                                                ctx,
                                                                IntegerArgumentType.getInteger(ctx, "gold"),
                                                                IntegerArgumentType.getInteger(ctx, "silver"),
                                                                IntegerArgumentType.getInteger(ctx, "copper")))))))
                        // /guild disband
                        .then(CommandManager.literal("disband")
                                .executes(GuildCommands::disband))
                        // /guild promote <player>
                        .then(CommandManager.literal("promote")
                                .then(CommandManager.argument("target", GameProfileArgumentType.gameProfile())
                                        .executes(GuildCommands::promote)))
                        // /guild demote <player>
                        .then(CommandManager.literal("demote")
                                .then(CommandManager.argument("target", GameProfileArgumentType.gameProfile())
                                        .executes(GuildCommands::demote)))
                        // /guild setmotd <text>
                        .then(CommandManager.literal("setmotd")
                                .then(CommandManager.argument("motd", StringArgumentType.greedyString())
                                        .executes(ctx -> setMotd(
                                                ctx,
                                                StringArgumentType.getString(ctx, "motd")))))
                        // /guild setopen <true|false>
                        .then(CommandManager.literal("setopen")
                                .then(CommandManager.argument("open", BoolArgumentType.bool())
                                        .executes(ctx -> setOpenJoin(
                                                ctx,
                                                BoolArgumentType.getBool(ctx, "open")))))
                        // /guild setmax <maxMembers>
                        .then(CommandManager.literal("setmax")
                                .then(CommandManager.argument("maxMembers", IntegerArgumentType.integer(0))
                                        .executes(ctx -> setMaxMembers(
                                                ctx,
                                                IntegerArgumentType.getInteger(ctx, "maxMembers")))))
                        // /guild settitle <role> <title>
                        .then(CommandManager.literal("settitle")
                                .then(CommandManager.argument("role", StringArgumentType.word())
                                        .then(CommandManager.argument("title", StringArgumentType.greedyString())
                                                .executes(ctx -> setTitle(
                                                        ctx,
                                                        StringArgumentType.getString(ctx, "role"),
                                                        StringArgumentType.getString(ctx, "title"))))))
        );
    }

    public static void registerVoxGuildCommands(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(
                CommandManager.literal("vox")
                        .then(CommandManager.literal("guild")
                                .requires(GuildCommands::hasVoxImperionRights)
                                // /vox guild info <name>
                                .then(CommandManager.literal("info")
                                        .then(CommandManager.argument("name", StringArgumentType.greedyString())
                                                .executes(ctx -> voxInfo(
                                                        ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "name")))))
                                // /vox guild setleader <guildName> <player>
                                .then(CommandManager.literal("setleader")
                                        .then(CommandManager.argument("guild", StringArgumentType.greedyString())
                                                .then(CommandManager.argument("player", GameProfileArgumentType.gameProfile())
                                                        .executes(GuildCommands::voxSetLeader))))
                                // /vox guild disband <guildName>
                                .then(CommandManager.literal("disband")
                                        .then(CommandManager.argument("guild", StringArgumentType.greedyString())
                                                .executes(GuildCommands::voxDisband)))
                                // /vox guild rename <guildName> <newName>
                                .then(CommandManager.literal("rename")
                                        .then(CommandManager.argument("guild", StringArgumentType.greedyString())
                                                .then(CommandManager.argument("newName", StringArgumentType.greedyString())
                                                        .executes(GuildCommands::voxRename))))
                        )
        );
    }

    // ---------------------------------------------------------------------
    // /guild helpers
    // ---------------------------------------------------------------------

    private static int showHelp(ServerCommandSource src) {
        Messenger.info(src, "Guild commands:");
        Messenger.info(src, "  /guild create <name>             - create a new guild.");
        Messenger.info(src, "  /guild info [name]               - show info about your or another guild.");
        Messenger.info(src, "  /guild settitle <role> <title>     - rename a rank for your guild.");
        Messenger.info(src, "  /guild list                      - list existing guilds.");
        Messenger.info(src, "  /guild invite <player>           - invite a player to your guild.");
        Messenger.info(src, "  /guild accept|deny               - respond to the latest invite.");
        Messenger.info(src, "  /guild leave                     - leave your current guild.");
        Messenger.info(src, "  /guild kick <player>             - remove a member (Leader/Officers only).");
        Messenger.info(src, "  /guild balance                   - view your guild Treasury balance.");
        Messenger.info(src, "  /guild deposit <G> <S> <C>       - deposit Gold/Silver/Copper into Treasury.");
        Messenger.info(src, "  /guild withdraw <G> <S> <C>      - leader withdraws from Treasury.");
        Messenger.info(src, "  /guild disband                   - leader disbands the guild.");
        Messenger.info(src, "  /guild promote|demote <player>   - adjust member rank.");
        Messenger.info(src, "  /guild setmotd <text>            - set guild description.");
        Messenger.info(src, "  /guild setopen <true|false>      - set open-join flag.");
        Messenger.info(src, "  /guild setmax <maxMembers>       - set member cap (0 = no limit).");
        return 1;
    }

    private static int createGuild(CommandContext<ServerCommandSource> ctx, String name) {
        ServerCommandSource src = ctx.getSource();
        ServerPlayerEntity player;
        try {
            player = src.getPlayer();
        } catch (Exception e) {
            Messenger.error(src, "Only in-game players can create guilds.");
            return 0;
        }

        name = name.trim();
        if (name.isEmpty()) {
            Messenger.error(src, "Guild name cannot be empty.");
            return 0;
        }

        if (GuildStore.getGuildForPlayer(player.getUuid()) != null) {
            Messenger.error(src, "You are already in a guild.");
            return 0;
        }

        if (GuildStore.findByName(name) != null) {
            Messenger.error(src, "A guild with that name already exists.");
            return 0;
        }

        Guild guild = GuildStore.createGuild(name, null, player.getUuid());
        Messenger.info(src, "You have founded the guild §e" + guild.name + "§r.");
        Messenger.info(src,
                "Use /guild settitle <role> <title> to customize your ranks " +
                        "(leader/officer/veteran/member/recruit).");
        return 1;
    }

    private static int infoSelf(ServerCommandSource src) {
        ServerPlayerEntity player;
        try {
            player = src.getPlayer();
        } catch (Exception e) {
            Messenger.error(src, "Only in-game players can use /guild info without a name.");
            return 0;
        }

        Guild guild = GuildStore.getGuildForPlayer(player.getUuid());
        if (guild == null) {
            Messenger.error(src, "You are not in a guild.");
            return 0;
        }

        return renderGuildInfo(src, guild, false);
    }

    private static int infoByName(ServerCommandSource src, String name) {
        Guild guild = GuildStore.findByName(name);
        if (guild == null) {
            Messenger.error(src, "No guild found with that name.");
            return 0;
        }
        return renderGuildInfo(src, guild, false);
    }

    private static int listGuilds(ServerCommandSource src) {
        GuildStore store = GuildStore.get();
        if (store.guilds.isEmpty()) {
            Messenger.info(src, "There are currently no guilds.");
            return 1;
        }

        Messenger.info(src, "Existing guilds:");
        for (Guild g : store.guilds) {
            int count = g.members != null ? g.members.size() : 0;
            Messenger.info(src, "  §e" + g.name + "§r (" + count + " members)");
        }
        return 1;
    }

    private static int invite(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource src = ctx.getSource();
        ServerPlayerEntity inviter;
        try {
            inviter = src.getPlayer();
        } catch (Exception e) {
            Messenger.error(src, "Only in-game players can invite members.");
            return 0;
        }

        Guild guild = GuildStore.getGuildForPlayer(inviter.getUuid());
        if (guild == null) {
            Messenger.error(src, "You are not in a guild.");
            return 0;
        }

        GuildRole role = guild.members.get(inviter.getUuid().toString());
        if (role == null ||
                (role != GuildRole.LEADER && role != GuildRole.OFFICER && role != GuildRole.VETERAN)) {
            Messenger.error(src, "You do not have permission to invite members.");
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

        UUID targetUuid = targetProfile.getId();

        Guild existing = GuildStore.getGuildForPlayer(targetUuid);
        if (existing != null) {
            Messenger.error(src, "That player is already in a guild.");
            return 0;
        }

        GuildStore.addInvite(targetUuid, guild);
        Messenger.info(src, "Invited §e" + targetProfile.getName() + "§r to join §e" + guild.name + "§r.");

        // If target is online, send them a hint.
        ServerPlayerEntity targetPlayer = src.getServer().getPlayerManager().getPlayer(targetUuid);
        if (targetPlayer != null) {
            Messenger.info(targetPlayer.getCommandSource(),
                    "You have been invited to join guild §e" + guild.name + "§r. Use /guild accept or /guild deny.");
        }

        return 1;
    }

    private static int accept(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource src = ctx.getSource();
        ServerPlayerEntity player;
        try {
            player = src.getPlayer();
        } catch (Exception e) {
            Messenger.error(src, "Only in-game players can accept guild invites.");
            return 0;
        }

        if (GuildStore.getGuildForPlayer(player.getUuid()) != null) {
            Messenger.error(src, "You are already in a guild.");
            return 0;
        }

        Guild guild = GuildStore.consumeInvite(player.getUuid());
        if (guild == null) {
            Messenger.error(src, "You have no pending guild invites.");
            return 0;
        }

        GuildStore.addMember(guild, player.getUuid(), GuildRole.RECRUIT);
        Messenger.info(src, "You have joined guild §e" + guild.name + "§r as a Recruit.");
        return 1;
    }

    private static int deny(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource src = ctx.getSource();
        ServerPlayerEntity player;
        try {
            player = src.getPlayer();
        } catch (Exception e) {
            Messenger.error(src, "Only in-game players can deny guild invites.");
            return 0;
        }

        Guild guild = GuildStore.consumeInvite(player.getUuid());
        if (guild == null) {
            Messenger.error(src, "You have no pending guild invites.");
        } else {
            Messenger.info(src, "You have declined the invite from guild §e" + guild.name + "§r.");
        }
        return 1;
    }

    private static int leave(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource src = ctx.getSource();
        ServerPlayerEntity player;
        try {
            player = src.getPlayer();
        } catch (Exception e) {
            Messenger.error(src, "Only in-game players can leave a guild.");
            return 0;
        }

        Guild guild = GuildStore.getGuildForPlayer(player.getUuid());
        if (guild == null) {
            Messenger.error(src, "You are not in a guild.");
            return 0;
        }

        String key = player.getUuid().toString();
        GuildRole role = guild.members.get(key);
        if (role == GuildRole.LEADER) {
            Messenger.error(src, "Leaders must transfer leadership or disband the guild before leaving.");
            return 0;
        }

        GuildStore.removeMember(guild, player.getUuid());
        Messenger.info(src, "You have left guild §e" + guild.name + "§r.");
        return 1;
    }

    private static int kick(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource src = ctx.getSource();
        ServerPlayerEntity caller;
        try {
            caller = src.getPlayer();
        } catch (Exception e) {
            Messenger.error(src, "Only in-game players can kick guild members.");
            return 0;
        }

        Guild guild = GuildStore.getGuildForPlayer(caller.getUuid());
        if (guild == null) {
            Messenger.error(src, "You are not in a guild.");
            return 0;
        }

        GuildRole callerRole = guild.members.get(caller.getUuid().toString());
        // Only Leader and Officer can kick (no Veteran kicks).
        if (callerRole != GuildRole.LEADER && callerRole != GuildRole.OFFICER) {
            Messenger.error(src, "You do not have permission to kick members.");
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

        UUID targetUuid = targetProfile.getId();
        Guild same = GuildStore.getGuildForPlayer(targetUuid);
        if (same == null || same.id != guild.id) {
            Messenger.error(src, "That player is not in your guild.");
            return 0;
        }

        String targetKey = targetUuid.toString();
        GuildRole targetRole = guild.members.get(targetKey);
        if (targetRole == GuildRole.LEADER) {
            Messenger.error(src, "You cannot kick the guild leader.");
            return 0;
        }

        // Officers cannot kick other officers.
        if (callerRole == GuildRole.OFFICER &&
                (targetRole == GuildRole.OFFICER)) {
            Messenger.error(src, "Officers cannot kick other officers.");
            return 0;
        }

        GuildStore.removeMember(guild, targetUuid);
        Messenger.info(src, "Kicked §e" + targetProfile.getName() + "§r from guild §e" + guild.name + "§r.");
        return 1;
    }

    private static int balance(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource src = ctx.getSource();
        ServerPlayerEntity player;
        try {
            player = src.getPlayer();
        } catch (Exception e) {
            Messenger.error(src, "Only in-game players can view guild balance.");
            return 0;
        }

        Guild guild = GuildStore.getGuildForPlayer(player.getUuid());
        if (guild == null) {
            Messenger.error(src, "You are not in a guild.");
            return 0;
        }

        if (guild.treasuryAccountId == null || guild.treasuryAccountId.isBlank()) {
            Messenger.error(src, "This guild has no Treasury account.");
            return 0;
        }

        EconomyAccount treasury = EconomyService.getAccountById(guild.treasuryAccountId);
        if (treasury == null) {
            Messenger.error(src, "Treasury account not found.");
            return 0;
        }

        int totalCopper = EconomyService.getBalanceCopper(treasury);
        String label = formatAmountGSCWithName(totalCopper);
        Messenger.info(src, "Guild Treasury balance: " + label);
        return 1;
    }

    private static int deposit(CommandContext<ServerCommandSource> ctx,
                               int gold,
                               int silver,
                               int copper) {
        ServerCommandSource src = ctx.getSource();
        ServerPlayerEntity player;
        try {
            player = src.getPlayer();
        } catch (Exception e) {
            Messenger.error(src, "Only in-game players can deposit into a guild.");
            return 0;
        }

        Guild guild = GuildStore.getGuildForPlayer(player.getUuid());
        if (guild == null) {
            Messenger.error(src, "You are not in a guild.");
            return 0;
        }

        if (!EconomyConfig.get().enabled) {
            Messenger.error(src, "The economy system is currently disabled.");
            return 0;
        }

        int amountCopper = toCopperFromGSC(gold, silver, copper);
        if (amountCopper <= 0) {
            Messenger.error(src, "Amount must be greater than zero.");
            return 0;
        }

        EconomyAccount from = EconomyService.getOrCreatePlayerAccount(player.getUuid());
        if (guild.treasuryAccountId == null || guild.treasuryAccountId.isBlank()) {
            Messenger.error(src, "This guild has no Treasury account.");
            return 0;
        }

        EconomyAccount to = EconomyService.getAccountById(guild.treasuryAccountId);
        if (to == null) {
            Messenger.error(src, "Treasury account not found.");
            return 0;
        }

        if (!EconomyService.canTransfer(from, amountCopper)) {
            Messenger.error(src, "You do not have enough funds.");
            return 0;
        }

        EconomyService.transfer(
                from,
                to,
                amountCopper,
                TransactionType.GUILD_DEPOSIT,
                "Guild deposit into treasury via /guild deposit."
        );

        String amountLabel = formatAmountGSCWithName(amountCopper);
        Messenger.info(src, "Deposited " + amountLabel + " into the guild Treasury.");
        return 1;
    }

    private static int withdraw(CommandContext<ServerCommandSource> ctx,
                                int gold,
                                int silver,
                                int copper) {
        ServerCommandSource src = ctx.getSource();
        ServerPlayerEntity player;
        try {
            player = src.getPlayer();
        } catch (Exception e) {
            Messenger.error(src, "Only in-game players can withdraw from a guild.");
            return 0;
        }

        Guild guild = GuildStore.getGuildForPlayer(player.getUuid());
        if (guild == null) {
            Messenger.error(src, "You are not in a guild.");
            return 0;
        }

        String key = player.getUuid().toString();
        GuildRole role = guild.members.get(key);
        // Phase 2: Leader-only withdraw.
        if (role != GuildRole.LEADER) {
            Messenger.error(src, "Only the guild leader may withdraw from the Treasury.");
            return 0;
        }

        if (!EconomyConfig.get().enabled) {
            Messenger.error(src, "The economy system is currently disabled.");
            return 0;
        }

        int amountCopper = toCopperFromGSC(gold, silver, copper);
        if (amountCopper <= 0) {
            Messenger.error(src, "Amount must be greater than zero.");
            return 0;
        }

        if (guild.treasuryAccountId == null || guild.treasuryAccountId.isBlank()) {
            Messenger.error(src, "This guild has no Treasury account.");
            return 0;
        }

        EconomyAccount treasury = EconomyService.getAccountById(guild.treasuryAccountId);
        if (treasury == null) {
            Messenger.error(src, "Treasury account not found.");
            return 0;
        }

        if (!EconomyService.canTransfer(treasury, amountCopper)) {
            Messenger.error(src, "The guild Treasury does not have enough funds.");
            return 0;
        }

        EconomyAccount leaderAccount = EconomyService.getOrCreatePlayerAccount(player.getUuid());

        EconomyService.transfer(
                treasury,
                leaderAccount,
                amountCopper,
                TransactionType.GUILD_WITHDRAW,
                "Guild withdrawal via /guild withdraw by leader."
        );

        String amountLabel = formatAmountGSCWithName(amountCopper);
        Messenger.info(src, "Withdrew " + amountLabel + " from the guild Treasury to your account.");
        return 1;
    }

    private static int disband(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource src = ctx.getSource();
        ServerPlayerEntity player;
        try {
            player = src.getPlayer();
        } catch (Exception e) {
            Messenger.error(src, "Only in-game players can disband a guild.");
            return 0;
        }

        Guild guild = GuildStore.getGuildForPlayer(player.getUuid());
        if (guild == null) {
            Messenger.error(src, "You are not in a guild.");
            return 0;
        }

        String key = player.getUuid().toString();
        GuildRole role = guild.members.get(key);
        if (role != GuildRole.LEADER) {
            Messenger.error(src, "Only the guild leader can disband the guild.");
            return 0;
        }

        // Treasury handling: move remaining funds to leader.
        if (guild.treasuryAccountId != null && !guild.treasuryAccountId.isBlank()) {
            EconomyAccount treasury = EconomyService.getAccountById(guild.treasuryAccountId);
            if (treasury != null) {
                int amount = EconomyService.getBalanceCopper(treasury);
                if (amount > 0) {
                    EconomyAccount leaderAcc = EconomyService.getOrCreatePlayerAccount(player.getUuid());
                    EconomyService.transfer(
                            treasury,
                            leaderAcc,
                            amount,
                            TransactionType.GUILD_WITHDRAW,
                            "Guild disbanded by leader; funds moved to leader."
                    );
                }
            }
        }

        String name = guild.name;
        GuildStore.disbandGuild(guild);
        Messenger.info(src, "You have disbanded the guild §e" + name + "§r.");
        return 1;
    }

    private static int promote(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource src = ctx.getSource();
        ServerPlayerEntity caller;
        try {
            caller = src.getPlayer();
        } catch (Exception e) {
            Messenger.error(src, "Only in-game players can promote guild members.");
            return 0;
        }

        Guild guild = GuildStore.getGuildForPlayer(caller.getUuid());
        if (guild == null) {
            Messenger.error(src, "You are not in a guild.");
            return 0;
        }

        GuildRole callerRole = guild.members.get(caller.getUuid().toString());
        if (callerRole != GuildRole.LEADER && callerRole != GuildRole.OFFICER) {
            Messenger.error(src, "You do not have permission to promote members.");
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

        UUID targetUuid = targetProfile.getId();
        Guild same = GuildStore.getGuildForPlayer(targetUuid);
        if (same == null || same.id != guild.id) {
            Messenger.error(src, "That player is not in your guild.");
            return 0;
        }

        String targetKey = targetUuid.toString();
        GuildRole targetRole = guild.members.get(targetKey);
        if (targetRole == null) {
            Messenger.error(src, "That player has no recorded guild role.");
            return 0;
        }

        if (targetRole == GuildRole.LEADER) {
            Messenger.error(src, "You cannot change the leader's rank with this command.");
            return 0;
        }

        GuildRole newRole = nextHigherRole(targetRole);
        if (newRole == null) {
            Messenger.error(src, "That member is already at the highest rank allowed for this command.");
            return 0;
        }

        String newTitle = getTitleFor(guild, newRole);

        // Officers may only promote between Veteran/Member/Recruit (cannot create Officers).
        if (callerRole == GuildRole.OFFICER && newRole == GuildRole.OFFICER) {
            Messenger.error(src, "Officers cannot promote members to Officer rank.");
            return 0;
        }

        guild.members.put(targetKey, newRole);
        GuildStore.save();

        Messenger.info(src, "Promoted §e" + targetProfile.getName() + "§r to §b" + newTitle + "§r.");
        return 1;
    }

    private static int demote(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource src = ctx.getSource();
        ServerPlayerEntity caller;
        try {
            caller = src.getPlayer();
        } catch (Exception e) {
            Messenger.error(src, "Only in-game players can demote guild members.");
            return 0;
        }

        Guild guild = GuildStore.getGuildForPlayer(caller.getUuid());
        if (guild == null) {
            Messenger.error(src, "You are not in a guild.");
            return 0;
        }

        GuildRole callerRole = guild.members.get(caller.getUuid().toString());
        if (callerRole != GuildRole.LEADER && callerRole != GuildRole.OFFICER) {
            Messenger.error(src, "You do not have permission to demote members.");
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

        UUID targetUuid = targetProfile.getId();
        Guild same = GuildStore.getGuildForPlayer(targetUuid);
        if (same == null || same.id != guild.id) {
            Messenger.error(src, "That player is not in your guild.");
            return 0;
        }

        String targetKey = targetUuid.toString();
        GuildRole targetRole = guild.members.get(targetKey);
        if (targetRole == null) {
            Messenger.error(src, "That player has no recorded guild role.");
            return 0;
        }

        if (targetRole == GuildRole.LEADER) {
            Messenger.error(src, "You cannot change the leader's rank with this command.");
            return 0;
        }

        // Officers cannot demote other Officers.
        if (callerRole == GuildRole.OFFICER && targetRole == GuildRole.OFFICER) {
            Messenger.error(src, "Officers cannot demote other officers.");
            return 0;
        }

        GuildRole newRole = lowerRole(targetRole);
        if (newRole == null) {
            Messenger.error(src, "That member is already at the lowest rank.");
            return 0;
        }

        String newTitle = getTitleFor(guild, newRole);

        guild.members.put(targetKey, newRole);
        GuildStore.save();

        Messenger.info(src, "Demoted §e" + targetProfile.getName() + "§r to §b" + newTitle + "§r.");
        return 1;
    }

    private static int setMotd(CommandContext<ServerCommandSource> ctx, String motd) {
        ServerCommandSource src = ctx.getSource();
        ServerPlayerEntity player;
        try {
            player = src.getPlayer();
        } catch (Exception e) {
            Messenger.error(src, "Only in-game players can set guild MOTD.");
            return 0;
        }

        Guild guild = GuildStore.getGuildForPlayer(player.getUuid());
        if (guild == null) {
            Messenger.error(src, "You are not in a guild.");
            return 0;
        }

        String key = player.getUuid().toString();
        GuildRole role = guild.members.get(key);
        // Phase 2: Leader-only settings.
        if (role != GuildRole.LEADER) {
            Messenger.error(src, "Only the guild leader may change guild settings.");
            return 0;
        }

        guild.motd = motd.trim();
        GuildStore.save();

        Messenger.info(src, "Guild MOTD updated.");
        return 1;
    }

    private static int setOpenJoin(CommandContext<ServerCommandSource> ctx, boolean open) {
        ServerCommandSource src = ctx.getSource();
        ServerPlayerEntity player;
        try {
            player = src.getPlayer();
        } catch (Exception e) {
            Messenger.error(src, "Only in-game players can change guild settings.");
            return 0;
        }

        Guild guild = GuildStore.getGuildForPlayer(player.getUuid());
        if (guild == null) {
            Messenger.error(src, "You are not in a guild.");
            return 0;
        }

        String key = player.getUuid().toString();
        GuildRole role = guild.members.get(key);
        if (role != GuildRole.LEADER) {
            Messenger.error(src, "Only the guild leader may change guild settings.");
            return 0;
        }

        guild.openJoin = open;
        GuildStore.save();

        Messenger.info(src, "Guild open-join setting set to: " + (open ? "OPEN" : "INVITE-ONLY") + ".");
        return 1;
    }

    private static int setMaxMembers(CommandContext<ServerCommandSource> ctx, int maxMembers) {
        ServerCommandSource src = ctx.getSource();
        ServerPlayerEntity player;
        try {
            player = src.getPlayer();
        } catch (Exception e) {
            Messenger.error(src, "Only in-game players can change guild settings.");
            return 0;
        }

        Guild guild = GuildStore.getGuildForPlayer(player.getUuid());
        if (guild == null) {
            Messenger.error(src, "You are not in a guild.");
            return 0;
        }

        String key = player.getUuid().toString();
        GuildRole role = guild.members.get(key);
        if (role != GuildRole.LEADER) {
            Messenger.error(src, "Only the guild leader may change guild settings.");
            return 0;
        }

        guild.maxMembers = maxMembers;
        GuildStore.save();

        Messenger.info(src, "Guild maximum members set to " + maxMembers +
                (maxMembers == 0 ? " (no limit)." : "."));
        return 1;
    }

    private static int setTitle(CommandContext<ServerCommandSource> ctx,
                                String roleKey,
                                String titleRaw) {
        ServerCommandSource src = ctx.getSource();
        ServerPlayerEntity player;
        try {
            player = src.getPlayer();
        } catch (Exception e) {
            Messenger.error(src, "Only in-game players can change guild titles.");
            return 0;
        }

        Guild guild = GuildStore.getGuildForPlayer(player.getUuid());
        if (guild == null) {
            Messenger.error(src, "You are not in a guild.");
            return 0;
        }

        String myKey = player.getUuid().toString();
        GuildRole myRole = guild.members.get(myKey);
        if (myRole != GuildRole.LEADER) {
            Messenger.error(src, "Only the guild leader may rename ranks.");
            return 0;
        }

        GuildRole targetRole = parseRoleKey(roleKey);
        if (targetRole == null) {
            Messenger.error(src,
                    "Unknown rank '" + roleKey + "'. Use: leader / officer / veteran / member / recruit.");
            return 0;
        }

        String normalized = normalizeTitle(titleRaw);

        switch (targetRole) {
            case LEADER -> guild.leaderTitle = normalized;
            case OFFICER -> guild.officerTitle = normalized;
            case VETERAN -> guild.veteranTitle = normalized;
            case MEMBER -> guild.memberTitle = normalized;
            case RECRUIT -> guild.recruitTitle = normalized;
        }

        GuildStore.save();

        Messenger.info(src, "Set title for " + targetRole.name() + " to §b" + normalized + "§r.");
        return 1;
    }

    // ---------------------------------------------------------------------
    // Vox Imperion (admin) commands
    // ---------------------------------------------------------------------

    private static int voxInfo(ServerCommandSource src, String name) {
        Guild guild = GuildStore.findByName(name);
        if (guild == null) {
            Messenger.error(src, "No guild found with that name.");
            return 0;
        }

        renderGuildInfo(src, guild, true);

        // Extra admin info: treasury & creation date.
        if (guild.treasuryAccountId != null && !guild.treasuryAccountId.isBlank()) {
            EconomyAccount treasury = EconomyService.getAccountById(guild.treasuryAccountId);
            if (treasury != null) {
                int totalCopper = EconomyService.getBalanceCopper(treasury);
                Messenger.info(src, "Treasury: " + formatAmountGSCWithName(totalCopper));
            }
        }

        if (guild.createdAt > 0L) {
            LocalDate date = Instant.ofEpochMilli(guild.createdAt)
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate();
            Messenger.info(src, "Created at: " + date);
        }

        return 1;
    }

    private static int voxSetLeader(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource src = ctx.getSource();
        String guildName = StringArgumentType.getString(ctx, "guild");

        Guild guild = GuildStore.findByName(guildName);
        if (guild == null) {
            Messenger.error(src, "No guild found with that name.");
            return 0;
        }

        Collection<GameProfile> targets;
        try {
            targets = GameProfileArgumentType.getProfileArgument(ctx, "player");
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

        UUID newLeaderUuid = targetProfile.getId();
        String newLeaderKey = newLeaderUuid.toString();

        // Ensure the new leader is a member; if not, add them.
        if (!guild.members.containsKey(newLeaderKey)) {
            GuildStore.addMember(guild, newLeaderUuid, GuildRole.LEADER);
        }

        // Downgrade old leader to Officer if still present.
        if (guild.leaderUuid != null && !guild.leaderUuid.equals(newLeaderKey)) {
            GuildRole oldRole = guild.members.get(guild.leaderUuid);
            if (oldRole != null && oldRole == GuildRole.LEADER) {
                guild.members.put(guild.leaderUuid, GuildRole.OFFICER);
            }
        }

        guild.leaderUuid = newLeaderKey;
        GuildStore.save();

        Messenger.info(src, "Set §e" + targetProfile.getName() + "§r as leader of guild §e" + guild.name + "§r.");
        return 1;
    }

    private static int voxDisband(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource src = ctx.getSource();
        String guildName = StringArgumentType.getString(ctx, "guild");

        Guild guild = GuildStore.findByName(guildName);
        if (guild == null) {
            Messenger.error(src, "No guild found with that name.");
            return 0;
        }

        // Handle Treasury: move remaining funds to the global Treasury.
        if (guild.treasuryAccountId != null && !guild.treasuryAccountId.isBlank()) {
            EconomyAccount guildTreasury = EconomyService.getAccountById(guild.treasuryAccountId);
            EconomyAccount globalTreasury = EconomyService.getTreasuryAccount();
            if (guildTreasury != null && globalTreasury != null) {
                int amount = EconomyService.getBalanceCopper(guildTreasury);
                if (amount > 0) {
                    EconomyService.transfer(
                            guildTreasury,
                            globalTreasury,
                            amount,
                            TransactionType.GUILD_WITHDRAW,
                            "Vox Imperion disbanded guild; funds moved to Council Treasury."
                    );
                }
            }
        }

        GuildStore.disbandGuild(guild);
        Messenger.info(src, "Disbanded guild §e" + guild.name + "§r.");
        return 1;
    }

    private static int voxRename(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource src = ctx.getSource();
        String guildName = StringArgumentType.getString(ctx, "guild");
        String newName = StringArgumentType.getString(ctx, "newName").trim();

        if (newName.isEmpty()) {
            Messenger.error(src, "New guild name cannot be empty.");
            return 0;
        }

        Guild guild = GuildStore.findByName(guildName);
        if (guild == null) {
            Messenger.error(src, "No guild found with that name.");
            return 0;
        }

        Guild existing = GuildStore.findByName(newName);
        if (existing != null && existing.id != guild.id) {
            Messenger.error(src, "Another guild already uses that name.");
            return 0;
        }

        guild.name = newName;
        GuildStore.save();
        Messenger.info(src, "Renamed guild to §e" + guild.name + "§r.");
        return 1;
    }

    // ---------------------------------------------------------------------
    // Shared helpers
    // ---------------------------------------------------------------------

    private static int renderGuildInfo(ServerCommandSource src, Guild guild, boolean isAdminView) {
        int totalMembers = guild.members != null ? guild.members.size() : 0;

        int leaders = 0, officers = 0, veterans = 0, members = 0, recruits = 0;
        if (guild.members != null) {
            for (GuildRole role : guild.members.values()) {
                if (role == null) continue;
                switch (role) {
                    case LEADER -> leaders++;
                    case OFFICER -> officers++;
                    case VETERAN -> veterans++;
                    case MEMBER -> members++;
                    case RECRUIT -> recruits++;
                }
            }
        }

        Messenger.info(src, "§6=== Guild: " + guild.name + " ===");
        if (guild.tag != null && !guild.tag.isBlank()) {
            Messenger.info(src, "Tag: [" + guild.tag + "]");
        }

        // Leader display: try to resolve name if possible, otherwise UUID.
        if (guild.leaderUuid != null && !guild.leaderUuid.isBlank()) {
            String leaderDisplay = guild.leaderUuid;
            try {
                UUID leaderUuid = UUID.fromString(guild.leaderUuid);
                GameProfile gp = src.getServer().getUserCache()
                        .getByUuid(leaderUuid)
                        .orElse(null);
                if (gp != null && gp.getName() != null) {
                    leaderDisplay = gp.getName();
                }
            } catch (IllegalArgumentException ignored) {
            }
            Messenger.info(src, "Leader: §e" + leaderDisplay + "§r");
        }

        Messenger.info(src, "Members: " + totalMembers + " (L:" + leaders +
                ", O:" + officers + ", V:" + veterans + ", M:" + members + ", R:" + recruits + ")");

        Messenger.info(src, "Rank titles: " +
                "Leader=" + getTitleFor(guild, GuildRole.LEADER) + ", " +
                "Officer=" + getTitleFor(guild, GuildRole.OFFICER) + ", " +
                "Veteran=" + getTitleFor(guild, GuildRole.VETERAN) + ", " +
                "Member=" + getTitleFor(guild, GuildRole.MEMBER) + ", " +
                "Recruit=" + getTitleFor(guild, GuildRole.RECRUIT));

        if (guild.motd != null && !guild.motd.isBlank()) {
            Messenger.info(src, "MOTD: " + guild.motd);
        }

        Messenger.info(src, "Open join: " + (guild.openJoin ? "OPEN" : "INVITE-ONLY"));
        if (guild.maxMembers > 0) {
            Messenger.info(src, "Member limit: " + guild.maxMembers);
        }

        return 1;
    }

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
        return (int) total;
    }

    private static String formatAmountGSCWithName(int totalCopper) {
        return formatAmountGSC(totalCopper) + " " + getCurrencyName();
    }

    private static String formatAmountGSC(int totalCopper) {
        EconomyConfigData cfg = EconomyConfig.get();
        int cps = Math.max(1, cfg.copperPerSilver);
        int spg = Math.max(1, cfg.silverPerGold);

        if (totalCopper <= 0) {
            return "0G 0S 0C";
        }

        int copper = totalCopper;
        int gold = copper / (cps * spg);
        copper -= gold * cps * spg;
        int silver = copper / cps;
        copper -= silver * cps;

        return gold + "G " + silver + "S " + copper + "C";
    }

    private static String getCurrencyName() {
        EconomyConfigData cfg = EconomyConfig.get();
        String name = cfg.currencyName;
        if (name == null || name.isBlank()) {
            return "Scales";
        }
        return name;
    }

    private static GuildRole nextHigherRole(GuildRole current) {
        if (current == null) return null;
        return switch (current) {
            case RECRUIT -> GuildRole.MEMBER;
            case MEMBER  -> GuildRole.VETERAN;
            case VETERAN -> GuildRole.OFFICER;
            case OFFICER, LEADER -> null;
        };
    }

    private static GuildRole lowerRole(GuildRole current) {
        if (current == null) return null;
        return switch (current) {
            case OFFICER -> GuildRole.VETERAN;
            case VETERAN -> GuildRole.MEMBER;
            case MEMBER  -> GuildRole.RECRUIT;
            case RECRUIT, LEADER -> null;
        };
    }

    private static GuildRole parseRoleKey(String key) {
        if (key == null) return null;
        String k = key.trim().toLowerCase(Locale.ROOT);
        return switch (k) {
            case "leader" -> GuildRole.LEADER;
            case "officer" -> GuildRole.OFFICER;
            case "veteran" -> GuildRole.VETERAN;
            case "member" -> GuildRole.MEMBER;
            case "recruit" -> GuildRole.RECRUIT;
            default -> null;
        };
    }

    private static String getTitleFor(Guild guild, GuildRole role) {
        if (role == null) return "";
        if (guild == null) return defaultTitleFor(role);

        String base = switch (role) {
            case LEADER -> guild.leaderTitle;
            case OFFICER -> guild.officerTitle;
            case VETERAN -> guild.veteranTitle;
            case MEMBER -> guild.memberTitle;
            case RECRUIT -> guild.recruitTitle;
        };

        if (base == null || base.isBlank()) {
            return defaultTitleFor(role);
        }
        return base;
    }

    private static String defaultTitleFor(GuildRole role) {
        return switch (role) {
            case LEADER -> "Leader";
            case OFFICER -> "Officer";
            case VETERAN -> "Veteran";
            case MEMBER -> "Member";
            case RECRUIT -> "Recruit";
        };
    }

    private static String normalizeTitle(String raw) {
        if (raw == null) return "";
        // Allow underscores in commands, display them as spaces.
        String s = raw.replace('_', ' ').trim();
        if (s.isEmpty()) return s;
        // Simple capitalization: first letter upper, rest unchanged.
        return s.substring(0, 1).toUpperCase(Locale.ROOT) + s.substring(1);
    }

    /**
     * Checks whether the command source should be treated as Vox Imperion
     * for guild admin commands.
     *
     * - Any player occupying the seat with id "vox_imperion" (case-insensitive), or
     * - Any source with vanilla permission level >= 3.
     */
    private static boolean hasVoxImperionRights(ServerCommandSource src) {
        if (src.hasPermissionLevel(3)) {
            return true;
        }
        ServerPlayerEntity player;
        try {
            player = src.getPlayer();
        } catch (Exception e) {
            return false;
        }

        SeatDefinition seat = CouncilUtil.getSeatFor(player);
        if (seat == null || seat.id == null) {
            return false;
        }
        return "vox_imperion".equalsIgnoreCase(seat.id);
    }



}
