package net.nanaky.ultimate_minecarts.common.utils;

import net.minecraft.world.entity.Entity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class DismountGrace {
    public static final int TICKS = 60;
    private static final Map<UUID, Long> UNTIL = new HashMap<>();

    private DismountGrace() {}

    public static void grant(UUID id, long now) {
        UNTIL.values().removeIf(t -> t < now);
        UNTIL.put(id, now + TICKS);
    }

    public static boolean isImmune(Entity e, long now) {
        Long until = UNTIL.get(e.getUUID());
        if (until == null) return false;
        if (now > until || until - now > TICKS) {
            UNTIL.remove(e.getUUID());
            return false;
        }
        return true;
    }
}