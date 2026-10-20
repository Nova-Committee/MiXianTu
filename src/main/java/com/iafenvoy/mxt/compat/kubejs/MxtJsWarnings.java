package com.iafenvoy.mxt.compat.kubejs;

import com.iafenvoy.mxt.util.profiling.LoggerDispatcher;
import com.iafenvoy.mxt.util.profiling.LoggingLevel;
import com.mojang.logging.LogUtils;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Warning sink for script misuse that would otherwise repeat on every tick: each distinct key is logged once, and
 * the set is capped so a broken script cannot grow it without bound.
 *
 * <p>{@link #WARNING} and {@link #FAILURE} are the two channels the callback classes report on. A bridge function is
 * read wherever the definition that names it is read, which can be every tick of every entity, so neither channel is
 * a place for a line a release build must show in full.
 */
public final class MxtJsWarnings {
    public static final LoggerDispatcher WARNING = LoggerDispatcher.of(LogUtils.getLogger(), LoggingLevel.WARN, LoggingLevel.WARN);
    public static final LoggerDispatcher FAILURE = LoggerDispatcher.of(LogUtils.getLogger(), LoggingLevel.ERROR, LoggingLevel.ERROR);
    private static final int LIMIT = 256;
    private static final Set<String> REPORTED = ConcurrentHashMap.newKeySet();

    private MxtJsWarnings() {
    }

    public static void warnOnce(String key, String message) {
        if (REPORTED.size() >= LIMIT) return;
        if (!REPORTED.add(key)) return;
        WARNING.log(message);
    }

    // Called when server scripts are re-evaluated, so a fixed script can report again after the next reload.
    public static void clear() {
        REPORTED.clear();
    }
}
