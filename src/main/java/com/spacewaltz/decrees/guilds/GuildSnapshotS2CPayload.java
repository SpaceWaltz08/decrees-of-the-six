package com.spacewaltz.decrees.guilds;

import com.spacewaltz.decrees.DecreesOfTheSix;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Server -> Client snapshot of guild state for the G-key UI.
 */
public record GuildSnapshotS2CPayload(
        boolean inGuild,
        String guildName,
        String myRoleKey,
        String myTitle,
        String leaderName,
        int totalMembers,
        int officerCount,
        int veteranCount,
        int memberCount,
        int recruitCount,
        long treasuryBalanceCopper,
        int pendingInvites
) implements CustomPayload {

    public static final Identifier RAW_ID =
            Identifier.of(DecreesOfTheSix.MOD_ID, "guild_snapshot");

    public static final CustomPayload.Id<GuildSnapshotS2CPayload> ID =
            new CustomPayload.Id<>(RAW_ID);

    public static final PacketCodec<RegistryByteBuf, GuildSnapshotS2CPayload> CODEC =
            PacketCodec.of(GuildSnapshotS2CPayload::write, GuildSnapshotS2CPayload::read);

    private static GuildSnapshotS2CPayload read(RegistryByteBuf buf) {
        boolean inGuild = buf.readBoolean();
        String guildName = buf.readString();
        String myRoleKey = buf.readString();
        String myTitle = buf.readString();
        String leaderName = buf.readString();

        int totalMembers = buf.readVarInt();
        int officerCount = buf.readVarInt();
        int veteranCount = buf.readVarInt();
        int memberCount = buf.readVarInt();
        int recruitCount = buf.readVarInt();

        long treasuryCopper = buf.readVarLong();
        int pendingInvites = buf.readVarInt();

        return new GuildSnapshotS2CPayload(
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
    }

    private void write(RegistryByteBuf buf) {
        buf.writeBoolean(this.inGuild);
        buf.writeString(this.guildName);
        buf.writeString(this.myRoleKey);
        buf.writeString(this.myTitle);
        buf.writeString(this.leaderName);

        buf.writeVarInt(this.totalMembers);
        buf.writeVarInt(this.officerCount);
        buf.writeVarInt(this.veteranCount);
        buf.writeVarInt(this.memberCount);
        buf.writeVarInt(this.recruitCount);

        buf.writeVarLong(this.treasuryBalanceCopper);
        buf.writeVarInt(this.pendingInvites);
    }

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
