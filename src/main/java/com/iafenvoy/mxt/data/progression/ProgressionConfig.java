package com.iafenvoy.mxt.data.progression;

import com.iafenvoy.mxt.data.ability.Ability;
import com.iafenvoy.mxt.data.action.EntityAction;
import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.util.codec.RegistryCodecs;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.tags.TagKey;

import java.util.List;

/**
 * What one owner says about one level of a chain: the condition to reach it, the abilities it grants there and
 * what entering it runs once. {@code condition} is required - a level that needs nothing writes {@code mxt:always}
 * - and {@code ability} is a minimum requirement, so its abilities stay active on that level and every later one.
 */
public record ProgressionConfig(EntityCondition condition,
                                List<Either<Holder<Ability>, TagKey<Ability>>> abilities,
                                EntityAction action) {
    public static final Codec<ProgressionConfig> CODEC = RecordCodecBuilder.create(i -> i.group(
            EntityCondition.CODEC.fieldOf("condition").forGetter(ProgressionConfig::condition),
            RegistryCodecs.holderOrTagList(MxtResourceKeys.ABILITY).optionalFieldOf("ability", List.of()).forGetter(ProgressionConfig::abilities),
            EntityAction.optionalCodec("action").forGetter(ProgressionConfig::action)
    ).apply(i, ProgressionConfig::new));
}
