package com.iafenvoy.mxt.data;

import com.iafenvoy.mxt.data.action.EntityAction;
import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.data.timeline.TimelineEntry;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.iafenvoy.mxt.util.formula.number.Constant;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.resources.RegistryFixedCodec;

import java.util.List;

/**
 * Heavenly tribulation definition: the gate that has to pass for it to start, the timeline it consumes, the
 * difficulty scale every wait is measured against, and the behaviour of its two endings.
 *
 * <p>{@code condition} is a gate evaluated once when the tribulation is started, not an event trigger: the
 * caller has already decided to start it, and this only decides whether that attempt is accepted. The
 * timeline is a list of beats consumed one entry at a time, so a definition describes what happens rather
 * than how many phases it has.</p>
 */
public record Tribulation(EntityCondition condition, List<TimelineEntry> timeline,
                          NumberProvider difficultyScale, EntityAction successAction,
                          EntityAction failAction) {
    public static final Codec<Holder<Tribulation>> CODEC = RegistryFixedCodec.create(MxtResourceKeys.TRIBULATION);
    public static final Codec<Tribulation> DIRECT_CODEC = RecordCodecBuilder.<Tribulation>create(i -> i.group(
            EntityCondition.optionalCodec("condition").forGetter(Tribulation::condition),
            TimelineEntry.CODEC.listOf().fieldOf("timeline").forGetter(Tribulation::timeline),
            NumberProvider.CODEC.optionalFieldOf("difficulty_scale", new Constant(1.0D)).forGetter(Tribulation::difficultyScale),
            EntityAction.optionalCodec("success_action").forGetter(Tribulation::successAction),
            EntityAction.optionalCodec("fail_action").forGetter(Tribulation::failAction)
    ).apply(i, Tribulation::new)).validate(Tribulation::validate);

    private static DataResult<Tribulation> validate(Tribulation value) {
        if (value.timeline.isEmpty())
            return DataResult.error(() -> "Tribulation requires at least one timeline entry");
        return DataResult.success(value);
    }
}
