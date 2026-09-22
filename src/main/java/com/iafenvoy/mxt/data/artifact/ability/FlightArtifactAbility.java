package com.iafenvoy.mxt.data.artifact.ability;

import com.iafenvoy.mxt.attachment.FlightAttachment;
import com.iafenvoy.mxt.data.resource.ResourceCost;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.runtime.artifact.FlightService;
import com.iafenvoy.mxt.util.HolderHelper;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Optional;

/**
 * Lets the artifact carry its holder: the speed the flying mount moves at, and what a tick of riding costs.
 * {@link com.iafenvoy.mxt.data.artifact.Artifact} refuses a second entry of this kind, so "how fast is this
 * artifact" always has one answer.
 *
 * <p>It is also a {@link ToggableArtifactAbility}, which is what puts it on the wheel: mounting and dismounting is
 * a switch, and a player holding the artifact can throw it from the wheel instead of needing a command. The state
 * it reports is the mount that is up <em>now</em>, read from the same attachment the flight controller writes, so
 * the cell and the sword can never disagree about whether the player is flying.</p>
 */
public record FlightArtifactAbility(NumberProvider speed, List<ResourceCost> costs) implements ToggableArtifactAbility {
    /** The name this capability is addressed by inside its artifact; see {@link #key()}. */
    public static final String KEY = "flight";
    public static final MapCodec<FlightArtifactAbility> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            NumberProvider.CODEC.fieldOf("speed").forGetter(FlightArtifactAbility::speed),
            ResourceCost.LIST_CODEC.optionalFieldOf("costs", List.of()).forGetter(FlightArtifactAbility::costs)
    ).apply(i, FlightArtifactAbility::new));

    @Override
    public MapCodec<FlightArtifactAbility> codec() {
        return CODEC;
    }

    @Override
    public String key() {
        return KEY;
    }

    /**
     * Whether <em>this</em> artifact is the one carrying the holder: a second flying sword in the bag must not
     * read as "on" while the first one is up, which is why the archetype the flight started with is compared.
     */
    @Override
    public Optional<Boolean> state(ArtifactToggleContext context) {
        FlightAttachment data = context.holder().getExistingData(MxtAttachments.FLIGHT).orElse(null);
        if (data == null || !data.active()) return Optional.of(false);
        Identifier self = HolderHelper.idOrNull(context.artifact());
        return Optional.of(self != null && data.archetype().map(HolderHelper::id).filter(self::equals).isPresent());
    }

    /**
     * Mounts or dismounts, through the same controller every other flight entry point uses, so the price, the
     * ownership gate and the attribute bookkeeping stay in one place. Which of the two it is comes from the state
     * this capability already reports, so the press carries no direction.
     */
    @Override
    public Result activate(ArtifactToggleContext context) {
        if (!(context.holder() instanceof ServerPlayer player)) return Result.refused(Failure.UNAVAILABLE);
        if (this.state(context).orElse(false)) {
            FlightService.dismount(player, FlightService.Failure.STOPPED);
            return Result.activated();
        }
        FlightService.Result mounted = FlightService.mount(player, context.stack(), context.artifact(), context.formula());
        if (mounted.failure() == null) return Result.activated();
        return Result.refused(switch (mounted.failure()) {
            case NOT_OWNED -> Failure.NOT_OWNED;
            case ALREADY_ACTIVE -> Failure.ALREADY_SET;
            default -> Failure.UNAVAILABLE;
        });
    }

    @Override
    public Component displayName() {
        return Component.translatable("wheel.mxt.artifact_skill.flight");
    }
}
