package com.iafenvoy.mxt.data.action.builtin;

import com.iafenvoy.mxt.data.action.EntityAction;
import com.iafenvoy.mxt.data.ability.Ability;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.util.HolderHelper;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.Entity;

/**
 * Removes only the specified source, preserving grants from every other source.
 */
public record RemoveAbilityEntityAction(Holder<Ability> ability, Identifier source) implements EntityAction {
    public static final MapCodec<RemoveAbilityEntityAction> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Ability.CODEC.fieldOf("ability").forGetter(RemoveAbilityEntityAction::ability),
            Identifier.CODEC.fieldOf("source").forGetter(RemoveAbilityEntityAction::source)
    ).apply(instance, RemoveAbilityEntityAction::new));

    @Override
    public void execute(Entity entity) {
        entity.getData(MxtAttachments.ABILITY_HOLDER).revoke(HolderHelper.id(this.ability), this.source);
    }

    @Override
    public MapCodec<RemoveAbilityEntityAction> codec() {
        return CODEC;
    }
}
