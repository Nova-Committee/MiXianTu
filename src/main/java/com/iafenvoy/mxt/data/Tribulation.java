package com.iafenvoy.mxt.data;

import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.saturn.data.action.EntityAction;
import com.iafenvoy.saturn.data.condition.EntityCondition;
import com.iafenvoy.saturn.data.timeline.TimelineEntry;
import com.iafenvoy.saturn.util.formula.FormulaContext;
import com.iafenvoy.saturn.util.formula.NumberProvider;
import com.iafenvoy.saturn.util.formula.number.Constant;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.resources.RegistryFixedCodec;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Optional;

/**
 * Heavenly tribulation definition: the gate that has to pass for it to start, the wind-up before the timeline,
 * the timeline itself, the difficulty scale every wait is measured against, whether the sky darkens, and the two
 * endings. {@code condition} is a gate evaluated once when it is started, not an event trigger; {@code windup}
 * is resolved through the same rule as every other wait, so the countdown a player watches is the number of ticks
 * that will really pass.
 */
public record Tribulation(EntityCondition condition, List<TimelineEntry> timeline, NumberProvider difficultyScale,
                          NumberProvider windup, boolean darkenSky, EntityAction successAction,
                          EntityAction failAction, Optional<StormCloud> stormCloud) {
    public static final Codec<Holder<Tribulation>> CODEC = RegistryFixedCodec.create(MxtResourceKeys.TRIBULATION);
    public static final Codec<Tribulation> DIRECT_CODEC = RecordCodecBuilder.<Tribulation>create(i -> i.group(
            EntityCondition.optionalCodec("condition").forGetter(Tribulation::condition),
            TimelineEntry.CODEC.listOf().fieldOf("timeline").forGetter(Tribulation::timeline),
            NumberProvider.CODEC.optionalFieldOf("difficulty_scale", new Constant(1.0D)).forGetter(Tribulation::difficultyScale),
            NumberProvider.CODEC.optionalFieldOf("windup", new Constant(0.0D)).forGetter(Tribulation::windup),
            Codec.BOOL.optionalFieldOf("darken_sky", true).forGetter(Tribulation::darkenSky),
            EntityAction.optionalCodec("success_action").forGetter(Tribulation::successAction),
            EntityAction.optionalCodec("fail_action").forGetter(Tribulation::failAction),
            StormCloud.CODEC.codec().optionalFieldOf("storm_cloud").forGetter(Tribulation::stormCloud)
    ).apply(i, Tribulation::new)).validate(Tribulation::validate);

    public Tribulation(EntityCondition condition, List<TimelineEntry> timeline, NumberProvider difficultyScale,
                       NumberProvider windup, boolean darkenSky, EntityAction successAction, EntityAction failAction) {
        this(condition, timeline, difficultyScale, windup, darkenSky, successAction, failAction, Optional.empty());
    }

    private static DataResult<Tribulation> validate(Tribulation value) {
        if (value.timeline.isEmpty())
            return DataResult.error(() -> "Tribulation requires at least one timeline entry");
        return DataResult.success(value);
    }

    public record StormCloud(StormCloudOptions.Appearance appearance, float radius, NumberProvider y) {
        public static final double DEFAULT_Y = 160.0D;
        private static final Codec<NumberProvider> Y_CODEC = NumberProvider.CODEC.validate(provider ->
                provider instanceof Constant(double value) && !validY(value)
                        ? DataResult.error(() -> "Storm cloud Y is outside the entity spawn range: " + value)
                        : DataResult.success(provider));
        public static final MapCodec<StormCloud> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                StormCloudOptions.Appearance.CODEC.forGetter(StormCloud::appearance),
                StormCloudOptions.RADIUS_CODEC.optionalFieldOf("radius", 64.0F).forGetter(StormCloud::radius),
                Y_CODEC.optionalFieldOf("y", new Constant(DEFAULT_Y)).forGetter(StormCloud::y)
        ).apply(i, StormCloud::new));

        public double resolveY(FormulaContext context, double fallback) {
            double height = this.y.evaluate(context);
            return validY(height) ? height : fallback;
        }

        private static boolean validY(double height) {
            return height >= -Level.MAX_ENTITY_SPAWN_Y && height < Level.MAX_ENTITY_SPAWN_Y;
        }
    }
}
