package com.iafenvoy.mxt.util.formula;

import com.iafenvoy.mxt.registry.MxtTypeRegistries;
import net.objecthunter.exp4j.function.Function;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

/**
 * Functions allowed by the formula language, collected from the intrinsic registry.
 */
public final class FormulaFunctions {
    private FormulaFunctions() {
    }

    public static List<Function> all() {
        LinkedHashMap<String, Function> functions = new LinkedHashMap<>();
        MxtTypeRegistries.FORMULA_FUNCTION.forEach(function -> {
            Function previous = functions.putIfAbsent(function.getName(), function);
            if (previous != null)
                throw new IllegalStateException("Duplicate formula function name: " + function.getName());
        });
        return new LinkedList<>(functions.values());
    }

    public static Set<String> names() {
        return new LinkedHashSet<>(all().stream().map(Function::getName).toList());
    }
}
