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
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemUseAnimation;

import java.util.List;
import java.util.Optional;

/**
 * Binds an existing physical item to a learnable cultivation technique. {@code learn_time} is the ticks the
 * item must be held down before the technique is learned; absent or zero means one right-click learns
 * immediately, and {@code hold_animation} and {@code hold_sound} only mean anything together with
 * {@code learn_time}.
 * <p>
 * The hold itself is not this module's: it is declared through {@link HoldBinding} and driven by the hold
 * module, which knows nothing about techniques. The names stay in this module's own vocabulary - a reader
 * studies for a {@code learn_time} - and the interface only asks what the gesture needs.
 */
public record TechniqueBinding(List<Entry> entries, Holder<CultivationTechnique> technique,
                               Optional<TagKey<ItemQuality>> qualityGroup,
                               List<DescribedEntry<EntityCondition>> conditions,
                               int learnTime, ItemUseAnimation holdAnimation,
                               Holder<SoundEvent> holdSound) implements ItemMatcher, HoldBinding {
    /**
     * Separate {@link MapCodec}s rather than further components of {@link #CODEC}, because each is checked by
     * {@link HoldBinding#validate} against whether a hold was asked for at all.
     */
    private static final MapCodec<ItemUseAnimation> HOLD_ANIMATION_CODEC =
            ItemUseAnimation.CODEC.optionalFieldOf("hold_animation", DEFAULT_HOLD_ANIMATION);
    private static final MapCodec<Holder<SoundEvent>> HOLD_SOUND_CODEC =
            SoundEvent.CODEC.optionalFieldOf("hold_sound", DEFAULT_HOLD_SOUND);

    public static final Codec<TechniqueBinding> CODEC = RecordCodecBuilder.<TechniqueBinding>create(i -> i.group(
            ENTRIES_CODEC.fieldOf("items").forGetter(TechniqueBinding::entries),
            CultivationTechnique.CODEC.fieldOf("technique").forGetter(TechniqueBinding::technique),
            TagKey.hashedCodec(MxtResourceKeys.ITEM_QUALITY).optionalFieldOf("quality_group").forGetter(TechniqueBinding::qualityGroup),
            DescribedEntry.codec(EntityCondition.CODEC, "condition").listOf().optionalFieldOf("conditions", List.of()).forGetter(TechniqueBinding::conditions),
            // Bounded because the value is handed to the use cycle, where an enormous duration would leave
            // the player holding the item forever with no way to tell it apart from a bug.
            Codec.intRange(0, 72_000).optionalFieldOf("learn_time", NO_HOLD).forGetter(TechniqueBinding::learnTime),
            HOLD_ANIMATION_CODEC.forGetter(TechniqueBinding::holdAnimation),
            HOLD_SOUND_CODEC.forGetter(TechniqueBinding::holdSound)
    ).apply(i, TechniqueBinding::new)).validate(TechniqueBinding::validate);

    private static DataResult<TechniqueBinding> validate(TechniqueBinding binding) {
        // The gesture's own validation is shared, so this module only has to keep the result.
        return HoldBinding.validate(binding).map(ignored -> binding);
    }

    /**
     * What this module calls the hold's length. The interface asks in gesture terms because it serves every
     * module that declares one; the file says {@code learn_time} because studying is what this module's holds
     * are for.
     */
    @Override
    public int holdTicks() {
        return this.learnTime;
    }
}
