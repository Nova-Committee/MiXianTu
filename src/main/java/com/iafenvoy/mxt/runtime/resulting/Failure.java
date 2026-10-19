package com.iafenvoy.mxt.runtime.resulting;

import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.util.DefinitionText;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.StringRepresentable;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

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
     * Which registry's definitions can refuse with this one. A module's constants point at their own table; a refusal
     * any module can answer with ({@link Failures}) stays {@link FailureSources#COMMON}.
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
        return this.source().id().toLanguageKey("failure", this.getSerializedName());
    }

    /**
     * The sentence this refusal is read as; {@code args} fill the placeholders the key spells.
     */
    default Component message(Object... args) {
        return Component.translatable(this.key(), args);
    }

    /**
     * The sentence, naming {@code missingResource} when the reason is one that can name it. Running short is the only
     * reason with a "&#95;named" variant, so every other reason answers with its own words and drops the name: a caller
     * that holds the resource never has to know which reason it is holding.
     */
    default Component message(@Nullable Identifier missingResource) {
        if (missingResource == null || !Failures.INSUFFICIENT_RESOURCE.equals(this)) return this.message();
        return Component.translatable(this.key() + "_named",
                DefinitionText.nameOrId(MxtResourceKeys.RESOURCE, missingResource));
    }
}
