package net.nanaky.ultimate_minecarts.common.packets;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.nanaky.ultimate_minecarts.UltimateMinecarts;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import java.util.Optional;
import java.util.UUID;

public record ClientboundSyncPendingChainPacket(int playerEntityId, Optional<UUID> targetCartUUID, int chainItemId)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ClientboundSyncPendingChainPacket> TYPE =
            new CustomPacketPayload.Type<>(UltimateMinecarts.id("sync_pending_chain"));

    public static final StreamCodec<FriendlyByteBuf, ClientboundSyncPendingChainPacket> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT,          ClientboundSyncPendingChainPacket::playerEntityId,
                    ByteBufCodecs.optional(ByteBufCodecs.fromCodec(net.minecraft.core.UUIDUtil.CODEC)),
                                                    ClientboundSyncPendingChainPacket::targetCartUUID,
                    ByteBufCodecs.VAR_INT,          ClientboundSyncPendingChainPacket::chainItemId,
                    ClientboundSyncPendingChainPacket::new
            );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void register() {
        PayloadTypeRegistry.clientboundPlay().register(TYPE, CODEC);
    }
}