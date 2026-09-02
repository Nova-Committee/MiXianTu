package com.iafenvoy.mxt.util.formula;

import com.iafenvoy.mxt.registry.MxtRegistries;
import net.objecthunter.exp4j.function.Function;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Functions allowed by the formula language, collected from the intrinsic registry.
 */
public final class FormulaFunctions {
    private FormulaFunctions() {
    }

    public static List<Function> all() {
        LinkedHashMap<String, Function> functions = new LinkedHashMap<>();
        MxtRegistries.FORMULA_FUNCTION.forEach(function -> {
            Function previous = functions.putIfAbsent(function.getName(), function);
            if (previous != null)
                throw new IllegalStateException("Duplicate formula function name: " + function.getName());
        });
        return new LinkedList<>(functions.values());
    }

    public static Set<String> names() {
        return all().stream().map(Function::getName).collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
