package com.iafenvoy.mxt.data.action;

import com.iafenvoy.mxt.data.action.builtin.bientity.meta.SequenceBiEntityAction;
import com.iafenvoy.mxt.data.context.Context;
import com.iafenvoy.mxt.data.context.action.BiEntityActionContext;
import com.iafenvoy.mxt.registry.MxtRegistries;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.NotNull;

import java.util.function.Function;

public interface BiEntityAction {
    Codec<BiEntityAction> SINGLE_CODEC = MxtRegistries.BI_ENTITY_ACTION_TYPE.byNameCodec().dispatch("type", BiEntityAction::codec, Function.identity());
    Codec<BiEntityAction> CODEC = Codec.either(SINGLE_CODEC.listOf(), SINGLE_CODEC).xmap(value -> value.map(SequenceBiEntityAction::new, Function.identity()), Either::right);

    static MapCodec<BiEntityAction> optionalCodec(String name) {
        return CODEC.optionalFieldOf(name, NoOpAction.INSTANCE);
    }

    @NotNull MapCodec<? extends BiEntityAction> codec();

    void execute(@NotNull BiEntityActionContext context);

    default void execute(Entity actor, Entity target, Context parent) {
        this.execute(parent.copyTo(new BiEntityActionContext(actor, target, parent.formula())));
    }

    default void execute(Entity actor, Entity target, FormulaContext formula) {
        this.execute(new BiEntityActionContext(actor, target, formula));
    }
}
