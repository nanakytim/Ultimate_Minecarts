package net.nanaky.ultimate_minecarts.mixin;

import net.minecraft.client.renderer.entity.state.MinecartRenderState;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.nanaky.ultimate_minecarts.api.ChainRenderStateAccessor;

import java.util.UUID;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(MinecartRenderState.class)
public class ChainRenderStateMixin implements ChainRenderStateAccessor {
    @Unique private boolean ultimate_minecarts$hasParent = false;
    @Unique private double  ultimate_minecarts$parentX   = 0;
    @Unique private double  ultimate_minecarts$parentY   = 0;
    @Unique private double  ultimate_minecarts$parentZ   = 0;
    @Unique private Item    ultimate_minecarts$chainItem  = Items.IRON_CHAIN;
    @Unique private UUID ultimate_minecarts$selfUUID = null;
    @Unique private double ultimate_minecarts$playerDX = 0;
    @Unique private double ultimate_minecarts$playerDY = 0;
    @Unique private double ultimate_minecarts$playerDZ = 0;

    @Override public boolean ultimate_minecarts$hasParent()  { return ultimate_minecarts$hasParent; }
    @Override public double  ultimate_minecarts$parentX()    { return ultimate_minecarts$parentX; }
    @Override public double  ultimate_minecarts$parentY()    { return ultimate_minecarts$parentY; }
    @Override public double  ultimate_minecarts$parentZ()    { return ultimate_minecarts$parentZ; }
    @Override public Item    ultimate_minecarts$chainItem()  { return ultimate_minecarts$chainItem; }
    @Override public UUID ultimate_minecarts$selfUUID()              { return ultimate_minecarts$selfUUID; }
    @Override public double ultimate_minecarts$playerDX() { return ultimate_minecarts$playerDX; }
    @Override public double ultimate_minecarts$playerDY() { return ultimate_minecarts$playerDY; }
    @Override public double ultimate_minecarts$playerDZ() { return ultimate_minecarts$playerDZ; }

    @Override public void ultimate_minecarts$setHasParent(boolean v) { ultimate_minecarts$hasParent = v; }
    @Override public void ultimate_minecarts$setParentX(double v)    { ultimate_minecarts$parentX = v; }
    @Override public void ultimate_minecarts$setParentY(double v)    { ultimate_minecarts$parentY = v; }
    @Override public void ultimate_minecarts$setParentZ(double v)    { ultimate_minecarts$parentZ = v; }
    @Override public void ultimate_minecarts$setChainItem(Item v)    { ultimate_minecarts$chainItem = v; }
    @Override public void ultimate_minecarts$setSelfUUID(UUID uuid)  { ultimate_minecarts$selfUUID = uuid; }
    @Override public void ultimate_minecarts$setPlayerDX(double v) { ultimate_minecarts$playerDX = v; }
    @Override public void ultimate_minecarts$setPlayerDY(double v) { ultimate_minecarts$playerDY = v; }
    @Override public void ultimate_minecarts$setPlayerDZ(double v) { ultimate_minecarts$playerDZ = v; }
}