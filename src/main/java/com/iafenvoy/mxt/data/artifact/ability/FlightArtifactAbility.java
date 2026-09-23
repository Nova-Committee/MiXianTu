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
 * Lets the artifact carry its holder: the speed the flying mount moves at, what a tick of riding costs, and how the
 * mount is drawn. Also a {@link ToggableArtifactAbility}, so mounting and dismounting is a wheel switch; the state it
 * reports is the mount up <em>now</em>, read from the same attachment the flight controller writes.
 */
public record FlightArtifactAbility(NumberProvider speed, List<ResourceCost> costs,
                                    FlightDisplay display) implements ToggableArtifactAbility {
    public static final String KEY = "flight";
    public static final MapCodec<FlightArtifactAbility> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            NumberProvider.CODEC.fieldOf("speed").forGetter(FlightArtifactAbility::speed),
            ResourceCost.LIST_CODEC.optionalFieldOf("costs", List.of()).forGetter(FlightArtifactAbility::costs),
            FlightDisplay.CODEC.optionalFieldOf("display", FlightDisplay.DEFAULT).forGetter(FlightArtifactAbility::display)
    ).apply(i, FlightArtifactAbility::new));

    @Override
    public MapCodec<FlightArtifactAbility> codec() {
        return CODEC;
    }

    @Override
    public String key() {
        return KEY;
    }

    // A second flying sword in the bag must not read as "on" while the first is up, hence the archetype check.
    @Override
    public Optional<Boolean> state(ArtifactToggleContext context) {
        FlightAttachment data = context.holder().getExistingData(MxtAttachments.FLIGHT).orElse(null);
        if (data == null || !data.active()) return Optional.of(false);
        Identifier self = HolderHelper.idOrNull(context.artifact());
        return Optional.of(self != null && data.archetype().map(HolderHelper::id).filter(self::equals).isPresent());
    }

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
