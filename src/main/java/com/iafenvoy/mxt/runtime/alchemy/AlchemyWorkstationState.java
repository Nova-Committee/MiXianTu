package com.iafenvoy.mxt.runtime.alchemy;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Optional;

/**
 * Process state only. Part block entities own the real items; the heat source is a block in the world.
 */
public final class AlchemyWorkstationState {
    public static final Codec<AlchemyWorkstationState> CODEC = RecordCodecBuilder.create(i -> i.group(
            AlchemySession.Snapshot.CODEC.lenientOptionalFieldOf("session").forGetter(AlchemyWorkstationState::snapshot),
            Codec.DOUBLE.lenientOptionalFieldOf("temperature", 0.0D).forGetter(AlchemyWorkstationState::temperature),
            Codec.DOUBLE.lenientOptionalFieldOf("target_temperature", 0.0D).forGetter(AlchemyWorkstationState::targetTemperature)
    ).apply(i, AlchemyWorkstationState::new));

    private AlchemySession session;
    private double temperature;
    private double targetTemperature;

    public AlchemyWorkstationState() {
    }

    private AlchemyWorkstationState(Optional<AlchemySession.Snapshot> session, double temperature, double targetTemperature) {
        this.session = session.map(AlchemySession::restore).orElse(null);
        this.temperature = finite(temperature);
        this.targetTemperature = finite(targetTemperature);
    }

    public Optional<AlchemySession.Snapshot> snapshot() {
        return this.session == null ? Optional.empty() : Optional.of(this.session.snapshot());
    }

    public Optional<AlchemySession> session() {
        return Optional.ofNullable(this.session);
    }

    public AlchemyPhase phase() {
        return this.session == null ? AlchemyPhase.IDLE : this.session.phase();
    }

    public boolean active() {
        AlchemyPhase phase = this.phase();
        return phase == AlchemyPhase.WARMING || phase == AlchemyPhase.RUNNING;
    }

    public boolean busy() {
        return this.session != null && !this.session.settled();
    }

    public void begin(AlchemySession session) {
        this.session = session;
    }

    public void clearSession() {
        this.session = null;
    }

    public double temperature() {
        return this.temperature;
    }

    public void setTemperature(double temperature) {
        this.temperature = finite(temperature);
    }

    public double targetTemperature() {
        return this.targetTemperature;
    }

    public void setTargetTemperature(double temperature) {
        this.targetTemperature = finite(temperature);
    }

    private static double finite(double value) {
        return Double.isFinite(value) ? value : 0.0D;
    }
}
