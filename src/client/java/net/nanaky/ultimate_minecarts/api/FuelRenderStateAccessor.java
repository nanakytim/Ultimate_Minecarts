package net.nanaky.ultimate_minecarts.api;

public interface FuelRenderStateAccessor {
    int   ultimate_minecarts$getFuel();
    int   ultimate_minecarts$getMaxFuel();
    int   ultimate_minecarts$getEntityId();
    float ultimate_minecarts$getNameTagY();
    void  ultimate_minecarts$setFuel(int fuel);
    void  ultimate_minecarts$setMaxFuel(int maxFuel);
    void  ultimate_minecarts$setEntityId(int id);
    void  ultimate_minecarts$setNameTagY(float y);
}