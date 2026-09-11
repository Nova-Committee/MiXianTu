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
 * Datapack-defined set of forging methods unlocked by holding one tool item.
 *
 * <p>The binding is referenced from an item through the {@code mxt:tool_binding}
 * data component, so the item itself only stores a registry id.</p>
 */
public record ToolBinding(List<Holder<ForgingMethod>> methods) {
    public static final Codec<Holder<ToolBinding>> CODEC = RegistryFixedCodec.create(MxtResourceKeys.TOOL_BINDING);
    public static final Codec<ToolBinding> DIRECT_CODEC = RecordCodecBuilder.<ToolBinding>create(i -> i.group(
            AutoIgnoreListCodec.create(ForgingMethod.CODEC).fieldOf("methods").forGetter(ToolBinding::methods)
    ).apply(i, ToolBinding::new)).validate(ToolBinding::validate);

    private static DataResult<ToolBinding> validate(ToolBinding value) {
        if (value.methods.isEmpty()) return DataResult.error(() -> "methods must not be empty");
        if (value.methods.stream().map(HolderHelper::id).distinct().count() != value.methods.size())
            return DataResult.error(() -> "methods must not contain duplicates");
        return DataResult.success(value);
    }
}
