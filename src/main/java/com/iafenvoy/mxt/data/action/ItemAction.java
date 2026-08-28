package com.iafenvoy.mxt.data.action;

import com.iafenvoy.mxt.data.action.builtin.item.meta.SequenceItemAction;
import com.iafenvoy.mxt.data.context.action.ItemActionContext;
import com.iafenvoy.mxt.data.context.Context;
import com.iafenvoy.mxt.registry.MxtRegistries;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import com.iafenvoy.mxt.util.formula.FormulaContext;

import java.util.List;
import java.util.function.Function;

/**
 * Code-owned action against one ItemStack, always initiated by the server-side holder.
 */
public interface ItemAction {
    Codec<ItemAction> SINGLE_CODEC = MxtRegistries.ITEM_ACTION_TYPE.byNameCodec().dispatch("type", ItemAction::codec, Function.identity());
    Codec<ItemAction> CODEC = Codec.either(SINGLE_CODEC, SINGLE_CODEC.listOf()).xmap(
            value -> value.map(action -> action, SequenceItemAction::new),
            action -> action instanceof SequenceItemAction(
                    List<ItemAction> actions
            ) ? Either.right(actions) : Either.left(action)
    );

    void execute(ItemActionContext context);

    default void execute(Entity holder, ItemStack stack, Context parent) {
        this.execute(parent.copyTo(new ItemActionContext(holder, stack, parent.formula())));
    }

    default void execute(Entity holder, ItemStack stack, FormulaContext formula) {
        this.execute(new ItemActionContext(holder, stack, formula));
    }

    MapCodec<? extends ItemAction> codec();
}
