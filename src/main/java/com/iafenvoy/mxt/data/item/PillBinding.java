package com.iafenvoy.mxt.data.item;

import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.util.codec.OptionalCodecs;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.iafenvoy.mxt.util.formula.number.Constant;
import com.iafenvoy.mxt.util.matcher.ItemMatcher;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.resources.RegistryFixedCodec;

import java.util.List;
import java.util.OptionalInt;

/**
 * Which already registered items are one pill, plus what that item family costs to use. Use limit and cooldown are
 * the binding's, not the pill's, so two bindings of the same pill keep separate counters. {@code pill} is required:
 * a binding that names no pill would bind items to nothing.
 */
public record PillBinding(List<Entry> entries, Holder<Pill> pill, OptionalInt maxUses, NumberProvider cooldown,
                          int priority) implements ItemMatcher {
    public static final Codec<Holder<PillBinding>> CODEC = RegistryFixedCodec.create(MxtResourceKeys.PILL_BINDING);
    public static final Codec<PillBinding> DIRECT_CODEC = RecordCodecBuilder.<PillBinding>create(i -> i.group(
            ENTRIES_CODEC.optionalFieldOf("items", List.of()).forGetter(PillBinding::entries),
            Pill.CODEC.fieldOf("pill").forGetter(PillBinding::pill),
            OptionalCodecs.intCodec(1, Integer.MAX_VALUE, "max_uses").forGetter(PillBinding::maxUses),
            NumberProvider.CODEC.optionalFieldOf("cooldown", new Constant(0.0D)).forGetter(PillBinding::cooldown),
            Codec.INT.optionalFieldOf("priority", DEFAULT_PRIORITY).forGetter(PillBinding::priority)
    ).apply(i, PillBinding::new)).validate(PillBinding::validate);

    // A constant cooldown can be rejected at load. A formula is checked when the dose is taken.
    private static DataResult<PillBinding> validate(PillBinding binding) {
        if (binding.cooldown() instanceof Constant(double value) && (!Double.isFinite(value) || value < 0.0D))
            return DataResult.error(() -> "pill cooldown must be a finite non-negative number");
        return DataResult.success(binding);
    }
}
