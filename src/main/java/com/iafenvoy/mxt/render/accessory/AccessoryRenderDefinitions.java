package com.iafenvoy.mxt.render.accessory;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.util.ItemMatcher;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Resource-reloadable definitions for Curios accessory item transforms. */
public final class AccessoryRenderDefinitions {
    private static final Logger LOGGER = MiXianTu.LOGGER;
    private static volatile List<Rule> BACK = List.of();
    private static volatile List<Rule> BELT = List.of();
    private static final Map<Identifier, AccessoryRenderDefinition> BACK_CACHE = new ConcurrentHashMap<>();
    private static final Map<Identifier, AccessoryRenderDefinition> BELT_CACHE = new ConcurrentHashMap<>();

    private AccessoryRenderDefinitions() {
    }

    public static AccessoryRenderDefinition back(ItemStack stack) {
        return resolve(stack, BACK, BACK_CACHE, AccessoryRenderDefinition.BACK_DEFAULT);
    }

    public static AccessoryRenderDefinition belt(ItemStack stack) {
        return resolve(stack, BELT, BELT_CACHE, AccessoryRenderDefinition.BELT_DEFAULT);
    }

    public static void reload(ResourceManager manager) {
        BACK = load(manager, "mxt/back_render", true);
        BELT = load(manager, "mxt/belt_render", false);
        BACK_CACHE.clear();
        BELT_CACHE.clear();
    }

    private static List<Rule> load(ResourceManager manager, String folder, boolean back) {
        List<Rule> result = new ArrayList<>();
        int[] sequence = {0};
        manager.listResources(folder, path -> path.getPath().endsWith(".json")).forEach((resourceId, resource) -> {
            try {
                try (var reader = resource.openAsReader()) {
                    JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                    if (!json.has("item")) throw new IllegalArgumentException("Missing required field 'item'");
                    AccessoryRenderDefinition definition = (back ? AccessoryRenderDefinition.CODEC : AccessoryRenderDefinition.BELT_CODEC)
                            .parse(JsonOps.INSTANCE, json)
                            .getOrThrow(message -> new IllegalArgumentException("Invalid accessory render definition: " + message));
                    List<ItemMatcher.Entry> entries = ItemMatcher.ENTRIES_CODEC.parse(JsonOps.INSTANCE, json.get("item"))
                            .getOrThrow(message -> new IllegalArgumentException("Invalid item matcher: " + message));
                    result.add(new Rule(entries, json.has("priority") ? json.get("priority").getAsInt() : 0,
                            sequence[0]++, definition));
                }
            } catch (IOException | RuntimeException exception) {
                LOGGER.warn("Failed to load {} accessory render definition {}: {}", folder, resourceId, exception.getMessage());
            }
        });
        return result.stream().sorted(Comparator.comparingInt(Rule::priority).thenComparingInt(Rule::sequence)).toList();
    }

    private static AccessoryRenderDefinition resolve(ItemStack stack, List<Rule> rules,
                                                     Map<Identifier, AccessoryRenderDefinition> cache,
                                                     AccessoryRenderDefinition fallback) {
        Identifier id = stack.getItem().builtInRegistryHolder().key().identifier();
        return cache.computeIfAbsent(id, ignored -> rules.stream()
                .filter(rule -> rule.entries().stream().anyMatch(entry -> entry.matches(stack)))
                .max(Comparator.comparingInt(Rule::priority).thenComparingInt(Rule::sequence))
                .map(Rule::definition).orElse(fallback));
    }

    private record Rule(List<ItemMatcher.Entry> entries, int priority, int sequence, AccessoryRenderDefinition definition) {
    }
}
