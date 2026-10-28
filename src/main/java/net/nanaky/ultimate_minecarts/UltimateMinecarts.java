package net.nanaky.ultimate_minecarts;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.EntityTrackingEvents;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.entity.vehicle.minecart.Minecart;
import net.minecraft.world.entity.vehicle.minecart.MinecartFurnace;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.nanaky.ultimate_minecarts.api.IFurnaceMinecart;
import net.nanaky.ultimate_minecarts.api.Linkable;
import net.nanaky.ultimate_minecarts.common.blocks.FurnaceMinecartModel;
import net.nanaky.ultimate_minecarts.common.packets.ClientboundSyncChainedMinecartPacket;
import net.nanaky.ultimate_minecarts.common.packets.ClientboundSyncFurnaceFuelPacket;
import net.nanaky.ultimate_minecarts.common.packets.ClientboundSyncPendingChainPacket;
import net.nanaky.ultimate_minecarts.common.utils.ChainConstraint;
import net.nanaky.ultimate_minecarts.common.utils.PendingChains;
import net.nanaky.ultimate_minecarts.common.utils.XtraCodecs;

import java.util.Optional;
import java.util.UUID;

public class UltimateMinecarts implements ModInitializer {
    public static final String MOD_ID = "ultimate_minecarts";
    public static FurnaceMinecartModel ULTIMATE_MINECART;
    public static DataComponentType<UUID> PARENT_ID;
    public static ResourceKey<DamageType> MINECART_DAMAGE;

    public static final TagKey<Item> LINKABLE_CHAINS = TagKey.create(
        Registries.ITEM, id("linkable_chains"));

    private static boolean wouldCreateCycle(AbstractMinecart parent, AbstractMinecart child) {
        AbstractMinecart cursor = parent;
        int depth = 0;
        while (cursor != null && depth++ < 64) {
            if (cursor.getUUID().equals(child.getUUID())) return true;
            cursor = ((Linkable) cursor).getLinkedParent();
        }
        return false;
    }

    private static boolean tryLink(AbstractMinecart A, AbstractMinecart B, Item chain) {
        Linkable lA = (Linkable) A, lB = (Linkable) B;
        boolean aN = lA.isUnlinked(), aC = lA.isTail(), aP = lA.isHead();
        boolean bN = lB.isUnlinked(), bC = lB.isTail(), bP = lB.isHead();
        boolean aF = A instanceof MinecartFurnace, bF = B instanceof MinecartFurnace;

        if (aF && bF) return false;
        if (aF && (aP || aC)) return false;
        if (bF && (bP || bC)) return false;
        if (aC && bC) return false;

        Linkable parent, child;
        if (aN && bN)       { if (aF) { parent = lA; child = lB; } else { parent = lB; child = lA; } }
        else if (aN && bC)  { if (aF) return false; parent = lB; child = lA; }
        else if (aC && bN)  { if (bF) return false; parent = lA; child = lB; }
        else if (aP && bN)  { parent = lB; child = lA; }
        else if (aP && bC)  { if (wouldCreateCycle(A, B) || wouldCreateCycle(B, A)) return false; parent = lB; child = lA; }
        else if (aN && bP)  { parent = lA; child = lB; }
        else if (aC && bP)  { if (wouldCreateCycle(A, B) || wouldCreateCycle(B, A)) return false; parent = lA; child = lB; }
        else return false;

        child.setLinkedChain(chain);
        Linkable.setParentChild(parent, child);
        return true;
    }

