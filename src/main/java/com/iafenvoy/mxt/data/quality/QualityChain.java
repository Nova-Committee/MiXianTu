package com.iafenvoy.mxt.data.quality;

import com.iafenvoy.mxt.api.NamedDefinition;
import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.data.cost.Cost;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.util.DefinitionText;
import com.iafenvoy.mxt.util.HolderHelper;
import com.iafenvoy.mxt.util.codec.ContextNameCodec;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryFixedCodec;
import org.jspecify.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * One ladder of qualities: the tiers from lowest to highest, which of them an item starts at, and what each step
 * up costs. The list order is the ladder order - unlike a tag, it cannot be reshuffled by data pack merge order.
 *
 * <p>A tier may sit in several chains, so a stack's chain is either the one its item declares or, when exactly
 * one chain holds its tier, that one; two chains holding the same tier leave "where does this climb to" unanswered
 * on purpose rather than guessing.
 */
public record QualityChain(Component name, Component description, List<Holder<ItemQuality>> tiers,
                           Optional<Holder<ItemQuality>> defaultTier, List<Step> upgrades) implements NamedDefinition {
    private static final String CATEGORY = DefinitionText.category(MxtResourceKeys.QUALITY_CHAIN.identifier());
    public static final Codec<Holder<QualityChain>> CODEC = RegistryFixedCodec.create(MxtResourceKeys.QUALITY_CHAIN);
    public static final Codec<QualityChain> DIRECT_CODEC = RecordCodecBuilder.<QualityChain>create(i -> i.group(
            ContextNameCodec.name(CATEGORY).forGetter(QualityChain::name),
            ContextNameCodec.description(CATEGORY).forGetter(QualityChain::description),
            ItemQuality.CODEC.listOf().fieldOf("tiers").forGetter(QualityChain::tiers),
            ItemQuality.CODEC.optionalFieldOf("default").forGetter(QualityChain::defaultTier),
            Step.CODEC.listOf().optionalFieldOf("upgrades", List.of()).forGetter(QualityChain::upgrades)
    ).apply(i, QualityChain::new)).validate(QualityChain::validate);

    /**
     * One step up. The list being shorter than the ladder is how a pack says "the rest cannot be climbed": an
     * undeclared step is refused, never treated as a free one.
     */
    public record Step(List<Cost> costs, EntityCondition condition) {
        public static final Codec<Step> CODEC = RecordCodecBuilder.create(i -> i.group(
                Cost.LIST_CODEC.optionalFieldOf("costs", List.of()).forGetter(Step::costs),
                EntityCondition.optionalCodec("condition").forGetter(Step::condition)
        ).apply(i, Step::new));
    }

    // The tier an item without a component and without a settlement starts at.
    public Holder<ItemQuality> first() {
        return this.defaultTier.orElseGet(this.tiers::getFirst);
    }

    // -1 when the tier is not on this ladder. Compared by id because a quality holder is not stable across reloads.
    public int indexOf(@Nullable Holder<ItemQuality> quality) {
        if (quality == null) return -1;
        Identifier id = HolderHelper.id(quality);
        for (int index = 0; index < this.tiers.size(); index++)
            if (this.tiers.get(index).is(id)) return index;
        return -1;
    }

    public boolean isMember(@Nullable Holder<ItemQuality> quality) {
        return this.indexOf(quality) >= 0;
    }

    public Optional<Holder<ItemQuality>> nextTier(@Nullable Holder<ItemQuality> quality) {
        int index = this.indexOf(quality);
        return index < 0 || index + 1 >= this.tiers.size() ? Optional.empty() : Optional.of(this.tiers.get(index + 1));
    }

    // Empty at the top of the ladder, for a tier this chain does not hold, and for a step the pack never declared.
    public Optional<Step> stepUp(@Nullable Holder<ItemQuality> quality) {
        int index = this.indexOf(quality);
        return index < 0 || index >= this.upgrades.size() ? Optional.empty() : Optional.of(this.upgrades.get(index));
    }

    private static DataResult<QualityChain> validate(QualityChain chain) {
        if (chain.tiers().isEmpty())
            return DataResult.error(() -> "A quality chain needs at least one tier");
        Set<Identifier> seen = new HashSet<>();
        for (Holder<ItemQuality> tier : chain.tiers())
            if (!seen.add(HolderHelper.id(tier)))
                return DataResult.error(() -> "A quality chain lists " + HolderHelper.id(tier) + " twice");
        if (chain.defaultTier().isPresent() && !chain.isMember(chain.defaultTier().orElseThrow()))
            return DataResult.error(() -> "The default tier of a quality chain has to be one of its own tiers");
        if (chain.upgrades().size() > chain.tiers().size() - 1)
            return DataResult.error(() -> "A quality chain of " + chain.tiers().size() + " tiers has room for "
                    + (chain.tiers().size() - 1) + " upgrade steps, not " + chain.upgrades().size());
        return DataResult.success(chain);
    }
}
