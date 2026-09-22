package com.iafenvoy.mxt.data.item;

import com.iafenvoy.mxt.util.HolderHelper;
import com.iafenvoy.mxt.util.matcher.ItemMatcher;
import com.mojang.serialization.DataResult;
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
 * What the hold module needs about an item used by holding it down. A module implements this on its own binding
 * record and registers a {@code HoldSource}; from then on the hold module drives the entire gesture without ever
 * reading that module's registries. Extending {@link ItemMatcher} is what lets a source hand over matchers and
 * holds in one object.
 */
public interface HoldBinding extends ItemMatcher {
    int NO_HOLD = 0;
    ItemUseAnimation DEFAULT_HOLD_ANIMATION = ItemUseAnimation.BLOCK;
    Holder<SoundEvent> DEFAULT_HOLD_SOUND = BuiltInRegistries.SOUND_EVENT.wrapAsHolder(SoundEvents.BOOK_PAGE_TURN);
    List<ItemUseAnimation> ALLOWED_ANIMATIONS = List.of(ItemUseAnimation.BLOCK, ItemUseAnimation.BRUSH, ItemUseAnimation.BUNDLE, ItemUseAnimation.NONE, ItemUseAnimation.TOOT_HORN);

    // A declaration whose length depends on the stack answers NO_HOLD here and overrides requiresHold() to say
    // outright that it does ask for a hold; its real length is the stack-level holdTicks(Provider, ItemStack).
    int holdTicks();

    // NO_HOLD means "not this stack": nothing is armed for it, and a stack already carrying the component is
    // left alone. Both sides answer off the registry view the declaration was captured from.
    default int holdTicks(Provider registries, ItemStack stack) {
        return this.holdTicks();
    }

    default int holdTicks(LivingEntity holder, Provider registries, ItemStack stack) {
        return this.holdTicks(registries, stack);
    }

    default boolean claims(Provider registries, ItemStack stack) {
        return this.holdTicks(registries, stack) > NO_HOLD;
    }

    // A refusal is not an error and not a message: the click is simply not taken over. Both sides ask it and must
    // agree, so an implementation reads only state the two sides share (synced components and sync registries).
    default boolean claims(LivingEntity holder, Provider registries, ItemStack stack) {
        return this.claims(registries, stack);
    }

    ItemUseAnimation holdAnimation();

    // The reader hears it from their own client's copy of the use component, the players around them from the
    // server.
    Holder<SoundEvent> holdSound();

    default boolean requiresHold() {
        return this.holdTicks() > NO_HOLD;
    }

    // A pose outside ALLOWED_ANIMATIONS is refused, and so is a pose or sound on a declaration that asks for no
    // hold, because nothing would ever play it. The wording is generic: the calling module owns the field names.
    static DataResult<HoldBinding> validate(HoldBinding hold) {
        if (!ALLOWED_ANIMATIONS.contains(hold.holdAnimation()))
            return DataResult.error(() -> "hold animation " + hold.holdAnimation().getSerializedName()
                    + " is not usable here, allowed values are " + ALLOWED_ANIMATIONS.stream()
                    .map(ItemUseAnimation::getSerializedName).toList());
        if (hold.requiresHold()) return DataResult.success(hold);
        if (hold.holdAnimation() != DEFAULT_HOLD_ANIMATION)
            return DataResult.error(() -> "a hold animation on a declaration that asks for no hold would never be played: " + hold.holdAnimation().getSerializedName());
        // Compared by id rather than by holder: a file that writes the default sound out in full is asking for
        // nothing, exactly as writing the default animation is, and it must not be rejected for spelling it.
        if (!HolderHelper.id(hold.holdSound()).equals(HolderHelper.id(DEFAULT_HOLD_SOUND)))
            return DataResult.error(() -> "a hold sound on a declaration that asks for no hold would never be played: " + HolderHelper.id(hold.holdSound()));
        return DataResult.success(hold);
    }
}
