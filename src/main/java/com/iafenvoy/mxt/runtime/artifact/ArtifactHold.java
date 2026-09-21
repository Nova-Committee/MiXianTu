package com.iafenvoy.mxt.runtime.artifact;

import com.iafenvoy.mxt.data.artifact.Artifact;
import com.iafenvoy.mxt.data.item.HoldBinding;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;

import java.util.List;

/**
 * The hold one artifact definition declares: a stack it claims is used by holding it down, and what the gesture
 * does is {@link ArtifactHoldService}'s.
 *
 * <p>Unlike {@link com.iafenvoy.mxt.runtime.spirit.SpiritChargeHold}, which matches a capability and is one
 * object for everything that will ever have it, a hold here <em>is</em> one definition: the length a gesture
 * lasts and the price of claiming it are declared per artifact, so the declaration is the object the hold module
 * matches and no second copy of those numbers exists.</p>
 *
 * <p>Its answer depends on who is holding it, which is why {@link HoldBinding} grew the entity-aware
 * {@code claims}: an artifact already bound to somebody else does not take the click over at all, so the item
 * answers it the way it would on its own, while a stack bound to the reader is taken over only for as long as
 * its store can still accept aura - a gesture that would move nothing is not worth playing.</p>
 */
public record ArtifactHold(Artifact artifact) implements HoldBinding {
    /**
     * The pose the gesture plays. {@code BLOCK} holds the item up in both hands, which reads as working on it
     * rather than as swinging it.
     */
    public static final ItemUseAnimation HOLD_ANIMATION = ItemUseAnimation.BLOCK;

    /**
     * The sound the gesture makes. Vanilla plays a hold's sound every four ticks, so this has to be short: a
     * beacon blip reads as something taking hold and does not pile up into a drone, and it is not the chime the
     * aura pour plays, so the two gestures are told apart with the eyes closed.
     */
    public static final Holder<SoundEvent> HOLD_SOUND = BuiltInRegistries.SOUND_EVENT.wrapAsHolder(SoundEvents.BEACON_POWER_SELECT);

    @Override
    public List<Entry> entries() {
        return this.artifact.items();
    }

    @Override
    public int holdTicks() {
        // The declaration's own length, read where no entity exists: it decides whether this definition is a
        // hold at all, which is the question HoldLookup asks once per pack load.
        return ArtifactService.holdTicks(this.artifact, FormulaContext.EMPTY);
    }

    @Override
    public int holdTicks(LivingEntity holder, Provider registries, ItemStack stack) {
        // With a holder the declared number is evaluated in that holder's context, so a pack may scale the
        // gesture by the person making it.
        return ArtifactService.holdTicks(this.artifact, FormulaContext.of(holder));
    }

    @Override
    public boolean claims(LivingEntity holder, Provider registries, ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (!ArtifactService.hasOwner(stack))
            // Somebody has to be the first: the gesture on offer is the claim itself, and it is the same for
            // every reader, so the price and the condition are settled when the hold finishes rather than here.
            return true;
        // An artifact that belongs to somebody is only held down by its owner, and only while it can still take
        // aura. Both answers live on the stack and the definition, so the client and the server agree.
        return ArtifactService.isOwner(stack, holder.getUUID())
                && ArtifactService.hasRoom(registries, stack, FormulaContext.of(holder));
    }

    @Override
    public ItemUseAnimation holdAnimation() {
        return HOLD_ANIMATION;
    }

    @Override
    public Holder<SoundEvent> holdSound() {
        return HOLD_SOUND;
    }
}
