package com.iafenvoy.mxt.data.item;

import com.iafenvoy.mxt.util.HolderHelper;
import com.iafenvoy.mxt.util.matcher.ItemMatcher;
import com.mojang.serialization.DataResult;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemUseAnimation;

import java.util.List;

/**
 * What the hold module needs to know about an item that is used by holding it down. A module with such items
 * implements this on its own binding record and registers a {@code HoldSource}; from then on the hold module
 * drives the entire gesture - starting it, timing it, the pose, the sound and keeping the item - without ever
 * reading that module's registries.
 * <p>
 * Extending {@link ItemMatcher} is what lets a source hand over matchers and holds in one object: a hold is
 * found by matching an item, the same way every other binding in this mod is.
 * <p>
 * The vocabulary every hold declaration shares lives here rather than in whichever module declared it first, so
 * a second module does not have to restate the whitelist or the defaults. Nothing here is about techniques: the
 * interface is named after the gesture, not after what the gesture is for.
 */
public interface HoldBinding extends ItemMatcher {
    /**
     * How long a hold lasts when a declaration does not say. Absent means "used at once", so this is the value
     * an absent field is not - the constant only names the default.
     */
    int NO_HOLD = 0;

    /**
     * The gesture an undeclared hold plays: raising the item in front of the chest reads as studying it, and it
     * is the only fitting animation with no side effects, see {@link #ALLOWED_ANIMATIONS}.
     */
    ItemUseAnimation DEFAULT_HOLD_ANIMATION = ItemUseAnimation.BLOCK;

    /**
     * What an undeclared hold sounds like: a page turning, which is what reading is. Vanilla's own default for a
     * consumable is the chewing sound it uses for food, which is not.
     */
    Holder<SoundEvent> DEFAULT_HOLD_SOUND = BuiltInRegistries.SOUND_EVENT.wrapAsHolder(SoundEvents.BOOK_PAGE_TURN);

    /**
     * The animations a hold may ask for. Vanilla drives far more than the arm pose from this value and those
     * extras cannot be suppressed, so anything reaching outside the held item is left out: {@code SPYGLASS}
     * locks the field of view, {@code EAT}, {@code DRINK} and {@code SPEAR} skip the arm transform the pose
     * needs, and {@code BOW}, {@code TRIDENT} and {@code CROSSBOW} scale the pose by an absent charge.
     */
    List<ItemUseAnimation> ALLOWED_ANIMATIONS = List.of(
            ItemUseAnimation.BLOCK, ItemUseAnimation.BRUSH, ItemUseAnimation.BUNDLE,
            ItemUseAnimation.NONE, ItemUseAnimation.TOOT_HORN);

    /**
     * The ticks the use cycle lasts.
     */
    int holdTicks();

    /**
     * The pose the use cycle plays.
     */
    ItemUseAnimation holdAnimation();

    /**
     * The sound the hold makes. The reader hears it from their own client's copy of the use component, and the
     * players around them hear it from the server.
     */
    Holder<SoundEvent> holdSound();

    /**
     * Whether this declaration asks for a hold at all. A declaration that does not is used on the click, which
     * the hold module leaves alone.
     */
    default boolean requiresHold() {
        return this.holdTicks() > NO_HOLD;
    }

    /**
     * The checks every hold declaration needs, so a module does not restate them: a pose outside the whitelist
     * is refused, and so is a pose or a sound on a declaration that asks for no hold, because nothing would
     * ever play it. The wording is deliberately generic - the calling module owns the field names a data pack
     * author sees.
     */
    static DataResult<HoldBinding> validate(HoldBinding hold) {
        if (!ALLOWED_ANIMATIONS.contains(hold.holdAnimation()))
            return DataResult.error(() -> "hold animation " + hold.holdAnimation().getSerializedName()
                    + " is not usable here, allowed values are " + ALLOWED_ANIMATIONS.stream()
                    .map(ItemUseAnimation::getSerializedName).toList());
        if (hold.requiresHold()) return DataResult.success(hold);
        if (hold.holdAnimation() != DEFAULT_HOLD_ANIMATION)
            return DataResult.error(() -> "a hold animation on a declaration that asks for no hold would never "
                    + "be played: " + hold.holdAnimation().getSerializedName());
        // Compared by id rather than by holder: a file that writes the default sound out in full is asking for
        // nothing, exactly as writing the default animation is, and it must not be rejected for spelling it.
        if (!HolderHelper.id(hold.holdSound()).equals(HolderHelper.id(DEFAULT_HOLD_SOUND)))
            return DataResult.error(() -> "a hold sound on a declaration that asks for no hold would never be "
                    + "played: " + HolderHelper.id(hold.holdSound()));
        return DataResult.success(hold);
    }
}
