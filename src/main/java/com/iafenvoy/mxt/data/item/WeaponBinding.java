package com.iafenvoy.mxt.data.item;

import com.iafenvoy.mxt.data.AttributeEntry;
import com.iafenvoy.mxt.data.DescribedEntry;
import com.iafenvoy.mxt.data.action.BiEntityAction;
import com.iafenvoy.mxt.data.action.EntityAction;
import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.data.cultivation.Element;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.util.codec.RegistryCodecs;
import com.iafenvoy.mxt.util.matcher.ItemMatcher;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.tags.TagKey;

import java.util.List;

/**
 * Weapon behaviour attached to an already registered physical item; the item is the data map's key. {@code element}
 * is what it is made of (falling back to the aura it stores or declares); attack numbers are ordinary
 * {@code attributes}, never a replacement.
 */
public record WeaponBinding(List<AttributeEntry> attributes, EntityAction useAction,
                            BiEntityAction attackAction, EntityAction tickAction,
                            List<DescribedEntry<EntityCondition>> conditions,
                            List<Either<Holder<Element>, TagKey<Element>>> element,
                            int priority) {
    public static final Codec<WeaponBinding> CODEC = RecordCodecBuilder.create(i -> i.group(
            AttributeEntry.CODEC.listOf().optionalFieldOf("attributes", List.of()).forGetter(WeaponBinding::attributes),
            EntityAction.optionalCodec("use_action").forGetter(WeaponBinding::useAction),
            BiEntityAction.optionalCodec("attack_action").forGetter(WeaponBinding::attackAction),
            EntityAction.optionalCodec("tick_action").forGetter(WeaponBinding::tickAction),
            DescribedEntry.codec(EntityCondition.CODEC, "condition").listOf().optionalFieldOf("conditions", List.of()).forGetter(WeaponBinding::conditions),
            RegistryCodecs.holderOrTagList(MxtResourceKeys.ELEMENT).optionalFieldOf("element", List.of()).forGetter(WeaponBinding::element),
            Codec.INT.optionalFieldOf("priority", ItemMatcher.DEFAULT_PRIORITY).forGetter(WeaponBinding::priority)).apply(i, WeaponBinding::new));
}
