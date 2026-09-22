package com.iafenvoy.mxt.util.formula;

import com.mojang.logging.LogUtils;
import net.neoforged.fml.loading.FMLEnvironment;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Single reporting policy for every formula problem that is only found while the game runs. Problems decidable
 * while a data pack is parsed do not come through here: a number provider reports those as a codec error, which
 * lets the loader collect every broken formula of one load instead of stopping at the first.
 */
public final class FormulaDiagnostics {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Set<String> REPORTED = ConcurrentHashMap.newKeySet();
    private static final int REPORT_LIMIT = 512;

    private FormulaDiagnostics() {
    }

    public static boolean development() {
        return !FMLEnvironment.isProduction();
    }

    public static void report(String message) {
        report(message, null);
    }

    // Development logs the full error and continues, using the caller's cause or the call site when there is
    // none; production logs one line per distinct message, at most REPORT_LIMIT times.
    public static void report(String message, @Nullable Throwable cause) {
        if (development()) {
            LOGGER.error(message, cause == null ? new Throwable(message) : cause);
            return;
        }
        if (REPORTED.size() < REPORT_LIMIT && REPORTED.add(message)) LOGGER.warn("{}", message);
    }
}
