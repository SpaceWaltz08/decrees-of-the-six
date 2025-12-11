package com.spacewaltz.decrees.guilds;

import com.spacewaltz.decrees.DecreesOfTheSix;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Client -> Server: request a fresh guild snapshot.
 * No fields, just a ping.
 */
public record GuildSnapshotRequestC2SPayload() implements CustomPayload {

    public static final Identifier RAW_ID =
            Identifier.of(DecreesOfTheSix.MOD_ID, "guild_snapshot_request");

    public static final CustomPayload.Id<GuildSnapshotRequestC2SPayload> ID =
            new CustomPayload.Id<>(RAW_ID);

    public static final PacketCodec<RegistryByteBuf, GuildSnapshotRequestC2SPayload> CODEC =
            PacketCodec.unit(new GuildSnapshotRequestC2SPayload());

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
