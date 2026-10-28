package net.nanaky.ultimate_minecarts.common.utils;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.nanaky.ultimate_minecarts.UltimateMinecarts;
import net.nanaky.ultimate_minecarts.common.packets.ClientboundSyncPendingChainPacket;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

public final class PendingChains {
    private PendingChains() {}

    public record Pending(UUID cart, Identifier chain, boolean refundable) {
        public static final Codec<Pending> CODEC = RecordCodecBuilder.create(i -> i.group(
            UUIDUtil.CODEC.fieldOf("cart").forGetter(Pending::cart),
            Identifier.CODEC.fieldOf("chain").forGetter(Pending::chain),
            Codec.BOOL.fieldOf("refundable").forGetter(Pending::refundable)
        ).apply(i, Pending::new));

        public Item item() {
            Item it = BuiltInRegistries.ITEM.getValue(chain);
            return it == Items.AIR ? Items.IRON_CHAIN : it;
        }
    }

    public static AttachmentType<Pending> TYPE;

    public static void register() {
        TYPE = AttachmentRegistry.create(UltimateMinecarts.id("pending_chain"),
            builder -> builder.persistent(Pending.CODEC));
    }

    public static @Nullable Pending get(ServerPlayer player) {
        return player.getAttached(TYPE);
    }

    public static void begin(ServerPlayer player, AbstractMinecart cart, Item chain, boolean refundable) {
        player.setAttached(TYPE, new Pending(cart.getUUID(), BuiltInRegistries.ITEM.getKey(chain), refundable));
        broadcast(player, Optional.of(cart.getUUID()), chain, true);
    }

    public static void clear(ServerPlayer player, boolean notifySelf) {
        Pending p = get(player);
        if (p == null) return;
        player.removeAttached(TYPE);
        broadcast(player, Optional.empty(), p.item(), notifySelf);
    }

    public static void refund(ServerPlayer player, boolean notifySelf) {
        Pending p = get(player);
        if (p == null) return;
        clear(player, notifySelf);
        if (!p.refundable()) return;
        ItemStack stack = new ItemStack(p.item());
        if (!player.getInventory().add(stack) && !stack.isEmpty())
            player.spawnAtLocation((ServerLevel) player.level(), stack);
    }

    public static void dropAt(ServerPlayer player, Entity where) {
        Pending p = get(player);
        if (p == null) return;
        clear(player, true);
        if (p.refundable())
            where.spawnAtLocation((ServerLevel) where.level(), new ItemStack(p.item()));
    }

    public static void tick(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Pending p = get(player);
            if (p == null) continue;

            if (!player.isAlive()) {
                dropAt(player, player);
                continue;
            }

            Entity cart = ((ServerLevel) player.level()).getEntity(p.cart());

            if (cart == null) {
                refund(player, true);
                continue;
            }

            if (player.distanceTo(cart) > 4) {
                cart.level().playSound(null, cart.blockPosition(),
                    SoundEvents.CHAIN_BREAK, SoundSource.NEUTRAL, 1f, 1f);
                dropAt(player, cart);
            }
        }
    }

    private static void broadcast(ServerPlayer player, Optional<UUID> target, Item chain, boolean notifySelf) {
        var pkt = new ClientboundSyncPendingChainPacket(
            player.getId(), target, BuiltInRegistries.ITEM.getId(chain));
        PlayerLookup.tracking(player).forEach(o -> ServerPlayNetworking.send(o, pkt));
        if (notifySelf) ServerPlayNetworking.send(player, pkt);
    }
}