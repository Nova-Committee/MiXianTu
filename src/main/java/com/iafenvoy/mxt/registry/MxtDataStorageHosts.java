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
 * The bridge a data pack needs to reach storage: which families keep state at all, and where the values of one
 * family live on an entity. A data pack can only name a registry, so one line per family is what connects the
 * registry to the attachment that holds its values.
 */
public final class MxtDataStorageHosts {
    private static final Map<Identifier, Function<Entity, DataStorageHolder>> BY_REGISTRY = new LinkedHashMap<>();

    static {
        register(MxtResourceKeys.ABILITY, entity -> entity.getData(MxtAttachments.ABILITY_HOLDER).storage());
    }

    private MxtDataStorageHosts() {
    }

    /**
     * Declares that the content of this registry keeps state, and where an entity's values for it live.
     */
    public static <T extends DataStorageDeclaration> void register(ResourceKey<? extends Registry<T>> registry, Function<Entity, DataStorageHolder> storage) {
        BY_REGISTRY.put(registry.identifier(), storage);
    }

    /**
     * The holder one family keeps its values in on this entity, or empty when that family keeps no state.
     */
    public static Optional<DataStorageHolder> holder(Entity entity, Identifier family) {
        return Optional.ofNullable(BY_REGISTRY.get(family)).map(access -> access.apply(entity));
    }

    /**
     * Resolves one host definition, so a writer can check which kinds it declares. A family that keeps no
     * storage is answered without touching the registry access, which would throw for a registry that does
     * not exist.
     */
    public static Optional<DataStorageDeclaration> definition(Identifier family, Identifier id) {
        if (!BY_REGISTRY.containsKey(family)) return Optional.empty();
        ResourceKey<Registry<Object>> registry = ResourceKey.createRegistryKey(family);
        return MxtDatapackRegistries.get(registry, id).filter(DataStorageDeclaration.class::isInstance)
                .map(DataStorageDeclaration.class::cast);
    }
}
