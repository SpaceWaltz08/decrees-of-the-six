package com.spacewaltz.decrees.economy;

import com.spacewaltz.decrees.DecreesOfTheSix;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Client → Server: asks for a fresh economy snapshot.
 * No fields, it's just a ping.
 */
public record EconomySnapshotRequestC2SPayload() implements CustomPayload {

    public static final Identifier RAW_ID =
            Identifier.of(DecreesOfTheSix.MOD_ID, "economy_snapshot_request");

    public static final CustomPayload.Id<EconomySnapshotRequestC2SPayload> ID =
            new CustomPayload.Id<>(RAW_ID);

    // No data → unit codec is enough.
    public static final PacketCodec<RegistryByteBuf, EconomySnapshotRequestC2SPayload> CODEC =
            PacketCodec.unit(new EconomySnapshotRequestC2SPayload());

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
