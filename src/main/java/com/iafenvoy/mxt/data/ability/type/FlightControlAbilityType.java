package com.iafenvoy.mxt.data.ability.type;

import com.iafenvoy.mxt.attachment.FlightAttachment;
import com.iafenvoy.mxt.data.ability.*;
import com.iafenvoy.mxt.data.storage.DataStorageCollector;
import com.iafenvoy.mxt.data.storage.builtin.CooldownDataStorage;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.runtime.artifact.FlightService;
import com.iafenvoy.mxt.runtime.resulting.Failure;
import com.iafenvoy.mxt.runtime.resulting.ResultWithData;
import com.iafenvoy.mxt.runtime.resulting.failures.FlightFailure;
import com.iafenvoy.mxt.util.OptionalBoolean;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.iafenvoy.mxt.util.formula.number.Constant;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.StringRepresentable;
import org.jspecify.annotations.NonNull;

import java.util.Locale;
import java.util.Optional;

/**
 * The skill that lets its holder fly an artifact: which hand is searched for a vehicle and how much the holder's own
 * command of the art adds to its speed. This is the pressable half - the wheel draws this entry, and what it summons
 * is data the artifact declares.
 */
public record FlightControlAbilityType(Hand hand, NumberProvider speedMultiplier,
                                       NumberProvider cooldown) implements AbilityType, Togglable, CooldownSource {
    public static final MapCodec<FlightControlAbilityType> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Hand.CODEC.optionalFieldOf("hand", Hand.EITHER).forGetter(FlightControlAbilityType::hand),
            NumberProvider.CODEC.optionalFieldOf("speed_multiplier", new Constant(1.0D)).forGetter(FlightControlAbilityType::speedMultiplier),
            NumberProvider.CODEC.optionalFieldOf("cooldown", new Constant(0.0D)).forGetter(FlightControlAbilityType::cooldown)
    ).apply(i, FlightControlAbilityType::new));

    // Which hand a flight may start from. Either is main hand first, so a pack that wants a sword fought with keeps
    // it free by writing `off`.
    public enum Hand implements StringRepresentable {
        MAIN, OFF, EITHER;

        public static final Codec<Hand> CODEC = StringRepresentable.fromEnum(Hand::values);

        @Override
        public @NonNull String getSerializedName() {
            return this.name().toLowerCase(Locale.ROOT);
        }
    }

    @Override
    public MapCodec<FlightControlAbilityType> codec() {
        return CODEC;
    }

    // Taking off is charged through the shared gate, so the one kind it keeps is the cooldown that gate writes.
    @Override
    public void createComponents(Ability ability, DataStorageCollector collector) {
        AbilityType.super.createComponents(ability, collector);
        collector.add(CooldownDataStorage.INSTANCE);
    }

    // A second artifact that also declares a mount must not read as "on" while another flight is up, hence the skill
    // check: what is flying is this entry, and the vehicle it picked up is the attachment's other half.
    @Override
    public OptionalBoolean state(ToggleContext context) {
        FlightAttachment data = context.holder().getExistingData(MxtAttachments.FLIGHT).orElse(null);
        if (data == null || !data.active()) return OptionalBoolean.of(false);
        return OptionalBoolean.of(data.archetype().filter(context.ability()::equals).isPresent());
    }

    // Landing is free: whatever the flight cost was paid per tick while it lasted, and charging a price for coming
    // down would leave a holder who cannot pay stuck in the air.
    @Override
    public boolean gated(ToggleContext context) {
        return !this.state(context).orElse(false);
    }

    @Override
    public Optional<Failure> canActivate(ToggleContext context) {
        // A press while flying means "put it away", which needs no vehicle to be read first.
        if (this.state(context).orElse(false)) return Optional.empty();
        return Optional.ofNullable(FlightService.canMount(context.holder(), context.ability(), context.formula()));
    }

    @Override
    public PressResult activate(ToggleContext context) {
        if (this.state(context).orElse(false)) {
            FlightService.dismount(context.holder(), FlightFailure.STOPPED);
            return PressResult.activated();
        }
        ResultWithData<FlightService.State> mounted = FlightService.mount(context.holder(), context.ability(), context.formula());
        if (mounted.failure() == null) return PressResult.activated();
        // Each reason a take-off can give is reported as itself; NOT_FLYABLE is the one failure this type cannot
        // produce, since the type was just read.
        return PressResult.refused(mounted.failure());
    }
}
