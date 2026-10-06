package com.iafenvoy.mxt.data;

import com.iafenvoy.mxt.api.NamedDefinition;
import com.iafenvoy.mxt.data.ability.Ability;
import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.data.cost.Cost;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.util.DefinitionText;
import com.iafenvoy.mxt.util.codec.ContextNameCodec;
import com.iafenvoy.mxt.util.codec.RegistryCodecs;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryFixedCodec;
import net.minecraft.tags.TagKey;

import java.util.List;

/**
 * One inscribed talisman a carrier can hold: {@code abilities} is the only effect field, {@code capacity} how many
 * invocations' worth of aura the carrier stores and {@code costs} what one invocation takes, both summed over
 * everything written on one carrier. Capacity is a multiplier of one invocation's aura entries, at least one, and
 * an aura entry of {@code costs} is drawn from that store - which is what lets a carrier written with room for
 * several invocations fire again without being poured, while every other entry is paid by the holder. The
 * multiplier is capped by the wear the carrier has left, so a carrier with two invocations in it is never poured
 * for five, and one whose definitions declare no wear has exactly one. {@code durability} is how much wear the
 * carrier made of it has and {@code consume} how much one invocation takes off; zero durability means the carrier
 * never wears out but is spent whole. {@code quality} is the tier a carrier made from this starts on; whether it
 * fires on its own lives on the {@code mxt:talisman} stack, not here. {@code condition} is asked of the holder
 * before {@code costs} are planned, so an inscription that says "not now" refuses the carrier before anything is
 * paid for it.
 */
public record Talisman(Component name, Component description,
                       List<Either<Holder<Ability>, TagKey<Ability>>> abilities,
                       double capacity,
                       int durability, int consume,
                       EntityCondition condition,
                       List<Cost> costs) implements NamedDefinition {
    private static final String CATEGORY = DefinitionText.category(MxtResourceKeys.TALISMAN.identifier());
    public static final Codec<Holder<Talisman>> CODEC = RegistryFixedCodec.create(MxtResourceKeys.TALISMAN);
    public static final Codec<Talisman> DIRECT_CODEC = RecordCodecBuilder.<Talisman>create(i -> i.group(
            ContextNameCodec.name(CATEGORY).forGetter(Talisman::name),
            ContextNameCodec.description(CATEGORY).forGetter(Talisman::description),
            RegistryCodecs.holderOrTagList(MxtResourceKeys.ABILITY).optionalFieldOf("abilities", List.of()).forGetter(Talisman::abilities),
            Codec.doubleRange(1.0D, Double.MAX_VALUE).optionalFieldOf("capacity", 1.0D).forGetter(Talisman::capacity),
            Codec.INT.optionalFieldOf("durability", 0).forGetter(Talisman::durability),
            Codec.INT.optionalFieldOf("consume", 1).forGetter(Talisman::consume),
            EntityCondition.optionalCodec("condition").forGetter(Talisman::condition),
            Cost.LIST_CODEC.optionalFieldOf("costs", List.of()).forGetter(Talisman::costs)
    ).apply(i, Talisman::new)).flatXmap(Talisman::validate, Talisman::validate);

    private static DataResult<Talisman> validate(Talisman talisman) {
        if (talisman.durability < 0)
            return DataResult.error(() -> "A talisman's durability cannot be negative");
        return DataResult.success(talisman);
    }
}
