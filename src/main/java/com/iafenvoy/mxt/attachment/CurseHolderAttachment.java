package com.iafenvoy.mxt.attachment;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.data.curse.Curse;
import com.iafenvoy.mxt.util.ShouldSyncAttachment;
import com.iafenvoy.mxt.util.SourceLedger;
import com.iafenvoy.mxt.util.codec.CollectionCodecs;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Persistent curse instances only; definitions are looked up from the reloadable curse registry.
 * <p>
 * An instance is two things, kept in one place: the lifecycle payload that belongs to the curse itself - stacks,
 * when it was applied, when it expires - and the {@link SourceLedger} of who keeps it alive. That ledger is the
 * same one ability grants use, so "removing one source cannot remove another source's curse" and "the last source
 * leaving is what removes it" mean exactly what they mean for abilities.
 */
public final class CurseHolderAttachment extends ShouldSyncAttachment {
    public static final MapCodec<CurseHolderAttachment> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            CollectionCodecs.map(Curse.CODEC, State.CODEC).optionalFieldOf("instances", Map.of()).forGetter(CurseHolderAttachment::instances),
            SourceLedger.codec(Curse.CODEC).optionalFieldOf("sources", new SourceLedger<>()).forGetter(CurseHolderAttachment::sources)
    ).apply(i, CurseHolderAttachment::new));
    private final Map<Holder<Curse>, State> instances;
    private final SourceLedger<Holder<Curse>> sources;

    public CurseHolderAttachment() {
        this(Map.of(), new SourceLedger<>());
    }

    private CurseHolderAttachment(Map<Holder<Curse>, State> instances, SourceLedger<Holder<Curse>> sources) {
        this.instances = new LinkedHashMap<>(instances);
        this.sources = sources.copy();
        this.migrateLegacySources();
    }

    public Map<Holder<Curse>, State> instances() {
        return this.instances;
    }

    /**
     * Who keeps each held curse alive.
     */
    public SourceLedger<Holder<Curse>> sources() {
        return this.sources;
    }

    public State remove(Holder<Curse> curse) {
        State state = this.instances.remove(curse);
        if (state != null) {
            this.sources.drop(curse);
            this.markDirty();
        }
        return state;
    }

    public void replace(Map<Holder<Curse>, State> values) {
        this.instances.clear();
        this.instances.putAll(values);
        // A source entry for a curse that is no longer held would claim to keep something alive that is gone.
        for (Holder<Curse> curse : List.copyOf(this.sources.keys()))
            if (!this.instances.containsKey(curse)) this.sources.drop(curse);
        this.markDirty();
    }

    /**
     * Marks an instance whose definition is no longer loaded. Returns whether the flag was not already set.
     */
    public boolean markUnknown(Holder<Curse> curse) {
        State state = this.instances.get(curse);
        if (state == null || state.unknownDefinition()) return false;
        this.instances.put(curse, state.markedUnknown());
        this.markDirty();
        return true;
    }

    public boolean markKnown(Holder<Curse> curse) {
        State state = this.instances.get(curse);
        if (state == null || !state.unknownDefinition()) return false;
        this.instances.put(curse, state.markedKnown());
        this.markDirty();
        return true;
    }

    /**
     * A save written before sources became a set records one free-form string per instance. It is read into the
     * ledger here, so the rest of the runtime only ever sees the shared shape.
     */
    private void migrateLegacySources() {
        this.instances.forEach((curse, state) -> state.legacySource()
                .filter(ignored -> !this.sources.holds(curse))
                .ifPresent(raw -> this.sources.grant(curse, migrate(raw))));
    }

    private static Identifier migrate(String raw) {
        Identifier parsed = Identifier.tryParse(raw);
        if (parsed != null) return parsed;
        String path = raw.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9/._-]", "_");
        return Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "legacy/" + (path.isBlank() ? "unknown" : path));
    }

    /**
     * One applied curse: how many stacks it holds, when it was applied and when it expires. Who keeps it alive is
     * the attachment's source ledger, not part of this payload.
     */
    public record State(int stacks, long appliedAt, long expiresAt, boolean unknownDefinition, Optional<String> legacySource) {
        public static final Codec<State> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.intRange(1, 256).fieldOf("stacks").forGetter(State::stacks),
                Codec.LONG.fieldOf("applied_at").forGetter(State::appliedAt),
                Codec.LONG.fieldOf("expires_at").forGetter(State::expiresAt),
                Codec.BOOL.optionalFieldOf("unknown_definition", false).forGetter(State::unknownDefinition),
                // Read for saves written before the ledger existed; never written back, since the ledger owns it.
                Codec.STRING.optionalFieldOf("source").forGetter(State::legacySource)
        ).apply(i, State::new));

        public State(int stacks, long appliedAt, long expiresAt) {
            this(stacks, appliedAt, expiresAt, false, Optional.empty());
        }

        public State(int stacks, long appliedAt, long expiresAt, boolean unknownDefinition) {
            this(stacks, appliedAt, expiresAt, unknownDefinition, Optional.empty());
        }

        public boolean expiredAt(long gameTime) {
            return this.expiresAt >= 0L && gameTime >= this.expiresAt;
        }

        public State markedKnown() {
            return new State(this.stacks, this.appliedAt, this.expiresAt, false, Optional.empty());
        }

        public State markedUnknown() {
            return new State(this.stacks, this.appliedAt, this.expiresAt, true, Optional.empty());
        }
    }
}
