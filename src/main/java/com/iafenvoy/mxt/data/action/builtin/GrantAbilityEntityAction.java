package com.iafenvoy.mxt.data.action.builtin;

import com.iafenvoy.mxt.data.action.EntityAction;
import com.iafenvoy.mxt.data.ability.AbilityDefinition;
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
public record GrantAbilityEntityAction(Holder<AbilityDefinition> ability, Identifier source) implements EntityAction {
    public static final MapCodec<GrantAbilityEntityAction> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            AbilityDefinition.HOLDER_CODEC.fieldOf("ability").forGetter(GrantAbilityEntityAction::ability),
            Identifier.CODEC.fieldOf("source").forGetter(GrantAbilityEntityAction::source)
    ).apply(instance, GrantAbilityEntityAction::new));

    @Override
    public void execute(Entity entity) {
        entity.getData(MxtAttachments.ABILITY_HOLDER).grant(HolderHelper.id(this.ability), this.source);
    }

    @Override
    public MapCodec<GrantAbilityEntityAction> codec() {
        return CODEC;
    }
}
