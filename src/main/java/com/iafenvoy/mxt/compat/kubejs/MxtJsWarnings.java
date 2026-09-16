package com.iafenvoy.mxt.compat.kubejs;

import com.iafenvoy.mxt.MiXianTu;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Warning sink for script misuse that would otherwise repeat on every tick: each distinct key is logged
 * once, and the set is capped so a broken script cannot grow it without bound.
 */
public final class MxtJsWarnings {
    private static final int LIMIT = 256;
    private static final Set<String> REPORTED = ConcurrentHashMap.newKeySet();

    private MxtJsWarnings() {
    }

    /**
     * Logs a warning once per distinct key.
     */
    public static void warnOnce(String key, String message) {
        if (REPORTED.size() >= LIMIT) return;
        if (!REPORTED.add(key)) return;
        MiXianTu.LOGGER.warn(message);
    }

    /**
     * Drops the reported keys. Called when server scripts are re-evaluated, so a fixed script can
     * report again after the next reload.
     */
    public static void clear() {
        REPORTED.clear();
    }
}
