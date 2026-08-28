package com.iafenvoy.mxt.data.action;

import com.iafenvoy.mxt.data.action.builtin.bientity.meta.SequenceBiEntityAction;
import com.iafenvoy.mxt.data.context.action.BiEntityActionContext;
import com.iafenvoy.mxt.data.context.Context;
import com.iafenvoy.mxt.registry.MxtRegistries;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.entity.Entity;
import com.iafenvoy.mxt.util.formula.FormulaContext;

import java.util.List;
import java.util.function.Function;

/**
 * Action with a source entity and a selected target entity.
 */
public interface BiEntityAction {
    Codec<BiEntityAction> SINGLE_CODEC = MxtRegistries.BI_ENTITY_ACTION_TYPE.byNameCodec().dispatch("type", BiEntityAction::codec, Function.identity());
    Codec<BiEntityAction> CODEC = Codec.either(SINGLE_CODEC, SINGLE_CODEC.listOf()).xmap(
            value -> value.map(action -> action, SequenceBiEntityAction::new),
            action -> action instanceof SequenceBiEntityAction(
                    List<BiEntityAction> actions
            ) ? Either.right(actions) : Either.left(action)
    );

    void execute(BiEntityActionContext context);

    default void execute(Entity actor, Entity target, Context parent) {
        this.execute(parent.copyTo(new BiEntityActionContext(actor, target, parent.formula())));
    }

    default void execute(Entity actor, Entity target, FormulaContext formula) {
        this.execute(new BiEntityActionContext(actor, target, formula));
    }

    MapCodec<? extends BiEntityAction> codec();
}
