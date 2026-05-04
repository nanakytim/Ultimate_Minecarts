package net.nanaky.ultimate_minecarts;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
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
import net.nanaky.ultimate_minecarts.common.utils.XtraCodecs;
import net.nanaky.ultimate_minecarts.mixin.FurnaceMinecartEntityMixin;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class UltimateMinecarts implements ModInitializer {
    public static final String MOD_ID = "ultimate_minecarts";
    public static FurnaceMinecartModel ULTIMATE_MINECART;
    public static DataComponentType<UUID> PARENT_ID;
    public static ResourceKey<DamageType> MINECART_DAMAGE;

    public static final TagKey<Item> LINKABLE_CHAINS = TagKey.create(
    Registries.ITEM, id("linkable_chains"));

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

        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (!(entity instanceof Minecart ridableCart))
                return InteractionResult.PASS;

            Linkable linkableCart = (Linkable) ridableCart;
            AbstractMinecart parent = linkableCart.getLinkedParent();
            AbstractMinecart child  = linkableCart.getLinkedChild();
            ItemStack stack = player.getItemInHand(hand);
            Item item = stack.getItem();

            EntityType<? extends AbstractMinecart> targetType = null;
            if (item == Items.FURNACE) targetType = EntityType.FURNACE_MINECART;
            if (item == Items.CHEST)   targetType = EntityType.CHEST_MINECART;
            if (item == Items.TNT)     targetType = EntityType.TNT_MINECART;
            if (item == Items.HOPPER)  targetType = EntityType.HOPPER_MINECART;

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
                    stack.remove(PARENT_ID);
                    return InteractionResult.SUCCESS;
                }
                AbstractMinecart B = cart;

                Linkable lA = (Linkable) A;
                Linkable lB = (Linkable) B;

                if (A.distanceTo(B) > 3) {
                    if (player instanceof ServerPlayer sp)
                        sp.sendSystemMessage(Component.translatable(MOD_ID + ".cant_link_too_far"));
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
                    if (player instanceof ServerPlayer sp)
                        //sp.sendSystemMessage(Component.translatable(MOD_ID + ".cant_link_two_furnaces"));
                        world.playSound(null, cart.getX(), cart.getY(), cart.getZ(),
                        SoundEvents.COPPER_BREAK, SoundSource.NEUTRAL, 1f, 1f);
                    stack.remove(PARENT_ID);
                    return InteractionResult.SUCCESS;
                }
                if (aIsFurnace && aIsP) {
                    if (player instanceof ServerPlayer sp)
                        //sp.sendSystemMessage(Component.translatable(MOD_ID + ".cant_link_complex"));
                        world.playSound(null, cart.getX(), cart.getY(), cart.getZ(),
                        SoundEvents.COPPER_BREAK, SoundSource.NEUTRAL, 1f, 1f);
                    stack.remove(PARENT_ID);
                    return InteractionResult.SUCCESS;
                }
                if (bIsFurnace && bIsP) {
                    if (player instanceof ServerPlayer sp)
                        //sp.sendSystemMessage(Component.translatable(MOD_ID + ".cant_link_complex"));
                        world.playSound(null, cart.getX(), cart.getY(), cart.getZ(),
                        SoundEvents.COPPER_BREAK, SoundSource.NEUTRAL, 1f, 1f);
                    stack.remove(PARENT_ID);
                    return InteractionResult.SUCCESS;
                }
                if (aIsFurnace && aIsC) {
                    if (player instanceof ServerPlayer sp)
                        //sp.sendSystemMessage(Component.translatable(MOD_ID + ".cant_link_complex"));
                        world.playSound(null, cart.getX(), cart.getY(), cart.getZ(),
                        SoundEvents.COPPER_BREAK, SoundSource.NEUTRAL, 1f, 1f);
                    stack.remove(PARENT_ID);
                    return InteractionResult.SUCCESS;
                }
                if (bIsFurnace && bIsC) {
                    if (player instanceof ServerPlayer sp)
                        //sp.sendSystemMessage(Component.translatable(MOD_ID + ".cant_link_complex"));
                        world.playSound(null, cart.getX(), cart.getY(), cart.getZ(),
                        SoundEvents.COPPER_BREAK, SoundSource.NEUTRAL, 1f, 1f);
                    stack.remove(PARENT_ID);
                    return InteractionResult.SUCCESS;
                }
                if (aIsC && bIsC) {
                    if (player instanceof ServerPlayer sp)
                        //sp.sendSystemMessage(Component.translatable(MOD_ID + ".cant_link_complex"));
                        world.playSound(null, cart.getX(), cart.getY(), cart.getZ(),
                        SoundEvents.COPPER_BREAK, SoundSource.NEUTRAL, 1f, 1f);
                    stack.remove(PARENT_ID);
                    return InteractionResult.SUCCESS;
                }

                AbstractMinecart parent, child;

                if (aIsN && bIsN) {
                    if (aIsFurnace)      { parent = A; child = B; }
                    else if (bIsFurnace) { parent = B; child = A; }
                    else                 { parent = B; child = A; }
                    ((Linkable) child).setLinkedChain(stack.getItem());
                    Linkable.setParentChild((Linkable) parent, (Linkable) child);

                } else if (aIsN && bIsC) {
                    if (aIsFurnace) {
                        if (player instanceof ServerPlayer sp)
                            //sp.sendSystemMessage(Component.translatable(MOD_ID + ".cant_link_complex"));
                            world.playSound(null, cart.getX(), cart.getY(), cart.getZ(),
                            SoundEvents.COPPER_BREAK, SoundSource.NEUTRAL, 1f, 1f);
                        stack.remove(PARENT_ID);
                        return InteractionResult.SUCCESS;
                    }
                    lA.setLinkedChain(stack.getItem());
                    Linkable.setParentChild(lB, lA);

                } else if (aIsC && bIsN) {
                    if (bIsFurnace) {
                        if (player instanceof ServerPlayer sp)
                            //sp.sendSystemMessage(Component.translatable(MOD_ID + ".cant_link_complex"));
                            world.playSound(null, cart.getX(), cart.getY(), cart.getZ(),
                            SoundEvents.COPPER_BREAK, SoundSource.NEUTRAL, 1f, 1f);
                        stack.remove(PARENT_ID);
                        return InteractionResult.SUCCESS;
                    }
                    lB.setLinkedChain(stack.getItem());
                    Linkable.setParentChild(lA, lB);

                } else if (aIsP && !aIsFurnace && bIsN) {
                    lA.setLinkedChain(stack.getItem());
                    Linkable.setParentChild(lB, lA);

                } else if (aIsP && aIsFurnace && bIsN) {
                    if (player instanceof ServerPlayer sp)
                        //sp.sendSystemMessage(Component.translatable(MOD_ID + ".cant_link_complex"));
                        world.playSound(null, cart.getX(), cart.getY(), cart.getZ(),
                        SoundEvents.COPPER_BREAK, SoundSource.NEUTRAL, 1f, 1f);
                    stack.remove(PARENT_ID);
                    return InteractionResult.SUCCESS;

                } else if (aIsP && !aIsFurnace && bIsC) {
                    lA.setLinkedChain(stack.getItem());
                    Linkable.setParentChild(lB, lA);

                } else if (aIsP && aIsFurnace && bIsC) {
                    if (player instanceof ServerPlayer sp)
                        //sp.sendSystemMessage(Component.translatable(MOD_ID + ".cant_link_complex"));
                        world.playSound(null, cart.getX(), cart.getY(), cart.getZ(),
                        SoundEvents.COPPER_BREAK, SoundSource.NEUTRAL, 1f, 1f);
                    stack.remove(PARENT_ID);
                    return InteractionResult.SUCCESS;

                } else if (aIsN && bIsP && !bIsFurnace) {
                    lB.setLinkedChain(stack.getItem());
                    Linkable.setParentChild(lA, lB);

                } else if (aIsN && bIsP && bIsFurnace) {
                    if (player instanceof ServerPlayer sp)
                        //sp.sendSystemMessage(Component.translatable(MOD_ID + ".cant_link_complex"));
                        world.playSound(null, cart.getX(), cart.getY(), cart.getZ(),
                        SoundEvents.COPPER_BREAK, SoundSource.NEUTRAL, 1f, 1f);
                    stack.remove(PARENT_ID);
                    return InteractionResult.SUCCESS;

                } else if (aIsC && bIsP && !bIsFurnace) {
                    lB.setLinkedChain(stack.getItem());
                    Linkable.setParentChild(lA, lB);

                } else if (aIsC && bIsP && bIsFurnace) {
                    if (player instanceof ServerPlayer sp)
                        //sp.sendSystemMessage(Component.translatable(MOD_ID + ".cant_link_complex"));
                        world.playSound(null, cart.getX(), cart.getY(), cart.getZ(),
                                SoundEvents.COPPER_BREAK, SoundSource.NEUTRAL, 1f, 1f);
                    stack.remove(PARENT_ID);
                    return InteractionResult.SUCCESS;

                } else {
                    if (player instanceof ServerPlayer sp)
                        //sp.sendSystemMessage(Component.translatable(MOD_ID + ".cant_link_complex"));
                        world.playSound(null, cart.getX(), cart.getY(), cart.getZ(),
                        SoundEvents.COPPER_BREAK, SoundSource.NEUTRAL, 1f, 1f);
                    stack.remove(PARENT_ID);
                    return InteractionResult.SUCCESS;
                }

                world.playSound(null, cart.getX(), cart.getY(), cart.getZ(),
                        SoundEvents.CHAIN_PLACE, SoundSource.NEUTRAL, 1f, 1f);
                if (!player.isCreative()) stack.shrink(1);
                stack.remove(PARENT_ID);

            } else if (uuid != null && cart.getUUID().equals(uuid)) {
                world.playSound(null, cart.getX(), cart.getY(), cart.getZ(),
                        SoundEvents.COPPER_BREAK, SoundSource.NEUTRAL, 1f, 1f);
                stack.remove(PARENT_ID);

            } else if (uuid == null) {
                stack.set(PARENT_ID, cart.getUUID());
                world.playSound(null, cart.getX(), cart.getY(), cart.getZ(),
                        SoundEvents.CHAIN_HIT, SoundSource.NEUTRAL, 1f, 1f);
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
                        Item chainItem = stack.getItem();
                        stack.remove(PARENT_ID);
                        if (cart != null) {
                            //net.minecraft.world.entity.item.ItemEntity drop = new net.minecraft.world.entity.item.ItemEntity(
                            //    cart.level(), cart.getX(), cart.getY(), cart.getZ(), new ItemStack(chainItem));
                            //((ServerLevel) cart.level()).addFreshEntity(drop);
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