package com.iafenvoy.mxt.data;

import com.iafenvoy.mxt.data.item.ItemEffect;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.iafenvoy.mxt.util.formula.NumberProvider.Constant;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;

/**
 * Pure, item-independent combat values for a bound weapon.
 */
public record Weapon(NumberProvider attackDamage, NumberProvider attackSpeed,
                     List<AttributeModifier> attributes) implements ItemEffect {
    public static final MapCodec<Weapon> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            NumberProvider.CODEC.optionalFieldOf("attack_damage", new Constant(0.0D)).forGetter(Weapon::attackDamage),
            NumberProvider.CODEC.optionalFieldOf("attack_speed", new Constant(0.0D)).forGetter(Weapon::attackSpeed),
            AttributeModifier.CODEC.listOf().optionalFieldOf("attributes", List.of()).forGetter(Weapon::attributes)
    ).apply(instance, Weapon::new));

    @Override
    public String type() {
        return "weapon";
    }

    @Override
    public MapCodec<Weapon> codec() {
        return CODEC;
    }
}
