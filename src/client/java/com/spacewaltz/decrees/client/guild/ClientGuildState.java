package com.spacewaltz.decrees.client.guild;

import com.spacewaltz.decrees.guilds.GuildSnapshotRequestC2SPayload;
import com.spacewaltz.decrees.guilds.GuildSnapshotS2CPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;

/**
 * Client-side cache of guild state for the G-key UI.
 */
public final class ClientGuildState {

    private static boolean inGuild = false;
    private static String guildName = "";
    private static String myRoleKey = "";
    private static String myTitle = "";
    private static String leaderName = "";
    private static int totalMembers = 0;
    private static int officerCount = 0;
    private static int veteranCount = 0;
    private static int memberCount = 0;
    private static int recruitCount = 0;
    private static long treasuryCopper = 0L;
    private static int pendingInvites = 0;

    private ClientGuildState() {
    }

    public static void requestSnapshot() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getNetworkHandler() == null) {
            return;
        }

        ClientPlayNetworking.send(new GuildSnapshotRequestC2SPayload());
    }

    public static void handleSnapshot(GuildSnapshotS2CPayload payload) {
        if (payload == null) {
            inGuild = false;
            guildName = "";
            myRoleKey = "";
            myTitle = "";
            leaderName = "";
            totalMembers = officerCount = veteranCount = memberCount = recruitCount = 0;
            treasuryCopper = 0L;
            pendingInvites = 0;
            return;
        }

        inGuild = payload.inGuild();
        guildName = payload.guildName();
        myRoleKey = payload.myRoleKey();
        myTitle = payload.myTitle();
        leaderName = payload.leaderName();
        totalMembers = payload.totalMembers();
        officerCount = payload.officerCount();
        veteranCount = payload.veteranCount();
        memberCount = payload.memberCount();
        recruitCount = payload.recruitCount();
        treasuryCopper = payload.treasuryBalanceCopper();
        pendingInvites = payload.pendingInvites();
    }

    // ---- Getters used by the UI ----

    public static boolean isInGuild() {
        return inGuild;
    }

    public static String getGuildName() {
        return guildName;
    }

    public static String getMyRoleKey() {
        return myRoleKey;
    }

    public static String getMyTitle() {
        return myTitle;
    }

    public static String getLeaderName() {
        return leaderName;
    }

    public static int getTotalMembers() {
        return totalMembers;
    }

    public static int getOfficerCount() {
        return officerCount;
    }

    public static int getVeteranCount() {
        return veteranCount;
    }

    public static int getMemberCount() {
        return memberCount;
    }

    public static int getRecruitCount() {
        return recruitCount;
    }

    public static long getTreasuryCopper() {
        return treasuryCopper;
    }

    public static int getPendingInvites() {
        return pendingInvites;
    }
}
