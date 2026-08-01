package com.iafenvoy.mxt.data.action.builtin;

import com.iafenvoy.mxt.data.action.EntityAction;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;

/**
 * Grants an ability using an explicit persistent source identity.
 */
public record GrantAbilityEntityAction(Identifier ability, Identifier source) implements EntityAction {
    public static final MapCodec<GrantAbilityEntityAction> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Identifier.CODEC.fieldOf("ability").forGetter(GrantAbilityEntityAction::ability),
            Identifier.CODEC.fieldOf("source").forGetter(GrantAbilityEntityAction::source)
    ).apply(instance, GrantAbilityEntityAction::new));

    @Override
    public void execute(Entity entity) {
        entity.getData(MxtAttachments.ABILITY_HOLDER).grant(this.ability, this.source);
    }

    @Override
    public MapCodec<GrantAbilityEntityAction> codec() {
        return CODEC;
    }
}
