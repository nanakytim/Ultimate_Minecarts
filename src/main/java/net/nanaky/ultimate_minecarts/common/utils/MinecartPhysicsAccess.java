package net.nanaky.ultimate_minecarts.common.utils;

import net.minecraft.server.level.ServerLevel;

public interface MinecartPhysicsAccess {
    boolean isSelfMovingOnRail();

    void applyCollisionDamage(ServerLevel level);
    void ultimate_minecarts$startGrace();
}