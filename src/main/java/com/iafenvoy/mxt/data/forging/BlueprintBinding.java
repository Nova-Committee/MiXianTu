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
 * The forging blueprints one item offers; the item is the data map's key. A stack may instead carry its own
 * blueprints through {@code mxt:forging_blueprints}, and the two are unioned.
 */
public record BlueprintBinding(List<Holder<ForgingBlueprint>> blueprints, int priority) {
    public static final Codec<BlueprintBinding> CODEC = RecordCodecBuilder.<BlueprintBinding>create(i -> i.group(
            AutoIgnoreListCodec.create(ForgingBlueprint.CODEC).fieldOf("blueprints").forGetter(BlueprintBinding::blueprints),
            Codec.INT.optionalFieldOf("priority", ItemMatcher.DEFAULT_PRIORITY).forGetter(BlueprintBinding::priority)
    ).apply(i, BlueprintBinding::new)).validate(BlueprintBinding::validate);

    private static DataResult<BlueprintBinding> validate(BlueprintBinding value) {
        if (value.blueprints.isEmpty()) return DataResult.error(() -> "blueprints must not be empty");
        if (value.blueprints.stream().map(HolderHelper::id).distinct().count() != value.blueprints.size())
            return DataResult.error(() -> "blueprints must not contain duplicates");
        return DataResult.success(value);
    }
}
