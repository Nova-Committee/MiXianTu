package com.iafenvoy.mxt.data.item;

import com.iafenvoy.mxt.MiXianTu;
import com.mojang.serialization.Codec;
import net.minecraft.resources.Identifier;
import net.minecraft.util.StringRepresentable;
import org.jspecify.annotations.NonNull;

import java.util.Arrays;
import java.util.Optional;

/**
 * The three datapack registries that can materialize a data-driven item.
 * The identifier is deliberately stored in item stacks instead of relying on
 * a definition ID alone, because IDs may be reused by different categories.
 */
public enum DatapackItemRegistry implements StringRepresentable {
    OTHER("item"),
    PILL("pill"),
    WEAPON("weapon");
    public static final Codec<DatapackItemRegistry> CODEC = StringRepresentable.fromValues(DatapackItemRegistry::values);
    private final Identifier id;

    DatapackItemRegistry(String path) {
        this.id = Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, path);
    }

    public Identifier id() {
        return this.id;
    }

    public static Optional<DatapackItemRegistry> fromId(Identifier id) {
        return Arrays.stream(values())
                .filter(value -> value.id.equals(id))
                .findFirst();
    }

    @Override
    public @NonNull String getSerializedName() {
        return this.id.toString();
    }
}
