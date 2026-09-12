package com.iafenvoy.mxt.util.formula;

import com.iafenvoy.mxt.registry.MxtRegistries;
import net.objecthunter.exp4j.function.Function;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Functions allowed by the formula language, collected from the intrinsic registry.
 *
 * <p>The registry is frozen once the game starts, so both the function list and its name set are
 * collected on first use and reused by every expression that is built afterwards.</p>
 */
public final class FormulaFunctions {
    private static volatile List<Function> all;
    private static volatile Set<String> names;

    private FormulaFunctions() {
    }

    public static List<Function> all() {
        List<Function> cached = all;
        if (cached != null) return cached;
        LinkedHashMap<String, Function> functions = new LinkedHashMap<>();
        MxtRegistries.FORMULA_FUNCTION.forEach(function -> {
            Function previous = functions.putIfAbsent(function.getName(), function);
            if (previous != null)
                throw new IllegalStateException("Duplicate formula function name: " + function.getName());
        });
        List<Function> built = List.copyOf(functions.values());
        all = built;
        return built;
    }

    public static Set<String> names() {
        Set<String> cached = names;
        if (cached != null) return cached;
        Set<String> built = all().stream().map(Function::getName).collect(Collectors.toCollection(LinkedHashSet::new));
        names = built;
        return built;
    }
}
