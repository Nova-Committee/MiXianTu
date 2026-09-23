package com.iafenvoy.mxt.data.item;

import com.iafenvoy.mxt.data.AttributeEntry;
import com.iafenvoy.mxt.data.DescribedEntry;
import com.iafenvoy.mxt.data.action.BiEntityAction;
import com.iafenvoy.mxt.data.action.EntityAction;
import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.data.cultivation.Element;
import com.iafenvoy.mxt.data.quality.QualityChain;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.util.codec.MiscCodecs;
import com.iafenvoy.mxt.util.codec.RegistryCodecs;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.iafenvoy.mxt.util.formula.number.Constant;
import com.iafenvoy.mxt.util.matcher.ItemMatcher;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.tags.TagKey;

import java.util.List;
import java.util.Optional;

/**
 * Weapon behaviour attached directly to an already registered physical item. {@code element} is what the weapon is
 * made of; an item whose definitions declare no element at all falls back to the element of the aura it stores or
 * declares, and a weapon that is neither declares nothing. {@code attachment_multiplier} is what it is worth as a
 * ward: while carried, every strike that leaves an element on the carrier leaves this fraction of it (0.5 = half
 * gets through, 0 = none). Several carried items multiply, and the default 1.0 changes nothing.
 */
public record WeaponBinding(List<Entry> entries, NumberProvider attackDamage, NumberProvider attackSpeed,
                            List<AttributeEntry> attributes, EntityAction useAction, BiEntityAction attackAction,
                            EntityAction tickAction, Optional<Holder<QualityChain>> qualityChain,
                            List<DescribedEntry<EntityCondition>> conditions,
                            List<Either<Holder<Element>, TagKey<Element>>> element, double attachmentMultiplier) implements ItemMatcher {
    public static final Codec<WeaponBinding> CODEC = RecordCodecBuilder.create(i -> i.group(
            ENTRIES_CODEC.fieldOf("items").forGetter(WeaponBinding::entries),
            NumberProvider.CODEC.optionalFieldOf("attack_damage", new Constant(0.0D)).forGetter(WeaponBinding::attackDamage),
            NumberProvider.CODEC.optionalFieldOf("attack_speed", new Constant(0.0D)).forGetter(WeaponBinding::attackSpeed),
            AttributeEntry.CODEC.listOf().optionalFieldOf("attributes", List.of()).forGetter(WeaponBinding::attributes),
            EntityAction.optionalCodec("use_action").forGetter(WeaponBinding::useAction),
            BiEntityAction.optionalCodec("attack_action").forGetter(WeaponBinding::attackAction),
            EntityAction.optionalCodec("tick_action").forGetter(WeaponBinding::tickAction),
            QualityChain.CODEC.optionalFieldOf("quality_chain").forGetter(WeaponBinding::qualityChain),
            DescribedEntry.codec(EntityCondition.CODEC, "condition").listOf().optionalFieldOf("conditions", List.of()).forGetter(WeaponBinding::conditions),
            RegistryCodecs.holderOrTagList(MxtResourceKeys.ELEMENT).optionalFieldOf("element", List.of()).forGetter(WeaponBinding::element),
            MiscCodecs.NON_NEGATIVE.optionalFieldOf("attachment_multiplier", 1.0D).forGetter(WeaponBinding::attachmentMultiplier)    ).apply(i, WeaponBinding::new));
}
