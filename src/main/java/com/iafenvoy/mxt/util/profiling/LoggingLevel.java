package com.iafenvoy.mxt.util.profiling;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.function.BiConsumer;
import java.util.function.Function;

public enum LoggingLevel {
    ERROR(logger -> logger::error, logger -> logger::error),
    WARN(logger -> logger::warn, logger -> logger::warn),
    INFO(logger -> logger::info, logger -> logger::info),
    DEBUG(logger -> logger::debug, logger -> logger::debug),
    NONE(null, null);
    @Nullable
    private final Function<Logger, BiConsumer<String, Object[]>> logMethod;
    @Nullable
    private final Function<Logger, BiConsumer<String, Throwable>> throwMethod;

    LoggingLevel(@Nullable Function<Logger, BiConsumer<String, Object[]>> logMethod, @Nullable Function<Logger, BiConsumer<String, Throwable>> throwMethod) {
        this.logMethod = logMethod;
        this.throwMethod = throwMethod;
    }

    public BiConsumer<String, Object[]> getLogMethod(Logger logger) {
        return this.logMethod != null ? this.logMethod.apply(logger) : (_, _) -> {
        };
    }

    public BiConsumer<String, Throwable> getThrowMethod(Logger logger) {
        return this.throwMethod != null ? this.throwMethod.apply(logger) : (_, _) -> {
        };
    }
}
