package com.iafenvoy.mxt.util.profiling;

import com.iafenvoy.mxt.MiXianTu;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.function.BiConsumer;

public final class LoggerDispatcher {
    private final BiConsumer<String, Object[]> devLogger, productionLogger;
    private final BiConsumer<String, Throwable> devThrower, productionThrower;
    private int reportLimit = 512, reported = 0;

    private LoggerDispatcher(Logger logger, LoggingLevel devLevel, LoggingLevel productionLevel) {
        this.devLogger = devLevel.getLogMethod(logger);
        this.productionLogger = productionLevel.getLogMethod(logger);
        this.devThrower = devLevel.getThrowMethod(logger);
        this.productionThrower = productionLevel.getThrowMethod(logger);
    }

    public static LoggerDispatcher of(Logger logger) {
        return new LoggerDispatcher(logger, LoggingLevel.WARN, LoggingLevel.DEBUG);
    }

    public static LoggerDispatcher of(Logger logger, LoggingLevel devLevel, LoggingLevel productionLevel) {
        return new LoggerDispatcher(logger, devLevel, productionLevel);
    }

    public LoggerDispatcher limit(int reportLimit) {
        this.reportLimit = reportLimit;
        return this;
    }

    public void log(String format, Object... args) {
        if (MiXianTu.isDevMode()) this.devLogger.accept(format, args);
        else if (this.reported < this.reportLimit) {
            this.productionLogger.accept(format, args);
            this.reported++;
        }
    }

    public void log(String format, @Nullable Throwable throwable) {
        if (throwable == null) this.log(format);
        else {
            if (MiXianTu.isDevMode()) this.devThrower.accept(format, throwable);
            else if (this.reported < this.reportLimit) {
                this.productionThrower.accept(format, throwable);
                this.reported++;
            }
        }
    }
}
