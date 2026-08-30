package com.iafenvoy.mxt.util.formula.number;

import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.entity.player.Player;

import java.util.List;
import java.util.Optional;

/**
 * Selects a number from ordered condition/value branches. The scalar fallback
 * is used when no Player is available or no branch matches.
 */
public record Conditional(List<Branch> branches, Optional<NumberProvider> fallback) implements NumberProvider {
    private static final Codec<NumberProvider> SCALAR_CODEC = Codec.either(FINITE_DOUBLE_CODEC, Codec.STRING).flatXmap(
            value -> value.map(number -> DataResult.success(new Constant(number)), expression -> Expression.create(expression)
                    .<DataResult<NumberProvider>>map(DataResult::success)
                    .orElseGet(() -> DataResult.error(() -> "Invalid number expression: " + expression))),
            value -> {
                if (value instanceof Constant(double value1)) return DataResult.success(Either.left(value1));
                if (value instanceof Expression expression) return DataResult.success(Either.right(expression.source()));
                return DataResult.error(() -> "Conditional fallback must be a number or expression string");
            }
    );

    public static final MapCodec<Conditional> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Branch.MAP_CODEC.codec().listOf().optionalFieldOf("branches", List.of()).forGetter(Conditional::branches),
            SCALAR_CODEC.optionalFieldOf("fallback").forGetter(Conditional::fallback)
    ).apply(i, Conditional::new));

    @Override
    public double evaluate(FormulaContext context) {
        Player player = context.player();
        if (player != null) {
            for (Branch branch : this.branches) {
                if (branch.condition().test(player, context)) return branch.value().evaluate(context);
            }
        }
        return this.fallback.map(value -> value.evaluate(context)).orElse(0.0D);
    }

    @Override
    public MapCodec<Conditional> codec() {
        return MAP_CODEC;
    }

    public record Branch(EntityCondition condition, NumberProvider value) {
        public static final MapCodec<Branch> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                EntityCondition.CODEC.fieldOf("condition").forGetter(Branch::condition),
                NumberProvider.CODEC.fieldOf("value").forGetter(Branch::value)
        ).apply(i, Branch::new));
    }
}
