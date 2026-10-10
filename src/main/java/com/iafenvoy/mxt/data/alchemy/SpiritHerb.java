package com.iafenvoy.mxt.data.alchemy;

import com.iafenvoy.mxt.api.NamedDefinition;
import com.iafenvoy.mxt.data.condition.BlockCondition;
import com.iafenvoy.mxt.data.cost.Cost;
import com.iafenvoy.mxt.data.cost.Costs;
import com.iafenvoy.mxt.data.cultivation.Element;
import com.iafenvoy.mxt.data.quality.ItemQuality;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.util.DefinitionText;
import com.iafenvoy.mxt.util.codec.ContextNameCodec;
import com.iafenvoy.mxt.util.codec.RegistryCodecs;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.iafenvoy.mxt.util.formula.number.Constant;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryFixedCodec;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Metadata bound to existing blocks. A pack entry never registers its own Block: {@code blocks} claims the blocks a
 * herb grows as, and {@code growth.drops} claims the stacks that count as its fruit. Omitting {@code growth} leaves
 * the herb unsowable and dropless but still usable in a furnace. Overlapping entries resolve by {@code priority}:
 * the highest wins, and a tie keeps registry order.
 */
public record SpiritHerb(List<Either<Holder<Block>, TagKey<Block>>> blocks, Component name, Component description,
                         int defaultAge, List<AgeQuality> qualityByAge,
                         List<Either<Holder<Element>, TagKey<Element>>> elementTags,
                         List<Identifier> materialTags, Map<Holder<MedicinalProperty>, NumberProvider> mainEffects,
                         Map<Holder<MedicinalProperty>, NumberProvider> auxiliaryEffects,
                         NumberProvider catalystPower, double thermalBias,
                         Optional<Growth> growth, int priority) implements NamedDefinition {
    public static final int DEFAULT_PRIORITY = 0;
    private static final String CATEGORY = DefinitionText.category(MxtResourceKeys.SPIRIT_HERB.identifier());
    // A bad property reference fails the whole entry. The shared map codec would log it and keep going.
    private static final Codec<Map<Holder<MedicinalProperty>, NumberProvider>> EFFECTS =
            Codec.unboundedMap(MedicinalProperty.CODEC, NumberProvider.CODEC);
    public static final Codec<Holder<SpiritHerb>> CODEC = RegistryFixedCodec.create(MxtResourceKeys.SPIRIT_HERB);
    public static final Codec<SpiritHerb> DIRECT_CODEC = RecordCodecBuilder.<SpiritHerb>create(i -> i.group(
            RegistryCodecs.holderOrTagList(Registries.BLOCK).fieldOf("blocks").forGetter(SpiritHerb::blocks),
            ContextNameCodec.name(CATEGORY).forGetter(SpiritHerb::name),
            ContextNameCodec.description(CATEGORY).forGetter(SpiritHerb::description),
            Codec.intRange(0, Integer.MAX_VALUE).optionalFieldOf("default_age", 0).forGetter(SpiritHerb::defaultAge),
            AgeQuality.CODEC.listOf().optionalFieldOf("quality_by_age", List.of()).forGetter(SpiritHerb::qualityByAge),
            RegistryCodecs.holderOrTagList(MxtResourceKeys.ELEMENT).optionalFieldOf("element_tags", List.of()).forGetter(SpiritHerb::elementTags),
            Identifier.CODEC.listOf().optionalFieldOf("material_tags", List.of()).forGetter(SpiritHerb::materialTags),
            EFFECTS.optionalFieldOf("main_effects", Map.of()).forGetter(SpiritHerb::mainEffects),
            EFFECTS.optionalFieldOf("auxiliary_effects", Map.of()).forGetter(SpiritHerb::auxiliaryEffects),
            NumberProvider.CODEC.optionalFieldOf("catalyst_power", new Constant(0.0D)).forGetter(SpiritHerb::catalystPower),
            Codec.DOUBLE.optionalFieldOf("thermal_bias", 0.0D).forGetter(SpiritHerb::thermalBias),
            Growth.CODEC.optionalFieldOf("growth").forGetter(SpiritHerb::growth),
            Codec.INT.optionalFieldOf("priority", DEFAULT_PRIORITY).forGetter(SpiritHerb::priority)
    ).apply(i, SpiritHerb::new)).validate(SpiritHerb::validate);

    private static DataResult<SpiritHerb> validate(SpiritHerb herb) {
        if (herb.blocks.isEmpty())
            return DataResult.error(() -> "blocks must name at least one block or block tag");
        if (!Double.isFinite(herb.thermalBias) || herb.thermalBias < -1.0D || herb.thermalBias > 1.0D)
            return DataResult.error(() -> "thermal_bias must be finite and in [-1, 1], was " + herb.thermalBias);
        DataResult<NumberProvider> catalyst = nonNegative(herb.catalystPower, "catalyst_power");
        if (catalyst instanceof DataResult.Error<NumberProvider> error) return DataResult.error(error::message);
        // Two rungs at one age are two answers for the same age, and a descending ladder can never be reached:
        // both are pack mistakes rather than a silent "first one wins".
        int previousMinAge = -1;
        for (AgeQuality entry : herb.qualityByAge) {
            if (entry.minAge() <= previousMinAge)
                return DataResult.error(() -> "quality_by_age must ascend by min_age, was " + entry.minAge());
            previousMinAge = entry.minAge();
        }
        if (herb.growth.isEmpty()) return DataResult.success(herb);
        Growth growth = herb.growth.get();
        // Harvest identity is not checked here. Item components are still unbound while a datapack registry
        // decodes, so building a dropped stack throws.
        DataResult<NumberProvider> rate = nonNegative(growth.growthRate, "growth.growth_rate");
        if (rate instanceof DataResult.Error<NumberProvider> error) return DataResult.error(error::message);
        return DataResult.success(herb);
    }

    // Expressions are checked when they run. A written constant that is already negative is a pack mistake.
    private static DataResult<NumberProvider> nonNegative(NumberProvider provider, String field) {
        if (provider instanceof Constant(double value) && value < 0.0D)
            return DataResult.error(() -> field + " must be non-negative, was " + value);
        return DataResult.success(provider);
    }

    /**
     * The tier this age earns, empty below the lowest rung. Rungs ascend by {@code min_age}, so the last one at or
     * below the age wins; a rung whose tier is unbound is skipped, and no mapping leaves the stack to default_quality.
     */
    public Optional<Holder<ItemQuality>> qualityFor(int age) {
        if (age < 0) throw new IllegalArgumentException("age must be non-negative");
        Holder<ItemQuality> earned = null;
        for (AgeQuality entry : this.qualityByAge) {
            if (entry.minAge() > age) break;
            if (entry.quality().isBound()) earned = entry.quality();
        }
        return earned == null ? Optional.empty() : Optional.of(earned);
    }

    /**
     * One rung of the age ladder. Written ascending and unique, so one age has exactly one answer.
     */
    public record AgeQuality(int minAge, Holder<ItemQuality> quality) {
        public static final Codec<AgeQuality> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.intRange(0, Integer.MAX_VALUE).fieldOf("min_age").forGetter(AgeQuality::minAge),
                ItemQuality.CODEC.fieldOf("quality").forGetter(AgeQuality::quality)
        ).apply(i, AgeQuality::new));
    }

    /**
     * One plant. Appearance is the block's own business, so there is no texture here; {@code wild_age} is the age a
     * never-sown plant starts at, rolled once and stored as a negative value so the row's sign says which it is.
     * {@code drops} claims the stacks that are this herb's fruit, and breaking one stamps its age onto them.
     */
    public record Growth(int matureAge, int maxAge, NumberProvider growthRate,
                         BlockCondition condition, List<Cost> costs,
                         List<Either<Holder<Item>, TagKey<Item>>> drops,
                         NumberProvider wildAge) {
        public static final Codec<Growth> CODEC = RecordCodecBuilder.<Growth>create(i -> i.group(
                Codec.intRange(1, Integer.MAX_VALUE).fieldOf("mature_age").forGetter(Growth::matureAge),
                Codec.intRange(1, Integer.MAX_VALUE).fieldOf("max_age").forGetter(Growth::maxAge),
                NumberProvider.CODEC.fieldOf("growth_rate").forGetter(Growth::growthRate),
                BlockCondition.optionalCodec("condition").forGetter(Growth::condition),
                Cost.CODEC.listOf().optionalFieldOf("costs", List.of()).forGetter(Growth::costs),
                RegistryCodecs.holderOrTagList(Registries.ITEM).fieldOf("drops").forGetter(Growth::drops),
                NumberProvider.CODEC.optionalFieldOf("wild_age", new Constant(0.0D)).forGetter(Growth::wildAge)
        ).apply(i, Growth::new)).validate(Growth::validate);

        private static DataResult<Growth> validate(Growth growth) {
            if (growth.maxAge < growth.matureAge)
                return DataResult.error(() -> "growth.max_age must be at least mature_age");
            if (growth.drops.isEmpty())
                return DataResult.error(() -> "growth.drops must name at least one drop");
            DataResult<List<Cost>> costs = Costs.validateAuras(growth.costs);
            if (costs instanceof DataResult.Error<List<Cost>> error) return DataResult.error(error::message);
            DataResult<List<Cost>> unique = Costs.validate(growth.costs);
            if (unique instanceof DataResult.Error<List<Cost>> error) return DataResult.error(error::message);
            return nonNegative(growth.wildAge, "growth.wild_age").map(ignored -> growth);
        }
    }
}
