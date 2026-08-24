package com.iafenvoy.mxt.data;

import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.data.condition.builtin.entity.meta.AlwaysTrueEntityCondition;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryFixedCodec;

import java.util.List;

/**
 * A displayable title with conflict groups and passive bonuses.
 */
public record Title(EntityCondition unlockCondition, int priority,
                    List<AttributeEntry> passiveModifiers, List<Identifier> exclusiveTags, int maximumLevel) {
    public static final Codec<Holder<Title>> CODEC = RegistryFixedCodec.create(MxtResourceKeys.TITLE);
    public static final Codec<Title> DIRECT_CODEC = RecordCodecBuilder.create(i -> i.group(
            EntityCondition.CODEC.optionalFieldOf("unlock_condition", AlwaysTrueEntityCondition.INSTANCE).forGetter(Title::unlockCondition),
            Codec.INT.optionalFieldOf("priority", 0).forGetter(Title::priority),
            AttributeEntry.CODEC.listOf().optionalFieldOf("passive_modifiers", List.of()).forGetter(Title::passiveModifiers),
            Identifier.CODEC.listOf().optionalFieldOf("exclusive_tags", List.of()).forGetter(Title::exclusiveTags),
            Codec.intRange(1, 1_000).optionalFieldOf("maximum_level", 1).forGetter(Title::maximumLevel)
    ).apply(i, Title::new));
}
