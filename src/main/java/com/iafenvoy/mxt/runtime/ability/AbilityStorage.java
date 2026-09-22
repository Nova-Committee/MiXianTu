package com.iafenvoy.mxt.runtime.ability;

import com.iafenvoy.mxt.attachment.AbilityAttachment;
import com.iafenvoy.mxt.data.ability.Ability;
import com.iafenvoy.mxt.data.storage.CastDeadline;
import com.iafenvoy.mxt.data.storage.ChargesDataStorage;
import com.iafenvoy.mxt.data.storage.CooldownDataStorage;
import com.iafenvoy.mxt.data.storage.DataStorage;
import com.iafenvoy.mxt.util.HolderHelper;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import net.minecraft.core.Holder;

import java.util.Optional;

/**
 * The state of one granted ability, addressed by the ability's id plus the kind's class inside the
 * {@link AbilityAttachment} that owns the grant.
 */
public final class AbilityStorage {
    // Written when there is no cast to finish, so "nothing pending" is stored state rather than a missing entry.
    public static final double NO_CAST = Double.MAX_VALUE;

    private AbilityStorage() {
    }

    public static <T extends DataStorage> Optional<T> get(AbilityAttachment attachment, Holder<Ability> ability, Class<T> kind) {
        return attachment.storage().get(HolderHelper.id(ability), kind);
    }

    public static void set(AbilityAttachment attachment, Holder<Ability> ability, DataStorage value, long gameTime) {
        attachment.storage().set(HolderHelper.id(ability), value, gameTime);
    }

    // Keeps whatever declaration was already stored.
    public static CooldownDataStorage cooldown(AbilityAttachment attachment, Holder<Ability> ability, double duration) {
        return get(attachment, ability, CooldownDataStorage.class).orElse(CooldownDataStorage.INSTANCE).withDuration(duration);
    }

    // Keeps whatever declaration was already stored.
    public static ChargesDataStorage charges(AbilityAttachment attachment, Holder<Ability> ability, double remaining) {
        return get(attachment, ability, ChargesDataStorage.class).orElse(ChargesDataStorage.INSTANCE).withRemaining(remaining);
    }

    // The last write is the interval's anchor, and both spending and recharging make one, so a caller may run
    // this every tick and still get at most one step per interval. Nothing is written while the pool is full or
    // was never written (which reads as full), so a tick that changes nothing leaves the attachment clean.
    public static boolean recharge(AbilityAttachment attachment, Holder<Ability> ability,
                                   ChargesDataStorage declaration, long gameTime, FormulaContext context) {
        Optional<ChargesDataStorage> stored = get(attachment, ability, ChargesDataStorage.class);
        if (stored.isEmpty() || stored.get().remaining().isEmpty()) return false;
        double remaining = stored.get().remaining().get();
        double maximum = declaration.maximum().evaluate(context);
        if (!Double.isFinite(remaining) || !Double.isFinite(maximum) || remaining >= maximum) return false;
        double ticks = declaration.rechargeTicks().evaluate(context);
        // A declaration without a usable interval never recharges; an interval of zero would otherwise refill
        // the pool with a write on every tick.
        if (!Double.isFinite(ticks) || ticks <= 0.0D) return false;
        if (gameTime - changedAt(attachment, ability, ChargesDataStorage.class) < Math.max(1L, Math.round(ticks)))
            return false;
        set(attachment, ability, stored.get().withRemaining(Math.min(maximum, remaining + 1.0D)), gameTime);
        return true;
    }

    // When a cast started or a state began; -1 when the kind was never written.
    public static long changedAt(AbilityAttachment attachment, Holder<Ability> ability, Class<? extends DataStorage> kind) {
        return attachment.storage().changedAt(HolderHelper.id(ability), kind);
    }

    // NO_CAST when no cast is pending.
    public static double castDeadline(AbilityAttachment attachment, Holder<Ability> ability) {
        return get(attachment, ability, CastDeadline.class).map(CastDeadline::endsAt).orElse(NO_CAST);
    }

    // A deadline that is neither the "no cast" sentinel nor due.
    public static boolean casting(AbilityAttachment attachment, Holder<Ability> ability, long gameTime) {
        double endsAt = castDeadline(attachment, ability);
        return endsAt < NO_CAST && endsAt > gameTime;
    }

    // No stored deadline and a deadline still in the future both mean there is nothing to finish.
    public static boolean castDue(AbilityAttachment attachment, Holder<Ability> ability, long gameTime) {
        double endsAt = castDeadline(attachment, ability);
        return endsAt <= gameTime;
    }

    // What releasing the input has to cancel.
    public static boolean hasCast(AbilityAttachment attachment, Holder<Ability> ability) {
        return castDeadline(attachment, ability) < NO_CAST;
    }

    // What both finishing and cancelling write.
    public static void clearCast(AbilityAttachment attachment, Holder<Ability> ability, long gameTime) {
        set(attachment, ability, new CastDeadline(NO_CAST), gameTime);
    }
}
