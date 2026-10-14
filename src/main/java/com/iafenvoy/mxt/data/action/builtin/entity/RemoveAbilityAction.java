package com.iafenvoy.mxt.data.action.builtin.entity;

import com.iafenvoy.mxt.data.ability.Ability;
import com.iafenvoy.mxt.data.action.EntityAction;
import com.iafenvoy.mxt.data.context.action.EntityActionContext;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.runtime.ability.AbilityEventBridge;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.NonNull;

/**
 * Removes only the specified source, preserving grants from every other source.
 */
public record RemoveAbilityAction(Holder<Ability> ability, Identifier source) implements EntityAction {
    public static final MapCodec<RemoveAbilityAction> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Ability.CODEC.fieldOf("ability").forGetter(RemoveAbilityAction::ability),
            Identifier.CODEC.fieldOf("source").forGetter(RemoveAbilityAction::source)
    ).apply(i, RemoveAbilityAction::new));

    @Override
    public void execute(@NonNull EntityActionContext ctx) {
        if (ctx.entity().getData(MxtAttachments.ABILITY_HOLDER).revoke(this.ability, this.source)
                && ctx.entity() instanceof LivingEntity living)
            AbilityEventBridge.rebuildTriggerSubscriptions(living);
    }

    @Override
    public @NonNull MapCodec<RemoveAbilityAction> codec() {
        return CODEC;
    }
}
