package com.iafenvoy.mxt.attachment;

import com.iafenvoy.mxt.util.codec.CollectionCodecs;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongMaps;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import net.minecraft.resources.Identifier;

/**
 * The tick each trigger rule of this actor may next fire at, keyed by the rule's own registry id. Server-owned
 * and never synced: nothing on the client draws a rule's cooldown the way the wheel draws an ability's.
 */
public final class TriggerCooldownAttachment {
    public static final MapCodec<TriggerCooldownAttachment> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            CollectionCodecs.longMap(Identifier.CODEC).lenientOptionalFieldOf("cooldowns", Object2LongMaps.emptyMap())
                    .forGetter(TriggerCooldownAttachment::cooldowns)
    ).apply(i, TriggerCooldownAttachment::new));
    private final Object2LongMap<Identifier> cooldowns;

    public TriggerCooldownAttachment() {
        this(Object2LongMaps.emptyMap());
    }

    private TriggerCooldownAttachment(Object2LongMap<Identifier> cooldowns) {
        this.cooldowns = new Object2LongOpenHashMap<>(cooldowns);
    }

    public Object2LongMap<Identifier> cooldowns() {
        return this.cooldowns;
    }

    public boolean isOnCooldown(Identifier rule, long gameTime) {
        return this.cooldowns.getOrDefault(rule, -1L) > gameTime;
    }

    public void setCooldownUntil(Identifier rule, long until) {
        this.cooldowns.put(rule, until);
    }

    // Mirrors ItemCooldowns.tick(), so a long-running server does not retain a key for every rule ever fired.
    public boolean clearExpired(long gameTime) {
        return this.cooldowns.object2LongEntrySet().removeIf(entry -> entry.getLongValue() <= gameTime);
    }
}
