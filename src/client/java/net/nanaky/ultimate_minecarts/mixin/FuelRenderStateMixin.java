package net.nanaky.ultimate_minecarts.mixin;

import net.minecraft.client.renderer.entity.state.MinecartRenderState;
import net.nanaky.ultimate_minecarts.api.FuelRenderStateAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(MinecartRenderState.class)
public class FuelRenderStateMixin implements FuelRenderStateAccessor {

    @Unique private int   ultimate_minecarts$fuel     = -1;
    @Unique private int   ultimate_minecarts$maxFuel  =  0;
    @Unique private int   ultimate_minecarts$entityId = -1;
    @Unique private float ultimate_minecarts$nameTagY =  0f;

    @Override public int   ultimate_minecarts$getFuel()      { return ultimate_minecarts$fuel; }
    @Override public int   ultimate_minecarts$getMaxFuel()   { return ultimate_minecarts$maxFuel; }
    @Override public int   ultimate_minecarts$getEntityId()  { return ultimate_minecarts$entityId; }
    @Override public float ultimate_minecarts$getNameTagY()  { return ultimate_minecarts$nameTagY; }
    @Override public void  ultimate_minecarts$setFuel(int v)      { ultimate_minecarts$fuel     = v; }
    @Override public void  ultimate_minecarts$setMaxFuel(int v)   { ultimate_minecarts$maxFuel  = v; }
    @Override public void  ultimate_minecarts$setEntityId(int v)  { ultimate_minecarts$entityId = v; }
    @Override public void  ultimate_minecarts$setNameTagY(float v){ ultimate_minecarts$nameTagY = v; }
}