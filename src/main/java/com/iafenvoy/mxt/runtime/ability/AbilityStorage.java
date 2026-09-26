package com.iafenvoy.mxt.runtime.ability;

import com.iafenvoy.mxt.attachment.AbilityAttachment;
import com.iafenvoy.mxt.data.storage.DataStorage;
import com.iafenvoy.mxt.data.storage.builtin.ChargesDataStorage;
import com.iafenvoy.mxt.data.storage.builtin.CooldownDataStorage;
import com.iafenvoy.mxt.data.storage.runtime.CastDeadline;
import net.minecraft.resources.Identifier;

import java.util.Optional;

/**
 * The state of one granted ability, addressed by the ability's id plus the kind's class inside the
 * {@link AbilityAttachment} that owns the grant. Values are mutable: runtime code asks for the one value of a kind
 * and changes it in place, and only a content write replaces one.
 */
public final class AbilityStorage {
    private AbilityStorage() {
    }

    public static <T extends DataStorage> Optional<T> get(AbilityAttachment attachment, Identifier ability, Class<T> kind) {
        return attachment.storage().get(ability, kind);
    }

    public static void put(AbilityAttachment attachment, Identifier ability, DataStorage value, long gameTime) {
        attachment.storage().set(ability, value, gameTime);
    }

    // The one value of a kind, made from the blank the first time it is asked for. A stored value is never the blank
    // itself, because the blank is the declaration every ability of that type shares.
    @SuppressWarnings("unchecked")
    public static <T extends DataStorage> T value(AbilityAttachment attachment, Identifier ability, Class<T> kind,
                                                  DataStorage blank, long gameTime) {
        Optional<T> stored = get(attachment, ability, kind);
        if (stored.isPresent()) return stored.get();
        T created = (T) blank.copy();
        put(attachment, ability, created, gameTime);
        return created;
    }

    // The one cooldown there is: the length the last payment wrote, counted from the tick it started. A length that
    // was never written, or one that has already run out, reads as nothing remaining.
    public static long cooldownRemaining(AbilityAttachment attachment, Identifier ability, long gameTime) {
        return get(attachment, ability, CooldownDataStorage.class).map(value -> value.remaining(gameTime)).orElse(0L);
    }

    public static boolean onCooldown(AbilityAttachment attachment, Identifier ability, long gameTime) {
        return cooldownRemaining(attachment, ability, gameTime) > 0L;
    }

    public static void startCooldown(AbilityAttachment attachment, Identifier ability, double duration, long gameTime) {
        value(attachment, ability, CooldownDataStorage.class, CooldownDataStorage.INSTANCE, gameTime).start(duration, gameTime);
    }

    // The pool that is spent by a payment; one that was never spent reads as full, which is why callers use the
    // declaration's maximum rather than a stored remaining.
    public static ChargesDataStorage charges(AbilityAttachment attachment, Identifier ability, ChargesDataStorage declaration, long gameTime) {
        return value(attachment, ability, ChargesDataStorage.class, declaration, gameTime);
    }

    // NO_CAST when no cast is pending.
    public static double castDeadline(AbilityAttachment attachment, Identifier ability) {
        return get(attachment, ability, CastDeadline.class).map(CastDeadline::endsAt).orElse(CastDeadline.NO_CAST);
    }

    // A deadline that is neither the "no cast" sentinel nor due.
    public static boolean casting(AbilityAttachment attachment, Identifier ability, long gameTime) {
        double endsAt = castDeadline(attachment, ability);
        return endsAt < CastDeadline.NO_CAST && endsAt > gameTime;
    }

    // No stored deadline and a deadline still in the future both mean there is nothing to finish.
    public static boolean castDue(AbilityAttachment attachment, Identifier ability, long gameTime) {
        return castDeadline(attachment, ability) <= gameTime;
    }

    // What releasing the input has to cancel.
    public static boolean hasCast(AbilityAttachment attachment, Identifier ability) {
        return castDeadline(attachment, ability) < CastDeadline.NO_CAST;
    }

    // What both finishing and cancelling write.
    public static void clearCast(AbilityAttachment attachment, Identifier ability, long gameTime) {
        value(attachment, ability, CastDeadline.class, new CastDeadline(CastDeadline.NO_CAST), gameTime).clear();
    }
}
