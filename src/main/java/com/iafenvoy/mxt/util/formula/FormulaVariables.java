package com.iafenvoy.mxt.util.formula;

import com.iafenvoy.mxt.registry.MxtRegistries;
import com.iafenvoy.mxt.util.formula.FormulaVariables.Binding.Candidate;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolver for the intrinsic {@code mxt:formula_variable} registry.
 *
 * <p>A requested name is split into the variable that claims it and the part of the name that
 * variable receives. That split is context independent, so a caller may keep the resulting
 * {@link Binding} and reuse it: {@code Expression} does exactly that, once per name per formula,
 * which is what stops a long lived formula from repeating the lookup on every evaluation.</p>
 *
 * <p>An unknown name is a content bug: a development environment logs the whole error and
 * production logs one warning line, and both keep evaluating with {@code 0}.</p>
 */
public final class FormulaVariables {
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
    private static final Set<String> FUNCTIONS = Set.of("abs", "acos", "asin", "atan", "cbrt", "ceil", "cos", "cosh", "exp", "floor", "log", "log10", "sin", "sinh", "sqrt", "tan", "tanh");
    private static final Set<String> CONSTANTS = Set.of("pi", "e");

    private static volatile Lookup lookup;

    private FormulaVariables() {
    }

    public static boolean isValidName(String name) {
        return IDENTIFIER.matcher(name).matches();
    }

    /**
     * Every identifier an expression reads, minus the functions and constants the formula
     * language already knows.
     */
    public static Set<String> find(String expression) {
        Set<String> functions = FormulaFunctions.names();
        LinkedHashSet<String> result = new LinkedHashSet<>();
        Matcher matcher = IDENTIFIER.matcher(expression);
        while (matcher.find()) {
            String token = matcher.group();
            if (!FUNCTIONS.contains(token) && !functions.contains(token) && !CONSTANTS.contains(token)) {
                result.add(token);
            }
        }
        return result;
    }

    public static boolean contains(String name) {
        return bind(name) != null;
    }

    public static double resolve(String name, FormulaContext context) {
        Binding binding = bind(name);
        if (binding == null) {
            FormulaDiagnostics.report("Unknown formula variable '" + name + "'");
            return 0.0D;
        }
        return read(name, context, binding);
    }

    /**
     * Resolves one name with a binding the caller already holds, which is what a provider that
     * reads a single fixed name should use.
     */
    public static double resolve(String name, FormulaContext context, Binding binding) {
        return read(name, context, binding);
    }

    /**
     * Resolves one name, reusing a per-formula binding cache.
     *
     * @param cache bindings of one expression, or {@code null} to look the name up every time
     */
    public static double resolve(String name, FormulaContext context, @Nullable Map<String, Binding> cache) {
        if (cache == null) return resolve(name, context);
        Binding binding = cache.get(name);
        if (binding == null) {
            binding = bind(name);
            if (binding == null) {
                FormulaDiagnostics.report("Unknown formula variable '" + name + "'");
                return 0.0D;
            }
            cache.put(name, binding);
        }
        return read(name, context, binding);
    }

    private static double read(String name, FormulaContext context, Binding binding) {
        try {
            double value = binding.value(context);
            if (Double.isNaN(value)) {
                FormulaDiagnostics.report("Formula variable '" + name + "' produced no value in this context");
                return 0.0D;
            }
            if (!Double.isFinite(value)) {
                FormulaDiagnostics.report("Formula variable '" + name + "' produced the non-finite value " + value);
                return 0.0D;
            }
            return value;
        } catch (RuntimeException exception) {
            FormulaDiagnostics.report("Formula variable '" + name + "' failed", exception);
            return 0.0D;
        }
    }

    /**
     * Resolves a name without reporting anything, for callers that treat a missing variable as a
     * normal outcome. Returns {@link Double#NaN} when the context cannot provide the name.
     */
    public static double peek(String name, FormulaContext context) {
        Binding binding = bind(name);
        if (binding == null) return Double.NaN;
        try {
            return binding.value(context);
        } catch (RuntimeException exception) {
            return Double.NaN;
        }
    }

    /**
     * Finds the variables that claim a name. The result depends only on the registry, so it stays
     * valid for as long as the game runs, and {@code null} means no variable provides the name.
     */
    @Nullable
    public static Binding bind(String name) {
        Lookup index = lookup();
        FormulaVariable exact = index.exact().get(name);
        List<Candidate> candidates = null;
        if (exact != null) {
            candidates = new ArrayList<>(2);
            candidates.add(new Candidate(exact, name, ""));
        }
        for (Lookup.Prefix prefix : index.prefixes()) {
            if (name.length() <= prefix.key().length() || !name.startsWith(prefix.key())) continue;
            if (candidates == null) candidates = new ArrayList<>(2);
            candidates.add(new Candidate(prefix.variable(), prefix.key(), name.substring(prefix.key().length())));
        }
        return candidates == null ? null : new Binding(List.copyOf(candidates));
    }

    private static Lookup lookup() {
        Lookup cached = lookup;
        if (cached != null) return cached;
        Map<String, FormulaVariable> exact = new LinkedHashMap<>();
        List<Lookup.Prefix> prefixes = new ArrayList<>();
        for (FormulaVariable variable : MxtRegistries.FORMULA_VARIABLE) {
            for (String name : variable.names()) {
                FormulaVariable previous = exact.putIfAbsent(name, variable);
                if (previous != null && previous != variable)
                    FormulaDiagnostics.report("Formula variable name '" + name + "' is provided by more than one variable");
            }
            for (String prefix : variable.prefixes()) prefixes.add(new Lookup.Prefix(prefix, variable));
        }
        Lookup built = new Lookup(Map.copyOf(exact), List.copyOf(prefixes));
        lookup = built;
        return built;
    }

    private record Lookup(Map<String, FormulaVariable> exact, List<Prefix> prefixes) {
        private record Prefix(String key, FormulaVariable variable) {
        }
    }

    /**
     * The variables that claim one requested name, in the order they are asked. The first
     * candidate that can provide a value in the current context wins.
     */
    public record Binding(List<Candidate> candidates) {
        /**
         * The value of the first candidate that can provide one in this context, or
         * {@link Double#NaN} when none of them can.
         */
        public double value(FormulaContext context) {
            for (Candidate candidate : this.candidates) {
                double value = candidate.value(context);
                if (!Double.isNaN(value)) return value;
            }
            return Double.NaN;
        }

        /**
         * One variable that claimed the name, together with the part of the name it receives.
         */
        public record Candidate(FormulaVariable variable, String key, String suffix) {
            private double value(FormulaContext context) {
                return this.variable.value(this.key, this.suffix, context);
            }
        }
    }
}
