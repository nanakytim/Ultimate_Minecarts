package net.nanaky.ultimate_minecarts.client;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PendingChainTracker {

    public record PendingEntry(UUID targetCartUUID, Item chainItem) {}

    private static final Map<Integer, PendingEntry> PENDING = new ConcurrentHashMap<>();

    public static void set(int playerEntityId, UUID targetCartUUID, Item chainItem) {
        PENDING.put(playerEntityId, new PendingEntry(targetCartUUID, chainItem));
    }

    public static void clear(int playerEntityId) {
        PENDING.remove(playerEntityId);
    }

    public static UUID getTarget(int playerEntityId) {
        PendingEntry e = PENDING.get(playerEntityId);
        return e != null ? e.targetCartUUID : null;
    }

    public static Item getChain(int playerEntityId) {
        PendingEntry e = PENDING.get(playerEntityId);
        return e != null ? e.chainItem : Items.IRON_CHAIN;
    }

    public static void clearAll() {
        PENDING.clear();
    }
}