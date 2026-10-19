package com.iafenvoy.mxt.runtime.ability;

import com.iafenvoy.mxt.attachment.AbilityAttachment;
import com.iafenvoy.mxt.attachment.ResourceHolderAttachment;
import com.iafenvoy.mxt.data.ability.Ability;
import com.iafenvoy.mxt.data.ability.Togglable;
import com.iafenvoy.mxt.data.ability.ToggleContext;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.runtime.failure.AbilityFailure;
import com.iafenvoy.mxt.runtime.failure.TogglableFailure;
import com.iafenvoy.mxt.util.OptionalBoolean;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import com.iafenvoy.mxt.runtime.failure.Failure;
import com.iafenvoy.mxt.runtime.failure.Failures;

/**
 * The one press entry point. The wheel, the command and the script bridge all come through here, so "what happens
 * when this is pressed" has a single answer and a single place the shared gate is applied.
 *
 * <p>The ability may be carried by the pressing item or merely granted to the holder; a type that acts on an item
 * says so by refusing a press with no carrier.
 */
public final class AbilityActivationService {
    private AbilityActivationService() {
    }

    public static Togglable.Result activate(LivingEntity holder, Holder<Ability> ability, @Nullable ItemStack carrier) {
        if (holder == null || ability == null || !(ability.value().type() instanceof Togglable togglable))
            return Togglable.Result.refused(TogglableFailure.UNAVAILABLE);
        // A press writes state and world, so only the server answers one; reading the state stays two-sided.
        if (holder.level().isClientSide()) return Togglable.Result.refused(TogglableFailure.UNAVAILABLE);
        ToggleContext context = new ToggleContext(holder, carrier, ability);
        // Nothing is paid for a press that cannot happen: the read-only precondition is asked before the gate.
        Optional<Failure> refusal = togglable.canActivate(context);
        if (refusal.isPresent()) return Togglable.Result.refused(refusal.get());
        if (!togglable.gated(context)) return togglable.activate(context);
        AbilityService.GateResult gate = AbilityService.gate(context);
        return gate.approved() ? togglable.activate(context)
                : Togglable.Result.refused(failureOf(gate.failure()), gate.failedResource());
    }

    // Read-only, and safe on either side: the state an implementation reports is part of its declaration.
    public static OptionalBoolean state(LivingEntity holder, Holder<Ability> ability) {
        if (holder == null || ability == null || !(ability.value().type() instanceof Togglable togglable))
            return OptionalBoolean.empty();
        return togglable.state(new ToggleContext(holder, null, ability));
    }

    public static boolean togglable(Holder<Ability> ability) {
        return ability != null && ability.value().type() instanceof Togglable;
    }

    // The cast path an mxt:active press takes; kept here so the type itself stays a plain data record.
    public static Togglable.Result cast(ToggleContext context) {
        LivingEntity holder = context.holder();
        AbilityAttachment abilities = holder.getData(MxtAttachments.ABILITY_HOLDER);
        ResourceHolderAttachment resources = holder.getData(MxtAttachments.RESOURCE_HOLDER);
        AbilityService.UseResult result = AbilityService.use(context.ability(), holder, abilities, resources,
                holder.level().getGameTime(), context.formula());
        if (result.committed() || result.casting()) return Togglable.Result.activated();
        return Togglable.Result.refused(failureOf(result.failure()), result.failedResource());
    }

    // A press keeps the cast pipeline's own names instead of collapsing them into "unavailable", because the
    // report the player reads is keyed by these: only a reason a press can never produce has nowhere else to go.
    public static Failure failureOf(@Nullable Failure failure) {
        if (failure == null) return TogglableFailure.UNAVAILABLE;
        if (failure == AbilityFailure.NOT_GRANTED) return TogglableFailure.NOT_GRANTED;
        if (failure == AbilityFailure.NO_CHARGES) return TogglableFailure.NO_CHARGES;
        if (failure == AbilityFailure.PERMISSION_DENIED) return TogglableFailure.PERMISSION_DENIED;
        if (failure == AbilityFailure.ELEMENT_AFFINITY) return TogglableFailure.ELEMENT_AFFINITY;
        // A refusal the press and the cast share already reads the same in both tables, so it travels unchanged.
        if (Failures.isShared(failure)) return failure;
        return TogglableFailure.UNAVAILABLE;
    }
}
