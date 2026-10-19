package com.iafenvoy.mxt.runtime.ability;

import com.iafenvoy.mxt.attachment.AbilityAttachment;
import com.iafenvoy.mxt.attachment.ResourceHolderAttachment;
import com.iafenvoy.mxt.data.ability.Ability;
import com.iafenvoy.mxt.data.ability.Togglable;
import com.iafenvoy.mxt.data.ability.ToggleContext;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.runtime.resulting.Failure;
import com.iafenvoy.mxt.runtime.resulting.Result;
import com.iafenvoy.mxt.runtime.resulting.failures.AbilityFailure;
import com.iafenvoy.mxt.util.OptionalBoolean;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

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

    public static Togglable.PressResult activate(LivingEntity holder, Holder<Ability> ability, @Nullable ItemStack carrier) {
        if (holder == null || ability == null || !(ability.value().type() instanceof Togglable togglable))
            return Togglable.PressResult.refused(AbilityFailure.UNAVAILABLE);
        // A press writes state and world, so only the server answers one; reading the state stays two-sided.
        if (holder.level().isClientSide()) return Togglable.PressResult.refused(AbilityFailure.UNAVAILABLE);
        ToggleContext context = new ToggleContext(holder, carrier, ability);
        // Nothing is paid for a press that cannot happen: the read-only precondition is asked before the gate.
        Optional<Failure> refusal = togglable.canActivate(context);
        if (refusal.isPresent()) return Togglable.PressResult.refused(refusal.get());
        if (!togglable.gated(context)) return togglable.activate(context);
        Result gate = AbilityService.gate(context);
        return gate.failure() == null ? togglable.activate(context)
                : Togglable.PressResult.refused(gate.failure(), gate.failedResource());
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
    public static Togglable.PressResult cast(ToggleContext context) {
        LivingEntity holder = context.holder();
        AbilityAttachment abilities = holder.getData(MxtAttachments.ABILITY_HOLDER);
        ResourceHolderAttachment resources = holder.getData(MxtAttachments.RESOURCE_HOLDER);
        AbilityService.UseResult result = AbilityService.use(context.ability(), holder, abilities, resources,
                holder.level().getGameTime(), context.formula());
        if (result.isSuccess() || result.casting()) return Togglable.PressResult.activated();
        return Togglable.PressResult.refused(result.failure(), result.failedResource());
    }
}
