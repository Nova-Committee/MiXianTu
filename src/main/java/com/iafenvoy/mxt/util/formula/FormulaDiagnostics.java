package com.iafenvoy.mxt.util.formula;

import com.mojang.logging.LogUtils;
import net.neoforged.fml.loading.FMLEnvironment;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Single reporting policy for every formula problem that is only found while the game runs. Problems
 * decidable while a data pack is parsed do not come through here: a number provider reports those as
 * a codec error, which lets the loader collect every broken formula of one load, as it does for the
 * other registry errors, instead of stopping at the first one.
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
     * Reports a runtime formula problem: development logs the full error and continues, using the caller's
     * cause or the call site when there is none; production logs one line per distinct message.
     */
    public static void report(String message, @Nullable Throwable cause) {
        if (development()) {
            LOGGER.error(message, cause == null ? new Throwable(message) : cause);
            return;
        }
        if (REPORTED.size() < REPORT_LIMIT && REPORTED.add(message)) LOGGER.warn("{}", message);
    }
}
