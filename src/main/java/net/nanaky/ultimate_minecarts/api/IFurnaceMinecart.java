package net.nanaky.ultimate_minecarts.api;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;

public interface IFurnaceMinecart {
    int  ultimate_minecarts$getFuel();
    void ultimate_minecarts$setFuel(int fuel);
    void ultimate_minecarts$extinguish(Player player, ServerLevel serverLevel);
}