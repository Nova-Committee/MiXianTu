package com.iafenvoy.mxt.data.action.builtin;

import com.iafenvoy.mxt.attachment.AbilityHolderData;
import com.iafenvoy.mxt.data.ability.AbilityComponentState;
import com.iafenvoy.mxt.data.ability.AbilityDefinition;
import com.iafenvoy.mxt.data.action.EntityAction;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.util.HolderHelper;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;

/**
 * Updates a declared ability-component state without allowing arbitrary NBT/component writes.
 */
public record ModifyComponentEntityAction(Holder<AbilityDefinition> ability, String component,
                                          NumberProvider value) implements EntityAction {
    public static final MapCodec<ModifyComponentEntityAction> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            AbilityDefinition.HOLDER_CODEC.fieldOf("ability").forGetter(ModifyComponentEntityAction::ability),
            Codec.STRING.fieldOf("component").forGetter(ModifyComponentEntityAction::component),
            NumberProvider.CODEC.fieldOf("value").forGetter(ModifyComponentEntityAction::value)
    ).apply(instance, ModifyComponentEntityAction::new));

    public ModifyComponentEntityAction {
        if (component.isBlank()) throw new IllegalArgumentException("component must not be blank");
    }

    @Override
    public void execute(Entity entity) {
        this.execute(entity, FormulaContext.EMPTY);
    }

    @Override
    public void execute(Entity entity, FormulaContext context) {
        double value = this.value.evaluate(context);
        if (!Double.isFinite(value)) return;
        AbilityHolderData holder = entity.getData(MxtAttachments.ABILITY_HOLDER);
        Identifier id = HolderHelper.id(this.ability);
        if (holder.has(id))
            holder.setComponentState(id, this.component, AbilityComponentState.initial(value, entity.level().getGameTime()));
    }

    @Override
    public MapCodec<ModifyComponentEntityAction> codec() {
        return CODEC;
    }
}
