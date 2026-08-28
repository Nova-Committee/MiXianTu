package com.iafenvoy.mxt.data.curse;

import com.iafenvoy.mxt.data.action.EntityAction;
import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.iafenvoy.mxt.util.formula.number.Constant;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.core.Holder;
import net.minecraft.resources.RegistryFixedCodec;
import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.Locale;

/**
 * Static definition for a named curse. Dynamic layers and expiry belong to an attachment.
 */
public record Curse(CurseType typedType, NumberProvider durationTicks, NumberProvider tickInterval, int maxStacks,
                    StackingMode stackingMode, EntityCondition applicationCondition, EntityAction onApply,
                    EntityAction onTick, EntityAction onRemove, List<Identifier> cleanseTags,
                    boolean allowForceRemove) {
    public static final Codec<Holder<Curse>> CODEC = RegistryFixedCodec.create(MxtResourceKeys.CURSE);
    public static final Codec<Curse> DIRECT_CODEC = RecordCodecBuilder.create(i -> i.group(
            CurseType.MAP_CODEC.forGetter(Curse::typedType),
            NumberProvider.CODEC.optionalFieldOf("duration_ticks", new Constant(0.0D)).forGetter(Curse::durationTicks),
            NumberProvider.CODEC.optionalFieldOf("tick_interval", new Constant(20.0D)).forGetter(Curse::tickInterval),
            Codec.intRange(1, 256).optionalFieldOf("max_stacks", 1).forGetter(Curse::maxStacks),
            StackingMode.CODEC.optionalFieldOf("stacking_mode", StackingMode.IGNORE).forGetter(Curse::stackingMode),
            EntityCondition.optionalCodec("application_condition").forGetter(Curse::applicationCondition),
            EntityAction.optionalCodec("on_apply").forGetter(Curse::onApply),
            EntityAction.optionalCodec("on_tick").forGetter(Curse::onTick),
            EntityAction.optionalCodec("on_remove").forGetter(Curse::onRemove),
            Identifier.CODEC.listOf().optionalFieldOf("cleanse_tags", List.of()).forGetter(Curse::cleanseTags),
            Codec.BOOL.optionalFieldOf("allow_force_remove", false).forGetter(Curse::allowForceRemove)
    ).apply(i, Curse::new));

    /**
     * Curse actions can apply or remove curses, including the defining curse itself.
     */
    @Override
    public @NonNull String toString() {
        return "Curse[type=" + this.typedType.id() + ", maxStacks=" + this.maxStacks
                + ", stackingMode=" + this.stackingMode + ", cleanseTags=" + this.cleanseTags + "]";
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
