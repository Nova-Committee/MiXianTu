package com.iafenvoy.mxt.data.action.builtin.entity;

import com.iafenvoy.mxt.data.context.action.EntityActionContext;

import com.iafenvoy.mxt.attachment.AbilityAttachment;
import com.iafenvoy.mxt.data.ability.AbilityComponentState;
import com.iafenvoy.mxt.data.ability.Ability;
import com.iafenvoy.mxt.data.action.EntityAction;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.NonNull;

/**
 * Updates a declared ability-component state without allowing arbitrary NBT/component writes.
 */
public record ModifyComponentAction(Holder<Ability> ability, String component,
                                    NumberProvider value) implements EntityAction {
    public static final MapCodec<ModifyComponentAction> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Ability.CODEC.fieldOf("ability").forGetter(ModifyComponentAction::ability),
            Codec.STRING.fieldOf("component").forGetter(ModifyComponentAction::component),
            NumberProvider.CODEC.fieldOf("value").forGetter(ModifyComponentAction::value)
    ).apply(i, ModifyComponentAction::new));

    public ModifyComponentAction {
        if (component.isBlank()) throw new IllegalArgumentException("component must not be blank");
    }

    @Override
    public void execute(@NonNull EntityActionContext ctx) {
        Entity entity = ctx.entity();
        FormulaContext context = ctx.formula();
        double value = this.value.evaluate(context);
        if (!Double.isFinite(value)) return;
        AbilityAttachment holder = entity.getData(MxtAttachments.ABILITY_HOLDER);
        if (holder.has(this.ability))
            holder.setComponentState(this.ability, this.component, AbilityComponentState.initial(value, entity.level().getGameTime()));
    }

    @Override
    public @NonNull MapCodec<ModifyComponentAction> codec() {
        return CODEC;
    }
}
