package com.iafenvoy.mxt.util.codec;

import com.iafenvoy.mxt.accessor.ResourceLoadingOps;
import com.iafenvoy.mxt.util.DefinitionText;
import com.mojang.serialization.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import org.jetbrains.annotations.Nullable;

import java.util.stream.Stream;

/**
 * One text field of a definition, defaulted from the id the definition is decoded as: {@link
 * DefinitionText#defaultText} builds {@code <category>.<registry namespace>.<id namespace>.<id path>} plus this
 * field's suffix. That id is only on the ops while a data pack or its network payload loads, so anywhere else the
 * field has to be written out.
 */
public class ContextNameCodec extends MapCodec<Component> {
    private final String field, category, suffix;

    public ContextNameCodec(String field, String category, String suffix) {
        this.field = field;
        this.category = category;
        this.suffix = suffix;
    }

    /**
     * The definition's own name, in the {@code name} field.
     */
    public static MapCodec<Component> name(String category) {
        return new ContextNameCodec("name", category, "");
    }

    /**
     * The definition's own description, in the {@code description} field.
     */
    public static MapCodec<Component> description(String category) {
        return new ContextNameCodec("description", category, ".description");
    }

    @Override
    public <T> Stream<T> keys(DynamicOps<T> ops) {
        return Stream.of(ops.createString(this.field));
    }

    @Override
    public <T> DataResult<Component> decode(DynamicOps<T> ops, MapLike<T> input) {
        T value = input.get(this.field);
        if (value != null) return MiscCodecs.TRANSLATABLE_COMPONENT.parse(ops, value);
        ResourceKey<?> entry = currentEntry(ops);
        return entry == null
                ? DataResult.error(() -> "The " + this.field + " of a definition has to be written out when the entry id is unknown")
                : DataResult.success(DefinitionText.defaultText(this.category, entry, this.suffix));
    }

    @Override
    public <T> RecordBuilder<T> encode(Component input, DynamicOps<T> ops, RecordBuilder<T> prefix) {
        return prefix.add(this.field, MiscCodecs.TRANSLATABLE_COMPONENT.encodeStart(ops, input));
    }

    // Null when this decode is not a data pack entry load, which is the only place the id is known.
    static <T> @Nullable ResourceKey<?> currentEntry(DynamicOps<T> ops) {
        return ops instanceof ResourceLoadingOps loading ? loading.mxt$getKey() : null;
    }
}
