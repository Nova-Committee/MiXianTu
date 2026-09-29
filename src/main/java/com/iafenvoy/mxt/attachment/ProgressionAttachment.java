package com.iafenvoy.mxt.attachment;

import com.iafenvoy.mxt.data.progression.Progression;
import com.iafenvoy.mxt.util.ShouldSyncAttachment;
import com.iafenvoy.mxt.util.codec.CollectionCodecs;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * How far a body has climbed the progressions it holds, keyed by the owning definition's id. A level is recorded
 * only once it has been reached, so a data pack that moves an owner's entry level moves everyone who never
 * advanced. Nothing here is technique-specific: the key is whoever owns the chain.
 */
public final class ProgressionAttachment extends ShouldSyncAttachment {
    public static final MapCodec<ProgressionAttachment> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            CollectionCodecs.map(Identifier.CODEC, Progression.CODEC).lenientOptionalFieldOf("levels", Map.of()).forGetter(ProgressionAttachment::levels)
    ).apply(i, ProgressionAttachment::new));

    private final Map<Identifier, Holder<Progression>> levels;

    public ProgressionAttachment() {
        this(Map.of());
    }

    private ProgressionAttachment(Map<Identifier, Holder<Progression>> levels) {
        this.levels = new LinkedHashMap<>(levels);
    }

    public Map<Identifier, Holder<Progression>> levels() {
        return this.levels;
    }

    // Null while the holder still stands on that owner's entry level.
    public @Nullable Holder<Progression> level(Identifier owner) {
        return this.levels.get(owner);
    }

    public void setLevel(Identifier owner, Holder<Progression> level) {
        if (owner == null || level == null) return;
        this.levels.put(owner, level);
        this.markDirty();
    }

    public boolean clearLevel(Identifier owner) {
        if (this.levels.remove(owner) == null) return false;
        this.markDirty();
        return true;
    }

    public void clear() {
        if (this.levels.isEmpty()) return;
        this.levels.clear();
        this.markDirty();
    }

    public void setLevels(Map<Identifier, Holder<Progression>> values) {
        this.levels.clear();
        this.levels.putAll(values);
        this.markDirty();
    }
}
