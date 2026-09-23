package com.iafenvoy.mxt.data.cost.builtin;

import com.iafenvoy.mxt.data.cost.Charge;
import com.iafenvoy.mxt.data.cost.Cost;
import com.iafenvoy.mxt.data.cost.context.CostChannel;
import com.iafenvoy.mxt.data.cost.context.CostContext;
import com.iafenvoy.mxt.data.cost.context.CostFailure;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.iafenvoy.mxt.util.matcher.ItemMatcher;
import com.iafenvoy.mxt.util.matcher.ItemMatcher.Entry;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.entity.LivingEntity;

import java.util.List;

/**
 * Takes matching items out of the payer's inventory.
 */
public record ItemCost(ItemMatcher matcher, NumberProvider amount) implements Cost {
    public static final MapCodec<ItemCost> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            ItemMatcher.ENTRIES_CODEC.fieldOf("items").xmap(ItemCost::newMatcher, ItemCost::entries).forGetter(ItemCost::matcher),
            NumberProvider.CODEC.fieldOf("amount").forGetter(ItemCost::amount)
    ).apply(i, ItemCost::new));

    private static ItemMatcher newMatcher(List<Entry> entries) {
        return () -> entries;
    }

    private static List<Entry> entries(ItemMatcher matcher) {
        return matcher.entries();
    }

    // A non-positive or non-finite amount means the cost cannot be paid at all.
    public int required(LivingEntity payer) {
        double value = this.amount.evaluate(FormulaContext.of(payer));
        return Double.isFinite(value) && value > 0.0D ? (int) Math.ceil(value) : 0;
    }

    @Override
    public Either<Charge, CostFailure> charge(CostContext context) {
        if (!context.hasChannel(CostChannel.PLAYER_INVENTORY)) return Either.right(CostFailure.NO_CHANNEL);
        int required = this.required(context.payer());
        if (required <= 0) return Either.right(CostFailure.INVALID_AMOUNT);
        return Either.left(new Charge.Items(this.matcher, required));
    }

    @Override
    public MapCodec<ItemCost> codec() {
        return CODEC;
    }
}
