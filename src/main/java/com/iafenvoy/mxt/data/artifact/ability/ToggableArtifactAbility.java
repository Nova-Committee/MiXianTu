package com.iafenvoy.mxt.data.artifact.ability;

import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

/**
 * The opt-in that puts an artifact capability on the wheel: everything a player has to press counts, whether it
 * is a switch or a one-shot. The wheel addresses a cell by the artifact and {@link #key()} together.
 */
public interface ToggableArtifactAbility extends ArtifactAbility {
    // Unique within one definition. Code-owned rather than written by a pack, because the capability itself is
    // code-owned: the type that exists is the thing that can be pressed.
    String key();

    Component displayName();

    // Absent is the ordinary case for a one-shot activation. Both sides read the implementation's own state, never
    // one kept by the wheel.
    default Optional<Boolean> state(ArtifactToggleContext context) {
        return Optional.empty();
    }

    // One call per press, server side only, which is why a refusal is a value rather than an exception.
    Result activate(ArtifactToggleContext context);

    record Result(boolean changed, @Nullable Failure failure) {
        public static Result activated() {
            return new Result(true, null);
        }

        public static Result refused(Failure failure) {
            return new Result(false, failure);
        }
    }

    enum Failure {
        // For the wheel this is normally a race between the page it read and the trigger, which the server drops
        // as a stale entry before an implementation is ever asked.
        NOT_CARRIED,
        NOT_OWNED,
        ALREADY_SET,
        UNAVAILABLE
    }
}
