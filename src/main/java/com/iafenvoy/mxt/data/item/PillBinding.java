package com.iafenvoy.mxt.data.item;

import com.iafenvoy.mxt.api.NamedDefinition;
import com.iafenvoy.mxt.data.DescribedEntry;
import com.iafenvoy.mxt.data.action.EntityAction;
import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.data.quality.QualityChain;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.util.DefinitionText;
import com.iafenvoy.mxt.util.codec.ContextNameCodec;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.iafenvoy.mxt.util.formula.number.Constant;
import com.iafenvoy.mxt.util.matcher.ItemMatcher;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryFixedCodec;

import java.util.List;
import java.util.Optional;

/**
 * Pill behaviour for an already registered consumable, or for the generic {@code mxt:pill} carrier via the
 * {@code mxt:pill} component. An omitted {@code items} list matches nothing; the component is then the only binding.
 */
public record PillBinding(Component name, Component description, List<Entry> entries, EntityAction onConsume,
                          NumberProvider toxicityGain, NumberProvider toxicityThreshold, EntityAction onOverdose,
                          NumberProvider toxicityAfterOverdose, Optional<Holder<QualityChain>> qualityChain,
                          List<DescribedEntry<EntityCondition>> conditions, Optional<Integer> maxUses,
                          NumberProvider cooldown) implements ItemMatcher, NamedDefinition {
    private static final String CATEGORY = DefinitionText.category(MxtResourceKeys.PILL_BINDING.identifier());
    public static final Codec<Holder<PillBinding>> CODEC = RegistryFixedCodec.create(MxtResourceKeys.PILL_BINDING);
    public static final Codec<PillBinding> DIRECT_CODEC = RecordCodecBuilder.<PillBinding>create(i -> i.group(
            ContextNameCodec.name(CATEGORY).forGetter(PillBinding::name),
            ContextNameCodec.description(CATEGORY).forGetter(PillBinding::description),
            ENTRIES_CODEC.optionalFieldOf("items", List.of()).forGetter(PillBinding::entries),
            EntityAction.optionalCodec("on_consume").forGetter(PillBinding::onConsume),
            NumberProvider.CODEC.optionalFieldOf("toxicity_gain", new Constant(0.0D)).forGetter(PillBinding::toxicityGain),
            NumberProvider.CODEC.optionalFieldOf("toxicity_threshold", new Constant(Double.MAX_VALUE)).forGetter(PillBinding::toxicityThreshold),
            EntityAction.optionalCodec("on_overdose").forGetter(PillBinding::onOverdose),
            NumberProvider.CODEC.optionalFieldOf("toxicity_after_overdose", new Constant(0.0D)).forGetter(PillBinding::toxicityAfterOverdose),
            QualityChain.CODEC.optionalFieldOf("quality_chain").forGetter(PillBinding::qualityChain),
            DescribedEntry.codec(EntityCondition.CODEC, "condition").listOf().optionalFieldOf("conditions", List.of()).forGetter(PillBinding::conditions),
            Codec.intRange(1, Integer.MAX_VALUE).optionalFieldOf("max_uses").forGetter(PillBinding::maxUses),
            NumberProvider.CODEC.optionalFieldOf("cooldown", new Constant(0.0D)).forGetter(PillBinding::cooldown)
    ).apply(i, PillBinding::new)).flatXmap(PillBinding::validate, PillBinding::validate);

    // A constant cooldown can be rejected at load. A formula is checked when the dose is taken.
    private static DataResult<PillBinding> validate(PillBinding binding) {
        if (binding.cooldown() instanceof Constant(double value) && (!Double.isFinite(value) || value < 0.0D))
            return DataResult.error(() -> "pill cooldown must be a finite non-negative number");
        return DataResult.success(binding);
    }
}
