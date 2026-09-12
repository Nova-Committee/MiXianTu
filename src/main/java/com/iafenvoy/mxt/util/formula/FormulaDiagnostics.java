package com.iafenvoy.mxt.util.formula;

import com.mojang.logging.LogUtils;
import net.neoforged.fml.loading.FMLEnvironment;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Single reporting policy for every formula problem that is only found while the game runs.
 *
 * <p>Problems that can already be decided while a data pack is parsed do not come through here. A
 * number provider reports those as a codec error, which is what lets the loader collect every broken
 * formula of one load and report them together — the same way it reports the other registry errors
 * — instead of stopping at the first one. That path is identical in a development and a production
 * environment.</p>
 *
 * <p>A runtime problem cannot be batched that way; it is found while a formula is evaluated. A
 * development environment logs the whole error, including the cause and its stack trace, and keeps
 * running so one evaluation can show every problem it hits; a production environment logs a single
 * warning line per distinct message and returns the fallback value.</p>
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
     * Reports a runtime formula problem that has no exception of its own.
     */
    public static void report(String message) {
        report(message, null);
    }

    /**
     * Reports a runtime formula problem.
     *
     * <p>Development logs the full error and continues — with the cause supplied by the caller, or
     * with the reporting call site when the problem has no exception of its own, so the formula and
     * its evaluation path are still visible. Production logs one line per distinct message and
     * never throws.</p>
     */
    public static void report(String message, @Nullable Throwable cause) {
        if (development()) {
            LOGGER.error(message, cause == null ? new Throwable(message) : cause);
            return;
        }
        if (REPORTED.size() < REPORT_LIMIT && REPORTED.add(message)) LOGGER.warn("{}", message);
    }
}
