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
 * The hold one artifact definition declares. Unlike a capability-matched hold, this <em>is</em> the definition:
 * the gesture's length and the price of claiming are declared per artifact, so no second copy of those numbers
 * exists and the declaration is the object the hold module matches.
 *
 * <p>Its answer depends on who holds it: an artifact bound to somebody else does not take the click over at all,
 * and a stack bound to the reader is taken over only while its store can still accept aura, because a gesture
 * that would move nothing is not worth playing.
 */
public record ArtifactHold(Artifact artifact) implements HoldBinding {
    // BLOCK holds the item up in both hands, which reads as working on it rather than as swinging it.
    public static final ItemUseAnimation HOLD_ANIMATION = ItemUseAnimation.BLOCK;

    // Vanilla plays a hold's sound every four ticks, so this has to be short: a beacon blip reads as something
    // taking hold, does not pile up into a drone, and is not the chime the aura pour plays.
    public static final Holder<SoundEvent> HOLD_SOUND = BuiltInRegistries.SOUND_EVENT.wrapAsHolder(SoundEvents.BEACON_POWER_SELECT);

    @Override
    public List<Entry> entries() {
        return this.artifact.items();
    }

    @Override
    public int priority() {
        return this.artifact.priority();
    }

    @Override
    public int holdTicks() {
        // Read where no entity exists: this decides whether the definition is a hold at all, which is the question
        // HoldLookup asks once per pack load.
        return ArtifactService.holdTicks(this.artifact, FormulaContext.EMPTY);
    }

    @Override
    public int holdTicks(LivingEntity holder, Provider registries, ItemStack stack) {
        // With a holder the declared number is evaluated in that holder's context, so a pack may scale the gesture
        // by the person making it.
        return ArtifactService.holdTicks(this.artifact, FormulaContext.of(holder));
    }

    @Override
    public boolean claims(LivingEntity holder, Provider registries, ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (!ArtifactService.hasOwner(stack))
            // Somebody has to be the first: the gesture on offer is the claim itself, and its price and condition
            // are settled when the hold finishes rather than here.
            return true;
        // An artifact that belongs to somebody is held down only by its owner, and only while it can still take
        // aura. Both answers live on the stack and the definition, so client and server agree.
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
