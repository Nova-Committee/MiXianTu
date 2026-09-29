package com.iafenvoy.mxt.data.alchemy;

import com.iafenvoy.mxt.api.NamedDefinition;
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

/**
 * One furnace specification. Slot counts, capacity and cooling are written here. Temperature ceiling comes from the
 * walls and the fire item, not from this definition. Quality is only the shared display and use-condition tier.
 */
public record AlchemyFurnaceDefinition(Component name, Component description, int mainSlots, int auxiliarySlots,
                                       Holder<ItemQuality> quality, int capacity, double coolingPerTick)
        implements NamedDefinition {
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
            ItemQuality.CODEC.fieldOf("quality").forGetter(AlchemyFurnaceDefinition::quality),
            Codec.intRange(1, MAX_CAPACITY).fieldOf("capacity").forGetter(AlchemyFurnaceDefinition::capacity),
            POSITIVE.fieldOf("cooling_per_tick").forGetter(AlchemyFurnaceDefinition::coolingPerTick)
    ).apply(i, AlchemyFurnaceDefinition::new));

    /**
     * The catalyst slot count is fixed; it is not a pack field.
     */
    public int catalystSlots() {
        return CATALYST_SLOTS;
    }

    private static DataResult<Double> positive(double value) {
        return Double.isFinite(value) && value > 0.0D
                ? DataResult.success(value)
                : DataResult.error(() -> "Furnace cooling_per_tick must be a finite positive number");
    }
}
