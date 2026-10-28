package net.nanaky.ultimate_minecarts.mixin;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.item.ItemStack;
import net.nanaky.ultimate_minecarts.api.Linkable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public class EntityRemoveMixin {

    @Inject(method = "remove", at = @At("HEAD"))
    private void ultimate_minecarts$onRemove(Entity.RemovalReason reason, CallbackInfo info) {
        if (!((Object) this instanceof AbstractMinecart cart)) return;
        if (!(cart.level() instanceof ServerLevel serverLevel)) return;
        if (reason != Entity.RemovalReason.KILLED) return;

        Linkable linkable = (Linkable) cart;
        AbstractMinecart parent = linkable.getLinkedParent();
        AbstractMinecart child  = linkable.getLinkedChild();

        if (parent != null) {
            serverLevel.addFreshEntity(new net.minecraft.world.entity.item.ItemEntity(
                serverLevel, cart.getX(), cart.getY(), cart.getZ(),
                new ItemStack(linkable.getLinkedChain())));
            serverLevel.playSound(null, cart.getX(), cart.getY(), cart.getZ(),
                SoundEvents.CHAIN_BREAK, SoundSource.NEUTRAL, 1f,
                0.8f + cart.getRandom().nextFloat() * 0.4f);
            Linkable.unsetParentChild((Linkable) parent, linkable);
        }

        if (child != null) {
            serverLevel.addFreshEntity(new net.minecraft.world.entity.item.ItemEntity(
                serverLevel, cart.getX(), cart.getY(), cart.getZ(),
                new ItemStack(((Linkable) child).getLinkedChain())));
            serverLevel.playSound(null, cart.getX(), cart.getY(), cart.getZ(),
                SoundEvents.CHAIN_BREAK, SoundSource.NEUTRAL, 1f,
                0.8f + cart.getRandom().nextFloat() * 0.4f);
            Linkable.unsetParentChild(linkable, (Linkable) child);
        }
    }
}