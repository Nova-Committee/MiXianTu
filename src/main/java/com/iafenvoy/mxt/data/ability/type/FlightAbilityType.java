package com.iafenvoy.mxt.data.ability.type;

import com.iafenvoy.mxt.attachment.FlightAttachment;
import com.iafenvoy.mxt.data.ability.AbilityType;
import com.iafenvoy.mxt.data.ability.Togglable;
import com.iafenvoy.mxt.data.ability.ToggleContext;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.runtime.artifact.FlightService;
import com.iafenvoy.mxt.util.HolderHelper;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

/**
 * Lets the ability carry its holder: the speed the flying mount moves at and how the mount is drawn. What a tick of
 * riding costs is the ability's own {@code costs}, and the mount's look is its carrier's item model.
 *
 * <p>Also a {@link Togglable}, so mounting and dismounting is one wheel cell; the state it reports is the mount being
 * up <em>now</em> and belonging to this exact ability, read from the same attachment the flight controller writes.
 */
public record FlightAbilityType(NumberProvider speed, FlightDisplay display) implements AbilityType, Togglable {
    public static final MapCodec<FlightAbilityType> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            NumberProvider.CODEC.fieldOf("speed").forGetter(FlightAbilityType::speed),
            FlightDisplay.CODEC.optionalFieldOf("display", FlightDisplay.DEFAULT).forGetter(FlightAbilityType::display)
    ).apply(i, FlightAbilityType::new));

    @Override
    public MapCodec<FlightAbilityType> codec() {
        return CODEC;
    }

    // A second flying sword in the bag must not read as "on" while the first is up, hence the ability check.
    @Override
    public Optional<Boolean> state(ToggleContext context) {
        FlightAttachment data = context.holder().getExistingData(MxtAttachments.FLIGHT).orElse(null);
        if (data == null || !data.active()) return Optional.of(false);
        return Optional.of(data.archetype().filter(archetype -> archetype.is(HolderHelper.id(context.ability()))).isPresent());
    }

    // Landing is free: whatever the flight cost was paid per tick while it lasted, and charging a price for coming
    // down would leave a player who cannot pay stuck in the air.
    @Override
    public boolean gated(ToggleContext context) {
        return !this.state(context).orElse(false);
    }

    @Override
    public Result activate(ToggleContext context) {
        if (!(context.holder() instanceof ServerPlayer player)) return Result.refused(Failure.UNAVAILABLE);
        if (this.state(context).orElse(false)) {
            FlightService.dismount(player, FlightService.Failure.STOPPED);
            return Result.activated();
        }
        ItemStack carrier = context.carrier();
        if (carrier == null || carrier.isEmpty()) return Result.refused(Failure.NO_CARRIER);
        FlightService.Result mounted = FlightService.mount(player, carrier, context.ability(), context.formula());
        if (mounted.failure() == null) return Result.activated();
        // Each reason a take-off can give is reported as itself: NOT_FLYABLE cannot happen here (the type was just
        // read) and the rest are dismount-time states.
        return Result.refused(switch (mounted.failure()) {
            case NOT_OWNED -> Failure.NOT_OWNED;
            case ALREADY_ACTIVE -> Failure.ALREADY_SET;
            case INVALID_FORMULA -> Failure.INVALID_FORMULA;
            case CANNOT_MOUNT -> Failure.CANNOT_MOUNT;
            default -> Failure.UNAVAILABLE;
        });
    }
}
