package com.iafenvoy.mxt.data.action.builtin.entity;

import com.iafenvoy.mxt.data.action.EntityAction;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.data.ability.Ability;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.util.HolderHelper;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.Entity;

/**
 * Grants an ability using an explicit persistent source identity.
 */
public record GrantAbilityAction(Holder<Ability> ability, Identifier source) implements EntityAction {
    public static final MapCodec<GrantAbilityAction> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Ability.CODEC.fieldOf("ability").forGetter(GrantAbilityAction::ability),
            Identifier.CODEC.fieldOf("source").forGetter(GrantAbilityAction::source)
    ).apply(instance, GrantAbilityAction::new));

    @Override
    public void execute(Entity entity, FormulaContext context) {
        entity.getData(MxtAttachments.ABILITY_HOLDER).grant(HolderHelper.id(this.ability), this.source);
    }

    @Override
    public MapCodec<GrantAbilityAction> codec() {
        return CODEC;
    }
}
