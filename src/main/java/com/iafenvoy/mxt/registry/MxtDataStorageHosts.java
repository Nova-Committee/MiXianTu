package com.iafenvoy.mxt.registry;

import com.iafenvoy.mxt.data.storage.DataStorageDeclaration;
import com.iafenvoy.mxt.data.storage.DataStorageHolder;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * The bridge from a data pack to storage: a data pack can only name a registry, so one line per family connects
 * that registry to the attachment that holds an entity's values for it.
 */
public final class MxtDataStorageHosts {
    private static final Map<Identifier, Function<Entity, DataStorageHolder>> BY_REGISTRY = new LinkedHashMap<>();

    static {
        register(MxtResourceKeys.ABILITY, entity -> entity.getData(MxtAttachments.ABILITY_HOLDER).storage());
    }

    private MxtDataStorageHosts() {
    }

    public static <T extends DataStorageDeclaration> void register(ResourceKey<? extends Registry<T>> registry, Function<Entity, DataStorageHolder> storage) {
        BY_REGISTRY.put(registry.identifier(), storage);
    }

    public static Optional<DataStorageHolder> holder(Entity entity, Identifier family) {
        return Optional.ofNullable(BY_REGISTRY.get(family)).map(access -> access.apply(entity));
    }

    // Checked before any registry lookup: a family that keeps no storage names a registry that does not exist.
    public static Optional<DataStorageDeclaration> definition(Identifier family, Identifier id) {
        if (!BY_REGISTRY.containsKey(family)) return Optional.empty();
        ResourceKey<Registry<Object>> registry = ResourceKey.createRegistryKey(family);
        return MxtDatapackRegistries.get(registry, id).filter(DataStorageDeclaration.class::isInstance)
                .map(DataStorageDeclaration.class::cast);
    }
}
