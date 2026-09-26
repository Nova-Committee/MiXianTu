package com.iafenvoy.mxt.runtime.spirit;

import com.iafenvoy.mxt.api.UseItemAuraAccess;
import com.iafenvoy.mxt.data.item.HoldBinding;
import com.iafenvoy.mxt.runtime.spirit.SpiritChargeService.Charge;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;

import java.util.List;

/**
 * The hold that pours aura into an item: every stack implementing {@link UseItemAuraAccess} is driven by
 * holding it down, and what the gesture does is {@link SpiritChargeService}'s. A stack no {@code item_aura}
 * definition describes, or one already filled, answers {@code NO_HOLD} and is left alone.
 * <p>
 * Its length is the stack's rather than the item's, hence the item-level duration is {@code NO_HOLD} while
 * {@link #requiresHold} is stated outright.
 */
public record SpiritChargeHold() implements HoldBinding {
    public static final SpiritChargeHold INSTANCE = new SpiritChargeHold();

    // BLOCK holds the item up in both hands, and is not the pose the reading gesture uses.
    public static final ItemUseAnimation CHARGE_ANIMATION = ItemUseAnimation.BLOCK;

    // Vanilla plays a hold's sound every four ticks, so this has to be short or it piles up into a drone.
    public static final Holder<SoundEvent> CHARGE_SOUND = BuiltInRegistries.SOUND_EVENT.wrapAsHolder(SoundEvents.AMETHYST_BLOCK_CHIME);

    @Override
    public List<Entry> entries() {
        return List.of(SpiritStorageEntry.INSTANCE);
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
        Charge charge = SpiritChargeService.resolve(registries, stack);
        return charge == null ? NO_HOLD : charge.holdTicks();
    }

    @Override
    public ItemUseAnimation holdAnimation() {
        return CHARGE_ANIMATION;
    }

    @Override
    public Holder<SoundEvent> holdSound() {
        return CHARGE_SOUND;
    }
}
