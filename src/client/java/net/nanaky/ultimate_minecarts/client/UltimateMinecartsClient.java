package net.nanaky.ultimate_minecarts.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.vehicle.minecart.MinecartFurnace;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class UltimateMinecartsClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientPacketHandlers.register();
        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            ItemStack stack = player.getItemInHand(hand);
            if (world instanceof Level level
                    && level.isClientSide()
                    && entity instanceof MinecartFurnace
                    && stack.isEnchanted()) {
                return InteractionResult.CONSUME;
            }
            return InteractionResult.PASS;
        });
    }
}