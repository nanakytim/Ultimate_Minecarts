package net.nanaky.ultimate_minecarts.api;

import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface Linkable {

    default @Nullable AbstractMinecart getLinkedParent() { return null; }
    default void setLinkedParent(@Nullable AbstractMinecart parent) {}

    default @Nullable AbstractMinecart getLinkedChild() { return null; }
    default void setLinkedChild(@Nullable AbstractMinecart child) {}

    default void setLinkedParentClient(int networkId) {}
    default void setLinkedChildClient(int networkId) {}

    default void setLinkedChain(Item chain) {}
    default Item getLinkedChain() { return Items.IRON_CHAIN; }

    default AbstractMinecart asAbstractMinecart() {
        return (AbstractMinecart) this;
    }

    default boolean isUnlinked() {
        return getLinkedParent() == null && getLinkedChild() == null;
    }

    default boolean isTail() {
        return getLinkedParent() != null && getLinkedChild() == null;
    }

    default boolean isHead() {
        return getLinkedParent() == null && getLinkedChild() != null;
    }

    default boolean isMiddle() {
        return getLinkedParent() != null && getLinkedChild() != null;
    }

    static void setParentChild(@NotNull Linkable parent, @NotNull Linkable child) {
        if (parent.getLinkedChild() instanceof Linkable existingChild)
            unsetParentChild(parent, existingChild);
        if (child.getLinkedParent() instanceof Linkable existingParent)
            unsetParentChild(existingParent, child);

        parent.setLinkedChild(child.asAbstractMinecart());
        child.setLinkedParent(parent.asAbstractMinecart());
    }

    static void unsetParentChild(@Nullable Linkable parent, @Nullable Linkable child) {
        if (parent != null) parent.setLinkedChild(null);
        if (child != null) child.setLinkedParent(null);
    }
}