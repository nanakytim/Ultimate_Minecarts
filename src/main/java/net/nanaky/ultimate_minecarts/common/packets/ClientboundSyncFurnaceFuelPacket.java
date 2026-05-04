package net.nanaky.ultimate_minecarts.common.packets;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.nanaky.ultimate_minecarts.UltimateMinecarts;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

public record ClientboundSyncFurnaceFuelPacket(int entityId, int fuel)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ClientboundSyncFurnaceFuelPacket> TYPE =
            new CustomPacketPayload.Type<>(UltimateMinecarts.id("sync_furnace_fuel"));

    public static final StreamCodec<FriendlyByteBuf, ClientboundSyncFurnaceFuelPacket> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, ClientboundSyncFurnaceFuelPacket::entityId,
                    ByteBufCodecs.VAR_INT, ClientboundSyncFurnaceFuelPacket::fuel,
                    ClientboundSyncFurnaceFuelPacket::new
            );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void register() {
        PayloadTypeRegistry.clientboundPlay().register(TYPE, CODEC);
    }
}