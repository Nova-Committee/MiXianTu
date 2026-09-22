package com.iafenvoy.mxt.data.action.builtin.entity;

import com.iafenvoy.mxt.data.action.EntityAction;
import com.iafenvoy.mxt.data.context.action.EntityActionContext;
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
 * Damages the acting entity; with no {@code damage_type} and no {@code element} nobody is credited, so it reads
 * no element relation and no attacker physique - recoil, backlash and hazard ticks.
 */
public record DamageAction(NumberProvider amount, Optional<Holder<DamageType>> damageType,
                           List<Either<Holder<Element>, TagKey<Element>>> element) implements EntityAction {
    public static final MapCodec<DamageAction> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            NumberProvider.CODEC.fieldOf("amount").forGetter(DamageAction::amount),
            DamageType.CODEC.optionalFieldOf("damage_type").forGetter(DamageAction::damageType),
            RegistryCodecs.holderOrTagList(MxtResourceKeys.ELEMENT).optionalFieldOf("element", List.of()).forGetter(DamageAction::element)
    ).apply(i, DamageAction::new));

    @Override
    public void execute(@NonNull EntityActionContext ctx) {
        Entity entity = ctx.entity();
        FormulaContext context = ctx.formula();
        double amount = this.amount.evaluate(context);
        // Server only: an action running on a client level must not pretend it dealt damage.
        if (!(entity.level() instanceof ServerLevel)) return;
        if (!Double.isFinite(amount) || amount <= 0.0D) return;
        DamageCalculationService.deal(attacker(entity, context), entity, amount, this.resolvedType(entity.level()), context);
    }

    // Empty means the strike is made of whatever the attacker's roots are (see DamageElements.resolveType).
    private Optional<Holder<DamageType>> resolvedType(Level level) {
        return DamageElements.resolveType(level.registryAccess(), this.element, this.damageType);
    }

    // A bearer that damages itself is taking a price, not being hit by anybody: credit nobody.
    private static Entity attacker(Entity entity, FormulaContext context) {
        Entity caster = context.caster();
        return caster == entity ? null : caster;
    }

    @Override
    public @NonNull MapCodec<DamageAction> codec() {
        return CODEC;
    }
}
