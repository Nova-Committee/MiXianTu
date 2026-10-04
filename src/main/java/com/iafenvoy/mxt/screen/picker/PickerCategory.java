package com.iafenvoy.mxt.screen.picker;

import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.neoforged.neoforge.registries.datamaps.DataMapType;

/**
 * One category of the picker's catalogue: the table its rows are read from, known by the id the command and the
 * open packet use. There are two kinds because the mod's item-keyed tables are of two kinds - a registry holds
 * definitions, an item data map holds one value per item.
 */
public sealed interface PickerCategory {
    Identifier id();

    /**
     * A datapack registry: the rows are one definition and whatever items it claims.
     */
    record OfRegistry<T>(ResourceKey<Registry<T>> key) implements PickerCategory {
        @Override
        public Identifier id() {
            return this.key.identifier();
        }
    }

    /**
     * A data map: the rows are the entries carrying a value.
     */
    record OfDataMap(DataMapType<?, ?> table) implements PickerCategory {
        @Override
        public Identifier id() {
            return this.table.id();
        }
    }
}
