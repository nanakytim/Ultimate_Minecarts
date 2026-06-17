package net.nanaky.ultimate_minecarts;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
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

    private static void broadcastPendingClear(Player player, ItemStack stack) {
        if (!(player instanceof ServerPlayer sp)) return;
        int chainItemId = BuiltInRegistries.ITEM.getId(stack.getItem());
        ClientboundSyncPendingChainPacket pkt =
            new ClientboundSyncPendingChainPacket(sp.getId(), Optional.empty(), chainItemId);
        PlayerLookup.tracking(sp).forEach(other -> ServerPlayNetworking.send(other, pkt));
        ServerPlayNetworking.send(sp, pkt);
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

            ItemStack stack = player.getItemInHand(hand);

            if (!player.isShiftKeyDown() || !stack.is(LINKABLE_CHAINS))
                return InteractionResult.PASS;

            if (!(world instanceof ServerLevel server))
                return InteractionResult.SUCCESS;

            UUID uuid = stack.get(PARENT_ID);

            if (uuid != null && !cart.getUUID().equals(uuid)) {
                Entity found = server.getEntity(uuid);
                if (!(found instanceof AbstractMinecart A)) {
                    broadcastPendingClear(player, stack);
                    stack.remove(PARENT_ID);
                    return InteractionResult.SUCCESS;
                }
                AbstractMinecart B = cart;

                Linkable lA = (Linkable) A;
                Linkable lB = (Linkable) B;

                if (A.distanceTo(B) > 3) {
                    if (player instanceof ServerPlayer sp)
                        sp.sendSystemMessage(Component.translatable(MOD_ID + ".cant_link_too_far"));
                    broadcastPendingClear(player, stack);
                    stack.remove(PARENT_ID);
                    return InteractionResult.SUCCESS;
                }

                boolean aIsN = lA.isUnlinked();
                boolean aIsC = lA.isTail();
                boolean aIsP = lA.isHead();

                boolean bIsN = lB.isUnlinked();
                boolean bIsC = lB.isTail();
                boolean bIsP = lB.isHead();

                boolean aIsFurnace = A instanceof MinecartFurnace;
                boolean bIsFurnace = B instanceof MinecartFurnace;

                if (aIsFurnace && bIsFurnace) {
                    world.playSound(null, cart.getX(), cart.getY(), cart.getZ(),
                        SoundEvents.COPPER_BREAK, SoundSource.NEUTRAL, 1f, 1f);
                    broadcastPendingClear(player, stack);
                    stack.remove(PARENT_ID);
                    return InteractionResult.SUCCESS;
                }
                if (aIsFurnace && aIsP) {
                    world.playSound(null, cart.getX(), cart.getY(), cart.getZ(),
                        SoundEvents.COPPER_BREAK, SoundSource.NEUTRAL, 1f, 1f);
                    broadcastPendingClear(player, stack);
                    stack.remove(PARENT_ID);
                    return InteractionResult.SUCCESS;
                }
                if (bIsFurnace && bIsP) {
                    world.playSound(null, cart.getX(), cart.getY(), cart.getZ(),
                        SoundEvents.COPPER_BREAK, SoundSource.NEUTRAL, 1f, 1f);
                    broadcastPendingClear(player, stack);
                    stack.remove(PARENT_ID);
                    return InteractionResult.SUCCESS;
                }
                if (aIsFurnace && aIsC) {
                    world.playSound(null, cart.getX(), cart.getY(), cart.getZ(),
                        SoundEvents.COPPER_BREAK, SoundSource.NEUTRAL, 1f, 1f);
                    broadcastPendingClear(player, stack);
                    stack.remove(PARENT_ID);
                    return InteractionResult.SUCCESS;
                }
                if (bIsFurnace && bIsC) {
                    world.playSound(null, cart.getX(), cart.getY(), cart.getZ(),
                        SoundEvents.COPPER_BREAK, SoundSource.NEUTRAL, 1f, 1f);
                    broadcastPendingClear(player, stack);
                    stack.remove(PARENT_ID);
                    return InteractionResult.SUCCESS;
                }
                if (aIsC && bIsC) {
                    world.playSound(null, cart.getX(), cart.getY(), cart.getZ(),
                        SoundEvents.COPPER_BREAK, SoundSource.NEUTRAL, 1f, 1f);
                    broadcastPendingClear(player, stack);
                    stack.remove(PARENT_ID);
                    return InteractionResult.SUCCESS;
                }

                AbstractMinecart parentCart, childCart;

                if (aIsN && bIsN) {
                    if (aIsFurnace)      { parentCart = A; childCart = B; }
                    else if (bIsFurnace) { parentCart = B; childCart = A; }
                    else                 { parentCart = B; childCart = A; }
                    ((Linkable) childCart).setLinkedChain(stack.getItem());
                    Linkable.setParentChild((Linkable) parentCart, (Linkable) childCart);

                } else if (aIsN && bIsC) {
                    if (aIsFurnace) {
                        world.playSound(null, cart.getX(), cart.getY(), cart.getZ(),
                            SoundEvents.COPPER_BREAK, SoundSource.NEUTRAL, 1f, 1f);
                        broadcastPendingClear(player, stack);
                        stack.remove(PARENT_ID);
                        return InteractionResult.SUCCESS;
                    }
                    lA.setLinkedChain(stack.getItem());
                    Linkable.setParentChild(lB, lA);

                } else if (aIsC && bIsN) {
                    if (bIsFurnace) {
                        world.playSound(null, cart.getX(), cart.getY(), cart.getZ(),
                            SoundEvents.COPPER_BREAK, SoundSource.NEUTRAL, 1f, 1f);
                        broadcastPendingClear(player, stack);
                        stack.remove(PARENT_ID);
                        return InteractionResult.SUCCESS;
                    }
                    lB.setLinkedChain(stack.getItem());
                    Linkable.setParentChild(lA, lB);

                } else if (aIsP && !aIsFurnace && bIsN) {
                    lA.setLinkedChain(stack.getItem());
                    Linkable.setParentChild(lB, lA);

                } else if (aIsP && aIsFurnace && bIsN) {
                    world.playSound(null, cart.getX(), cart.getY(), cart.getZ(),
                        SoundEvents.COPPER_BREAK, SoundSource.NEUTRAL, 1f, 1f);
                    broadcastPendingClear(player, stack);
                    stack.remove(PARENT_ID);
                    return InteractionResult.SUCCESS;

                } else if (aIsP && !aIsFurnace && bIsC) {
                    if (wouldCreateCycle(A, B) || wouldCreateCycle(B, A)) {
                        world.playSound(null, cart.getX(), cart.getY(), cart.getZ(),
                            SoundEvents.COPPER_BREAK, SoundSource.NEUTRAL, 1f, 1f);
                        broadcastPendingClear(player, stack);
                        stack.remove(PARENT_ID);
                        return InteractionResult.SUCCESS;
                    }
                    lA.setLinkedChain(stack.getItem());
                    Linkable.setParentChild(lB, lA);

                } else if (aIsP && aIsFurnace && bIsC) {
                    world.playSound(null, cart.getX(), cart.getY(), cart.getZ(),
                        SoundEvents.COPPER_BREAK, SoundSource.NEUTRAL, 1f, 1f);
                    broadcastPendingClear(player, stack);
                    stack.remove(PARENT_ID);
                    return InteractionResult.SUCCESS;

                } else if (aIsN && bIsP && !bIsFurnace) {
                    lB.setLinkedChain(stack.getItem());
                    Linkable.setParentChild(lA, lB);

                } else if (aIsN && bIsP && bIsFurnace) {
                    world.playSound(null, cart.getX(), cart.getY(), cart.getZ(),
                        SoundEvents.COPPER_BREAK, SoundSource.NEUTRAL, 1f, 1f);
                    broadcastPendingClear(player, stack);
                    stack.remove(PARENT_ID);
                    return InteractionResult.SUCCESS;

                } else if (aIsC && bIsP && !bIsFurnace) {
                    if (wouldCreateCycle(A, B) || wouldCreateCycle(B, A)) {
                        world.playSound(null, cart.getX(), cart.getY(), cart.getZ(),
                            SoundEvents.COPPER_BREAK, SoundSource.NEUTRAL, 1f, 1f);
                        broadcastPendingClear(player, stack);
                        stack.remove(PARENT_ID);
                        return InteractionResult.SUCCESS;
                    }
                    lB.setLinkedChain(stack.getItem());
                    Linkable.setParentChild(lA, lB);

                } else if (aIsC && bIsP && bIsFurnace) {
                    world.playSound(null, cart.getX(), cart.getY(), cart.getZ(),
                        SoundEvents.COPPER_BREAK, SoundSource.NEUTRAL, 1f, 1f);
                    broadcastPendingClear(player, stack);
                    stack.remove(PARENT_ID);
                    return InteractionResult.SUCCESS;

                } else {
                    world.playSound(null, cart.getX(), cart.getY(), cart.getZ(),
                        SoundEvents.COPPER_BREAK, SoundSource.NEUTRAL, 1f, 1f);
                    broadcastPendingClear(player, stack);
                    stack.remove(PARENT_ID);
                    return InteractionResult.SUCCESS;
                }

                world.playSound(null, cart.getX(), cart.getY(), cart.getZ(),
                        SoundEvents.CHAIN_PLACE, SoundSource.NEUTRAL, 1f, 1f);
                broadcastPendingClear(player, stack);
                if (!player.isCreative()) stack.shrink(1);
                stack.remove(PARENT_ID);

            } else if (uuid != null && cart.getUUID().equals(uuid)) {
                world.playSound(null, cart.getX(), cart.getY(), cart.getZ(),
                        SoundEvents.COPPER_BREAK, SoundSource.NEUTRAL, 1f, 1f);
                broadcastPendingClear(player, stack);
                stack.remove(PARENT_ID);

            } else if (uuid == null) {
                stack.set(PARENT_ID, cart.getUUID());
                world.playSound(null, cart.getX(), cart.getY(), cart.getZ(),
                        SoundEvents.CHAIN_HIT, SoundSource.NEUTRAL, 1f, 1f);
                int chainItemId = BuiltInRegistries.ITEM.getId(stack.getItem());
                ClientboundSyncPendingChainPacket pkt = new ClientboundSyncPendingChainPacket(
                    player.getId(), Optional.of(cart.getUUID()), chainItemId);
                if (player instanceof ServerPlayer sp) {
                    PlayerLookup.tracking(sp).forEach(other -> ServerPlayNetworking.send(other, pkt));
                    ServerPlayNetworking.send(sp, pkt);
                }
            }
            return InteractionResult.SUCCESS;
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                for (InteractionHand hand : InteractionHand.values()) {
                    ItemStack stack = player.getItemInHand(hand);
                    UUID uuid = stack.get(PARENT_ID);
                    if (uuid == null) continue;

                    Entity cart = ((ServerLevel) player.level()).getEntity(uuid);
                    if (cart == null || player.distanceTo(cart) > 4) {
                        broadcastPendingClear(player, stack);
                        stack.remove(PARENT_ID);
                        if (cart != null) {
                            cart.level().playSound(null, cart.blockPosition(),
                                SoundEvents.CHAIN_BREAK, SoundSource.NEUTRAL, 1f, 1f);
                        }
                    }
                }
            }
        });
    }

    public static Identifier id(String name) {
        return Identifier.fromNamespaceAndPath(MOD_ID, name);
    }
}