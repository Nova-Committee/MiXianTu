package com.iafenvoy.mxt.data.formation;

import com.iafenvoy.mxt.data.action.builtin.entity.ApplyEffectAction;
import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.iafenvoy.mxt.util.formula.number.Constant;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.world.damagesource.DamageType;

import java.util.List;
import java.util.Optional;

/**
 * The attack module: what an attacking array does to everything it covers, once per period. It hits everyone
 * the array affects, so sparing the owner and his friends is the formation's {@code spare_friends} switch,
 * not this module's business — this module only says what the strike is. Damage with no
 * attacker credits nobody, so {@code attribute_to_owner} defaults to true, and effects reuse
 * {@link ApplyEffectAction} rather than declaring a second shape for the same three fields.
 */
public record AttackFormationAction(NumberProvider damage, Optional<Holder<DamageType>> damageType,
                                    boolean attributeToOwner, List<ApplyEffectAction> effects,
                                    EntityCondition targetCondition) implements FormationActionType {
    public static final MapCodec<AttackFormationAction> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            NumberProvider.CODEC.optionalFieldOf("damage", new Constant(0.0D)).forGetter(AttackFormationAction::damage),
            DamageType.CODEC.optionalFieldOf("damage_type").forGetter(AttackFormationAction::damageType),
            Codec.BOOL.optionalFieldOf("attribute_to_owner", true).forGetter(AttackFormationAction::attributeToOwner),
            ApplyEffectAction.CODEC.codec().listOf().optionalFieldOf("effects", List.of()).forGetter(AttackFormationAction::effects),
            EntityCondition.optionalCodec("target_condition").forGetter(AttackFormationAction::targetCondition)
    ).apply(i, AttackFormationAction::new));

    @Override
    public MapCodec<AttackFormationAction> codec() {
        return CODEC;
    }
}
