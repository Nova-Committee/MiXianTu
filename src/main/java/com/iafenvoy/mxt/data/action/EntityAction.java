package com.iafenvoy.mxt.data.action;

import com.iafenvoy.mxt.data.action.builtin.entity.meta.SequenceAction;
import com.iafenvoy.mxt.registry.MxtRegistries;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.entity.Entity;

import java.util.List;
import java.util.function.Function;

/**
 * Java-owned entity action selected by a datapack {@code type} object.
 */
public interface EntityAction {
    Codec<EntityAction> SINGLE_CODEC = MxtRegistries.ENTITY_ACTION_TYPE.byNameCodec().dispatch("type", EntityAction::codec, Function.identity());
    Codec<EntityAction> CODEC = Codec.either(SINGLE_CODEC, SINGLE_CODEC.listOf()).xmap(
            value -> value.map(action -> action, SequenceAction::new),
            action -> action instanceof SequenceAction(
                    List<EntityAction> actions
            ) ? Either.right(actions) : Either.left(action)
    );

    void execute(Entity entity, FormulaContext context);

    MapCodec<? extends EntityAction> codec();
}
