package com.iafenvoy.mxt.data;

import com.iafenvoy.mxt.api.NamedDefinition;
import com.iafenvoy.mxt.data.action.EntityAction;
import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.data.timeline.TimelineEntry;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.util.DefinitionText;
import com.iafenvoy.mxt.util.codec.ContextNameCodec;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.iafenvoy.mxt.util.formula.number.Constant;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryFixedCodec;

import java.util.List;

/**
 * Heavenly tribulation definition: the gate that has to pass for it to start, the wind-up before the timeline,
 * the timeline itself, the difficulty scale every wait is measured against, whether the sky darkens, and the two
 * endings. {@code condition} is a gate evaluated once when it is started, not an event trigger; {@code windup}
 * is resolved through the same rule as every other wait, so the countdown a player watches is the number of ticks
 * that will really pass.
 */
public record Tribulation(Component name, Component description, EntityCondition condition,
                          List<TimelineEntry> timeline,
                          NumberProvider difficultyScale, NumberProvider windup, boolean darkenSky,
                          EntityAction successAction, EntityAction failAction) implements NamedDefinition {
    private static final String CATEGORY = DefinitionText.category(MxtResourceKeys.TRIBULATION.identifier());
    public static final Codec<Holder<Tribulation>> CODEC = RegistryFixedCodec.create(MxtResourceKeys.TRIBULATION);
    public static final Codec<Tribulation> DIRECT_CODEC = RecordCodecBuilder.<Tribulation>create(i -> i.group(
            ContextNameCodec.name(CATEGORY).forGetter(Tribulation::name),
            ContextNameCodec.description(CATEGORY).forGetter(Tribulation::description),
            EntityCondition.optionalCodec("condition").forGetter(Tribulation::condition),
            TimelineEntry.CODEC.listOf().fieldOf("timeline").forGetter(Tribulation::timeline),
            NumberProvider.CODEC.optionalFieldOf("difficulty_scale", new Constant(1.0D)).forGetter(Tribulation::difficultyScale),
            NumberProvider.CODEC.optionalFieldOf("windup", new Constant(0.0D)).forGetter(Tribulation::windup),
            Codec.BOOL.optionalFieldOf("darken_sky", true).forGetter(Tribulation::darkenSky),
            EntityAction.optionalCodec("success_action").forGetter(Tribulation::successAction),
            EntityAction.optionalCodec("fail_action").forGetter(Tribulation::failAction)
    ).apply(i, Tribulation::new)).validate(Tribulation::validate);

    private static DataResult<Tribulation> validate(Tribulation value) {
        if (value.timeline.isEmpty())
            return DataResult.error(() -> "Tribulation requires at least one timeline entry");
        return DataResult.success(value);
    }
}
