package net.nanaky.ultimate_minecarts;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

public class UltimateMinecartsConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger(UltimateMinecarts.MOD_ID);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_FILE =
            FabricLoader.getInstance().getConfigDir().resolve(UltimateMinecarts.MOD_ID + "_server.json");

    private static UltimateMinecartsConfig INSTANCE = new UltimateMinecartsConfig();

    public boolean linkingEnabled = true;
    public boolean minecartDamageEnabled = true;
    public int furnaceMaxBurnTime = 72000;
    public double furnaceMinecartSpeed = 32;
    public double otherMinecartSpeed = 16;

    public float chainResistance = 4f;
    public float ironChainDistance = 1.2f;
    public float copperChainDistance = 1.6f;
    public float exposedCopperChainDistance = 2f;
    public float weatheredCopperChainDistance = 2.6f;
    public float oxidizedCopperChainDistance = 3.2f;

    public static UltimateMinecartsConfig get() {
        return INSTANCE;
    }

    public static void load() {
        if (Files.exists(CONFIG_FILE)) {
            try (Reader reader = Files.newBufferedReader(CONFIG_FILE)) {
                UltimateMinecartsConfig loaded = GSON.fromJson(reader, UltimateMinecartsConfig.class);
                if (loaded != null) INSTANCE = loaded;
            } catch (IOException e) {
                LOGGER.error("[{}] Failed to read config — using defaults.", UltimateMinecarts.MOD_ID, e);
            }
        }
        save();
    }

    public static void save() {
        try {
            Files.createDirectories(CONFIG_FILE.getParent());
            try (Writer writer = Files.newBufferedWriter(CONFIG_FILE)) {
                GSON.toJson(INSTANCE, writer);
            }
        } catch (IOException e) {
            LOGGER.error("[{}] Failed to save config.", UltimateMinecarts.MOD_ID, e);
        }
    }

    public static double getFurnaceMinecartSpeed() {
        return Math.max(0.1, INSTANCE.furnaceMinecartSpeed * 0.05);
    }

    public static double getOtherMinecartSpeed() {
        return Math.max(0.1, INSTANCE.otherMinecartSpeed * 0.05);
    }

    public static float getChainDistance(String chainPath) {
        return switch (chainPath) {
            case "copper_chain",
                "waxed_copper_chain"           -> INSTANCE.copperChainDistance;
            case "exposed_copper_chain",
                "waxed_exposed_copper_chain"   -> INSTANCE.exposedCopperChainDistance;
            case "weathered_copper_chain",
                "waxed_weathered_copper_chain" -> INSTANCE.weatheredCopperChainDistance;
            case "oxidized_copper_chain",
                "waxed_oxidized_copper_chain"  -> INSTANCE.oxidizedCopperChainDistance;
            default                              -> INSTANCE.ironChainDistance;
        };
    }
}