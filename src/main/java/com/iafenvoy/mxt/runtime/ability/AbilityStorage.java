package com.iafenvoy.mxt.runtime.ability;

import com.iafenvoy.mxt.attachment.AbilityAttachment;
import com.iafenvoy.mxt.data.ability.Ability;
import com.iafenvoy.mxt.data.storage.AuraPulse;
import com.iafenvoy.mxt.data.storage.CastDeadline;
import com.iafenvoy.mxt.data.storage.ChannelPulse;
import com.iafenvoy.mxt.data.storage.ChargesDataStorage;
import com.iafenvoy.mxt.data.storage.CooldownDataStorage;
import com.iafenvoy.mxt.data.storage.DataStorage;
import com.iafenvoy.mxt.util.HolderHelper;
import net.minecraft.core.Holder;

import java.util.Optional;

/**
 * How the ability runtime addresses the state of one granted ability. Values are addressed by the ability's id
 * plus the kind's class, inside the {@link AbilityAttachment} that owns the grant; the kinds the runtime keeps for
 * itself live in the storage package as {@link CastDeadline}, {@link ChannelPulse} and {@link AuraPulse}.
 */
public final class AbilityStorage {
    /**
     * The deadline written when there is no cast to finish, so that "nothing pending" is stored state rather
     * than a missing entry.
     */
    public static final double NO_CAST = Double.MAX_VALUE;

    private AbilityStorage() {
    }

    public static <T extends DataStorage> Optional<T> get(AbilityAttachment attachment, Holder<Ability> ability, Class<T> kind) {
        return attachment.storage().get(HolderHelper.id(ability), kind);
    }

    public static void set(AbilityAttachment attachment, Holder<Ability> ability, DataStorage value, long gameTime) {
        attachment.storage().set(HolderHelper.id(ability), value, gameTime);
    }

    /**
     * The cooldown value with the length a use got, keeping whatever declaration was already stored.
     */
    public static CooldownDataStorage cooldown(AbilityAttachment attachment, Holder<Ability> ability, double duration) {
        return get(attachment, ability, CooldownDataStorage.class).orElse(CooldownDataStorage.INSTANCE).withDuration(duration);
    }

    /**
     * The charges value with a new count left, keeping whatever declaration was already stored.
     */
    public static ChargesDataStorage charges(AbilityAttachment attachment, Holder<Ability> ability, double remaining) {
        return get(attachment, ability, ChargesDataStorage.class).orElse(ChargesDataStorage.INSTANCE).withRemaining(remaining);
    }

    /**
     * The tick a kind was last written, which is when a cast started or a state began; {@code -1} when the kind
     * has never been written.
     */
    public static long changedAt(AbilityAttachment attachment, Holder<Ability> ability, Class<? extends DataStorage> kind) {
        return attachment.storage().changedAt(HolderHelper.id(ability), kind);
    }

    /**
     * The deadline the stored cast finishes at, or {@link #NO_CAST} when no cast is pending.
     */
    public static double castDeadline(AbilityAttachment attachment, Holder<Ability> ability) {
        return get(attachment, ability, CastDeadline.class).map(CastDeadline::endsAt).orElse(NO_CAST);
    }

    /**
     * Whether a cast is running right now: a deadline that is neither the "no cast" sentinel nor due.
     */
    public static boolean casting(AbilityAttachment attachment, Holder<Ability> ability, long gameTime) {
        double endsAt = castDeadline(attachment, ability);
        return endsAt < NO_CAST && endsAt > gameTime;
    }

    /**
     * Whether a cast is due to finish, which is the only case a cast is finished in: no stored deadline and a
     * deadline still in the future both mean there is nothing to finish.
     */
    public static boolean castDue(AbilityAttachment attachment, Holder<Ability> ability, long gameTime) {
        double endsAt = castDeadline(attachment, ability);
        return endsAt <= gameTime;
    }

    /**
     * Whether a cast deadline is stored at all, which is what releasing the input has to cancel.
     */
    public static boolean hasCast(AbilityAttachment attachment, Holder<Ability> ability) {
        return castDeadline(attachment, ability) < NO_CAST;
    }

    /**
     * Marks the cast as finished, which is what both finishing and cancelling write.
     */
    public static void clearCast(AbilityAttachment attachment, Holder<Ability> ability, long gameTime) {
        set(attachment, ability, new CastDeadline(NO_CAST), gameTime);
    }
}
