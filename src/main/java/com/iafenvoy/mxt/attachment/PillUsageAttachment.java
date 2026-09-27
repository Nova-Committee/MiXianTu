package com.iafenvoy.mxt.attachment;

import com.iafenvoy.mxt.data.item.PillBinding;
import com.iafenvoy.mxt.util.HolderHelper;
import com.iafenvoy.mxt.util.ShouldSyncAttachment;
import com.iafenvoy.mxt.util.codec.CollectionCodecs;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Uses and cooldown expiry for each pill binding, keyed by the binding holder rather than the carrier item.
 * Cooldown expiry is an overworld game time. Counts are not cleared by death, dimension change or logout.
 */
public final class PillUsageAttachment extends ShouldSyncAttachment {
    public static final MapCodec<PillUsageAttachment> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            CollectionCodecs.map(PillBinding.CODEC, Dose.CODEC).lenientOptionalFieldOf("entries", Map.of())
                    .forGetter(PillUsageAttachment::entries)
    ).apply(i, PillUsageAttachment::new));
    private final Map<Holder<PillBinding>, Dose> entries;

    public PillUsageAttachment() {
        this(Map.of());
    }

    private PillUsageAttachment(Map<Holder<PillBinding>, Dose> entries) {
        this.entries = new LinkedHashMap<>(entries);
    }

    public Map<Holder<PillBinding>, Dose> entries() {
        return this.entries;
    }

    public int uses(Holder<PillBinding> pill) {
        Dose dose = this.dose(pill);
        return dose == null ? 0 : dose.uses();
    }

    public long cooldownUntil(Holder<PillBinding> pill) {
        Dose dose = this.dose(pill);
        return dose == null ? 0L : dose.cooldownUntil();
    }

    public boolean onCooldown(Holder<PillBinding> pill, long gameTime) {
        return this.cooldownUntil(pill) > gameTime;
    }

    // Holder identity is not stable across a reload, so a saved key is matched by id when the instance differs.
    public Dose dose(Holder<PillBinding> pill) {
        Dose direct = this.entries.get(pill);
        if (direct != null) return direct;
        Identifier id = HolderHelper.idOrNull(pill);
        if (id == null) return null;
        for (Map.Entry<Holder<PillBinding>, Dose> entry : this.entries.entrySet())
            if (entry.getKey().is(id)) return entry.getValue();
        return null;
    }

    public void record(Holder<PillBinding> pill, int uses, long cooldownUntil) {
        Identifier id = HolderHelper.idOrNull(pill);
        if (id != null) this.entries.keySet().removeIf(key -> key != pill && key.is(id));
        this.entries.put(pill, new Dose(Math.max(0, uses), cooldownUntil));
        this.markDirty();
    }

    public record Dose(int uses, long cooldownUntil) {
        public static final Codec<Dose> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.lenientOptionalFieldOf("uses", 0).forGetter(Dose::uses),
                Codec.LONG.lenientOptionalFieldOf("cooldown_until", 0L).forGetter(Dose::cooldownUntil)
        ).apply(i, Dose::new));

        public Dose {
            uses = Math.max(0, uses);
        }
    }
}
