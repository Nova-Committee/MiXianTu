package com.iafenvoy.mxt.runtime.resource;

import com.iafenvoy.mxt.data.item.HoldBinding;
import com.iafenvoy.mxt.item.SpiritVesselItem;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;

import java.util.List;

/**
 * The hold that pours a vessel out: every stack carrying a resource container is driven by holding it down, and what
 * the gesture does is {@link VesselPourService}'s.
 *
 * <p>Its length comes from the stack - a vessel with nothing inside asks for no hold at all, which leaves that click
 * to the item - and it is sized by the cap rather than by what is inside, because the length is read on every tick
 * and a number that shrank as the vessel emptied would take the sound's cadence with it.
 */
public record VesselPourHold() implements HoldBinding {
    public static final VesselPourHold INSTANCE = new VesselPourHold();
    public static final ItemUseAnimation POUR_ANIMATION = ItemUseAnimation.BUNDLE;
    public static final Holder<SoundEvent> POUR_SOUND = BuiltInRegistries.SOUND_EVENT.wrapAsHolder(SoundEvents.BOTTLE_EMPTY);
    // The charge gesture's cap, for its reason: a hold nobody would ever finish stops and is repeated instead.
    public static final int MAX_HOLD_TICKS = 200;

    @Override
    public List<Entry> entries() {
        return List.of(ResourceContainerEntry.INSTANCE);
    }

    @Override
    public int priority() {
        return DEFAULT_PRIORITY;
    }

    @Override
    public int holdTicks() {
        return NO_HOLD;
    }

    @Override
    public boolean requiresHold() {
        return true;
    }

    @Override
    public int holdTicks(Provider registries, ItemStack stack) {
        double stored = SpiritVesselItem.totalStored(stack);
        if (stored <= 0.0D) return NO_HOLD;
        // A vessel nobody wrote a cap for still pours, sized by what is inside it.
        double size = Math.max(SpiritVesselItem.maxCapacity(stack), stored);
        return Mth.clamp((int) Math.ceil(size / SpiritVesselItem.POUR_PER_TICK), 1, MAX_HOLD_TICKS);
    }

    @Override
    public ItemUseAnimation holdAnimation() {
        return POUR_ANIMATION;
    }

    @Override
    public Holder<SoundEvent> holdSound() {
        return POUR_SOUND;
    }
}
