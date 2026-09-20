package com.iafenvoy.mxt.data.action.builtin.bientity;

import com.iafenvoy.mxt.data.action.BiEntityAction;
import com.iafenvoy.mxt.data.context.action.BiEntityActionContext;
import com.iafenvoy.mxt.data.cultivation.Element;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.runtime.damage.DamageCalculationService;
import com.iafenvoy.mxt.runtime.damage.DamageElements;
import com.iafenvoy.mxt.util.codec.RegistryCodecs;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.Optional;

/**
 * Applies the actor's damage to the target. This is the shape a hit with an owner takes: the actor is credited
 * as the attacker, so a kill counts as theirs and the element edges its roots hold are read against the
 * target's, and the caster's mastery bonus travels in the formula context the ability built.
 *
 * <p>{@code element} and {@code damage_type} say what the strike is made of, and are how a technique of one
 * element is written by a cultivator of another: declaring an element replaces the reading off the attacker's
 * roots with that element, and declaring nothing keeps it. The damage type is the one the element claims
 * unless it is given outright, in which case the load checks that the element claims it - the reduction layer
 * only ever sees the damage source, so the element has to be readable from the type.</p>
 */
public record DamageTargetBiEntityAction(NumberProvider amount, Optional<Holder<DamageType>> damageType,
                                         List<Either<Holder<Element>, TagKey<Element>>> element) implements BiEntityAction {
    public static final MapCodec<DamageTargetBiEntityAction> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            NumberProvider.CODEC.fieldOf("amount").forGetter(DamageTargetBiEntityAction::amount),
            DamageType.CODEC.optionalFieldOf("damage_type").forGetter(DamageTargetBiEntityAction::damageType),
            RegistryCodecs.holderOrTagList(MxtResourceKeys.ELEMENT).optionalFieldOf("element", List.of()).forGetter(DamageTargetBiEntityAction::element)
    ).apply(i, DamageTargetBiEntityAction::new));

    @Override
    public void execute(@NonNull BiEntityActionContext ctx) {
        Entity target = ctx.target();
        FormulaContext context = ctx.formula();
        double amount = this.amount.evaluate(context);
        // Damage is a server decision; the deprecated {@code Entity#hurt} only ever applied on a server
        // anyway, so asking for the server level first is the same behaviour stated outright.
        if (!(target.level() instanceof ServerLevel)) return;
        if (!Double.isFinite(amount) || amount <= 0.0D) return;
        DamageCalculationService.deal(ctx.actor(), target, amount, this.resolvedType(target.level()), context);
    }

    /**
     * The damage type this action travels as, resolved from the declaration; see
     * {@link DamageElements#resolveType}. An empty result keeps the reading this action always had - the strike
     * is made of the actor's own roots.
     */
    private Optional<Holder<DamageType>> resolvedType(Level level) {
        return DamageElements.resolveType(level.registryAccess(), this.element, this.damageType);
    }

    @Override
    public @NonNull MapCodec<DamageTargetBiEntityAction> codec() {
        return CODEC;
    }
}
