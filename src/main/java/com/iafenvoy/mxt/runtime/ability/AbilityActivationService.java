package com.iafenvoy.mxt.runtime.ability;

import com.iafenvoy.mxt.attachment.AbilityAttachment;
import com.iafenvoy.mxt.attachment.ResourceHolderAttachment;
import com.iafenvoy.mxt.data.ability.Ability;
import com.iafenvoy.mxt.data.ability.Togglable;
import com.iafenvoy.mxt.data.ability.ToggleContext;
import com.iafenvoy.mxt.registry.MxtAttachments;
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

    public static Togglable.Result activate(LivingEntity holder, Holder<Ability> ability, @Nullable ItemStack carrier) {
        if (holder == null || ability == null || !(ability.value().type() instanceof Togglable togglable))
            return Togglable.Result.refused(Togglable.Failure.UNAVAILABLE);
        ToggleContext context = new ToggleContext(holder, carrier, ability);
        if (!togglable.gated(context)) return togglable.activate(context);
        AbilityService.GateResult gate = AbilityService.gate(context);
        return gate.approved() ? togglable.activate(context) : Togglable.Result.refused(failureOf(gate.failure()));
    }

    // Read-only, and safe on either side: the state an implementation reports is part of its declaration.
    public static Optional<Boolean> state(LivingEntity holder, Holder<Ability> ability) {
        if (holder == null || ability == null || !(ability.value().type() instanceof Togglable togglable))
            return Optional.empty();
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
        return Togglable.Result.refused(failureOf(result.failure()));
    }

    public static Togglable.Failure failureOf(@Nullable AbilityService.Failure failure) {
        if (failure == null) return Togglable.Failure.UNAVAILABLE;
        return switch (failure) {
            case COOLDOWN -> Togglable.Failure.ON_COOLDOWN;
            case INSUFFICIENT_RESOURCE, INSUFFICIENT_COST -> Togglable.Failure.INSUFFICIENT_COST;
            case NOT_GRANTED -> Togglable.Failure.NOT_CARRIED;
            default -> Togglable.Failure.UNAVAILABLE;
        };
    }
}
