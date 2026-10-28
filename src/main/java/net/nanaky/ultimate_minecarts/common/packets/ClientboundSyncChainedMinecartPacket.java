package net.nanaky.ultimate_minecarts.common.packets;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.nanaky.ultimate_minecarts.UltimateMinecarts;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

public record ClientboundSyncChainedMinecartPacket(int parentId, int childId, int chainItemId)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ClientboundSyncChainedMinecartPacket> TYPE =
            new CustomPacketPayload.Type<>(UltimateMinecarts.id("sync_chained_minecart"));

    public static final StreamCodec<FriendlyByteBuf, ClientboundSyncChainedMinecartPacket> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, ClientboundSyncChainedMinecartPacket::parentId,
                    ByteBufCodecs.VAR_INT, ClientboundSyncChainedMinecartPacket::childId,
                    ByteBufCodecs.VAR_INT, ClientboundSyncChainedMinecartPacket::chainItemId,
                    ClientboundSyncChainedMinecartPacket::new
            );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void register() {
        PayloadTypeRegistry.clientboundPlay().register(TYPE, CODEC);
    }
}