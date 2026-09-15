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
 * The attack module: what a hostile array does to everything it covers, once per period.
 *
 * <p>This is the one module that is hostile by construction. A formation carrying it spares whoever its
 * owner counts as his own, exactly as if it had declared {@code hostile} — see
 * {@code FormationRelations} — because "this array exists to hurt people" is not something the runtime
 * should have to infer from a field next to it.</p>
 *
 * <p><b>Who gets the kill.</b> {@code attribute_to_owner} defaults to true, and it matters: damage with
 * no attacker credits nobody, so loot, mob aggro and every condition that reads the attacking entity
 * would see a formation kill as an act of weather. The field exists rather than being always-on because
 * an array can legitimately want the anonymous "environmental damage" reading, which is what
 * {@code mxt:damage} has always been.</p>
 *
 * <p>Effects reuse {@link ApplyEffectAction} rather than declaring a second shape for the same three
 * fields, so an entry copied between an {@code mxt:apply_effect} action and a module's {@code effects}
 * list means the same thing and is validated the same way.</p>
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
