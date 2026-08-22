package com.iafenvoy.mxt.attachment;

import com.iafenvoy.mxt.data.curse.Curse;
import com.iafenvoy.mxt.util.codec.CollectionCodecs;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Persistent curse instances only; definitions are looked up from the reloadable curse registry.
 */
public final class CurseHolderComponent {
    public static final MapCodec<CurseHolderComponent> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            CollectionCodecs.map(Curse.CODEC, State.CODEC).optionalFieldOf("instances", Map.of()).forGetter(CurseHolderComponent::instances)
    ).apply(i, CurseHolderComponent::new));
    private final Map<Holder<Curse>, State> instances;

    public CurseHolderComponent() {
        this(Map.of());
    }

    private CurseHolderComponent(Map<Holder<Curse>, State> instances) {
        this.instances = new LinkedHashMap<>(instances);
    }

    public Map<Holder<Curse>, State> instances() {
        return this.instances;
    }

    public void put(Holder<Curse> curse, State state) {
        this.instances.put(curse, state);
    }

    public State remove(Holder<Curse> curse) {
        return this.instances.remove(curse);
    }

    public void replace(Map<Holder<Curse>, State> values) {
        this.instances.clear();
        this.instances.putAll(values);
    }

    public void markUnknown(Holder<Curse> curse) {
        State state = this.instances.get(curse);
        if (state != null && !state.unknownDefinition()) this.instances.put(curse, state.markedUnknown());
    }

    public void markKnown(Holder<Curse> curse) {
        State state = this.instances.get(curse);
        if (state != null) this.instances.put(curse, state.markedKnown());
    }

    public record State(int stacks, long appliedAt, long expiresAt, String source, Map<String, String> componentState,
                        boolean unknownDefinition) {
        public static final Codec<State> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.intRange(1, 256).fieldOf("stacks").forGetter(State::stacks), Codec.LONG.fieldOf("applied_at").forGetter(State::appliedAt),
                Codec.LONG.fieldOf("expires_at").forGetter(State::expiresAt), Codec.STRING.optionalFieldOf("source", "unknown").forGetter(State::source),
                CollectionCodecs.map(Codec.STRING, Codec.STRING).optionalFieldOf("component_state", Map.of()).forGetter(State::componentState),
                Codec.BOOL.optionalFieldOf("unknown_definition", false).forGetter(State::unknownDefinition)
        ).apply(i, State::new));

        public State(int stacks, long appliedAt, long expiresAt, String source) {
            this(stacks, appliedAt, expiresAt, source, Map.of(), false);
        }

        public State {
            componentState = new LinkedHashMap<>(componentState);
            if (source == null || source.isBlank())
                throw new IllegalArgumentException("Invalid curse state");
        }

        public boolean expiredAt(long gameTime) {
            return this.expiresAt >= 0L && gameTime >= this.expiresAt;
        }

        public State markedKnown() {
            return new State(this.stacks, this.appliedAt, this.expiresAt, this.source, this.componentState, false);
        }

        public State markedUnknown() {
            return new State(this.stacks, this.appliedAt, this.expiresAt, this.source, this.componentState, true);
        }
    }
}
