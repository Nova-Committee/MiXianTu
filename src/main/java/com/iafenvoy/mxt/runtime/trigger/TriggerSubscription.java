package com.iafenvoy.mxt.runtime.trigger;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.data.trigger.Trigger;
import com.iafenvoy.mxt.data.trigger.TriggerSignal;

import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Runtime-only subscription. It is deliberately not a Codec/persistent
 * object; owning modules reconstruct it from their persisted state.
 */
public final class TriggerSubscription {
    public enum State {DISABLED, ARMED, CONSUMED}

    private final UUID owner;
    private final String module;
    private final String identity;
    private final Trigger trigger;
    private final Predicate<TriggerSignal> gate;
    private final Consumer<TriggerSignal> listener;
    private final boolean oneShot;
    private State state = State.ARMED;

    public TriggerSubscription(UUID owner, String module, String identity, Trigger trigger,
                               Predicate<TriggerSignal> gate, Consumer<TriggerSignal> listener,
                               boolean oneShot) {
        this.owner = owner;
        this.module = module;
        this.identity = identity;
        this.trigger = trigger;
        this.gate = gate;
        this.listener = listener;
        this.oneShot = oneShot;
    }

    public UUID owner() {
        return this.owner;
    }

    public String module() {
        return this.module;
    }

    public String identity() {
        return this.identity;
    }

    public Trigger trigger() {
        return this.trigger;
    }

    public State state() {
        return this.state;
    }

    public void disable() {
        this.state = State.DISABLED;
    }

    public void arm() {
        this.state = State.ARMED;
    }

    boolean accepts(TriggerSignal signal) {
        if (this.state != State.ARMED) return false;
        try {
            if (!this.trigger.matches(signal)) return false;
            return this.gate.test(signal);
        } catch (RuntimeException exception) {
            MiXianTu.LOGGER.error("Trigger matcher/gate {} failed for {}", this.identity, signal.type(), exception);
            return false;
        }
    }

    void invoke(TriggerSignal signal) {
        this.listener.accept(signal);
        if (this.oneShot) this.state = State.CONSUMED;
    }
}
