package net.nanaky.ultimate_minecarts.api;

import java.util.UUID;

import net.minecraft.world.item.Item;

public interface ChainRenderStateAccessor {
    boolean ultimate_minecarts$hasParent();
    double  ultimate_minecarts$parentX();
    double  ultimate_minecarts$parentY();
    double  ultimate_minecarts$parentZ();
    Item    ultimate_minecarts$chainItem();
    UUID ultimate_minecarts$selfUUID();
    double ultimate_minecarts$playerDX();
    double ultimate_minecarts$playerDY();
    double ultimate_minecarts$playerDZ();

    void ultimate_minecarts$setHasParent(boolean v);
    void ultimate_minecarts$setParentX(double v);
    void ultimate_minecarts$setParentY(double v);
    void ultimate_minecarts$setParentZ(double v);
    void ultimate_minecarts$setChainItem(Item v);
    void ultimate_minecarts$setSelfUUID(UUID uuid);
    void ultimate_minecarts$setPlayerDX(double v);
    void ultimate_minecarts$setPlayerDY(double v);
    void ultimate_minecarts$setPlayerDZ(double v);
}