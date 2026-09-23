package com.iafenvoy.mxt.data.item;

import com.iafenvoy.mxt.data.DescribedEntry;
import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.data.cultivation.Technique;
import com.iafenvoy.mxt.data.quality.ItemQuality;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemUseAnimation;

import java.util.List;
import java.util.Optional;

/**
 * How one technique is read: the length, pose and sound of the gesture, the quality its carrier shows and the
 * conditions that gate an attempt. Which technique a stack teaches is the stack's own {@code mxt:technique}
 * component, so a technique with no declaration of its own still reads - it reads with the defaults
 * {@link #defaults(Holder)} builds. {@code carrier_item} names the item the mod generates for the picker and the
 * creative tab, and absent means the jade slip.
 */
public record TechniqueBinding(Holder<Technique> technique, Optional<Item> carrierItem,
                               Optional<TagKey<ItemQuality>> qualityGroup,
                               List<DescribedEntry<EntityCondition>> conditions,
                               int learnTime, ItemUseAnimation holdAnimation,
                               Holder<SoundEvent> holdSound) {
    public static final Codec<TechniqueBinding> CODEC = RecordCodecBuilder.<TechniqueBinding>create(i -> i.group(
            Technique.CODEC.fieldOf("technique").forGetter(TechniqueBinding::technique),
            BuiltInRegistries.ITEM.byNameCodec().optionalFieldOf("carrier_item").forGetter(TechniqueBinding::carrierItem),
            TagKey.hashedCodec(MxtResourceKeys.ITEM_QUALITY).optionalFieldOf("quality_group").forGetter(TechniqueBinding::qualityGroup),
            DescribedEntry.codec(EntityCondition.CODEC, "condition").listOf().optionalFieldOf("conditions", List.of()).forGetter(TechniqueBinding::conditions),
            // Bounded because the value is handed to the use cycle, where an enormous duration would leave
            // the player holding the item forever with no way to tell it apart from a bug.
            Codec.intRange(0, 72_000).optionalFieldOf("learn_time", HoldBinding.NO_HOLD).forGetter(TechniqueBinding::learnTime),
            ItemUseAnimation.CODEC.optionalFieldOf("hold_animation", HoldBinding.DEFAULT_HOLD_ANIMATION).forGetter(TechniqueBinding::holdAnimation),
            SoundEvent.CODEC.optionalFieldOf("hold_sound", HoldBinding.DEFAULT_HOLD_SOUND).forGetter(TechniqueBinding::holdSound)
    ).apply(i, TechniqueBinding::new)).validate(TechniqueBinding::validate);

    public static TechniqueBinding defaults(Holder<Technique> technique) {
        return new TechniqueBinding(technique, Optional.empty(), Optional.empty(), List.of(),
                HoldBinding.NO_HOLD, HoldBinding.DEFAULT_HOLD_ANIMATION, HoldBinding.DEFAULT_HOLD_SOUND);
    }

    private static DataResult<TechniqueBinding> validate(TechniqueBinding binding) {
        return HoldBinding.validate(binding.holdAnimation(), binding.holdSound(), binding.requiresHold())
                .map(ignored -> binding);
    }

    public boolean requiresHold() {
        return this.learnTime > HoldBinding.NO_HOLD;
    }
}
