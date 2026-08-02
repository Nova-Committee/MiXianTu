package com.iafenvoy.mxt.data.curse;

import com.iafenvoy.mxt.data.action.EntityAction;
import com.iafenvoy.mxt.data.action.builtin.NoOpEntityAction;
import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.data.condition.builtin.AlwaysTrueEntityCondition;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.iafenvoy.mxt.util.formula.NumberProvider.Constant;
import com.iafenvoy.mxt.registry.MxtRegistryKeys;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.core.Holder;
import net.minecraft.resources.RegistryFixedCodec;

import java.util.List;
import java.util.Locale;

/**
 * Static definition for a named curse. Dynamic layers and expiry belong to an attachment.
 */
public record Curse(CurseType typedType, NumberProvider durationTicks, NumberProvider tickInterval, int maxStacks,
                    StackingMode stackingMode, EntityCondition applicationCondition, EntityAction onApply,
                    EntityAction onTick, EntityAction onRemove, List<Identifier> cleanseTags,
                    boolean allowForceRemove) {
    public static final Codec<Curse> DIRECT_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            CurseType.MAP_CODEC.forGetter(Curse::typedType),
            NumberProvider.CODEC.optionalFieldOf("duration_ticks", new Constant(0.0D)).forGetter(Curse::durationTicks),
            NumberProvider.CODEC.optionalFieldOf("tick_interval", new Constant(20.0D)).forGetter(Curse::tickInterval),
            Codec.intRange(1, 256).optionalFieldOf("max_stacks", 1).forGetter(Curse::maxStacks),
            StackingMode.CODEC.optionalFieldOf("stacking_mode", StackingMode.IGNORE).forGetter(Curse::stackingMode),
            EntityCondition.CODEC.optionalFieldOf("application_condition", AlwaysTrueEntityCondition.INSTANCE).forGetter(Curse::applicationCondition),
            EntityAction.CODEC.optionalFieldOf("on_apply", NoOpEntityAction.INSTANCE).forGetter(Curse::onApply),
            EntityAction.CODEC.optionalFieldOf("on_tick", NoOpEntityAction.INSTANCE).forGetter(Curse::onTick),
            EntityAction.CODEC.optionalFieldOf("on_remove", NoOpEntityAction.INSTANCE).forGetter(Curse::onRemove),
            Identifier.CODEC.listOf().optionalFieldOf("cleanse_tags", List.of()).forGetter(Curse::cleanseTags),
            Codec.BOOL.optionalFieldOf("allow_force_remove", false).forGetter(Curse::allowForceRemove)
    ).apply(instance, Curse::new));
    public static final Codec<Holder<Curse>> CODEC = RegistryFixedCodec.create(MxtRegistryKeys.CURSE);

    public Identifier type() {
        return this.typedType.id();
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
