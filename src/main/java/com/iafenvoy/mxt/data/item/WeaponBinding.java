package com.iafenvoy.mxt.data.item;

import com.iafenvoy.mxt.data.AttributeModifier;
import com.iafenvoy.mxt.data.action.BiEntityAction;
import com.iafenvoy.mxt.data.action.EntityAction;
import com.iafenvoy.mxt.data.action.builtin.bientity.meta.BiEntityNoOpAction;
import com.iafenvoy.mxt.data.action.builtin.entity.meta.NoOpAction;
import com.iafenvoy.mxt.util.ItemMatcher.Entry;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.iafenvoy.mxt.util.formula.number.Constant;
import com.iafenvoy.mxt.util.ItemMatcher;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;

/**
 * Weapon behaviour attached directly to an already registered physical item.
 */
public record WeaponBinding(List<Entry> entries, NumberProvider attackDamage, NumberProvider attackSpeed,
                            List<AttributeModifier> attributes, EntityAction useAction, BiEntityAction attackAction,
                            EntityAction tickAction) implements ItemMatcher {
    public static final Codec<WeaponBinding> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ENTRIES_CODEC.fieldOf("items").forGetter(WeaponBinding::entries),
            NumberProvider.CODEC.optionalFieldOf("attack_damage", new Constant(0.0D)).forGetter(WeaponBinding::attackDamage),
            NumberProvider.CODEC.optionalFieldOf("attack_speed", new Constant(0.0D)).forGetter(WeaponBinding::attackSpeed),
            AttributeModifier.CODEC.listOf().optionalFieldOf("attributes", List.of()).forGetter(WeaponBinding::attributes),
            EntityAction.CODEC.optionalFieldOf("use_action", NoOpAction.INSTANCE).forGetter(WeaponBinding::useAction),
            BiEntityAction.CODEC.optionalFieldOf("attack_action", BiEntityNoOpAction.INSTANCE).forGetter(WeaponBinding::attackAction),
            EntityAction.CODEC.optionalFieldOf("tick_action", NoOpAction.INSTANCE).forGetter(WeaponBinding::tickAction)
    ).apply(instance, WeaponBinding::new));
}
