package com.iafenvoy.mxt.data.curse;

import com.iafenvoy.mxt.data.action.EntityAction;
import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.data.curse.CurseType.Timed;
import com.iafenvoy.mxt.data.curse.CurseType.Triggered;
import com.iafenvoy.mxt.data.trigger.Trigger;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.iafenvoy.mxt.util.formula.number.Constant;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.resources.RegistryFixedCodec;
import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.Locale;

/**
 * Static definition for a named curse. Dynamic layers and expiry belong to an attachment.
 * <p>
 * A curse owns behaviour for exactly two moments of its own life: natural expiry and being cleansed. What may
 * cleanse it is never decided here - the cure side names the curse tags it removes, and the tag file lists the
 * curses that belong to them.
 * <p>
 * {@code display_condition} is the one field about being seen rather than felt: the character panel only lists a
 * curse while it passes, so a content pack can keep a hidden curse hidden (and reveal it once its own state says
 * so).
 * <p>
 * {@code on_apply} runs when an instance is <em>created</em>. Stacking onto, or refreshing, a curse that is
 * already held is a later application of the same instance and does not run it again.
 */
public record Curse(CurseType typedType, NumberProvider durationTicks, NumberProvider tickInterval, int maxStacks,
                    StackingMode stackingMode, EntityCondition applicationCondition, EntityCondition displayCondition,
                    EntityAction onApply, EntityAction onTick, EntityAction onExpire, EntityAction onCleanse) {
    public static final Codec<Holder<Curse>> CODEC = RegistryFixedCodec.create(MxtResourceKeys.CURSE);
    public static final Codec<Curse> DIRECT_CODEC = RecordCodecBuilder.<Curse>create(i -> i.group(
            CurseType.MAP_CODEC.forGetter(Curse::typedType),
            NumberProvider.CODEC.optionalFieldOf("duration_ticks", new Constant(0.0D)).forGetter(Curse::durationTicks),
            NumberProvider.CODEC.optionalFieldOf("tick_interval", new Constant(20.0D)).forGetter(Curse::tickInterval),
            Codec.intRange(1, 256).optionalFieldOf("max_stacks", 1).forGetter(Curse::maxStacks),
            StackingMode.CODEC.optionalFieldOf("stacking_mode", StackingMode.IGNORE).forGetter(Curse::stackingMode),
            EntityCondition.optionalCodec("application_condition").forGetter(Curse::applicationCondition),
            EntityCondition.optionalCodec("display_condition").forGetter(Curse::displayCondition),
            EntityAction.optionalCodec("on_apply").forGetter(Curse::onApply),
            EntityAction.optionalCodec("on_tick").forGetter(Curse::onTick),
            EntityAction.optionalCodec("on_expire").forGetter(Curse::onExpire),
            EntityAction.optionalCodec("on_cleanse").forGetter(Curse::onCleanse)
    ).apply(i, Curse::new)).validate(Curse::validate);

    /**
     * Rejects at load what would otherwise fail much later, at the first application: a {@code mxt:timed} curse
     * needs a duration it can expire from, and a {@code mxt:triggered} curse needs something to trigger it.
     * <p>
     * Only durations written as constants are judged here; a duration given as an expression is judged when it
     * is evaluated, where an unusable result rejects that one application instead of throwing.
     */
    private static DataResult<Curse> validate(Curse curse) {
        if (curse.typedType() == Timed.INSTANCE && curse.durationTicks() instanceof Constant(double value)
                && value <= 0.0D)
            return DataResult.error(() -> "An mxt:timed curse needs a positive duration_ticks");
        if (curse.typedType() instanceof Triggered(
                List<Trigger> triggers
        ) && triggers.isEmpty())
            return DataResult.error(() -> "An mxt:triggered curse needs at least one entry in triggers");
        return DataResult.success(curse);
    }

    @Override
    public @NonNull String toString() {
        return "Curse[type=" + this.typedType.id() + ", maxStacks=" + this.maxStacks
                + ", stackingMode=" + this.stackingMode + "]";
    }

    public enum StackingMode {
        IGNORE,
        REFRESH_DURATION,
        ADD_STACKS_REFRESH_DURATION,
        ADD_STACKS_KEEP_DURATION,
        REPLACE;

        public static final Codec<StackingMode> CODEC = Codec.STRING.xmap(value -> valueOf(value.toUpperCase(Locale.ROOT)), value -> value.name().toLowerCase(Locale.ROOT));
    }
}
