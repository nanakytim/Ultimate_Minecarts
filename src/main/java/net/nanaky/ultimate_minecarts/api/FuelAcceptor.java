package net.nanaky.ultimate_minecarts.api;

import net.minecraft.world.item.ItemStack;

public interface FuelAcceptor {
    boolean ultimate_minecarts$acceptFuel(ItemStack stack);
    boolean ultimate_minecarts$acceptCoolant(ItemStack stack);
}