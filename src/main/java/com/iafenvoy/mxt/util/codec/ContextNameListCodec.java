package com.iafenvoy.mxt.util.codec;

import com.iafenvoy.mxt.util.DefinitionText;
import com.mojang.serialization.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * A list of text fields of a definition, either written out (a bare string is a translation key, an object is a
 * full component) or given as a count, which names every entry after the id the definition is decoded as. An
 * absent field means an empty list.
 */
public class ContextNameListCodec extends MapCodec<List<Component>> {
    private static final int MAX_COUNT = 1024;
    private static final Codec<List<Component>> EXPLICIT = MiscCodecs.TRANSLATABLE_COMPONENT.listOf();

    private final String field, category, indexSuffix;

    public ContextNameListCodec(String field, String category, String indexSuffix) {
        this.field = field;
        this.category = category;
        this.indexSuffix = indexSuffix;
    }

    /**
     * The sub-stage names of a realm stage, in the {@code minor_stages} field.
     */
    public static MapCodec<List<Component>> minorStages(String category) {
        return new ContextNameListCodec("minor_stages", category, ".minor_stage.");
    }

    @Override
    public <T> Stream<T> keys(DynamicOps<T> ops) {
        return Stream.of(ops.createString(this.field));
    }

    @Override
    public <T> DataResult<List<Component>> decode(DynamicOps<T> ops, MapLike<T> input) {
        T value = input.get(this.field);
        if (value == null) return DataResult.success(List.of());
        Optional<Number> count = ops.getNumberValue(value).result();
        if (count.isEmpty()) return EXPLICIT.parse(ops, value);
        double size = count.get().doubleValue();
        if (size != Math.rint(size) || size < 1.0D || size > MAX_COUNT)
            return DataResult.error(() -> this.field + " count must be a whole number between 1 and " + MAX_COUNT + ": " + size);
        ResourceKey<?> entry = ContextNameCodec.currentEntry(ops);
        if (entry == null)
            return DataResult.error(() -> "A counted " + this.field + " needs the entry id, which is only known while a data pack loads");
        List<Component> stages = new ArrayList<>((int) size);
        for (int index = 0; index < (int) size; index++)
            stages.add(DefinitionText.defaultText(this.category, entry, this.indexSuffix + index));
        return DataResult.success(List.copyOf(stages));
    }

    @Override
    public <T> RecordBuilder<T> encode(List<Component> input, DynamicOps<T> ops, RecordBuilder<T> prefix) {
        return input.isEmpty() ? prefix : prefix.add(this.field, EXPLICIT.encodeStart(ops, input));
    }
}
