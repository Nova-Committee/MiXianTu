package com.iafenvoy.mxt.data.weapon;

import com.iafenvoy.mxt.data.common.AttributeModifierDefinition;
import com.iafenvoy.mxt.data.item.ItemEffectDefinition;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.iafenvoy.mxt.util.formula.NumberProvider.Constant;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;

/** Pure, item-independent combat values for a bound weapon. */
public record WeaponDefinition(NumberProvider attackDamage, NumberProvider attackSpeed,
                               List<AttributeModifierDefinition> attributes) implements ItemEffectDefinition {
    public static final MapCodec<WeaponDefinition> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            NumberProvider.CODEC.optionalFieldOf("attack_damage", new Constant(0.0D)).forGetter(WeaponDefinition::attackDamage),
            NumberProvider.CODEC.optionalFieldOf("attack_speed", new Constant(0.0D)).forGetter(WeaponDefinition::attackSpeed),
            AttributeModifierDefinition.CODEC.listOf().optionalFieldOf("attributes", List.of()).forGetter(WeaponDefinition::attributes)
    ).apply(instance, WeaponDefinition::new));

    @Override
    public String type() {
        return "weapon";
    }

    @Override
    public MapCodec<WeaponDefinition> codec() {
        return CODEC;
    }
}