    @Override
    public void onInitialize() {
        UltimateMinecartsConfig.load();
        ResourceKey<Block> displayKey = ResourceKey.create(Registries.BLOCK, id("ultimate_minecart"));
        ULTIMATE_MINECART = new FurnaceMinecartModel(
            BlockBehaviour.Properties.ofFullCopy(Blocks.BLAST_FURNACE)
                .noCollision()
                .noOcclusion()
                .setId(displayKey));
        Registry.register(BuiltInRegistries.BLOCK, displayKey, ULTIMATE_MINECART);
        ResourceKey<DataComponentType<?>> parentIdKey =
                ResourceKey.create(Registries.DATA_COMPONENT_TYPE, id("parent_id"));
        PARENT_ID = DataComponentType.<UUID>builder()
                .persistent(XtraCodecs.UUID_CODEC)
                .networkSynchronized(XtraCodecs.UUID_STREAM_CODEC)
                .build();
        Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, id("parent_id"), PARENT_ID);
        MINECART_DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE, id("minecart"));

        ClientboundSyncChainedMinecartPacket.register();
        ClientboundSyncFurnaceFuelPacket.register();
        ClientboundSyncPendingChainPacket.register();

        PendingChains.register();
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
        PendingChains.refund(handler.getPlayer(), false));
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
        PendingChains.refund(handler.getPlayer(), true));

        EntityTrackingEvents.START_TRACKING.register((tracked, player) -> {
            if (tracked instanceof IFurnaceMinecart furnace) {
                ServerPlayNetworking.send(player, new ClientboundSyncFurnaceFuelPacket(
                    tracked.getId(), furnace.ultimate_minecarts$getFuel()));
            }
        });

        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (!(entity instanceof Minecart ridableCart))
                return InteractionResult.PASS;

            Linkable linkableCart = (Linkable) ridableCart;
            AbstractMinecart parent = linkableCart.getLinkedParent();
            AbstractMinecart child  = linkableCart.getLinkedChild();
            ItemStack stack = player.getItemInHand(hand);
            Item item = stack.getItem();

            EntityType<? extends AbstractMinecart> targetType = null;
            if (item == Items.FURNACE) targetType = EntityTypes.FURNACE_MINECART;
            if (item == Items.CHEST)   targetType = EntityTypes.CHEST_MINECART;
            if (item == Items.TNT)     targetType = EntityTypes.TNT_MINECART;
            if (item == Items.HOPPER)  targetType = EntityTypes.HOPPER_MINECART;

            if (targetType == null || !(world instanceof ServerLevel serverLevel))
                return InteractionResult.PASS;

            AbstractMinecart minecart = targetType.create(serverLevel, EntitySpawnReason.MOB_SUMMONED);
            if (minecart == null)
                return InteractionResult.PASS;

            minecart.copyPosition(ridableCart);
            serverLevel.addFreshEntity(minecart);

            if (parent != null) {
                Linkable.unsetParentChild((Linkable) parent, linkableCart);
                Linkable.setParentChild((Linkable) parent, (Linkable) minecart);
            }
            if (child != null) {
                Linkable.unsetParentChild(linkableCart, (Linkable) child);
                Linkable.setParentChild((Linkable) minecart, (Linkable) child);
            }

            ridableCart.remove(Entity.RemovalReason.DISCARDED);

            if (!player.isCreative())
                stack.shrink(1);

            return InteractionResult.SUCCESS;
        });

        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (!(entity instanceof MinecartFurnace cart)) return InteractionResult.PASS;
            if (player.getItemInHand(hand).getItem() != Items.WATER_BUCKET) return InteractionResult.PASS;
            if (!(world instanceof ServerLevel serverLevel)) return InteractionResult.SUCCESS;

            IFurnaceMinecart mixin = (IFurnaceMinecart) cart;
            if (mixin.ultimate_minecarts$getFuel() <= 0) return InteractionResult.PASS;

            mixin.ultimate_minecarts$extinguish(player, serverLevel);
            return InteractionResult.SUCCESS;
        });

        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (!(entity instanceof AbstractMinecart cart) || !UltimateMinecartsConfig.get().linkingEnabled)
                return InteractionResult.PASS;
            if (!player.isShiftKeyDown())
                return InteractionResult.PASS;

            ItemStack stack = player.getItemInHand(hand);

            if (!(world instanceof ServerLevel server) || !(player instanceof ServerPlayer sp)) {
                boolean pending = net.nanaky.ultimate_minecarts.common.utils.PendingChainTracker
                        .getTarget(player.getId()) != null;
                return (stack.is(LINKABLE_CHAINS) || pending)
                        ? InteractionResult.SUCCESS : InteractionResult.PASS;
            }

            PendingChains.Pending pending = PendingChains.get(sp);

            if (pending == null) {
                if (!stack.is(LINKABLE_CHAINS)) return InteractionResult.PASS;
                Item chain = stack.getItem();
                boolean refundable = !player.isCreative();
                if (refundable) stack.shrink(1);
                PendingChains.begin(sp, cart, chain, refundable);
                world.playSound(null, cart.getX(), cart.getY(), cart.getZ(),
                        SoundEvents.CHAIN_HIT, SoundSource.NEUTRAL, 1f, 1f);
                return InteractionResult.SUCCESS;
            }

            boolean linked = false;
            if (!cart.getUUID().equals(pending.cart())
                    && server.getEntity(pending.cart()) instanceof AbstractMinecart A
                    && A.distanceTo(cart) <= 3) {
                linked = tryLink(A, cart, pending.item());
            }

            if (linked) {
                world.playSound(null, cart.getX(), cart.getY(), cart.getZ(),
                        SoundEvents.CHAIN_PLACE, SoundSource.NEUTRAL, 1f, 1f);
                PendingChains.clear(sp, true);
            } else {
                world.playSound(null, cart.getX(), cart.getY(), cart.getZ(),
                        SoundEvents.COPPER_BREAK, SoundSource.NEUTRAL, 1f, 1f);
                PendingChains.refund(sp, true);
            }
            return InteractionResult.SUCCESS;
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            PendingChains.tick(server);
            ChainConstraint.solve();
        });
    }

    public static Identifier id(String name) {
        return Identifier.fromNamespaceAndPath(MOD_ID, name);
    }
}