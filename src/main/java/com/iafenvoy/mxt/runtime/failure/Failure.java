package com.iafenvoy.mxt.runtime.failure;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.StringRepresentable;
import org.jspecify.annotations.NonNull;

import java.util.Locale;

/**
 * Why an action was refused: a value, never an exception.
 */
public interface Failure extends StringRepresentable {
    /**
     * The constant's own name. An enum satisfies this for free through {@link Enum#name()}.
     */
    String name();

    @Override
    default @NonNull String getSerializedName() {
        return this.name().toLowerCase(Locale.ROOT);
    }

    /**
     * Which registry's definitions can refuse with this one. A module enum points at its own table; a refusal any
     * module can answer with ({@link Failures}) stays {@link FailureSources#COMMON}.
     */
    default FailureSource source() {
        return FailureSources.COMMON;
    }

    /**
     * The one language key of a refusal: {@code failure.<source namespace>.<source path>.<name>}. The source is part of
     * the key so one name can say something different per registry - {@code failure.mxt.pill_binding.cooldown} - while
     * everything that lands in {@link Failures} shares {@code failure.mxt.common.*}.
     */
    default String key() {
        return this.source().id().toLanguageKey("failure",this.getSerializedName());
    }

    default Component message(Object... args) {
        return Component.translatable(this.key(), args);
    }
}
