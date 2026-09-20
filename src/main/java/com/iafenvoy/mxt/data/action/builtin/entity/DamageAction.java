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
 * Applies generic damage to the acting entity, which is the "environment" reading of a hit: with no damage
 * type and no element it is credited to nobody, so it takes no element edge and no mastery bonus, and it stays
 * the right shape for recoil, backlash and hazard ticks.
 *
 * <p>An action can still land on somebody other than the caster - nested under a target action, a passenger
 * action or an equipped-item action the acting entity is not the caster - and there the caster is credited,
 * because a hit somebody caused is theirs.</p>
 *
 * <p>{@code damage_type} and {@code element} are opt-in and say what the strike is made of. With
 * {@code element} alone the damage type is the one that element claims (an element owns the meaning of a
 * damage type, so declaring the element is enough to make the strike readable as it everywhere else); with
 * both, the load checks that the element really claims that type. Neither is a second way to express the
 * other: a strike that needs a type of its own is still written the same way, and one that needs a deliberate
 * attacker still uses the bi-entity path.</p>
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
        // Damage is a server decision: {@code Entity#hurt} still routes to the server and is deprecated,
        // and an action running on a client level must not pretend it dealt damage.
        if (!(entity.level() instanceof ServerLevel)) return;
        if (!Double.isFinite(amount) || amount <= 0.0D) return;
        DamageCalculationService.deal(attacker(entity, context), entity, amount, this.resolvedType(entity.level()), context);
    }

    /**
     * The damage type this action travels as, resolved from the declaration; see
     * {@link DamageElements#resolveType}. An empty result is the reading this action always had - the strike is
     * made of whatever the attacker's roots are.
     */
    private Optional<Holder<DamageType>> resolvedType(Level level) {
        return DamageElements.resolveType(level.registryAccess(), this.element, this.damageType);
    }

    /**
     * Who to credit, which is the caster only when the damage is not the caster's own: a bearer that damages
     * itself is taking a price, not being hit by anybody.
     */
    private static Entity attacker(Entity entity, FormulaContext context) {
        Entity caster = context.caster();
        return caster == entity ? null : caster;
    }

    @Override
    public @NonNull MapCodec<DamageAction> codec() {
        return CODEC;
    }
}
