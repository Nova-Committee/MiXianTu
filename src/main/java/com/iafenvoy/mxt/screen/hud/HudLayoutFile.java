package com.iafenvoy.mxt.screen.hud;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.iafenvoy.mxt.MiXianTu;
import com.mojang.serialization.JsonOps;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;

/**
 * The HUD layout file, read and written here instead of through the config framework: it is one object per element
 * rather than a settings tree, and it is written on every drag step. The version key guards the shape, so a file left
 * by an older layout is ignored - those elements go back to their defaults instead of being read with the wrong
 * meaning - while entries are decoded one at a time, so a hand-edited value that does not parse costs only the
 * element it was written for.
 */
public final class HudLayoutFile {
    private static final int VERSION = 3;
    private static final Path PATH = FMLPaths.CONFIGDIR.get().resolve("mxt").resolve("mxt-hud.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // The whole file in memory, loaded once: an element asks for its own key on the frame it is built, and every
    // change from then on is a write this class already knows about.
    private static Map<String, HudPlacement> cache;

    private HudLayoutFile() {
    }

    public static Optional<HudPlacement> get(String layoutKey) {
        return Optional.ofNullable(entries().get(layoutKey));
    }

    public static void put(String layoutKey, HudPlacement placement) {
        entries().put(layoutKey, placement);
        save();
    }

    public static void remove(String layoutKey) {
        if (entries().remove(layoutKey) == null) return;
        save();
    }

    private static Map<String, HudPlacement> entries() {
        if (cache == null) cache = new HashMap<>(load());
        return cache;
    }

    private static Map<String, HudPlacement> load() {
        if (!Files.isRegularFile(PATH)) return Map.of();
        try {
            JsonElement root = JsonParser.parseString(Files.readString(PATH));
            if (!root.isJsonObject() || root.getAsJsonObject().has("version")
                    && root.getAsJsonObject().get("version").getAsInt() != VERSION) {
                MiXianTu.LOGGER.warn("Ignoring {}: it is not a version {} HUD layout", PATH, VERSION);
                return Map.of();
            }
            JsonElement layout = root.getAsJsonObject().get("layout");
            if (layout == null || !layout.isJsonObject()) return Map.of();
            Map<String, HudPlacement> placements = new HashMap<>();
            for (Entry<String, JsonElement> entry : layout.getAsJsonObject().entrySet()) {
                HudPlacement placement = HudPlacement.CODEC.parse(JsonOps.INSTANCE, entry.getValue()).result().orElse(null);
                if (placement == null) {
                    MiXianTu.LOGGER.warn("Ignoring the HUD layout entry {}: it does not parse", entry.getKey());
                    continue;
                }
                placements.put(entry.getKey(), placement);
            }
            return placements;
        } catch (IOException | RuntimeException exception) {
            MiXianTu.LOGGER.warn("Could not read {}: {}", PATH, exception.getMessage());
            return Map.of();
        }
    }

    private static void save() {
        try {
            JsonObject layout = new JsonObject();
            entries().forEach((layoutKey, placement) -> HudPlacement.CODEC.encodeStart(JsonOps.INSTANCE, placement)
                    .result().ifPresent(json -> layout.add(layoutKey, json)));
            JsonObject root = new JsonObject();
            root.addProperty("version", VERSION);
            root.add("layout", layout);
            Files.createDirectories(PATH.getParent());
            Files.writeString(PATH, GSON.toJson(root));
        } catch (IOException | RuntimeException exception) {
            MiXianTu.LOGGER.warn("Could not write {}: {}", PATH, exception.getMessage());
        }
    }
}
