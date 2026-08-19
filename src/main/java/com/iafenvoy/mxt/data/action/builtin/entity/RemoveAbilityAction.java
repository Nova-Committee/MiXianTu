package com.iafenvoy.mxt.data.action.builtin.entity;

import com.iafenvoy.mxt.data.action.EntityAction;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.data.ability.Ability;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.Entity;

/**
 * Removes only the specified source, preserving grants from every other source.
 */
public record RemoveAbilityAction(Holder<Ability> ability, Identifier source) implements EntityAction {
    public static final MapCodec<RemoveAbilityAction> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Ability.CODEC.fieldOf("ability").forGetter(RemoveAbilityAction::ability),
            Identifier.CODEC.fieldOf("source").forGetter(RemoveAbilityAction::source)
    ).apply(i, RemoveAbilityAction::new));

    @Override
    public void execute(Entity entity, FormulaContext context) {
        entity.getData(MxtAttachments.ABILITY_HOLDER).revoke(this.ability, this.source);
    }

    @Override
    public MapCodec<RemoveAbilityAction> codec() {
        return CODEC;
    }
}
