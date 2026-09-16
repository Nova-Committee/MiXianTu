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
 * Binds an existing physical item to a learnable cultivation technique. {@code learn_time} is the ticks the
 * item must be held down before the technique is learned; absent or zero means one right-click learns
 * immediately, and {@code hold_animation} only means anything together with {@code learn_time}.
 */
public record TechniqueBinding(List<Entry> entries, Holder<CultivationTechnique> technique,
                               Optional<TagKey<ItemQuality>> qualityGroup,
                               List<DescribedEntry<EntityCondition>> conditions,
                               int learnTime, ItemUseAnimation holdAnimation) implements ItemMatcher {
    /**
     * How long a hold-to-learn item must be held when {@code learn_time} is not declared. Absent means
     * "learn at once" rather than this value; the constant only names the default.
     */
    public static final int NO_HOLD = 0;

    /**
     * The gesture an undeclared hold plays: raising the item in front of the chest reads as studying it, and
     * it is the only fitting animation with no side effects, see {@link #ALLOWED_ANIMATIONS}.
     */
    public static final ItemUseAnimation DEFAULT_HOLD_ANIMATION = ItemUseAnimation.BLOCK;

    /**
     * The animations a hold may ask for. Vanilla drives far more than the arm pose from this value and those
     * extras cannot be suppressed, so anything reaching outside the held item is left out: {@code SPYGLASS}
     * locks the field of view, {@code EAT}, {@code DRINK} and {@code SPEAR} skip the arm transform the pose
     * needs, and {@code BOW}, {@code TRIDENT} and {@code CROSSBOW} scale the pose by an absent charge.
     */
    public static final List<ItemUseAnimation> ALLOWED_ANIMATIONS = List.of(
            ItemUseAnimation.BLOCK, ItemUseAnimation.BRUSH, ItemUseAnimation.BUNDLE,
            ItemUseAnimation.NONE, ItemUseAnimation.TOOT_HORN);

    /**
     * A separate {@link MapCodec} rather than a sixth component of {@link #CODEC}, because the two fields are
     * checked against each other in {@link #validate}.
     */
    private static final MapCodec<ItemUseAnimation> HOLD_ANIMATION_CODEC =
            ItemUseAnimation.CODEC.optionalFieldOf("hold_animation", DEFAULT_HOLD_ANIMATION);

    public static final Codec<TechniqueBinding> CODEC = RecordCodecBuilder.<TechniqueBinding>create(i -> i.group(
            ENTRIES_CODEC.fieldOf("items").forGetter(TechniqueBinding::entries),
            CultivationTechnique.CODEC.fieldOf("technique").forGetter(TechniqueBinding::technique),
            TagKey.hashedCodec(MxtResourceKeys.ITEM_QUALITY).optionalFieldOf("quality_group").forGetter(TechniqueBinding::qualityGroup),
            DescribedEntry.codec(EntityCondition.CODEC, "condition").listOf().optionalFieldOf("conditions", List.of()).forGetter(TechniqueBinding::conditions),
            // Bounded because the value is handed to the use cycle, where an enormous duration would leave
            // the player holding the item forever with no way to tell it apart from a bug.
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

    public boolean requiresHold() {
        return this.learnTime > NO_HOLD;
    }
}
