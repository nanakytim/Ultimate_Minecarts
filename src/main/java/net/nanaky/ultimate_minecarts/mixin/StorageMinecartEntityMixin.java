package net.nanaky.ultimate_minecarts.mixin;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.minecart.MinecartChest;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.nanaky.ultimate_minecarts.UltimateMinecartsConfig;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MinecartChest.class)
public class StorageMinecartEntityMixin {

    @Inject(method = "interact", at = @At("HEAD"), cancellable = true)
    public void ultimate_minecarts$heckUMojang(Player player, InteractionHand hand,
                                        net.minecraft.world.phys.Vec3 hitPos,
                                        CallbackInfoReturnable<InteractionResult> info) {
        if (UltimateMinecartsConfig.get().linkingEnabled) {
            ItemStack stack = player.getItemInHand(hand);
            if (player.isShiftKeyDown() && stack.is(Items.IRON_CHAIN))
                info.setReturnValue(InteractionResult.SUCCESS);
        }
    }
}