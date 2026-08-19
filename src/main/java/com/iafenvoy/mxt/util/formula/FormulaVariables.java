package com.iafenvoy.mxt.util.formula;

import com.iafenvoy.mxt.registry.MxtRegistries;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class FormulaVariables {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
    private static final Set<String> FUNCTIONS = Set.of("abs", "acos", "asin", "atan", "cbrt", "ceil", "cos", "cosh", "exp", "floor", "log", "log10", "sin", "sinh", "sqrt", "tan", "tanh");
    private static final Set<String> CONSTANTS = Set.of("pi", "e");

    private FormulaVariables() {
    }

    public static boolean isValidName(String name) {
        return IDENTIFIER.matcher(name).matches();
    }

    public static Set<String> find(String expression) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        Matcher matcher = IDENTIFIER.matcher(expression);
        while (matcher.find()) {
            String token = matcher.group();
            if (!FUNCTIONS.contains(token) && !FormulaFunctions.names().contains(token) && !CONSTANTS.contains(token)) {
                result.add(token);
            }
        }
        return result;
    }

    public static boolean contains(String name) {
        return variable(name).isPresent();
    }

    public static double resolve(String name, FormulaContext context) {
        Optional<FormulaVariable> variable = variable(name);
        if (variable.isEmpty()) return 0.0D;
        try {
            double value = variable.get().resolve(context);
            if (Double.isFinite(value)) return value;
            LOGGER.warn("Formula variable {} produced non-finite value {}; using 0", name, value);
        } catch (RuntimeException exception) {
            LOGGER.warn("Formula variable {} failed at runtime: {}; using 0", name, exception.getMessage() == null ? "unknown error" : exception.getMessage());
        }
        return 0.0D;
    }

    private static Optional<FormulaVariable> variable(String name) {
        FormulaVariable result = null;
        for (FormulaVariable candidate : MxtRegistries.FORMULA_VARIABLE) {
            Identifier id = MxtRegistries.FORMULA_VARIABLE.getKey(candidate);
            if (!Objects.equals(id.getPath(), name)) continue;
            if (result != null) throw new IllegalStateException("Duplicate formula variable name: " + name);
            result = candidate;
        }
        return Optional.ofNullable(result);
    }
}
