package com.iafenvoy.mxt.attachment;

import com.iafenvoy.mxt.data.resource.Resource;
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
public final class SpiritBurstCooldownComponent {
    public static final MapCodec<SpiritBurstCooldownComponent> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            CollectionCodecs.longMap(Resource.CODEC).optionalFieldOf("cooldowns", Object2LongMaps.emptyMap())
                    .forGetter(SpiritBurstCooldownComponent::cooldowns)
    ).apply(i, SpiritBurstCooldownComponent::new));

    private final Object2LongMap<Holder<Resource>> cooldowns;

    public SpiritBurstCooldownComponent() {
        this(Object2LongMaps.emptyMap());
    }

    private SpiritBurstCooldownComponent(Object2LongMap<Holder<Resource>> cooldowns) {
        this.cooldowns = new Object2LongOpenHashMap<>(cooldowns);
    }

    public Object2LongMap<Holder<Resource>> cooldowns() {
        return this.cooldowns;
    }

    public boolean isOnCooldown(Holder<Resource> resource, long gameTime) {
        return this.cooldowns.getOrDefault(resource, -1L) > gameTime;
    }

    public void setCooldownUntil(Holder<Resource> resource, long gameTime) {
        this.cooldowns.put(resource, gameTime);
    }

    /**
     * Removes expired entries, mirroring {@code ItemCooldowns.tick()} so long-running servers do
     * not retain a key for every resource a player has ever fired.
     */
    public boolean clearExpired(long gameTime) {
        return this.cooldowns.object2LongEntrySet().removeIf(entry -> entry.getLongValue() <= gameTime);
    }
}
