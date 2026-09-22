package com.iafenvoy.mxt.data.item;

import com.iafenvoy.mxt.data.AttributeEntry;
import com.iafenvoy.mxt.data.DescribedEntry;
import com.iafenvoy.mxt.data.action.BiEntityAction;
import com.iafenvoy.mxt.data.action.EntityAction;
import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.data.cultivation.Element;
import com.iafenvoy.mxt.data.quality.ItemQuality;
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
 * Weapon behaviour attached directly to an already registered physical item.
 *
 * <p>{@code element} is what this weapon is made of, which is the reading
 * {@link com.iafenvoy.mxt.runtime.cultivation.ItemElements} takes: an item whose definitions declare no element
 * at all falls back to the element of the aura it stores or declares, and a weapon that is neither declares
 * nothing.</p>
 *
 * <p>{@code attachment_multiplier} is what this weapon is worth as a ward: while it is carried, every strike
 * that leaves an element on the carrier leaves this fraction of it, so a pack writes {@code 0.5} for "half of it
 * gets through" and {@code 0} for "none of it does". Several carried items multiply, and the default {@code 1.0}
 * means a pack that never writes the field is never affected.</p>
 */
public record WeaponBinding(List<Entry> entries, NumberProvider attackDamage, NumberProvider attackSpeed,
                            List<AttributeEntry> attributes, EntityAction useAction, BiEntityAction attackAction,
                            EntityAction tickAction, Optional<TagKey<ItemQuality>> qualityGroup,
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
            TagKey.hashedCodec(MxtResourceKeys.ITEM_QUALITY).optionalFieldOf("quality_group").forGetter(WeaponBinding::qualityGroup),
            DescribedEntry.codec(EntityCondition.CODEC, "condition").listOf().optionalFieldOf("conditions", List.of()).forGetter(WeaponBinding::conditions),
            RegistryCodecs.holderOrTagList(MxtResourceKeys.ELEMENT).optionalFieldOf("element", List.of()).forGetter(WeaponBinding::element),
            MiscCodecs.NON_NEGATIVE.optionalFieldOf("attachment_multiplier", 1.0D).forGetter(WeaponBinding::attachmentMultiplier)    ).apply(i, WeaponBinding::new));
}
