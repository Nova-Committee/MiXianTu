package com.iafenvoy.mxt.util.formula;

import com.mojang.logging.LogUtils;
import net.neoforged.fml.loading.FMLEnvironment;
import org.slf4j.Logger;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Single reporting policy for every formula problem.
 *
 * <p>A wrong variable name, a duplicated name or a variable that fails at runtime is a
 * content bug. Development environments throw immediately so the author sees the call
 * site; production logs one warning per distinct message and keeps the game running.</p>
 */
public final class FormulaDiagnostics {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Set<String> REPORTED = ConcurrentHashMap.newKeySet();
    private static final int REPORT_LIMIT = 512;

    private FormulaDiagnostics() {
    }

    /**
     * Whether the game runs from a development environment.
     */
    public static boolean development() {
        return !FMLEnvironment.isProduction();
    }

    /**
     * Reports a formula problem. Development environments throw, production warns once.
     */
    public static void report(String message) {
        if (development()) throw new FormulaException(message);
        if (REPORTED.size() < REPORT_LIMIT && REPORTED.add(message)) LOGGER.warn("{}", message);
    }
}
