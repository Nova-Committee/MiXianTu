package com.iafenvoy.mxt.data.item;

import com.iafenvoy.mxt.data.AttributeEntry;
import com.iafenvoy.mxt.data.action.BiEntityAction;
import com.iafenvoy.mxt.data.action.EntityAction;
import com.iafenvoy.mxt.data.quality.ItemQuality;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.util.matcher.ItemMatcher;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.iafenvoy.mxt.util.formula.number.Constant;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.tags.TagKey;

import java.util.List;
import java.util.Optional;

/**
 * Weapon behaviour attached directly to an already registered physical item.
 */
public record WeaponBinding(List<Entry> entries, NumberProvider attackDamage, NumberProvider attackSpeed,
                            List<AttributeEntry> attributes, EntityAction useAction, BiEntityAction attackAction,
                            EntityAction tickAction, Optional<TagKey<ItemQuality>> qualityGroup,
                            List<ConditionEntry> conditions) implements ItemMatcher {
    public static final Codec<WeaponBinding> CODEC = RecordCodecBuilder.create(i -> i.group(
            ENTRIES_CODEC.fieldOf("items").forGetter(WeaponBinding::entries),
            NumberProvider.CODEC.optionalFieldOf("attack_damage", new Constant(0.0D)).forGetter(WeaponBinding::attackDamage),
            NumberProvider.CODEC.optionalFieldOf("attack_speed", new Constant(0.0D)).forGetter(WeaponBinding::attackSpeed),
            AttributeEntry.CODEC.listOf().optionalFieldOf("attributes", List.of()).forGetter(WeaponBinding::attributes),
            EntityAction.optionalCodec("use_action").forGetter(WeaponBinding::useAction),
            BiEntityAction.optionalCodec("attack_action").forGetter(WeaponBinding::attackAction),
            EntityAction.optionalCodec("tick_action").forGetter(WeaponBinding::tickAction),
            TagKey.hashedCodec(MxtResourceKeys.ITEM_QUALITY).optionalFieldOf("quality_group").forGetter(WeaponBinding::qualityGroup),
            ConditionEntry.CODEC.listOf().optionalFieldOf("conditions", List.of()).forGetter(WeaponBinding::conditions)
    ).apply(i, WeaponBinding::new));
}
