package com.iafenvoy.mxt.runtime.cultivation;

import com.iafenvoy.mxt.data.item.HoldBinding;
import com.iafenvoy.mxt.data.item.TechniqueBinding;
import com.iafenvoy.mxt.runtime.item.ItemBindingService;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;

import java.util.List;

/**
 * Drives the reading gesture for any stack carrying a technique. Which technique the stack teaches is its own
 * component and how reading one feels is that technique's declaration, so nothing here is decided by the item: a
 * stack teaching a technique with no declaration is still read, with the defaults.
 */
public record TechniqueHold() implements HoldBinding {
    public static final TechniqueHold INSTANCE = new TechniqueHold();

    @Override
    public List<Entry> entries() {
        return List.of(TechniqueEntry.INSTANCE);
    }

    // The length is the stack's, so the item-level answer says "not this stack" while requiresHold says otherwise.
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
        return ItemBindingService.technique(registries, stack).map(TechniqueBinding::learnTime).orElse(NO_HOLD);
    }

    @Override
    public ItemUseAnimation holdAnimation(Provider registries, ItemStack stack) {
        return ItemBindingService.technique(registries, stack).map(TechniqueBinding::holdAnimation).orElse(DEFAULT_HOLD_ANIMATION);
    }

    @Override
    public Holder<SoundEvent> holdSound(Provider registries, ItemStack stack) {
        return ItemBindingService.technique(registries, stack).map(TechniqueBinding::holdSound).orElse(DEFAULT_HOLD_SOUND);
    }

    // Asked only where no stack exists, which is the question whether this declaration is a hold at all.
    @Override
    public ItemUseAnimation holdAnimation() {
        return DEFAULT_HOLD_ANIMATION;
    }

    @Override
    public Holder<SoundEvent> holdSound() {
        return DEFAULT_HOLD_SOUND;
    }
}
