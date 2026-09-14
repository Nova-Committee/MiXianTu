package com.iafenvoy.mxt.data.item;

import com.iafenvoy.mxt.data.DescribedEntry;
import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.data.cultivation.CultivationTechnique;
import com.iafenvoy.mxt.data.quality.ItemQuality;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.util.matcher.ItemMatcher;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemUseAnimation;

import java.util.List;
import java.util.Optional;

/**
 * Binds an existing physical item to a learnable cultivation technique.
 *
 * <p>{@code learn_time} is how long the item must be held down before the technique is learned, in
 * ticks. It is in ticks rather than seconds because that is the unit the use cycle already counts in,
 * so no conversion has to be kept in step. Absent (or zero) keeps the original behaviour: one
 * right-click learns immediately.</p>
 *
 * <p>{@code hold_animation} picks the gesture played while the item is held down. It only means
 * anything together with {@code learn_time}: a binding that teaches on the click never starts a use
 * cycle, so it has no animation to play.</p>
 */
public record TechniqueBinding(List<Entry> entries, Holder<CultivationTechnique> technique,
                               Optional<TagKey<ItemQuality>> qualityGroup,
                               List<DescribedEntry<EntityCondition>> conditions,
                               int learnTime, ItemUseAnimation holdAnimation) implements ItemMatcher {
    /**
     * How long a hold-to-learn item must be held when {@code learn_time} is not declared. Absent means
     * "learn at once" rather than this value, so the constant only exists to name the default.
     */
    public static final int NO_HOLD = 0;

    /**
     * The gesture an undeclared hold plays.
     *
     * <p>Raising the item in front of the chest reads as studying it, and it is also the only fitting
     * animation with no side effects at all - see {@link #ALLOWED_ANIMATIONS}.</p>
     */
    public static final ItemUseAnimation DEFAULT_HOLD_ANIMATION = ItemUseAnimation.BLOCK;

    /**
     * The animations a hold may ask for.
     *
     * <p>Vanilla drives far more than the arm pose from this value, and those extras cannot be
     * suppressed from here, so the ones that reach outside the held item are left out rather than
     * quietly surprising whoever writes a datapack:</p>
     * <ul>
     *   <li>{@code SPYGLASS} is refused because the pose <em>is</em> {@code Player#isScoping}, and that
     *       predicate locks the field of view to {@code 0.1} and drops mouse sensitivity in classes
     *       nothing can reach. It is also the one pose that would render no item at all:
     *       {@code ItemInHandRenderer} skips the whole hand while scoping, so a version with the
     *       scoping suppressed would simply show nothing.</li>
     *   <li>{@code EAT}, {@code DRINK} and {@code SPEAR} advertise
     *       {@code hasCustomArmTransform}, which means the arm transform meant to hold the item up is
     *       skipped on the strength of an arm model these items do not have. {@code SPEAR} additionally
     *       reads the kinetic-hit timer.</li>
     *   <li>{@code BOW}, {@code TRIDENT} and {@code CROSSBOW} draw a projectile the item does not have:
     *       they scale the pose by the charge already built up, so a read would visibly wind up a
     *       nonexistent bow.</li>
     * </ul>
     *
     * <p>What is left is still the whole range of poses that hold an item up and change nothing
     * else.</p>
     */
    public static final List<ItemUseAnimation> ALLOWED_ANIMATIONS = List.of(
            ItemUseAnimation.BLOCK, ItemUseAnimation.BRUSH, ItemUseAnimation.BUNDLE,
            ItemUseAnimation.NONE, ItemUseAnimation.TOOT_HORN);

    /**
     * The animation field on its own.
     *
     * <p>Kept as a separate {@link MapCodec} rather than a sixth component of {@link #CODEC} because
     * the two fields are checked against each other in {@link #validate}, and because a
     * {@code RecordCodecBuilder} overload stops being inferable once this many components are mixed
     * in. It is folded in through {@link #CODEC} below with {@code forGetter} over the record field.</p>
     */
    private static final MapCodec<ItemUseAnimation> HOLD_ANIMATION_CODEC =
            ItemUseAnimation.CODEC.optionalFieldOf("hold_animation", DEFAULT_HOLD_ANIMATION);

    public static final Codec<TechniqueBinding> CODEC = RecordCodecBuilder.<TechniqueBinding>create(i -> i.group(
            ENTRIES_CODEC.fieldOf("items").forGetter(TechniqueBinding::entries),
            CultivationTechnique.CODEC.fieldOf("technique").forGetter(TechniqueBinding::technique),
            TagKey.hashedCodec(MxtResourceKeys.ITEM_QUALITY).optionalFieldOf("quality_group").forGetter(TechniqueBinding::qualityGroup),
            DescribedEntry.codec(EntityCondition.CODEC, "condition").listOf().optionalFieldOf("conditions", List.of()).forGetter(TechniqueBinding::conditions),
            // Bounded because the value is handed to the use cycle, where an enormous duration would
            // simply leave the player holding the item forever with no way to tell it apart from a bug.
            Codec.intRange(0, 72_000).optionalFieldOf("learn_time", NO_HOLD).forGetter(TechniqueBinding::learnTime),
            HOLD_ANIMATION_CODEC.forGetter(TechniqueBinding::holdAnimation)
    ).apply(i, TechniqueBinding::new)).validate(TechniqueBinding::validate);

    private static DataResult<TechniqueBinding> validate(TechniqueBinding binding) {
        if (binding.learnTime <= NO_HOLD && binding.holdAnimation != DEFAULT_HOLD_ANIMATION)
            return DataResult.error(() -> "hold_animation only applies to a binding with a learn_time: "
                    + binding.holdAnimation.getSerializedName() + " would never be played");
        if (!ALLOWED_ANIMATIONS.contains(binding.holdAnimation))
            return DataResult.error(() -> "hold_animation " + binding.holdAnimation.getSerializedName()
                    + " is not usable here, allowed values are " + ALLOWED_ANIMATIONS.stream()
                    .map(ItemUseAnimation::getSerializedName).toList());
        return DataResult.success(binding);
    }

    /**
     * Whether this item has to be held down before it teaches anything.
     */
    public boolean requiresHold() {
        return this.learnTime > NO_HOLD;
    }
}
