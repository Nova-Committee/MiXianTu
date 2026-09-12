package com.iafenvoy.mxt.util.formula;

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
 * Turns registry ids into the flat identifiers a formula can name, and keeps the name indexes the
 * entity variables resolve {@code caster_<resource>} and {@code caster_<attribute>} against.
 *
 * <p>The attribute registry is static, so its index is built once. The resource registry belongs
 * to one world, and its index is keyed by the registry instance: reads are a plain volatile read
 * of an immutable snapshot and only a registry the process has not seen yet pays for a build. The
 * cached value holds resource keys rather than entries, so an index never keeps an old world's
 * registry alive.</p>
 */
public final class FormulaNames {
    private static final int MAX_CACHED_REGISTRIES = 4;
    private static final Object RESOURCE_LOCK = new Object();

    private static volatile Map<Registry<?>, Map<String, ResourceKey<Resource>>> resourceNames = Map.of();
    private static volatile Map<String, Holder<Attribute>> attributeNames;

    private FormulaNames() {
    }

    /**
     * Flattens a registry id into an exp4j identifier: the namespace and the path joined by an
     * underscore, with every {@code /}, {@code .} and {@code -} replaced by an underscore.
     *
     * <p>{@code mxt:common} becomes {@code mxt_common} and {@code minecraft:max_health} becomes
     * {@code minecraft_max_health}.</p>
     */
    public static String flatten(Identifier id) {
        return (id.getNamespace() + "_" + id.getPath()).replace('/', '_').replace('.', '_').replace('-', '_');
    }

    /**
     * Resolves a flattened attribute name, or {@code null} when no attribute uses it.
     */
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

    /**
     * Resolves a flattened resource name against the registry access that owns it, or
     * {@code null} when no resource uses it.
     */
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
            // A reloaded world brings a new registry; keeping a handful of indexes is enough for a
            // client and a server in one process and stops retired registries from piling up.
            Map<Registry<?>, Map<String, ResourceKey<Resource>>> updated =
                    cached.size() + 1 > MAX_CACHED_REGISTRIES ? new HashMap<>() : new HashMap<>(cached);
            updated.put(registry, index);
            resourceNames = Map.copyOf(updated);
            return index;
        }
    }

    private static <T> void put(Map<String, T> index, Identifier id, T value, String kind) {
        String name = flatten(id);
        T previous = index.putIfAbsent(name, value);
        if (previous != null)
            FormulaDiagnostics.report("Two formula " + kind + " names flatten to '" + name + "'; '" + id + "' is ignored");
    }
}
