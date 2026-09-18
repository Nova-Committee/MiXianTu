package com.iafenvoy.mxt.attachment;

import com.iafenvoy.mxt.data.aura.Aura;
import com.iafenvoy.mxt.util.ShouldSyncAttachment;
import com.iafenvoy.mxt.util.codec.CollectionCodecs;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongMaps;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import net.minecraft.core.Holder;

/**
 * Server-authoritative cooldowns for the spirit-burst hotbar. The client receives this
 * attachment only to render the remaining fraction, following vanilla item cooldown semantics.
 */
public final class SpiritBurstCooldownAttachment extends ShouldSyncAttachment {
    public static final MapCodec<SpiritBurstCooldownAttachment> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            CollectionCodecs.longMap(Aura.CODEC).optionalFieldOf("cooldowns", Object2LongMaps.emptyMap())
                    .forGetter(SpiritBurstCooldownAttachment::cooldowns)
    ).apply(i, SpiritBurstCooldownAttachment::new));
    private final Object2LongMap<Holder<Aura>> cooldowns;

    public SpiritBurstCooldownAttachment() {
        this(Object2LongMaps.emptyMap());
    }

    private SpiritBurstCooldownAttachment(Object2LongMap<Holder<Aura>> cooldowns) {
        this.cooldowns = new Object2LongOpenHashMap<>(cooldowns);
    }

    public Object2LongMap<Holder<Aura>> cooldowns() {
        return this.cooldowns;
    }

    public boolean isOnCooldown(Holder<Aura> aura, long gameTime) {
        return this.cooldowns.getOrDefault(aura, -1L) > gameTime;
    }

    public void setCooldownUntil(Holder<Aura> aura, long gameTime) {
        this.cooldowns.put(aura, gameTime);
        this.markDirty();
    }

    /**
     * Removes expired entries, mirroring {@code ItemCooldowns.tick()} so long-running servers do
     * not retain a key for every aura a player has ever fired.
     */
    public boolean clearExpired(long gameTime) {
        boolean changed = this.cooldowns.object2LongEntrySet().removeIf(entry -> entry.getLongValue() <= gameTime);
        if (changed) this.markDirty();
        return changed;
    }
}
