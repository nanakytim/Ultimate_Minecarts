package net.nanaky.ultimate_minecarts.mixin;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.item.Item;
import net.nanaky.ultimate_minecarts.api.Linkable;
import net.nanaky.ultimate_minecarts.common.packets.ClientboundSyncChainedMinecartPacket;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerEntity.class)
public class EntityTrackerEntryMixin {

    @Shadow @Final
    private Entity entity;

    @Inject(
        method = "addPairing",
        at = @At(value = "INVOKE",
                 target = "Lnet/minecraft/world/entity/Entity;startSeenByPlayer(Lnet/minecraft/server/level/ServerPlayer;)V")
    )
    public void ultimate_minecarts$sendLinkingInitData(ServerPlayer player, CallbackInfo ci) {
        if (!(this.entity instanceof AbstractMinecart minecart)) return;
        if (!(minecart instanceof Linkable linkable)) return;

        AbstractMinecart parent = linkable.getLinkedParent();
        AbstractMinecart child  = linkable.getLinkedChild();
        int chainId = BuiltInRegistries.ITEM.getId(linkable.getLinkedChain());

        ServerPlayNetworking.send(player,
            new ClientboundSyncChainedMinecartPacket(
                parent != null ? parent.getId() : -1,
                entity.getId(),
                chainId
            )
        );

        if (child instanceof Linkable childLinkable) {
            int childChainId = BuiltInRegistries.ITEM.getId(childLinkable.getLinkedChain());
            ServerPlayNetworking.send(player,
                new ClientboundSyncChainedMinecartPacket(
                    entity.getId(),
                    child.getId(),
                    childChainId
                )
            );
        } else {
            ServerPlayNetworking.send(player,
                new ClientboundSyncChainedMinecartPacket(
                    entity.getId(),
                    child != null ? child.getId() : -1,
                    chainId
                )
            );
        }
    }
}