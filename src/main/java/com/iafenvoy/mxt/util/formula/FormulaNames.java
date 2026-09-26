package com.iafenvoy.mxt.util.formula;

import com.iafenvoy.mxt.data.cultivation.Element;
import com.iafenvoy.mxt.data.resource.Resource;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.util.HolderHelper;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.ai.attributes.Attribute;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Turns registry ids into the flat identifiers a formula can name, and keeps the indexes behind
 * {@code caster_<resource>}, {@code caster_<attribute>} and {@code caster_<element>}. The attribute registry is
 * static so its index is built once; the other two are keyed by registry instance and hold registry keys, and
 * ServerCache drops them on every datapack load, so a reloaded pack is never answered out of the old index.
 */
public final class FormulaNames {
    private static final int MAX_CACHED_REGISTRIES = 4;
    private static final Object RESOURCE_LOCK = new Object();
    private static final Object ELEMENT_LOCK = new Object();

    private static volatile Map<Registry<?>, Map<String, ResourceKey<Resource>>> resourceNames = Map.of();
    private static volatile Map<Registry<?>, Map<String, ResourceKey<Element>>> elementNames = Map.of();
    private static volatile Map<String, Holder<Attribute>> attributeNames;

    private FormulaNames() {
    }

    // The flat id shape every formula name uses: exp4j identifiers cannot hold / . -
    public static String flatten(Identifier id) {
        return (id.getNamespace() + "_" + id.getPath()).replace('/', '_').replace('.', '_').replace('-', '_');
    }

    @Nullable
    public static Holder<Attribute> attribute(String name) {
        Map<String, Holder<Attribute>> index = attributeNames;
        if (index == null) {
            Map<String, Holder<Attribute>> built = new LinkedHashMap<>();
            BuiltInRegistries.ATTRIBUTE.listElements()
                    .forEach(holder -> put(built, HolderHelper.id(holder), holder, "attribute"));
            attributeNames = index = Map.copyOf(built);
        }
        return index.get(name);
    }

    @Nullable
    public static Holder<Resource> resource(RegistryAccess access, String name) {
        Registry<Resource> registry = access.lookupOrThrow(MxtResourceKeys.RESOURCE);
        Map<String, ResourceKey<Resource>> index = resourceNames.get(registry);
        if (index == null) index = indexResources(registry);
        ResourceKey<Resource> key = index.get(name);
        return key == null ? null : registry.get(key).orElse(null);
    }

    private static Map<String, ResourceKey<Resource>> indexResources(Registry<Resource> registry) {
        synchronized (RESOURCE_LOCK) {
            Map<Registry<?>, Map<String, ResourceKey<Resource>>> cached = resourceNames;
            Map<String, ResourceKey<Resource>> existing = cached.get(registry);
            if (existing != null) return existing;
            Map<String, ResourceKey<Resource>> built = new LinkedHashMap<>();
            registry.listElements().forEach(holder -> {
                Identifier id = HolderHelper.id(holder);
                put(built, id, ResourceKey.create(MxtResourceKeys.RESOURCE, id), "resource");
            });
            Map<String, ResourceKey<Resource>> index = Map.copyOf(built);
            // A handful of indexes covers a client and a server in one process; invalidate() drops them on a
            // datapack load, so the bound only ever covers retired instances between loads.
            Map<Registry<?>, Map<String, ResourceKey<Resource>>> updated =
                    cached.size() + 1 > MAX_CACHED_REGISTRIES ? new HashMap<>() : new HashMap<>(cached);
            updated.put(registry, index);
            resourceNames = Map.copyOf(updated);
            return index;
        }
    }

    // Null when no element uses that name. {@code caster_<namespace>_<path>} answers 1 or 0 for "is this element
    // among the ones this entity's spirit roots name".
    @Nullable
    public static Holder<Element> element(RegistryAccess access, String name) {
        Registry<Element> registry = access.lookupOrThrow(MxtResourceKeys.ELEMENT);
        Map<String, ResourceKey<Element>> index = elementNames.get(registry);
        if (index == null) index = indexElements(registry);
        ResourceKey<Element> key = index.get(name);
        return key == null ? null : registry.get(key).orElse(null);
    }

    private static Map<String, ResourceKey<Element>> indexElements(Registry<Element> registry) {
        synchronized (ELEMENT_LOCK) {
            Map<Registry<?>, Map<String, ResourceKey<Element>>> cached = elementNames;
            Map<String, ResourceKey<Element>> existing = cached.get(registry);
            if (existing != null) return existing;
            Map<String, ResourceKey<Element>> built = new LinkedHashMap<>();
            registry.listElements().forEach(holder -> {
                Identifier id = HolderHelper.id(holder);
                put(built, id, ResourceKey.create(MxtResourceKeys.ELEMENT, id), "element");
            });
            Map<String, ResourceKey<Element>> index = Map.copyOf(built);
            Map<Registry<?>, Map<String, ResourceKey<Element>>> updated =
                    cached.size() + 1 > MAX_CACHED_REGISTRIES ? new HashMap<>() : new HashMap<>(cached);
            updated.put(registry, index);
            elementNames = Map.copyOf(updated);
            return index;
        }
    }

    // Called on every datapack load: both indexes are keyed by registry instance, which a reloaded pack may
    // keep, so they are dropped rather than trusted to miss on their own.
    public static void invalidate() {
        synchronized (RESOURCE_LOCK) {
            resourceNames = Map.of();
        }
        synchronized (ELEMENT_LOCK) {
            elementNames = Map.of();
        }
    }

    private static <T> void put(Map<String, T> index, Identifier id, T value, String kind) {
        String name = flatten(id);
        T previous = index.putIfAbsent(name, value);
        if (previous != null)
            FormulaDiagnostics.report("Two formula " + kind + " names flatten to '" + name + "'; '" + id + "' is ignored");
    }
}