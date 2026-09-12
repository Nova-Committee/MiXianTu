package com.iafenvoy.mxt.util.formula.number;

import com.iafenvoy.mxt.util.codec.CollectionCodecs;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.formula.FormulaException;
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
    private final String[] variableNames;
    private final NumberProvider[] variableOverrides;
    private final ThreadLocal<Compiled> compiled;

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
        // The evaluation loop walks arrays instead of the set, and knows per name whether the
        // expression itself overrides it.
        this.variableNames = this.variables.toArray(String[]::new);
        this.variableOverrides = new NumberProvider[this.variableNames.length];
        for (int index = 0; index < this.variableNames.length; index++) {
            this.variableOverrides[index] = this.params.get(this.variableNames[index]);
        }
        this.compiled = ThreadLocal.withInitial(() -> new Compiled(new ExpressionBuilder(this.source)
                .functions(FormulaFunctions.all())
                .variables(this.variables)
                .build(), new HashMap<>()));
        this.validate();
    }

    /**
     * Evaluates the expression once with placeholder values on the constructing thread.
     *
     * <p>exp4j accepts a structurally broken source when it builds — {@code 1 +}, {@code 1 +* 2}
     * and {@code (1 + 2} all build fine — and only refuses them when they are evaluated. Doing that
     * here keeps the documented contract: a malformed formula fails the data pack load instead of
     * turning into a warning on the first evaluation. Values only need to exist, so a formula that
     * divides by a variable stays valid.</p>
     */
    private void validate() {
        try {
            net.objecthunter.exp4j.Expression expression = this.compiled.get().expression();
            for (String variable : this.variableNames) expression.setVariable(variable, 1.0D);
            expression.evaluate();
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Invalid number expression '" + this.source + "': " + exception.getMessage(), exception);
        }
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
            Compiled state = this.compiled.get();
            for (int index = 0; index < this.variableNames.length; index++) {
                String variable = this.variableNames[index];
                NumberProvider override = this.variableOverrides[index];
                state.expression().setVariable(variable, override == null
                        ? FormulaVariables.resolve(variable, context, state.bindings())
                        : override.evaluate(context));
            }
            double result = state.expression().evaluate();
            return this.assertFinite(result) ? result : 0.0D;
        } catch (FormulaException exception) {
            // A development environment reports a broken variable as a hard error instead of
            // turning it into a silent zero.
            throw exception;
        } catch (RuntimeException exception) {
            LOGGER.warn("Number provider Expression failed at runtime: {}; using 0", exception.getMessage() == null ? "unknown error" : exception.getMessage());
            return 0.0D;
        }
    }

    @Override
    public MapCodec<Expression> codec() {
        return MAP_CODEC;
    }

    /**
     * The compiled expression of one thread, together with the bindings it resolved.
     *
     * <p>A binding only depends on the variable registry, so a long lived formula — one evaluated
     * every tick, for example — resolves each name once and afterwards only re-reads the values
     * from the current context.</p>
     */
    private record Compiled(net.objecthunter.exp4j.Expression expression, Map<String, FormulaVariables.Binding> bindings) {
    }
}
