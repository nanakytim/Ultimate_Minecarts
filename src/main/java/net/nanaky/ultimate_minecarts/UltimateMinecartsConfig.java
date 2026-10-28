package net.nanaky.ultimate_minecarts;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.stream.JsonReader;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class UltimateMinecartsConfig {
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    @interface Comment {
        String value();
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    @interface Section {
        String value();
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(UltimateMinecarts.MOD_ID);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_FILE =
            FabricLoader.getInstance().getConfigDir().resolve(UltimateMinecarts.MOD_ID + "_server.json");

    private static UltimateMinecartsConfig INSTANCE = new UltimateMinecartsConfig();

    // ---- GENERAL ----

    @Section("GENERAL CONFIG")
    @Comment("Allow Players to link minecarts with chains.")
    public boolean linkingEnabled = true;

    @Comment("Allow damage and knockback from fast-moving Minecarts.")
    public boolean minecartDamageEnabled = true;

    @Comment("Fast Minecarts (8 blocks/s or more) destroy other carts they run into, dropping them as items.")
    public boolean cartCollisionBreaking = true;

    @Comment("Hopper Minecarts pass their items to the linked parent, or to a linked storage Minecart child. A Furnace Minecart parent only receives fuel.")
    public boolean hopperMinecartOverhaul = true;

    @Comment("Top speed of a fuelled Furnace Minecart, in blocks per second.")
    public double furnaceMinecartSpeed = 32;

    @Comment("Top speed of every other unlinked minecart, in blocks per second.")
    public double otherMinecartSpeed = 16;

    public static final double MINECART_SLOWDOWN = 0.975;

    // ---- CHAIN ----

    @Section("CHAIN CONFIG")
    @Comment("How many blocks a chain can stretch before it breaks.")
    public float chainResistance = 4f;

    @Comment("Target gap between linked minecarts for an iron chain, in blocks.")
    public float ironChainDistance = 1.2f;

    @Comment("Target gap between linked minecarts for a copper chain, in blocks.")
    public float copperChainDistance = 1.6f;

    @Comment("Target gap between linked minecarts for an exposed copper chain, in blocks.")
    public float exposedCopperChainDistance = 2f;

    @Comment("Target gap between linked minecarts for a weathered copper chain, in blocks.")
    public float weatheredCopperChainDistance = 2.6f;

    @Comment("Target gap between linked minecarts for an oxidized copper chain, in blocks.")
    public float oxidizedCopperChainDistance = 3.2f;

    // ---- FUEL ----

    @Section("FUEL CONFIG")
    @Comment("Set the Maximum fuel a Furnace Minecart can hold. Fuel burns at 20 units per tick (200 per second).")
    public int furnaceMaxBurnTime = 32000;
    
    @Comment("How much fuel one Lava Bucket adds.")
    public int fuelLavaBucket = 32000;

    @Comment("How much fuel one Blaze Rod adds.")
    public int fuelBlazeRod = 24000;

    @Comment("How much fuel one Block of Coal adds.")
    public int fuelCoalBlock = 16000;

    @Comment("How much fuel one Coal adds.")
    public int fuelCoal = 1600;

    @Comment("How much fuel one Log or Wood adds.")
    public int fuelLog = 800;

    @Comment("How much fuel one Plank adds.")
    public int fuelPlank = 400;

    @Comment("How much fuel one Stick adds.")
    public int fuelStick = 100;

    @Comment("How much fuel one Water Bucket removes.")
    public int waterBucket = 32000;

    @Comment("How much fuel one Water Bottle removes.")
    public int waterBottle = 8000;

    public static UltimateMinecartsConfig get() {
        return INSTANCE;
    }

    public static void load() {
        if (Files.exists(CONFIG_FILE)) {
            try (Reader reader = Files.newBufferedReader(CONFIG_FILE)) {
                JsonReader json = new JsonReader(reader);
                json.setLenient(true);
                UltimateMinecartsConfig loaded = GSON.fromJson(json, UltimateMinecartsConfig.class);
                if (loaded != null) INSTANCE = loaded;
            } catch (IOException e) {
                LOGGER.error("[{}] Failed to read config — using defaults.", UltimateMinecarts.MOD_ID, e);
            } catch (JsonParseException e) {
                LOGGER.error("[{}] Config is not valid JSON — using defaults and leaving the file untouched.",
                        UltimateMinecarts.MOD_ID, e);
                return;
            }
        }
        save();
    }

    public static void save() {
        try {
            Files.createDirectories(CONFIG_FILE.getParent());
            try (Writer writer = Files.newBufferedWriter(CONFIG_FILE)) {
                writeWithComments(writer);
            }
        } catch (IOException e) {
            LOGGER.error("[{}] Failed to save config.", UltimateMinecarts.MOD_ID, e);
        }
    }

    private static void writeWithComments(Writer writer) throws IOException {
        JsonObject tree = GSON.toJsonTree(INSTANCE).getAsJsonObject();
        JsonObject defaults = GSON.toJsonTree(new UltimateMinecartsConfig()).getAsJsonObject();

        List<Field> fields = new ArrayList<>();
        for (Field f : UltimateMinecartsConfig.class.getDeclaredFields()) {
            int mod = f.getModifiers();
            if (Modifier.isStatic(mod) || Modifier.isTransient(mod) || f.isSynthetic()) continue;
            if (!tree.has(f.getName())) continue;
            fields.add(f);
        }

        StringBuilder out = new StringBuilder("{\n");
        for (int i = 0; i < fields.size(); i++) {
            Field f = fields.get(i);
            Section section = f.getAnnotation(Section.class);
            Comment comment = f.getAnnotation(Comment.class);

            if (i > 0) out.append(section != null ? "\n\n" : "\n");

            if (section != null) {
                out.append("  // ").append(section.value()).append("\n\n");
            }
            if (comment != null) {
                out.append("  // ").append(comment.value())
                   .append(" Default: ").append(describe(defaults.get(f.getName())))
                   .append('\n');
            }

            out.append("  ")
               .append(GSON.toJson(f.getName()))
               .append(": ")
               .append(GSON.toJson(tree.get(f.getName())));
            if (i < fields.size() - 1) out.append(',');
            out.append('\n');
        }
        out.append("}\n");
        writer.write(out.toString());
    }

    private static String describe(JsonElement e) {
        if (e == null) return "none";
        if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber()) {
            return e.getAsBigDecimal().stripTrailingZeros().toPlainString();
        }
        return e.getAsString();
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