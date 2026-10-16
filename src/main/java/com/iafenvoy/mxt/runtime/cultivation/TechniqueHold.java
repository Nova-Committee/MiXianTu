package com.iafenvoy.mxt.runtime.cultivation;

import com.iafenvoy.mxt.data.item.HoldBinding;
import com.iafenvoy.mxt.data.item.TechniqueReading;
import com.iafenvoy.mxt.registry.MxtItems;
import com.iafenvoy.mxt.runtime.item.ItemBindingService;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;

import java.util.List;

/**
 * Drives the reading gesture for a stack carrying a technique, and for the items a declaration claims itself.
 * Which technique a stack teaches is its own component and how long reading one takes is that technique's own
 * declaration, so nothing here is decided by the item; a declaration that claims items is the same gesture over
 * those items.
 */
public record TechniqueHold(List<Entry> entries) implements HoldBinding {
    // The component's own hold: any stack carrying mxt:technique is read.
    public static final TechniqueHold INSTANCE = new TechniqueHold(List.of(TechniqueEntry.INSTANCE));

    @Override
    public int priority() {
        return DEFAULT_PRIORITY;
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
        return ticks(FormulaContext.EMPTY, registries, stack);
    }

    // The length is a formula over the reader, so the holder's own answer is the one the gesture runs on.
    @Override
    public int holdTicks(LivingEntity holder, Provider registries, ItemStack stack) {
        return ticks(FormulaContext.of(holder), registries, stack);
    }

    // Written out rather than left to the interface default, which would ask the holder-less side and could refuse a
    // read the reader is entitled to.
    @Override
    public boolean claims(LivingEntity holder, Provider registries, ItemStack stack) {
        return this.holdTicks(holder, registries, stack) > NO_HOLD;
    }

    private static int ticks(FormulaContext context, Provider registries, ItemStack stack) {
        return ItemBindingService.reading(registries, stack).map(reading -> reading.holdTicks(context)).orElse(NO_HOLD);
    }

    @Override
    public ItemUseAnimation holdAnimation(Provider registries, ItemStack stack) {
        // The built-in book always reads the same way: the pose belongs to that shared item, not to a technique, so
        // a technique's own pose only reaches the manuals that bring an item of their own.
        if (stack.is(MxtItems.TECHNIQUE_BOOK)) return DEFAULT_HOLD_ANIMATION;
        return ItemBindingService.reading(registries, stack).map(TechniqueReading::holdAnimation).orElse(DEFAULT_HOLD_ANIMATION);
    }

    @Override
    public Holder<SoundEvent> holdSound(Provider registries, ItemStack stack) {
        return ItemBindingService.reading(registries, stack).map(TechniqueReading::holdSound).orElse(DEFAULT_HOLD_SOUND);
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
