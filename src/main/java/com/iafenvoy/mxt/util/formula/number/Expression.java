package com.iafenvoy.mxt.util.formula.number;

import com.iafenvoy.mxt.util.codec.CollectionCodecs;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.formula.FormulaDiagnostics;
import com.iafenvoy.mxt.util.formula.FormulaFunctions;
import com.iafenvoy.mxt.util.formula.FormulaVariables;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.objecthunter.exp4j.ExpressionBuilder;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public final class Expression implements NumberProvider {
    private static final MapCodec<Expression> RAW_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.STRING.fieldOf("expression").forGetter(Expression::source),
            Codec.lazyInitialized(() -> CollectionCodecs.map(Codec.STRING, CODEC))
                    .optionalFieldOf("params", Map.of()).forGetter(Expression::params)
    ).apply(i, Expression::new));

    /**
     * Decoding collects every problem of the expression and reports them as one error, which is
     * what lets the data pack loader list all broken formulas of a load at once.
     */
    public static final MapCodec<Expression> MAP_CODEC = RAW_CODEC.validate(Expression::validated);

    private final String source;
    private final Map<String, NumberProvider> params;
    private final Set<String> variables;
    private final String[] variableNames;
    private final NumberProvider[] variableOverrides;
    private final ThreadLocal<Compiled> compiled;
    private final List<String> problems;

    public Expression(@NotNull String source) {
        this(source, Map.of());
    }

    /**
     * Builds an expression and records every problem it has instead of failing on the first one.
     *
     * <p>The codec turns a non-empty {@link #problems()} list into a decode error, so a data pack
     * with several broken formulas reports all of them in one load failure, exactly like the other
     * registry errors.</p>
     */
    public Expression(@NotNull String source, @NotNull Map<String, NumberProvider> params) {
        this.source = source.trim();
        this.params = new LinkedHashMap<>(params);
        List<String> problems = new ArrayList<>();
        if (this.source.isEmpty()) problems.add("the expression is empty");
        for (String name : this.params.keySet())
            if (!FormulaVariables.isValidName(name)) problems.add("parameter name '" + name + "' is not a valid variable name");
        this.variables = new LinkedHashSet<>(FormulaVariables.find(this.source));
        for (String name : this.params.keySet())
            if (!this.variables.contains(name)) problems.add("parameter '" + name + "' is not used by the expression");
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
        problems.addAll(this.syntaxProblems());
        this.problems = List.copyOf(problems);
    }

    /**
     * Every problem found while building this expression; empty when the expression is usable.
     */
    public List<String> problems() {
        return this.problems;
    }

    /**
     * Decodes the shorthand string form, keeping every problem in the error message.
     */
    public static DataResult<Expression> decode(String source) {
        return validated(new Expression(source));
    }

    private static DataResult<Expression> validated(Expression expression) {
        if (expression.problems.isEmpty()) return DataResult.success(expression);
        return DataResult.error(() -> "Invalid number expression '" + expression.source + "': "
                + String.join("; ", expression.problems));
    }

    /**
     * Evaluates the expression once with placeholder values on the constructing thread.
     *
     * <p>exp4j accepts a structurally broken source when it builds — {@code 1 +}, {@code 1 +* 2}
     * and {@code (1 + 2} all build fine — and only refuses them when they are evaluated. Doing that
     * here keeps the documented contract: a malformed formula fails the data pack load, together
     * with every other malformed formula of the same load. Values only need to exist, so a formula
     * that divides by a variable stays valid.</p>
     */
    private List<String> syntaxProblems() {
        try {
            net.objecthunter.exp4j.Expression expression = this.compiled.get().expression();
            for (String variable : this.variableNames) expression.setVariable(variable, 1.0D);
            expression.evaluate();
            return List.of();
        } catch (RuntimeException exception) {
            return List.of(exception.getMessage() == null ? exception.toString() : exception.getMessage());
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
                if (override != null) {
                    state.expression().setVariable(variable, override.evaluate(context));
                    continue;
                }
                // Documented precedence: the expression's own params win, then an explicit value the
                // context carries, and only then the variable registry. A registered name such as
                // 'level' must not shadow an event payload that the caller wrote into the context.
                double explicit = context.explicit(variable);
                state.expression().setVariable(variable, Double.isNaN(explicit)
                        ? FormulaVariables.resolve(variable, context, state.bindings())
                        : explicit);
            }
            double result = state.expression().evaluate();
            return this.assertFinite(result) ? result : 0.0D;
        } catch (RuntimeException exception) {
            FormulaDiagnostics.report("Number provider expression '" + this.source + "' failed: "
                    + (exception.getMessage() == null ? exception.toString() : exception.getMessage()), exception);
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
