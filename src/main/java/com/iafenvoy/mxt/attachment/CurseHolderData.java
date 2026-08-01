package com.iafenvoy.mxt.attachment;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Persistent curse instances only; definitions are looked up from the reloadable curse registry.
 */
public final class CurseHolderData {
    public static final MapCodec<CurseHolderData> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.unboundedMap(Identifier.CODEC, State.CODEC).optionalFieldOf("instances", Map.of()).forGetter(CurseHolderData::instances)
    ).apply(instance, CurseHolderData::decode));
    public static final Codec<CurseHolderData> CODEC = MAP_CODEC.codec();
    private final Map<Identifier, State> instances;

    public CurseHolderData() {
        this(Map.of());
    }

    private CurseHolderData(Map<Identifier, State> instances) {
        this.instances = new LinkedHashMap<>(instances);
    }

    private static CurseHolderData decode(Map<Identifier, State> instances) {
        return new CurseHolderData(instances);
    }

    public Map<Identifier, State> instances() {
        return Map.copyOf(this.instances);
    }

    public void put(Identifier curse, State state) {
        this.instances.put(curse, state);
    }

    public State remove(Identifier curse) {
        return this.instances.remove(curse);
    }

    public void replace(Map<Identifier, State> values) {
        this.instances.clear();
        this.instances.putAll(values);
    }

    public void markUnknown(Identifier curse) {
        State state = this.instances.get(curse);
        if (state != null && !state.unknownDefinition()) this.instances.put(curse, state.markedUnknown());
    }

    public void markKnown(Identifier curse) {
        State state = this.instances.get(curse);
        if (state != null) this.instances.put(curse, state.markedKnown());
    }

    public record State(int stacks, long appliedAt, long expiresAt, String source,
                        Map<String, String> componentState, boolean unknownDefinition) {
        public static final Codec<State> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.intRange(1, 256).fieldOf("stacks").forGetter(State::stacks), Codec.LONG.fieldOf("applied_at").forGetter(State::appliedAt),
                Codec.LONG.fieldOf("expires_at").forGetter(State::expiresAt), Codec.STRING.optionalFieldOf("source", "unknown").forGetter(State::source),
                Codec.unboundedMap(Codec.STRING, Codec.STRING).optionalFieldOf("component_state", Map.of()).forGetter(State::componentState),
                Codec.BOOL.optionalFieldOf("unknown_definition", false).forGetter(State::unknownDefinition)
        ).apply(instance, State::new));

        public State(int stacks, long appliedAt, long expiresAt, String source) {
            this(stacks, appliedAt, expiresAt, source, Map.of(), false);
        }

        public State {
            componentState = Map.copyOf(componentState);
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
