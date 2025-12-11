package com.spacewaltz.decrees.guilds;

import com.spacewaltz.decrees.DecreesOfTheSix;
import com.spacewaltz.decrees.economy.EconomyAccount;
import com.spacewaltz.decrees.economy.EconomyService;
import com.spacewaltz.decrees.economy.EconomyStore;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.UUID;

/**
 * Registers guild networking and handles snapshot requests for the G-key UI.
 */
public final class GuildNetworking {

    private GuildNetworking() {
    }

    public static void init() {
        // Register payload types
        PayloadTypeRegistry.playC2S().register(
                GuildSnapshotRequestC2SPayload.ID,
                GuildSnapshotRequestC2SPayload.CODEC
        );
        PayloadTypeRegistry.playS2C().register(
                GuildSnapshotS2CPayload.ID,
                GuildSnapshotS2CPayload.CODEC
        );

        // Handle client -> server snapshot requests
        ServerPlayNetworking.registerGlobalReceiver(
                GuildSnapshotRequestC2SPayload.ID,
                (payload, context) -> {
                    ServerPlayerEntity player = context.player();
                    MinecraftServer server = context.server();
                    if (player == null || server == null) {
                        return;
                    }

                    server.execute(() -> sendSnapshotTo(player));
                }
        );

        DecreesOfTheSix.LOGGER.info("Guild networking initialized");
    }

    private static void sendSnapshotTo(ServerPlayerEntity player) {
        if (player == null || player.getServer() == null) {
            return;
        }

        UUID uuid = player.getUuid();
        Guild guild = GuildStore.getGuildForPlayer(uuid);

        boolean inGuild = guild != null;

        String guildName = "";
        String myRoleKey = "";
        String myTitle = "";
        String leaderName = "";
        int totalMembers = 0;
        int officerCount = 0;
        int veteranCount = 0;
        int memberCount = 0;
        int recruitCount = 0;
        long treasuryCopper = 0L;

        if (guild != null) {
            guildName = guild.name != null ? guild.name : "";

            // Role & title for this player
            String myKey = uuid.toString();
            GuildRole myRole = guild.members != null ? guild.members.get(myKey) : null;
            myRoleKey = (myRole != null ? myRole.name() : "");
            myTitle = resolveTitle(guild, myRole);

            // Leader name resolution (online / offline)
            leaderName = resolveLeaderName(player, guild);

            // Member counts
            if (guild.members != null) {
                totalMembers = guild.members.size();
                for (GuildRole role : guild.members.values()) {
                    if (role == null) continue;
                    switch (role) {
                        case OFFICER -> officerCount++;
                        case VETERAN -> veteranCount++;
                        case MEMBER -> memberCount++;
                        case RECRUIT -> recruitCount++;
                        default -> {
                            // LEADER not counted in the breakdown, but included in totalMembers
                        }
                    }
                }
            }

            // Treasury balance
            if (guild.treasuryAccountId != null) {
                EconomyStore econStore = EconomyStore.get();
                EconomyAccount acc = econStore.accounts.get(guild.treasuryAccountId);
                if (acc != null) {
                    treasuryCopper = EconomyService.getBalanceCopper(acc);
                }
            }
        }

        int pendingInvites = GuildStore.getPendingInviteCount(uuid);

        GuildSnapshotS2CPayload snapshot = new GuildSnapshotS2CPayload(
                inGuild,
                guildName,
                myRoleKey,
                myTitle,
                leaderName,
                totalMembers,
                officerCount,
                veteranCount,
                memberCount,
                recruitCount,
                treasuryCopper,
                pendingInvites
        );

        ServerPlayNetworking.send(player, snapshot);
    }

    private static String resolveLeaderName(ServerPlayerEntity viewer, Guild guild) {
        if (guild == null || guild.leaderUuid == null || guild.leaderUuid.isBlank()) {
            return "";
        }

        try {
            UUID leaderUuid = UUID.fromString(guild.leaderUuid);
            if (leaderUuid.equals(viewer.getUuid())) {
                return viewer.getName().getString();
            }

            MinecraftServer server = viewer.getServer();
            if (server != null) {
                ServerPlayerEntity leaderOnline = server.getPlayerManager().getPlayer(leaderUuid);
                if (leaderOnline != null) {
                    return leaderOnline.getName().getString();
                }

                var profileOpt = server.getUserCache().getByUuid(leaderUuid);
                if (profileOpt.isPresent() && profileOpt.get().getName() != null) {
                    return profileOpt.get().getName();
                }
            }
        } catch (Exception ignored) {
        }

        return "";
    }

    private static String resolveTitle(Guild guild, GuildRole role) {
        if (guild == null || role == null) {
            return "";
        }

        switch (role) {
            case LEADER -> {
                if (guild.leaderTitle != null && !guild.leaderTitle.isBlank()) {
                    return guild.leaderTitle;
                }
                return "Leader";
            }
            case OFFICER -> {
                if (guild.officerTitle != null && !guild.officerTitle.isBlank()) {
                    return guild.officerTitle;
                }
                return "Officer";
            }
            case VETERAN -> {
                if (guild.veteranTitle != null && !guild.veteranTitle.isBlank()) {
                    return guild.veteranTitle;
                }
                return "Veteran";
            }
            case MEMBER -> {
                if (guild.memberTitle != null && !guild.memberTitle.isBlank()) {
                    return guild.memberTitle;
                }
                return "Member";
            }
            case RECRUIT -> {
                if (guild.recruitTitle != null && !guild.recruitTitle.isBlank()) {
                    return guild.recruitTitle;
                }
                return "Recruit";
            }
        }
        return "";
    }
}
