package com.iafenvoy.mxt.data.artifact.ability;

import com.iafenvoy.mxt.data.ability.Ability;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.util.codec.RegistryCodecs;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.tags.TagKey;

import java.util.List;

/**
 * Grants abilities while the artifact is held or equipped.
 *
 * <p>The passive and active registrations read the same field, because the lifecycle is the granted ability's
 * own {@code type}'s business; the two names state the pack's intent, and {@code ServerCache} reports an entry
 * whose ability disagrees with it. Abilities may be written as ids or {@code #tags}.</p>
 */
public record GrantArtifactAbility(Intent intent, List<Either<Holder<Ability>, TagKey<Ability>>> abilities) implements ArtifactAbility {
    public static final MapCodec<GrantArtifactAbility> PASSIVE_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            RegistryCodecs.holderOrTagList(MxtResourceKeys.ABILITY).fieldOf("abilities").forGetter(GrantArtifactAbility::abilities)
    ).apply(i, abilities -> new GrantArtifactAbility(Intent.PASSIVE, abilities)));
    public static final MapCodec<GrantArtifactAbility> ACTIVE_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            RegistryCodecs.holderOrTagList(MxtResourceKeys.ABILITY).fieldOf("abilities").forGetter(GrantArtifactAbility::abilities)
    ).apply(i, abilities -> new GrantArtifactAbility(Intent.ACTIVE, abilities)));

    public GrantArtifactAbility {
        if (abilities.isEmpty())
            throw new IllegalArgumentException("An artifact ability grant must name at least one ability");
    }

    @Override
    public MapCodec<? extends ArtifactAbility> codec() {
        return this.intent == Intent.ACTIVE ? ACTIVE_CODEC : PASSIVE_CODEC;
    }

    /** What the pack meant when it picked one of the two names, which is the only thing telling them apart. */
    public enum Intent {PASSIVE, ACTIVE}
}
