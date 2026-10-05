package com.iafenvoy.mxt.data.alchemy;

import com.iafenvoy.mxt.api.NamedDefinition;
import com.iafenvoy.mxt.api.QualityProvider;
import com.iafenvoy.mxt.data.quality.ItemQuality;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.util.DefinitionText;
import com.iafenvoy.mxt.util.codec.ContextNameCodec;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryFixedCodec;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

/**
 * One furnace specification. Slot counts, capacity, cooling and the furnace's own temperature ceiling are written
 * here. The ceiling is the lowest of three: this {@code max_temperature}, the walls' material rating and the heat
 * block's own maximum, so a better specification only raises it as far as the structure allows. {@code quality} is
 * the tier a furnace built to this specification starts on: every specification shares one block item, so the item
 * cannot say which tier applies.
 */
public record AlchemyFurnaceDefinition(Component name, Component description, int mainSlots, int auxiliarySlots,
                                       Optional<Holder<ItemQuality>> quality, int capacity, double coolingPerTick,
                                       Optional<Double> maxTemperature)
        implements NamedDefinition, QualityProvider {
    public static final int CATALYST_SLOTS = 1;
    public static final int MAX_CAPACITY = 320;
    private static final String CATEGORY = DefinitionText.category(MxtResourceKeys.ALCHEMY_FURNACE.identifier());
    private static final Codec<Double> POSITIVE = Codec.DOUBLE.validate(AlchemyFurnaceDefinition::positive);
    public static final Codec<Holder<AlchemyFurnaceDefinition>> CODEC = RegistryFixedCodec.create(MxtResourceKeys.ALCHEMY_FURNACE);
    public static final Codec<AlchemyFurnaceDefinition> DIRECT_CODEC = RecordCodecBuilder.create(i -> i.group(
            ContextNameCodec.name(CATEGORY).forGetter(AlchemyFurnaceDefinition::name),
            ContextNameCodec.description(CATEGORY).forGetter(AlchemyFurnaceDefinition::description),
            Codec.intRange(1, 2).fieldOf("main_slots").forGetter(AlchemyFurnaceDefinition::mainSlots),
            Codec.intRange(0, 2).optionalFieldOf("auxiliary_slots", 0).forGetter(AlchemyFurnaceDefinition::auxiliarySlots),
            ItemQuality.CODEC.optionalFieldOf("quality").forGetter(AlchemyFurnaceDefinition::quality),
            Codec.intRange(1, MAX_CAPACITY).fieldOf("capacity").forGetter(AlchemyFurnaceDefinition::capacity),
            POSITIVE.fieldOf("cooling_per_tick").forGetter(AlchemyFurnaceDefinition::coolingPerTick),
            POSITIVE.optionalFieldOf("max_temperature").forGetter(AlchemyFurnaceDefinition::maxTemperature)
    ).apply(i, AlchemyFurnaceDefinition::new));

    @Override
    public Optional<Holder<ItemQuality>> defaultQuality() {
        return this.quality;
    }

    /**
     * The catalyst slot count is fixed; it is not a pack field.
     */
    public int catalystSlots() {
        return CATALYST_SLOTS;
    }

    // The one temperature ceiling rule: a specification is one of three ceilings, never the only one, so a better
    // core only helps up to what the walls and the heat block give. Zero means the furnace cannot run at all.
    public static double temperatureLimit(double wallLimit, double heatLimit, @Nullable AlchemyFurnaceDefinition spec) {
        if (wallLimit <= 0.0D || heatLimit <= 0.0D) return 0.0D;
        double own = spec == null ? Double.MAX_VALUE : spec.maxTemperature().orElse(Double.MAX_VALUE);
        return Math.min(own, Math.min(wallLimit, heatLimit));
    }

    private static DataResult<Double> positive(double value) {
        return Double.isFinite(value) && value > 0.0D
                ? DataResult.success(value)
                : DataResult.error(() -> "Furnace cooling_per_tick and max_temperature must be finite positive numbers");
    }
}
