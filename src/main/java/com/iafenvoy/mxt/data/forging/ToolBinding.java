package com.iafenvoy.mxt.data.forging;

import com.iafenvoy.mxt.util.HolderHelper;
import com.iafenvoy.mxt.util.codec.AutoIgnoreListCodec;
import com.iafenvoy.mxt.util.matcher.ItemMatcher;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;

import java.util.List;

/**
 * The forging methods one item unlocks, claimed by item like every other binding table. A stack may instead carry
 * its own methods through {@code mxt:forging_methods}, and the two are unioned.
 */
public record ToolBinding(List<Entry> entries, List<Holder<ForgingMethod>> methods, int priority) implements ItemMatcher {
    public static final Codec<ToolBinding> DIRECT_CODEC = RecordCodecBuilder.<ToolBinding>create(i -> i.group(
            ENTRIES_CODEC.fieldOf("items").forGetter(ToolBinding::entries),
            AutoIgnoreListCodec.create(ForgingMethod.CODEC).fieldOf("methods").forGetter(ToolBinding::methods),
            Codec.INT.optionalFieldOf("priority", DEFAULT_PRIORITY).forGetter(ToolBinding::priority)
    ).apply(i, ToolBinding::new)).validate(ToolBinding::validate);

    private static DataResult<ToolBinding> validate(ToolBinding value) {
        // Nothing reaches a definition that claims no item: the component that used to point at one is gone, so
        // an empty list would leave a file that loads and can never be read.
        if (value.entries.isEmpty()) return DataResult.error(() -> "items must not be empty");
        if (value.methods.isEmpty()) return DataResult.error(() -> "methods must not be empty");
        if (value.methods.stream().map(HolderHelper::id).distinct().count() != value.methods.size())
            return DataResult.error(() -> "methods must not contain duplicates");
        return DataResult.success(value);
    }
}
