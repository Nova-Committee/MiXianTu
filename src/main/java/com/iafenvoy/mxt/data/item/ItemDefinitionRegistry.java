package com.iafenvoy.mxt.data.item;

import com.iafenvoy.mxt.MiXianTu;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import net.minecraft.resources.Identifier;

import java.util.Arrays;
import java.util.Optional;

/**
 * The three datapack registries that can materialize a data-driven item.
 * The identifier is deliberately stored in item stacks instead of relying on
 * a definition ID alone, because IDs may be reused by different categories.
 */
public enum ItemDefinitionRegistry {
    OTHER("item"),
    PILL("pill"),
    WEAPON("weapon");

    public static final Codec<ItemDefinitionRegistry> CODEC = Identifier.CODEC.comapFlatMap(
            ItemDefinitionRegistry::byId,
            ItemDefinitionRegistry::id
    );

    private final Identifier id;

    ItemDefinitionRegistry(String path) {
        this.id = Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, path);
    }

    public Identifier id() {
        return this.id;
    }

    public static Optional<ItemDefinitionRegistry> fromId(Identifier id) {
        return Arrays.stream(values())
                .filter(value -> value.id.equals(id))
                .findFirst();
    }

    private static DataResult<ItemDefinitionRegistry> byId(Identifier id) {
        return fromId(id)
                .map(DataResult::success)
                .orElseGet(() -> DataResult.error(() -> "Unknown data-driven item registry: " + id));
    }
}
