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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Turns registry ids into the flat identifiers a formula can name, and keeps the indexes behind
 * {@code caster_<resource>}, {@code caster_<attribute>} and {@code caster_<element>}. The attribute registry is
 * static so its index is built once; the other two are keyed by registry instance and hold registry keys, and
 * DatapackCache drops them on every datapack load, so a reloaded pack is never answered out of the old index.
 */
public final class FormulaNames {
    private static final Map<String, ResourceKey<Resource>> RESOURCE_NAMES_CACHE = new LinkedHashMap<>();
    private static final Map<String, ResourceKey<Element>> ELEMENT_NAMES_CACHE = new LinkedHashMap<>();
    private static Map<String, Holder<Attribute>> attributeNames;

    public static void clear() {
        RESOURCE_NAMES_CACHE.clear();
        ELEMENT_NAMES_CACHE.clear();
    }

    public static void reload(RegistryAccess access) {
        clear();
        indexResources(access.lookupOrThrow(MxtResourceKeys.RESOURCE));
        indexElements(access.lookupOrThrow(MxtResourceKeys.ELEMENT));
    }

    private static void indexResources(Registry<Resource> registry) {
        registry.listElements().forEach(holder -> {
            Identifier id = HolderHelper.id(holder);
            put(RESOURCE_NAMES_CACHE, id, ResourceKey.create(MxtResourceKeys.RESOURCE, id), "resource");
        });
    }

    private static void indexElements(Registry<Element> registry) {
        registry.listElements().forEach(holder -> {
            Identifier id = HolderHelper.id(holder);
            put(ELEMENT_NAMES_CACHE, id, ResourceKey.create(MxtResourceKeys.ELEMENT, id), "element");
        });
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
        return Optional.ofNullable(RESOURCE_NAMES_CACHE.get(name)).flatMap(registry::get).orElse(null);
    }

    @Nullable
    public static Holder.Reference<Element> element(RegistryAccess access, String name) {
        Registry<Element> registry = access.lookupOrThrow(MxtResourceKeys.ELEMENT);
        return Optional.ofNullable(ELEMENT_NAMES_CACHE.get(name)).flatMap(registry::get).orElse(null);
    }

    private static <T> void put(Map<String, T> index, Identifier id, T value, String kind) {
        String name = flatten(id);
        T previous = index.putIfAbsent(name, value);
        if (previous != null)
            FormulaDiagnostics.report("Two formula " + kind + " names flatten to '" + name + "'; '" + id + "' is ignored");
    }
}