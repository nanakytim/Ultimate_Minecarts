package net.nanaky.ultimate_minecarts.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.item.Item;
import net.nanaky.ultimate_minecarts.api.IFurnaceMinecart;
import net.nanaky.ultimate_minecarts.api.Linkable;
import net.nanaky.ultimate_minecarts.common.packets.ClientboundSyncChainedMinecartPacket;
import net.nanaky.ultimate_minecarts.common.packets.ClientboundSyncFurnaceFuelPacket;
import net.nanaky.ultimate_minecarts.common.packets.ClientboundSyncPendingChainPacket;

import org.jetbrains.annotations.Nullable;

public class ClientPacketHandlers {
    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(
                ClientboundSyncChainedMinecartPacket.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                ClientLevel world = Minecraft.getInstance().level;
                if (world == null) return;
                @Nullable Entity parentEntity = world.getEntity(payload.parentId());
                @Nullable Entity childEntity  = world.getEntity(payload.childId());
                if (parentEntity instanceof Linkable l) l.setLinkedChildClient(payload.childId());
                if (childEntity  instanceof Linkable l) {
                    l.setLinkedParentClient(payload.parentId());
                    if (childEntity instanceof AbstractMinecart) {
                        Item chain = BuiltInRegistries.ITEM.byId(payload.chainItemId());
                        l.setLinkedChain(chain);
                    }
                }
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(
                ClientboundSyncFurnaceFuelPacket.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                ClientLevel world = Minecraft.getInstance().level;
                if (world == null) return;
                Entity entity = world.getEntity(payload.entityId());
                if (entity instanceof IFurnaceMinecart furnace)
                    furnace.ultimate_minecarts$setFuel(payload.fuel());
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(
                ClientboundSyncPendingChainPacket.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                if (payload.targetCartUUID().isPresent()) {
                    Item chain = BuiltInRegistries.ITEM.byId(payload.chainItemId());
                    PendingChainTracker.set(payload.playerEntityId(), payload.targetCartUUID().get(), chain);
                } else {
                    PendingChainTracker.clear(payload.playerEntityId());
                }
            });
        });
    }
}