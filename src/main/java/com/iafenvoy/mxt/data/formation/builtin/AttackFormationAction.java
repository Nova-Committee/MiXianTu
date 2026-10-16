package com.iafenvoy.mxt.data.formation.builtin;

import com.iafenvoy.mxt.data.action.builtin.entity.ApplyEffectAction;
import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.data.context.action.EntityActionContext;
import com.iafenvoy.mxt.data.formation.FormationActionType;
import com.iafenvoy.mxt.data.formation.FormationIntervals;
import com.iafenvoy.mxt.runtime.damage.DamageCalculationService;
import com.iafenvoy.mxt.runtime.formation.FormationActionContext;
import com.iafenvoy.mxt.util.MiscUtil;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.iafenvoy.mxt.util.formula.number.Constant;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;

import java.util.List;
import java.util.Optional;

/**
 * The attack module: what an attacking array does to everything it covers, once per due period. Sparing the owner
 * and his friends is the formation's {@code spare_friends} switch, not this module's business - this only says what
 * the strike is. Damage with no attacker credits nobody, so {@code attribute_to_owner} defaults to true, and effects
 * reuse {@link ApplyEffectAction} rather than declaring a second shape for the same three fields.
 */
public record AttackFormationAction(NumberProvider damage, Optional<Holder<DamageType>> damageType,
                                    boolean attributeToOwner, List<ApplyEffectAction> effects,
                                    EntityCondition targetCondition,
                                    int intervalTicks) implements FormationActionType {
    public static final MapCodec<AttackFormationAction> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            NumberProvider.CODEC.optionalFieldOf("damage", new Constant(0.0D)).forGetter(AttackFormationAction::damage),
            DamageType.CODEC.optionalFieldOf("damage_type").forGetter(AttackFormationAction::damageType),
            Codec.BOOL.optionalFieldOf("attribute_to_owner", true).forGetter(AttackFormationAction::attributeToOwner),
            ApplyEffectAction.CODEC.codec().listOf().optionalFieldOf("effects", List.of()).forGetter(AttackFormationAction::effects),
            EntityCondition.optionalCodec("target_condition").forGetter(AttackFormationAction::targetCondition),
            FormationIntervals.CODEC.optionalFieldOf("interval_ticks", FormationIntervals.PERIOD).forGetter(AttackFormationAction::intervalTicks)
    ).apply(i, AttackFormationAction::new));

    @Override
    public MapCodec<AttackFormationAction> codec() {
        return CODEC;
    }

    // Damage with no attacker credits nobody, so the first listed owner is the source unless the module says
    // otherwise; a spare entity is not struck at all, which is why the target condition is asked first.
    @Override
    public void interval(FormationActionContext context) {
        Entity entity = context.entity();
        if (entity == null) return;
        EntityActionContext target = context.entityContext();
        if (!this.targetCondition.test(entity, target)) return;
        double amount = MiscUtil.positive(this.damage.evaluate(target.formula()));
        if (amount > 0.0D) {
            Entity owner = this.attributeToOwner ? context.carrier().owners().primaryEntity(context.level()) : null;
            DamageCalculationService.deal(owner, entity, amount, this.damageType, target.formula());
        }
        for (ApplyEffectAction effect : this.effects) effect.execute(target);
    }
}
