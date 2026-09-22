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
 * The hold that pours aura into an item: every stack that implements {@link UseItemAuraAccess} is driven by
 * holding it down, and what the gesture does is {@link SpiritChargeService}'s.
 * <p>
 * The declaration matches a capability rather than an item, so it is one object for every item that will ever
 * have one - but not every matching stack is a hold, because a gesture that would move nothing is not worth
 * playing: a stack no {@code item_aura} definition describes, or one already filled, answers
 * {@code NO_HOLD} and is left alone. That answer is the same on both sides, since it is read from the synced
 * registries and the synced charge component.
 * <p>
 * Its length is the stack's rather than the item's, which is why the item-level duration here is
 * {@link #NO_HOLD} while {@link #requiresHold} is stated outright: the item asks for a hold, and how long one
 * lasts comes from the stack. The duration is the time a full charge takes, capped, so the pose ends when the
 * work does for everything the cap does not reach.
 */
public record SpiritChargeHold() implements HoldBinding {
    public static final SpiritChargeHold INSTANCE = new SpiritChargeHold();

    /**
     * The pose the pour plays. {@code BLOCK} holds the item up in both hands, which reads as working on it,
     * and it is not the pose the reading gesture uses, so the two are told apart at a glance.
     */
    public static final ItemUseAnimation CHARGE_ANIMATION = ItemUseAnimation.BLOCK;

    /**
     * The sound the pour makes. Vanilla plays a hold's sound every four ticks, so this has to be short: a
     * chime reads as aura arriving and does not pile up, while anything a second long would overlap itself
     * into a drone.
     */
    public static final Holder<SoundEvent> CHARGE_SOUND = BuiltInRegistries.SOUND_EVENT.wrapAsHolder(SoundEvents.AMETHYST_BLOCK_CHIME);

    @Override
    public List<Entry> entries() {
        return List.of(SpiritStorageEntry.INSTANCE);
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
