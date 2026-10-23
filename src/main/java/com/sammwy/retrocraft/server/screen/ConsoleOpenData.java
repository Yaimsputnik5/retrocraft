package com.sammwy.retrocraft.server.screen;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.util.Identifier;

public record ConsoleOpenData(Identifier console, int lockedSlot) {
    public static final PacketCodec<RegistryByteBuf, ConsoleOpenData> CODEC =
            new PacketCodec<>() {
                public ConsoleOpenData decode(RegistryByteBuf b) {
                    return new ConsoleOpenData(b.readIdentifier(), b.readInt());
                }

                public void encode(RegistryByteBuf b, ConsoleOpenData data) {
                    b.writeIdentifier(data.console);
                    b.writeInt(data.lockedSlot);
                }
            };
}
