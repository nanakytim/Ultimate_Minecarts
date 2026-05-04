package net.nanaky.ultimate_minecarts.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.locale.Language;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;
import java.util.Set;

@Mixin(Language.class)
public abstract class LanguageMixin {

    private static final Set<String> ENGLISH_LOCALES = Set.of("en_us", "en_gb");

    private static final Map<String, String> OVERRIDES = Map.of(
        "entity.minecraft.furnace_minecart", "Minecart with Blast Furnace",
        "item.minecraft.furnace_minecart", "Minecart with Blast Furnace"
    );

    @Inject(method = "getOrDefault", at = @At("HEAD"), cancellable = true)
    private void injectTranslations(String key, CallbackInfoReturnable<String> cir) {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.options == null) return;

            String lang = mc.options.languageCode;
            if (!ENGLISH_LOCALES.contains(lang)) return;
        } catch (Exception e) {
            return;
        }

        if (OVERRIDES.containsKey(key)) {
            cir.setReturnValue(OVERRIDES.get(key));
        }
    }
}