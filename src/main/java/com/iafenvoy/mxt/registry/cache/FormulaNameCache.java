package com.iafenvoy.mxt.registry.cache;

import com.iafenvoy.mxt.util.HolderHelper;
import com.iafenvoy.mxt.util.formula.FormulaDiagnostics;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

public class FormulaNameCache<T> extends RegistryCache<Map<String, Holder<T>>> {
    private final ResourceKey<Registry<T>> registry;
    private final String kind;
    private final Map<String, Holder<T>> data = new LinkedHashMap<>();

    public FormulaNameCache(ResourceKey<Registry<T>> registry, String kind) {
        this.registry = registry;
        this.kind = kind;
    }

    @Override
    protected void build(RegistryAccess access) {
        access.lookupOrThrow(this.registry).listElements().forEach(holder -> put(this.data, HolderHelper.id(holder), holder, this.kind));
    }

    @Override
    protected void clear() {
        this.data.clear();
    }

    @Override
    public Map<String, Holder<T>> get() {
        return this.data;
    }

    @Nullable
    public Holder<T> get(String key) {
        return this.data.get(key);
    }

    private static <T> void put(Map<String, T> index, Identifier id, T value, String kind) {
        String name = flatten(id);
        T previous = index.putIfAbsent(name, value);
        if (previous != null)
            FormulaDiagnostics.report("Two formula " + kind + " names flatten to '" + name + "'; '" + id + "' is ignored");
    }

    // The flat id shape every formula name uses: exp4j identifiers cannot hold / . -
    public static String flatten(Identifier id) {
        return (id.getNamespace() + "_" + id.getPath()).replace('/', '_').replace('.', '_').replace('-', '_');
    }
}
