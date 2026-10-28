package net.nanaky.ultimate_minecarts.common.utils;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.minecart.MinecartHopper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.HopperBlockEntity;

public final class ReturnItem {
    private ReturnItem() {}

    public static void giveBack(Player player, InteractionHand hand, ItemStack leftover) {
        if (player.isCreative()) return;

        ItemStack held = player.getItemInHand(hand);
        held.shrink(1);
        if (held.isEmpty()) {
            player.setItemInHand(hand, leftover);
        } else if (!player.getInventory().add(leftover) && !player.level().isClientSide()) {
            ItemEntity drop = new ItemEntity(player.level(),
                    player.getX(), player.getEyeY() - 0.3, player.getZ(), leftover);
            drop.setDefaultPickUpDelay();
            player.level().addFreshEntity(drop);
        }
    }

    public static void giveBack(MinecartHopper hopper, int slot, ItemStack leftover) {
        ItemStack stack = hopper.getItem(slot);
        stack.shrink(1);
        if (stack.isEmpty()) {
            hopper.setItem(slot, leftover);
            return;
        }

        ItemStack remainder = HopperBlockEntity.addItem(null, hopper, leftover, null);
        if (!remainder.isEmpty()) {
            ItemEntity drop = new ItemEntity(hopper.level(), hopper.getX(), hopper.getY(), hopper.getZ(), remainder);
            drop.setDefaultPickUpDelay();
            hopper.level().addFreshEntity(drop);
        }
    }
}