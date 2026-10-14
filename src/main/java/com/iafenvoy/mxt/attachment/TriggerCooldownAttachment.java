package com.iafenvoy.mxt.attachment;

import com.iafenvoy.mxt.data.trigger.TriggerRule;
import com.iafenvoy.mxt.util.codec.CollectionCodecs;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongMaps;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import net.minecraft.core.Holder;

/**
 * The tick each trigger rule of this actor may next fire at, keyed by the rule's own holder. Server-owned and never
 * synced: nothing on the client draws a rule's cooldown the way the wheel draws an ability's. A rule the pack no
 * longer provides is dropped at load, which is also the only cooldown it could have owed.
 */
public final class TriggerCooldownAttachment {
    public static final MapCodec<TriggerCooldownAttachment> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            CollectionCodecs.longMap(TriggerRule.CODEC).lenientOptionalFieldOf("cooldowns", Object2LongMaps.emptyMap()).forGetter(TriggerCooldownAttachment::cooldowns)
    ).apply(i, TriggerCooldownAttachment::new));
    private final Object2LongMap<Holder<TriggerRule>> cooldowns;

    public TriggerCooldownAttachment() {
        this(Object2LongMaps.emptyMap());
    }

    private TriggerCooldownAttachment(Object2LongMap<Holder<TriggerRule>> cooldowns) {
        this.cooldowns = new Object2LongOpenHashMap<>(cooldowns);
    }

    public Object2LongMap<Holder<TriggerRule>> cooldowns() {
        return this.cooldowns;
    }

    public boolean isOnCooldown(Holder<TriggerRule> rule, long gameTime) {
        return this.cooldowns.getOrDefault(rule, -1L) > gameTime;
    }

    public void setCooldownUntil(Holder<TriggerRule> rule, long until) {
        this.cooldowns.put(rule, until);
    }

    // Mirrors ItemCooldowns.tick(), so a long-running server does not retain a key for every rule ever fired.
    public boolean clearExpired(long gameTime) {
        return this.cooldowns.object2LongEntrySet().removeIf(entry -> entry.getLongValue() <= gameTime);
    }
}
