package net.nanaky.ultimate_minecarts.mixin;

import net.minecraft.locale.Language;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.Map;

@Mixin(Language.class)
public abstract class LanguageMixin {
    private static final Map<String, String> KEY_REDIRECTS = Map.of(
        "entity.minecraft.furnace_minecart", "ultimate_minecarts.furnace_minecart_rename",
        "item.minecraft.furnace_minecart",   "ultimate_minecarts.furnace_minecart_item_rename"
    );

    @Inject(method = "getOrDefault", at = @At("HEAD"), cancellable = true)
    private void injectTranslations(String key, CallbackInfoReturnable<String> cir) {
        String redirect = KEY_REDIRECTS.get(key);
        if (redirect == null) return;
        Language self = (Language)(Object)this;
        if (self.has(redirect)) {
            cir.setReturnValue(self.getOrDefault(redirect));
        }
    }
}