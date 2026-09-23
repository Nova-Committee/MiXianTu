package com.iafenvoy.mxt.data.artifact.ability;

import com.iafenvoy.mxt.data.action.ItemAction;
import com.iafenvoy.mxt.data.cost.Cost;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.iafenvoy.mxt.util.formula.number.Constant;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;

/**
 * What carrying the artifact costs over time: resource prices paid once every {@code interval} ticks, and what
 * happens when the holder cannot pay. The interval is measured from the world clock, not from pick-up, so the
 * price falls on every tick whose game time is a multiple of it and no per-stack counter is needed. Nothing is
 * consumed partially: a tick either pays every price or pays none and runs {@code on_fail} (default no-op).
 */
public record UpkeepArtifactAbility(List<Cost> costs, NumberProvider interval, ItemAction onFail,
                                    boolean ownerOnly) implements ArtifactAbility {
    public static final double DEFAULT_INTERVAL = 20.0D;
    public static final MapCodec<UpkeepArtifactAbility> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Cost.LIST_CODEC.optionalFieldOf("costs", List.of()).forGetter(UpkeepArtifactAbility::costs),
            NumberProvider.CODEC.optionalFieldOf("interval", new Constant(DEFAULT_INTERVAL)).forGetter(UpkeepArtifactAbility::interval),
            ItemAction.optionalCodec("on_fail").forGetter(UpkeepArtifactAbility::onFail),
            Codec.BOOL.optionalFieldOf("owner_only", true).forGetter(UpkeepArtifactAbility::ownerOnly)
    ).apply(i, UpkeepArtifactAbility::new));

    @Override
    public MapCodec<UpkeepArtifactAbility> codec() {
        return CODEC;
    }
}
