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
 * Applies the actor's damage to the target, crediting the actor as the attacker. Declaring an {@code element}
 * replaces the reading off the attacker's roots; {@code damage_type} defaults to the one the element claims.
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
        // Server only: the deprecated Entity#hurt only ever applied on a server anyway.
        if (!(target.level() instanceof ServerLevel)) return;
        if (!Double.isFinite(amount) || amount <= 0.0D) return;
        DamageCalculationService.deal(ctx.actor(), target, amount, this.resolvedType(target.level()), context);
    }

    // Empty keeps the reading this action always had: the strike is made of the actor's own roots.
    private Optional<Holder<DamageType>> resolvedType(Level level) {
        return DamageElements.resolveType(level.registryAccess(), this.element, this.damageType);
    }

    @Override
    public @NonNull MapCodec<DamageTargetBiEntityAction> codec() {
        return CODEC;
    }
}
