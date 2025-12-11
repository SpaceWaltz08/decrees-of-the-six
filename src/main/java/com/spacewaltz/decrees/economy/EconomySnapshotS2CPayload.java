package com.spacewaltz.decrees.economy;

import com.spacewaltz.decrees.DecreesOfTheSix;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * Server → Client snapshot:
 * - balance in base units (e.g. copper)
 * - up to N formatted ledger lines.
 */
public record EconomySnapshotS2CPayload(long balanceCopper,
                                        List<String> recentLines) implements CustomPayload {

    public static final Identifier RAW_ID =
            Identifier.of(DecreesOfTheSix.MOD_ID, "economy_snapshot");

    public static final CustomPayload.Id<EconomySnapshotS2CPayload> ID =
            new CustomPayload.Id<>(RAW_ID);

    public static final PacketCodec<RegistryByteBuf, EconomySnapshotS2CPayload> CODEC =
            PacketCodec.of(EconomySnapshotS2CPayload::write, EconomySnapshotS2CPayload::read);

    private static EconomySnapshotS2CPayload read(RegistryByteBuf buf) {
        long balance = buf.readVarLong();

        int size = buf.readVarInt();
        List<String> lines = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            lines.add(buf.readString());
        }

        return new EconomySnapshotS2CPayload(balance, lines);
    }

    private void write(RegistryByteBuf buf) {
        buf.writeVarLong(this.balanceCopper);

        buf.writeVarInt(this.recentLines.size());
        for (String line : this.recentLines) {
            buf.writeString(line);
        }
    }

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
