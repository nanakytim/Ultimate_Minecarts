package net.nanaky.ultimate_minecarts.common.utils;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.entity.vehicle.minecart.MinecartFurnace;
import net.minecraft.world.entity.vehicle.minecart.MinecartHopper;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.nanaky.ultimate_minecarts.UltimateMinecartsConfig;
import net.nanaky.ultimate_minecarts.api.FuelAcceptor;
import net.nanaky.ultimate_minecarts.api.Linkable;

import java.util.Map;
import java.util.WeakHashMap;

public final class HopperFeed {
    private static final long COOLDOWN_TICKS = 8;

    private static final Map<MinecartHopper, Long> NEXT = new WeakHashMap<>();
    private static boolean warnedNoAcceptor = false;

    private HopperFeed() {}

    public static void tick(ServerLevel level, MinecartHopper hopper) {
        if (!UltimateMinecartsConfig.get().hopperMinecartOverhaul) return;
        if (!hopper.isAlive() || !hopper.isEnabled() || hopper.isEmpty()) return;

        long now = level.getGameTime();
        Long next = NEXT.get(hopper);
        if (next != null && now < next) return;

        Linkable link = (Linkable) hopper;
        boolean moved = feedParent(hopper, link.getLinkedParent())
                || feedChild(hopper, link.getLinkedChild());
        if (moved) {
            NEXT.put(hopper, now + COOLDOWN_TICKS);
            hopper.setChanged();
        }
    }

    private static boolean feedParent(MinecartHopper hopper, AbstractMinecart parent) {
        if (parent == null) return false;
        if (parent instanceof MinecartFurnace) return feedFuel(hopper, parent);
        if (parent instanceof Container container) return moveOneItem(hopper, container);
        return false;
    }

    private static boolean feedChild(MinecartHopper hopper, AbstractMinecart child) {
        if (child == null || child instanceof MinecartHopper) return false;
        if (child instanceof Container container) return moveOneItem(hopper, container);
        return false;
    }

    private static boolean moveOneItem(MinecartHopper from, Container to) {
        for (int slot = 0; slot < from.getContainerSize(); slot++) {
            ItemStack stack = from.getItem(slot);
            if (stack.isEmpty()) continue;

            ItemStack remainder = HopperBlockEntity.addItem(from, to, stack.copyWithCount(1), null);
            if (!remainder.isEmpty()) continue;

            stack.shrink(1);
            if (stack.isEmpty()) from.setItem(slot, ItemStack.EMPTY);
            to.setChanged();
            return true;
        }
        return false;
    }

    private static boolean feedFuel(MinecartHopper hopper, AbstractMinecart furnace) {
        if (!(furnace instanceof FuelAcceptor acceptor)) {
            if (!warnedNoAcceptor) {
                warnedNoAcceptor = true;
            }
            return false;
        }

        for (int slot = 0; slot < hopper.getContainerSize(); slot++) {
            ItemStack stack = hopper.getItem(slot);
            if (stack.isEmpty()) continue;
            if (!acceptor.ultimate_minecarts$acceptFuel(stack)
                    && !acceptor.ultimate_minecarts$acceptCoolant(stack)) continue;
            ItemStack leftover = ItemStack.EMPTY;
            if (stack.getItem() instanceof BucketItem) leftover = new ItemStack(Items.BUCKET);
            else if (stack.is(Items.POTION)) leftover = new ItemStack(Items.GLASS_BOTTLE);

            if (!leftover.isEmpty()) {
                ReturnItem.giveBack(hopper, slot, leftover);
            } else {
                stack.shrink(1);
                if (stack.isEmpty()) hopper.setItem(slot, ItemStack.EMPTY);
            }
            return true;
        }
        return false;
    }
}