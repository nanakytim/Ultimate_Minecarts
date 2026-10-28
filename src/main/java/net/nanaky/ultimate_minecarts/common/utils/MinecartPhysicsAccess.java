package net.nanaky.ultimate_minecarts.common.utils;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

public interface MinecartPhysicsAccess {
    boolean isSelfMovingOnRail();
    void applyCollisionDamage(ServerLevel level);
    void ultimate_minecarts$startGrace();
    default long ultimate_minecarts$getLastTick() { return -1L; }
    Vec3 ultimate_minecarts$getPreTickVelocity();
}