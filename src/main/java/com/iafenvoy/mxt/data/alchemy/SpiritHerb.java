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
import com.iafenvoy.mxt.util.matcher.ItemMatcher;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryFixedCodec;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStackTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Metadata bound to existing items. A pack entry never registers its own Item. {@code element_tags} is affinity,
 * written with the element registry and read by {@code mxt:herb_tag}; it is not a medicinal property. Omitting
 * {@code growth} leaves the item usable in a furnace but unsowable. Overlapping entries resolve by {@code priority}:
 * the highest wins, and a tie keeps registry order.
 */
public record SpiritHerb(List<Entry> entries, Holder<ItemQuality> quality, Component name, Component description,
                         int defaultAge, List<Either<Holder<Element>, TagKey<Element>>> elementTags,
                         List<Identifier> materialTags, Map<Holder<MedicinalProperty>, NumberProvider> mainEffects,
                         Map<Holder<MedicinalProperty>, NumberProvider> auxiliaryEffects,
                         NumberProvider catalystPower, double thermalBias,
                         Optional<Growth> growth, int priority) implements ItemMatcher, NamedDefinition {
    private static final String CATEGORY = DefinitionText.category(MxtResourceKeys.SPIRIT_HERB.identifier());
    // A bad property reference fails the whole entry. The shared map codec would log it and keep going.
    private static final Codec<Map<Holder<MedicinalProperty>, NumberProvider>> EFFECTS =
            Codec.unboundedMap(MedicinalProperty.CODEC, NumberProvider.CODEC);
    public static final Codec<Holder<SpiritHerb>> CODEC = RegistryFixedCodec.create(MxtResourceKeys.SPIRIT_HERB);
    public static final Codec<SpiritHerb> DIRECT_CODEC = RecordCodecBuilder.<SpiritHerb>create(i -> i.group(
            ENTRIES_CODEC.fieldOf("items").forGetter(SpiritHerb::entries),
            ItemQuality.CODEC.fieldOf("quality").forGetter(SpiritHerb::quality),
            ContextNameCodec.name(CATEGORY).forGetter(SpiritHerb::name),
            ContextNameCodec.description(CATEGORY).forGetter(SpiritHerb::description),
            Codec.intRange(0, Integer.MAX_VALUE).optionalFieldOf("default_age", 0).forGetter(SpiritHerb::defaultAge),
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
        if (!Double.isFinite(herb.thermalBias) || herb.thermalBias < -1.0D || herb.thermalBias > 1.0D)
            return DataResult.error(() -> "thermal_bias must be finite and in [-1, 1], was " + herb.thermalBias);
        DataResult<NumberProvider> catalyst = nonNegative(herb.catalystPower, "catalyst_power");
        if (catalyst instanceof DataResult.Error<NumberProvider> error) return DataResult.error(error::message);
        if (herb.growth.isEmpty()) return DataResult.success(herb);
        Growth growth = herb.growth.get();
        if (growth.seeds.isEmpty()) return DataResult.error(() -> "growth.seeds must name at least one seed");
        if (growth.harvest.count() < 1)
            return DataResult.error(() -> "growth.harvest must contain at least one item");
        // Harvest identity is not checked here. Item components are still unbound while a datapack registry
        // decodes, so building the template's stack throws, and a bare item would also drop the template's components.
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
     * One plot, one plant. A model-style texture id is resolved to {@code textures/<path>.png} when drawn.
     */
    public record Growth(List<Entry> seeds, int matureAge, int maxAge, NumberProvider growthRate,
                         BlockCondition condition, List<Cost> costs, ItemStackTemplate harvest,
                         Identifier texture) {
        public static final Codec<Growth> CODEC = RecordCodecBuilder.<Growth>create(i -> i.group(
                ENTRIES_CODEC.fieldOf("seeds").forGetter(Growth::seeds),
                Codec.intRange(1, Integer.MAX_VALUE).fieldOf("mature_age").forGetter(Growth::matureAge),
                Codec.intRange(1, Integer.MAX_VALUE).fieldOf("max_age").forGetter(Growth::maxAge),
                NumberProvider.CODEC.fieldOf("growth_rate").forGetter(Growth::growthRate),
                BlockCondition.optionalCodec("condition").forGetter(Growth::condition),
                Cost.CODEC.listOf().optionalFieldOf("costs", List.of()).forGetter(Growth::costs),
                ItemStackTemplate.CODEC.fieldOf("harvest").forGetter(Growth::harvest),
                Identifier.CODEC.fieldOf("texture").forGetter(Growth::texture)
        ).apply(i, Growth::new)).validate(Growth::validate);

        private static DataResult<Growth> validate(Growth growth) {
            if (growth.maxAge < growth.matureAge)
                return DataResult.error(() -> "growth.max_age must be at least mature_age");
            DataResult<List<Cost>> costs = Costs.validateAuras(growth.costs);
            if (costs instanceof DataResult.Error<List<Cost>> error) return DataResult.error(error::message);
            DataResult<List<Cost>> unique = Costs.validate(growth.costs);
            if (unique instanceof DataResult.Error<List<Cost>> error) return DataResult.error(error::message);
            if (growth.texture.getPath().isEmpty())
                return DataResult.error(() -> "growth.texture must name a texture");
            return DataResult.success(growth);
        }

        public Identifier textureResource() {
            String path = this.texture.getPath();
            if (path.startsWith("textures/") && path.endsWith(".png")) return this.texture;
            String file = path.endsWith(".png") ? path : "textures/" + path + ".png";
            return Identifier.fromNamespaceAndPath(this.texture.getNamespace(), file);
        }
    }
}
