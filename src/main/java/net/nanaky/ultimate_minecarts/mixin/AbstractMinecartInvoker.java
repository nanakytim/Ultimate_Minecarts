package net.nanaky.ultimate_minecarts.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(AbstractMinecart.class)
public interface AbstractMinecartInvoker {
    @Invoker("getMaxSpeed")
    double invokeGetMaxSpeed(ServerLevel level);
}