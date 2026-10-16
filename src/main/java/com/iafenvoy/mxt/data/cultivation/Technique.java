package com.iafenvoy.mxt.data.cultivation;

import com.iafenvoy.mxt.api.QualityProvider;
import com.iafenvoy.mxt.data.AttributeEntry;
import com.iafenvoy.mxt.data.IconReference;
import com.iafenvoy.mxt.data.ability.Ability;
import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.data.item.HoldBinding;
import com.iafenvoy.mxt.data.progression.Progression;
import com.iafenvoy.mxt.data.progression.ProgressionConfig;
import com.iafenvoy.mxt.data.progression.ProgressionOwner;
import com.iafenvoy.mxt.data.quality.ItemQuality;
import com.iafenvoy.mxt.data.resource.Resource;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.util.codec.RegistryCodecs;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.iafenvoy.mxt.util.formula.number.Constant;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryFixedCodec;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.TagKey;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.ItemUseAnimation;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A learnable technique grants named abilities and cultivation modifiers, unconditionally
 * ({@code granted_abilities}) or by mastery ({@code configuration}), whose levels come from a shared
 * {@link Progression} chain. {@code default_level} is the chain entry point and is mandatory as soon as any level
 * is configured; {@code mastery_resource} names the stored value that measures mastery. {@code quality} is the
 * technique's own tier, which is what a manual made for it starts on. Manuals default to one shared built-in
 * carrier, so that item alone cannot say which tier applies. {@code primary_color} and {@code secondary_color} are
 * the two colours the built-in book is tinted with, one per texture layer. {@code learn_time} is how long a read of
 * this technique lasts, as a formula over the reader, with {@code hold_animation} and {@code hold_sound} describing
 * that hold; the {@code mxt:technique_reading} component overrides the three field by field on one stack.
 */
public record Technique(Optional<Holder<ItemQuality>> quality, Optional<IconReference> icon, int primaryColor,
                        int secondaryColor, EntityCondition learnCondition, NumberProvider learnTime,
                        ItemUseAnimation holdAnimation, Holder<SoundEvent> holdSound, List<Identifier> exclusiveTags,
                        NumberProvider cultivationModifier, List<AttributeEntry> passiveModifiers,
                        List<Either<Holder<Ability>, TagKey<Ability>>> grantedAbilities,
                        Optional<Holder<Progression>> defaultLevel, Optional<Holder<Resource>> masteryResource,
                        Map<Holder<Progression>, ProgressionConfig> configuration) implements ProgressionOwner, QualityProvider {
    public static final int DEFAULT_PRIMARY_COLOR = 0x0E54A0, DEFAULT_SECONDARY_COLOR = 0xE4E8EB;
    public static final Codec<Holder<Technique>> CODEC = RegistryFixedCodec.create(MxtResourceKeys.TECHNIQUE);
    public static final Codec<Technique> DIRECT_CODEC = RecordCodecBuilder.<Technique>create(i -> i.group(
            ItemQuality.CODEC.optionalFieldOf("quality").forGetter(Technique::quality),
            IconReference.CODEC.optionalFieldOf("icon").forGetter(Technique::icon),
            ExtraCodecs.STRING_RGB_COLOR.optionalFieldOf("primary_color", DEFAULT_PRIMARY_COLOR).forGetter(Technique::primaryColor),
            ExtraCodecs.STRING_RGB_COLOR.optionalFieldOf("secondary_color", DEFAULT_SECONDARY_COLOR).forGetter(Technique::secondaryColor),
            EntityCondition.optionalCodec("learn_condition").forGetter(Technique::learnCondition),
            // A formula rather than a number: the length may depend on the reader, and it is read again on every tick
            // of the hold. Zero means this technique is learned on the first click, and nothing caps it.
            NumberProvider.CODEC.optionalFieldOf("learn_time", new Constant(HoldBinding.NO_HOLD)).forGetter(Technique::learnTime),
            ItemUseAnimation.CODEC.optionalFieldOf("hold_animation", HoldBinding.DEFAULT_HOLD_ANIMATION).forGetter(Technique::holdAnimation),
            SoundEvent.CODEC.optionalFieldOf("hold_sound", HoldBinding.DEFAULT_HOLD_SOUND).forGetter(Technique::holdSound),
            Identifier.CODEC.listOf().optionalFieldOf("exclusive_tags", List.of()).forGetter(Technique::exclusiveTags),
            NumberProvider.CODEC.optionalFieldOf("cultivation_modifier", new Constant(1.0D)).forGetter(Technique::cultivationModifier),
            AttributeEntry.CODEC.listOf().optionalFieldOf("passive_modifiers", List.of()).forGetter(Technique::passiveModifiers),
            RegistryCodecs.holderOrTagList(MxtResourceKeys.ABILITY).optionalFieldOf("granted_abilities", List.of()).forGetter(Technique::grantedAbilities),
            Progression.CODEC.optionalFieldOf("default_level").forGetter(Technique::defaultLevel),
            Resource.CODEC.optionalFieldOf("mastery_resource").forGetter(Technique::masteryResource),
            Codec.unboundedMap(Progression.CODEC, ProgressionConfig.CODEC).optionalFieldOf("configuration", Map.of()).forGetter(Technique::configuration)
    ).apply(i, Technique::new)).validate(Technique::validate);

    private static DataResult<Technique> validate(Technique technique) {
        if (technique.defaultLevel().isEmpty() && !technique.configuration().isEmpty())
            return DataResult.error(() -> "configuration needs default_level to name the progression chain it belongs to");
        if (technique.defaultLevel().isEmpty() && technique.masteryResource().isPresent())
            return DataResult.error(() -> "mastery_resource needs default_level to name the progression chain it measures");
        // A pose outside the hold's whitelist is refused here, where the rest of a read is declared too; a pose
        // written without a learn_time is never played and is not an error.
        return HoldBinding.validate(technique.holdAnimation()).map(ignored -> technique);
    }

    @Override
    public Optional<Holder<Progression>> entryLevel() {
        return this.defaultLevel;
    }

    @Override
    public Map<Holder<Progression>, ProgressionConfig> levels() {
        return this.configuration;
    }
}
