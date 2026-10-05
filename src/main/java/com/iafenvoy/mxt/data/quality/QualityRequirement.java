package com.iafenvoy.mxt.data.quality;

import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.util.codec.RegistryCodecs;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.tags.TagKey;

import java.util.List;
import java.util.Optional;

/**
 * "Is this stack's tier good enough": a membership list and/or a minimum tier. The list is what
 * {@code mxt:item_quality} always meant; the minimum is answered by position on the chain the server indexed, so it
 * needs registry access and answers no across chains - two chains' positions have nothing to do with each other.
 *
 * <p>Nothing written means "no requirement", which every reader documents as "any tier, including none".
 */
public record QualityRequirement(List<Either<Holder<ItemQuality>, TagKey<ItemQuality>>> qualities,
                                 Optional<Holder<ItemQuality>> minQuality) {
    /**
     * The membership field every adapter writes inline, so its JSON key is stated once. A tag has to be checked
     * through the holder, which is why this is a holder-or-tag list rather than a list of ids.
     */
    public static final MapCodec<List<Either<Holder<ItemQuality>, TagKey<ItemQuality>>>> QUALITIES_FIELD =
            RegistryCodecs.holderOrTagList(MxtResourceKeys.ITEM_QUALITY).optionalFieldOf("quality", List.of());

    /**
     * The minimum field, the same way. Optional rather than a maybe-null holder, because "no minimum" and "a
     * minimum that no longer resolves" have to read the same.
     */
    public static final MapCodec<Optional<Holder<ItemQuality>>> MIN_QUALITY_FIELD =
            ItemQuality.CODEC.optionalFieldOf("min_quality");

    public static final MapCodec<QualityRequirement> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            QUALITIES_FIELD.forGetter(QualityRequirement::qualities),
            MIN_QUALITY_FIELD.forGetter(QualityRequirement::minQuality)
    ).apply(i, QualityRequirement::new));

    public static QualityRequirement of(List<Either<Holder<ItemQuality>, TagKey<ItemQuality>>> qualities) {
        return new QualityRequirement(qualities, Optional.empty());
    }

    // Nothing to ask: every stack passes, including one with no tier at all.
    public boolean isEmpty() {
        return this.qualities.isEmpty() && this.minQuality.isEmpty();
    }

    // The membership half, which needs no registry. The minimum is answered in QualityRequirements, which is where
    // the ladder lives.
    public boolean admits(Holder<ItemQuality> quality) {
        return this.qualities.isEmpty() || RegistryCodecs.matches(this.qualities, quality);
    }
}
