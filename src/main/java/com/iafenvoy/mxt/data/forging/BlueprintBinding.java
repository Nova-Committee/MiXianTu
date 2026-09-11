package com.iafenvoy.mxt.data.forging;

import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.util.HolderHelper;
import com.iafenvoy.mxt.util.codec.AutoIgnoreListCodec;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.resources.RegistryFixedCodec;

import java.util.List;

/**
 * Datapack-defined set of blueprints provided by one blueprint item or book.
 *
 * <p>The binding is referenced from an item through the
 * {@code mxt:blueprint_binding} data component.</p>
 */
public record BlueprintBinding(List<Holder<ForgingBlueprint>> blueprints) {
    public static final Codec<Holder<BlueprintBinding>> CODEC = RegistryFixedCodec.create(MxtResourceKeys.BLUEPRINT_BINDING);
    public static final Codec<BlueprintBinding> DIRECT_CODEC = RecordCodecBuilder.<BlueprintBinding>create(i -> i.group(
            AutoIgnoreListCodec.create(ForgingBlueprint.CODEC).fieldOf("blueprints").forGetter(BlueprintBinding::blueprints)
    ).apply(i, BlueprintBinding::new)).validate(BlueprintBinding::validate);

    private static DataResult<BlueprintBinding> validate(BlueprintBinding value) {
        if (value.blueprints.isEmpty()) return DataResult.error(() -> "blueprints must not be empty");
        if (value.blueprints.stream().map(HolderHelper::id).distinct().count() != value.blueprints.size())
            return DataResult.error(() -> "blueprints must not contain duplicates");
        return DataResult.success(value);
    }
}
