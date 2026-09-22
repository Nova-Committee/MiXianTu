package com.iafenvoy.mxt.data.artifact.ability;

import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

/**
 * One artifact capability that needs a key to fire, and therefore belongs on the wheel: implementing this
 * interface is the whole of the opt-in, and the wheel lists exactly the entries that do.
 *
 * <p>Everything a player has to press for counts, whether it is a switch or a one-shot. Flight is a switch
 * (on or off), the storage is a one-shot (the container opens; nothing stays on), and both are the same thing to
 * the wheel: a cell that says a name, draws a state if it has one, and reports a press. The name is loose on
 * purpose - the reference implementation this follows calls the same pair of things "toggleable" and lets the
 * inventory power answer with "open the menu".</p>
 *
 * <p>Every other entry of {@code abilities} says what an artifact is <em>by itself</em> - it grants skills, it
 * carries its holder, it charges a price - and none of those is something a player presses, so none of them
 * becomes a cell.</p>
 *
 * <p>An artifact may declare several of these, one per {@link #key()}: the wheel addresses a cell by the artifact
 * and that key together, which is what lets one sword offer both flight and storage.</p>
 */
public interface ToggableArtifactAbility extends ArtifactAbility {
    /**
     * The name this capability is addressed by inside its artifact, unique within one definition - the wheel's
     * cell is "this artifact's capability {@code key}". Code-owned rather than written by a pack, because the
     * capability itself is code-owned: the type that exists is the thing that can be pressed.
     */
    String key();

    /** What the wheel names this capability - "flight" rather than the artifact, which the tooltip adds. */
    Component displayName();

    /**
     * Whether the capability is a state that stays on, and which side it is on right now. Absent is the ordinary
     * case for a one-shot activation: the storage opens, nothing about the artifact changes, and the wheel draws
     * the cell without a state line.
     *
     * <p>{@link #activate} consults this on the server to decide which way a press goes, and the client draws it,
     * so both sides read the same state - the implementation's own, never one kept by the wheel.</p>
     */
    default Optional<Boolean> state(ArtifactToggleContext context) {
        return Optional.empty();
    }

    /**
     * The key was pressed. One call per press, server side only, which is why a refusal is a value rather than an
     * exception: the wheel repeats it on the action bar and in the log, because from the wheel there is nothing
     * else to read.
     */
    Result activate(ArtifactToggleContext context);

    /** What the press did: whether anything happened, and why nothing did when nothing did. */
    record Result(boolean changed, @Nullable Failure failure) {
        /** The press did its thing, which is also what the wheel reads to decide it went through. */
        public static Result activated() {
            return new Result(true, null);
        }

        public static Result refused(Failure failure) {
            return new Result(false, failure);
        }
    }

    /**
     * Why a press did nothing. Deliberately short: the wheel can say one line, and these are the cases a player
     * can do something about.
     */
    enum Failure {
        /**
         * The artifact that declares this capability is not carried any more. An implementation whose state lives
         * on the stack can report it itself; for the wheel it is normally a race between the page it read and the
         * trigger, which the server drops as a stale entry before an implementation is ever asked.
         */
        NOT_CARRIED,
        /** The artifact refuses this holder: it belongs to somebody else, or it still needs claiming. */
        NOT_OWNED,
        /** It is already in the state that was asked for. */
        ALREADY_SET,
        /** The capability cannot be used right now - whatever the reason is, this implementation knows it. */
        UNAVAILABLE
    }
}
