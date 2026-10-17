package com.iafenvoy.mxt.item;

import com.iafenvoy.mxt.data.item.HoldBinding;
import com.iafenvoy.mxt.registry.MxtItems;
import com.iafenvoy.mxt.util.matcher.builtin.ItemEntry;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;

import java.util.List;

/**
 * The hold that reads a jade slip: every stack of the slip is read by holding it down, and how long that takes is the
 * stack's own {@code mxt:jade_slip_reading} component. What the gesture does is {@link JadeSlipReading}'s.
 * <p>
 * Its length is the stack's rather than the item's, hence the item-level duration is {@code NO_HOLD} while
 * {@link #requiresHold} is stated outright.
 */
public record JadeSlipHold() implements HoldBinding {
    public static final JadeSlipHold INSTANCE = new JadeSlipHold();

    @Override
    public List<Entry> entries() {
        return List.of(new ItemEntry(MxtItems.JADE_SLIP.get()));
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
        return JadeSlipItem.readTime(stack);
    }

    @Override
    public ItemUseAnimation holdAnimation() {
        return DEFAULT_HOLD_ANIMATION;
    }

    @Override
    public Holder<SoundEvent> holdSound() {
        return DEFAULT_HOLD_SOUND;
    }
}
