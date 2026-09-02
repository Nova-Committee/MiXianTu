package com.iafenvoy.mxt.util.formula.number;

import com.iafenvoy.mxt.util.codec.CollectionCodecs;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.formula.FormulaFunctions;
import com.iafenvoy.mxt.util.formula.FormulaVariables;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.objecthunter.exp4j.ExpressionBuilder;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public final class Expression implements NumberProvider {
    public static final MapCodec<Expression> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.STRING.fieldOf("expression").forGetter(Expression::source),
            Codec.lazyInitialized(() -> CollectionCodecs.map(Codec.STRING, CODEC))
                    .optionalFieldOf("params", Map.of()).forGetter(Expression::params)
    ).apply(i, Expression::new));

    private final String source;
    private final Map<String, NumberProvider> params;
    private final Set<String> variables;
    private final ThreadLocal<net.objecthunter.exp4j.Expression> compiled;

    public Expression(@NotNull String source) {
        this(source, Map.of());
    }

    public Expression(@NotNull String source, @NotNull Map<String, NumberProvider> params) {
        this.source = source.trim();
        if (this.source.isEmpty()) throw new IllegalArgumentException("Expression must not be empty");
        if (params.keySet().stream().anyMatch(name -> !FormulaVariables.isValidName(name))) {
            throw new IllegalArgumentException("Expression parameter names must be valid variable names");
        }
        this.params = new LinkedHashMap<>(params);
        this.variables = new LinkedHashSet<>(FormulaVariables.find(this.source));
        if (!this.variables.containsAll(params.keySet())) {
            throw new IllegalArgumentException("Expression parameters must reference variables used by the expression");
        }
        this.compiled = ThreadLocal.withInitial(() -> new ExpressionBuilder(this.source)
                .functions(FormulaFunctions.all())
                .variables(this.variables)
                .build());
    }

    public static Optional<Expression> create(String source) {
        try {
            return Optional.of(new Expression(source));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    public String source() {
        return this.source;
    }

    public Map<String, NumberProvider> params() {
        return this.params;
    }

    @Override
    public double evaluate(FormulaContext context) {
        try {
            net.objecthunter.exp4j.Expression expression = this.compiled.get();
            for (String variable : this.variables) {
                NumberProvider override = this.params.get(variable);
                expression.setVariable(variable, override == null ? context.value(variable) : override.evaluate(context));
            }
            double result = expression.evaluate();
            return this.assertFinite(result) ? result : 0.0D;
        } catch (RuntimeException exception) {
            LOGGER.warn("Number provider Expression failed at runtime: {}; using 0", exception.getMessage() == null ? "unknown error" : exception.getMessage());
            return 0.0D;
        }
    }

    @Override
    public MapCodec<Expression> codec() {
        return MAP_CODEC;
    }
}
