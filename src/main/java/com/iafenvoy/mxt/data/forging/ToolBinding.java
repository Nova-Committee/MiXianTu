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
 * The forging methods one item unlocks; the item is the data map's key. A stack may instead carry its own methods
 * through {@code mxt:forging_methods}, and the two are unioned.
 */
public record ToolBinding(List<Holder<ForgingMethod>> methods, int priority) {
    public static final Codec<ToolBinding> CODEC = RecordCodecBuilder.<ToolBinding>create(i -> i.group(
            AutoIgnoreListCodec.create(ForgingMethod.CODEC).fieldOf("methods").forGetter(ToolBinding::methods),
            Codec.INT.optionalFieldOf("priority", ItemMatcher.DEFAULT_PRIORITY).forGetter(ToolBinding::priority)
    ).apply(i, ToolBinding::new)).validate(ToolBinding::validate);

    private static DataResult<ToolBinding> validate(ToolBinding value) {
        if (value.methods.isEmpty()) return DataResult.error(() -> "methods must not be empty");
        if (value.methods.stream().map(HolderHelper::id).distinct().count() != value.methods.size())
            return DataResult.error(() -> "methods must not contain duplicates");
        return DataResult.success(value);
    }
}
