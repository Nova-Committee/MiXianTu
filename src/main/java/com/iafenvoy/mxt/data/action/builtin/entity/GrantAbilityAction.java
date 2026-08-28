package com.iafenvoy.mxt.data.action.builtin.entity;

import com.iafenvoy.mxt.data.context.action.EntityActionContext;

import com.iafenvoy.mxt.data.action.EntityAction;
import com.iafenvoy.mxt.data.ability.Ability;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.NonNull;

/**
 * Grants an ability using an explicit persistent source identity.
 */
public record GrantAbilityAction(Holder<Ability> ability, Identifier source) implements EntityAction {
    public static final MapCodec<GrantAbilityAction> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Ability.CODEC.fieldOf("ability").forGetter(GrantAbilityAction::ability),
            Identifier.CODEC.fieldOf("source").forGetter(GrantAbilityAction::source)
    ).apply(i, GrantAbilityAction::new));

    @Override
    public void execute(@NonNull EntityActionContext ctx) {
        Entity entity = ctx.entity();
        entity.getData(MxtAttachments.ABILITY_HOLDER).grant(this.ability, this.source);
    }

    @Override
    public @NonNull MapCodec<GrantAbilityAction> codec() {
        return CODEC;
    }
}
