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
public record CurseDefinition(CurseType typedType, NumberProvider durationTicks, NumberProvider tickInterval,
                              int maxStacks,
                              StackingMode stackingMode, EntityCondition applicationCondition,
                              EntityAction onApply, EntityAction onTick, EntityAction onRemove,
                              List<Identifier> cleanseTags, boolean allowForceRemove) {
    public static final Codec<Holder<CurseDefinition>> HOLDER_CODEC = RegistryFixedCodec.create(MxtRegistryKeys.CURSE);

    public Identifier type() {
        return this.typedType.id();
    }

    public static final Codec<CurseDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            CurseType.MAP_CODEC.forGetter(CurseDefinition::typedType),
            NumberProvider.CODEC.optionalFieldOf("duration_ticks", new Constant(0.0D)).forGetter(CurseDefinition::durationTicks),
            NumberProvider.CODEC.optionalFieldOf("tick_interval", new Constant(20.0D)).forGetter(CurseDefinition::tickInterval),
            Codec.intRange(1, 256).optionalFieldOf("max_stacks", 1).forGetter(CurseDefinition::maxStacks),
            StackingMode.CODEC.optionalFieldOf("stacking_mode", StackingMode.IGNORE).forGetter(CurseDefinition::stackingMode),
            EntityCondition.CODEC.optionalFieldOf("application_condition", AlwaysTrueEntityCondition.INSTANCE).forGetter(CurseDefinition::applicationCondition),
            EntityAction.CODEC.optionalFieldOf("on_apply", NoOpEntityAction.INSTANCE).forGetter(CurseDefinition::onApply),
            EntityAction.CODEC.optionalFieldOf("on_tick", NoOpEntityAction.INSTANCE).forGetter(CurseDefinition::onTick),
            EntityAction.CODEC.optionalFieldOf("on_remove", NoOpEntityAction.INSTANCE).forGetter(CurseDefinition::onRemove),
            Identifier.CODEC.listOf().optionalFieldOf("cleanse_tags", List.of()).forGetter(CurseDefinition::cleanseTags),
            Codec.BOOL.optionalFieldOf("allow_force_remove", false).forGetter(CurseDefinition::allowForceRemove)
    ).apply(instance, CurseDefinition::new));

    public enum StackingMode {
        IGNORE,
        REFRESH_DURATION,
        ADD_STACKS_REFRESH_DURATION,
        ADD_STACKS_KEEP_DURATION,
        REPLACE;

        public static final Codec<StackingMode> CODEC = Codec.STRING.xmap(value -> valueOf(value.toUpperCase(Locale.ROOT)), value -> value.name().toLowerCase(Locale.ROOT));
    }
}
