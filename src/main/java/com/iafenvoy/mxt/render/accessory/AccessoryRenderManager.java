package com.iafenvoy.mxt.render.accessory;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.util.matcher.ItemMatcher;
import com.iafenvoy.mxt.util.matcher.ItemMatcher.Entry;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resource-reloadable definitions for Curios accessory item transforms.
 */
@EventBusSubscriber(Dist.CLIENT)
public enum AccessoryRenderManager implements ResourceManagerReloadListener {
    INSTANCE;
    private static final Logger LOGGER = MiXianTu.LOGGER;
    private static volatile List<Rule> BACK = List.of();
    private static volatile List<Rule> BELT = List.of();
    private static final Map<Identifier, AccessoryRenderDefinition> BACK_CACHE = new ConcurrentHashMap<>();
    private static final Map<Identifier, AccessoryRenderDefinition> BELT_CACHE = new ConcurrentHashMap<>();

    public static AccessoryRenderDefinition back(ItemStack stack) {
        return resolve(stack, BACK, BACK_CACHE, getDefault(stack));
    }

    public static AccessoryRenderDefinition belt(ItemStack stack) {
        return resolve(stack, BELT, BELT_CACHE, getDefault(stack));
    }

    private static AccessoryRenderDefinition getDefault(ItemStack stack) {
        return stack.has(DataComponents.WEAPON) ? AccessoryRenderDefinition.WEAPON : AccessoryRenderDefinition.DEFAULT;
    }

    @SubscribeEvent
    public static void registerAccessoryRenderReload(AddClientReloadListenersEvent event) {
        event.addListener(Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "accessory_render"), INSTANCE);
    }

    @Override
    public void onResourceManagerReload(@NonNull ResourceManager manager) {
        BACK = load(manager, "mxt/back_render");
        BELT = load(manager, "mxt/belt_render");
        BACK_CACHE.clear();
        BELT_CACHE.clear();
    }

    private static List<Rule> load(ResourceManager manager, String folder) {
        List<Rule> result = new ArrayList<>();
        manager.listResources(folder, path -> path.getPath().endsWith(".json")).forEach((resourceId, resource) -> {
            try {
                try (BufferedReader reader = resource.openAsReader()) {
                    JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                    Rule decoded = Rule.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow(message -> new IllegalArgumentException("Invalid accessory render rule: " + message));
                    result.add(decoded);
                }
            } catch (IOException | RuntimeException exception) {
                LOGGER.warn("Failed to load {} accessory render definition {}: {}", folder, resourceId, exception.getMessage());
            }
        });
        return result.stream().sorted(Comparator.comparingInt(Rule::priority)).toList();
    }

    private static AccessoryRenderDefinition resolve(ItemStack stack, List<Rule> rules, Map<Identifier, AccessoryRenderDefinition> cache, AccessoryRenderDefinition fallback) {
        Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return cache.computeIfAbsent(id, ignored -> rules.stream()
                .filter(rule -> rule.entries().stream().anyMatch(entry -> entry.matches(stack)))
                .max(Comparator.comparingInt(Rule::priority))
                .map(Rule::definition).orElse(fallback));
    }

    private record Rule(List<Entry> entries, int priority, AccessoryRenderDefinition definition) {
        public static final Codec<Rule> CODEC = RecordCodecBuilder.create(i -> i.group(
                ItemMatcher.ENTRIES_CODEC.fieldOf("item").forGetter(Rule::entries),
                Codec.INT.optionalFieldOf("priority", 0).forGetter(Rule::priority),
                AccessoryRenderDefinition.CODEC.forGetter(Rule::definition)
        ).apply(i, Rule::new));
    }
}
