package com.iafenvoy.mxt.data.artifact.ability;

import com.iafenvoy.mxt.data.action.ItemAction;
import com.iafenvoy.mxt.data.action.NoOpAction;
import com.iafenvoy.mxt.data.resource.ResourceCost;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.iafenvoy.mxt.util.formula.number.Constant;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;

/**
 * What carrying the artifact costs over time: a set of resource prices paid once every {@code interval} ticks
 * while it is held or equipped, and what happens when the holder cannot pay.
 *
 * <p>This is deliberately an entry of {@code abilities} rather than another field of the artifact: a periodic
 * price is something the artifact <em>does</em> to whoever carries it, exactly like granting skills or carrying
 * its holder, and keeping it here means one place decides what an artifact does. {@code interval} is measured
 * from the world clock rather than from the moment the artifact was picked up, so a definition reads the same
 * on every client and needs no per-stack counter: the price falls on every tick whose game time is a multiple
 * of the interval.</p>
 *
 * <p>{@code owner_only} (default true) is who pays: with no owner the artifact has nobody to charge, so an
 * unowned definition is free until it is claimed; with {@code false} the price is owed by whoever carries it,
 * claimed or not.</p>
 *
 * <p>{@code on_fail} is the consequence of a price that could not be paid - one action, run on the carrier with
 * the artifact stack, which is where a backlash, a durability cost or a warning belongs. It defaults to
 * {@link NoOpAction}, so a definition that only wants the price to bite can say nothing at all. Nothing is
 * consumed partially: a tick either pays every price or pays none and runs {@code on_fail}.</p>
 */
public record UpkeepArtifactAbility(List<ResourceCost> costs, NumberProvider interval, ItemAction onFail,
                                    boolean ownerOnly) implements ArtifactAbility {
    /** How often a definition that says nothing is charged: once a second. */
    public static final double DEFAULT_INTERVAL = 20.0D;
    public static final MapCodec<UpkeepArtifactAbility> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            ResourceCost.LIST_CODEC.optionalFieldOf("costs", List.of()).forGetter(UpkeepArtifactAbility::costs),
            NumberProvider.CODEC.optionalFieldOf("interval", new Constant(DEFAULT_INTERVAL)).forGetter(UpkeepArtifactAbility::interval),
            ItemAction.optionalCodec("on_fail").forGetter(UpkeepArtifactAbility::onFail),
            Codec.BOOL.optionalFieldOf("owner_only", true).forGetter(UpkeepArtifactAbility::ownerOnly)
    ).apply(i, UpkeepArtifactAbility::new));

    @Override
    public MapCodec<UpkeepArtifactAbility> codec() {
        return CODEC;
    }
}
