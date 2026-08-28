package com.iafenvoy.mxt.data.action;

import com.iafenvoy.mxt.data.action.builtin.entity.meta.SequenceAction;
import com.iafenvoy.mxt.data.context.action.EntityActionContext;
import com.iafenvoy.mxt.data.context.Context;
import com.iafenvoy.mxt.registry.MxtRegistries;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.entity.Entity;
import com.iafenvoy.mxt.util.formula.FormulaContext;

import java.util.function.Function;

import org.jetbrains.annotations.NotNull;

public interface EntityAction {
    Codec<EntityAction> SINGLE_CODEC = MxtRegistries.ENTITY_ACTION_TYPE.byNameCodec().dispatch("type", EntityAction::codec, Function.identity());
    Codec<EntityAction> CODEC = Codec.either(SINGLE_CODEC.listOf(), SINGLE_CODEC).xmap(value -> value.map(SequenceAction::new, Function.identity()), Either::right);

    static MapCodec<EntityAction> optionalCodec(String name) {
        return CODEC.optionalFieldOf(name, NoOpAction.INSTANCE);
    }

    @NotNull MapCodec<? extends EntityAction> codec();

    void execute(@NotNull EntityActionContext context);

    default void execute(Entity entity, Context parent) {
        this.execute(parent.copyTo(new EntityActionContext(entity, parent.formula())));
    }

    default void execute(Entity entity, FormulaContext formula) {
        this.execute(new EntityActionContext(entity, formula));
    }
}
