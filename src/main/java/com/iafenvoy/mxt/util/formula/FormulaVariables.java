package com.iafenvoy.mxt.util.formula;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class FormulaVariables {
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
    private static final Set<String> FUNCTIONS = Set.of("abs", "acos", "asin", "atan", "cbrt", "ceil", "clamp", "cos", "cosh", "exp", "floor", "log", "log10", "max", "min", "round", "sin", "sinh", "sqrt", "tan", "tanh");
    private static final Set<String> CONSTANTS = Set.of("pi", "e");

    private FormulaVariables() {
    }

    static Set<String> find(String expression) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        Matcher matcher = IDENTIFIER.matcher(expression);
        while (matcher.find()) {
            String token = matcher.group();
            if (!FUNCTIONS.contains(token) && !CONSTANTS.contains(token)) {
                result.add(token);
            }
        }
        return Set.copyOf(result);
    }
}
