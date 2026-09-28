package com.iafenvoy.mxt.attachment;

import com.iafenvoy.mxt.data.aura.Aura;
import com.iafenvoy.mxt.data.cultivation.Cultivation;
import com.iafenvoy.mxt.data.cultivation.RealmStage;
import com.iafenvoy.mxt.util.ShouldSyncAttachment;
import com.iafenvoy.mxt.util.codec.CollectionCodecs;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.*;
import net.minecraft.core.Holder;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Persisted cultivation progress and the currently selected cultivation method. Progress and the current realm are
 * keyed by the cultivation profile, so storing the profile is the whole identity and the stored state cannot
 * duplicate what the data already says.
 */
public final class CultivationAttachment extends ShouldSyncAttachment {
    public static final MapCodec<CultivationAttachment> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            CollectionCodecs.doubleMap(Aura.CODEC).lenientOptionalFieldOf("cultivation_progress", Object2DoubleMaps.emptyMap()).forGetter(CultivationAttachment::cultivationProgresses),
            CollectionCodecs.map(Aura.CODEC, RealmStage.CODEC).lenientOptionalFieldOf("realm_stages", Map.of()).forGetter(CultivationAttachment::realmStages),
            Cultivation.CODEC.lenientOptionalFieldOf("cultivation").forGetter(CultivationAttachment::cultivation),
            Codec.BOOL.lenientOptionalFieldOf("cultivating", false).forGetter(CultivationAttachment::cultivating),
            Codec.LONG.lenientOptionalFieldOf("cultivate_started_at", 0L).forGetter(CultivationAttachment::cultivateStartedAt),
            Codec.LONG.lenientOptionalFieldOf("next_cultivate_tick", 0L).forGetter(CultivationAttachment::nextCultivateTick),
            CollectionCodecs.longMap(Cultivation.CODEC).lenientOptionalFieldOf("cultivate_cooldowns", Object2LongMaps.emptyMap()).forGetter(CultivationAttachment::cultivateCooldowns)
    ).apply(i, CultivationAttachment::new));

    private final Object2DoubleMap<Holder<Aura>> cultivationProgresses;
    private final Map<Holder<Aura>, Holder<RealmStage>> realmStages;
    private Optional<Holder<Cultivation>> cultivation;
    private boolean cultivating;
    private long cultivateStartedAt, nextCultivateTick;
    private final Object2LongMap<Holder<Cultivation>> cultivateCooldowns;

    public CultivationAttachment() {
        this(Object2DoubleMaps.emptyMap(), Map.of(), Optional.empty(), false, 0L, 0L, Object2LongMaps.emptyMap());
    }

    private CultivationAttachment(Object2DoubleMap<Holder<Aura>> cultivationProgresses, Map<Holder<Aura>, Holder<RealmStage>> realmStages,
                                  Optional<Holder<Cultivation>> cultivation, boolean cultivating,
                                  long cultivateStartedAt, long nextCultivateTick,
                                  Map<Holder<Cultivation>, Long> cultivateCooldowns) {
        this.cultivationProgresses = new Object2DoubleOpenHashMap<>(cultivationProgresses);
        this.realmStages = new LinkedHashMap<>(realmStages);
        this.cultivation = cultivation;
        this.cultivating = cultivating;
        this.cultivateStartedAt = cultivateStartedAt;
        this.nextCultivateTick = nextCultivateTick;
        this.cultivateCooldowns = new Object2LongOpenHashMap<>(cultivateCooldowns);
    }

    public Object2DoubleMap<Holder<Aura>> cultivationProgresses() {
        return this.cultivationProgresses;
    }

    public double cultivationProgress(Holder<Aura> aura) {
        return this.cultivationProgresses.getDouble(aura);
    }

    public Map<Holder<Aura>, Holder<RealmStage>> realmStages() {
        return this.realmStages;
    }

    // Null while the chain still stands on its entry stage.
    public @Nullable Holder<RealmStage> realmStage(Holder<Aura> aura) {
        return this.realmStages.get(aura);
    }

    public Optional<Holder<Cultivation>> cultivation() {
        return this.cultivation;
    }

    public boolean cultivating() {
        return this.cultivating;
    }

    public long cultivateStartedAt() {
        return this.cultivateStartedAt;
    }

    public long nextCultivateTick() {
        return this.nextCultivateTick;
    }

    public Object2LongMap<Holder<Cultivation>> cultivateCooldowns() {
        return this.cultivateCooldowns;
    }

    public void setCultivationProgress(Holder<Aura> aura, double value) {
        if (!Double.isFinite(value) || value < 0.0D)
            throw new IllegalArgumentException("Cultivation progress must be finite and non-negative");
        this.cultivationProgresses.put(aura, value);
        this.markDirty();
    }

    public void setRealmStage(Holder<RealmStage> value) {
        if (value == null) return;
        this.realmStages.put(value.value().aura(), value);
        this.markDirty();
    }

    public void setRealmStages(Map<Holder<Aura>, Holder<RealmStage>> values) {
        this.realmStages.clear();
        this.realmStages.putAll(values);
        this.markDirty();
    }

    public void startCultivation(Holder<Cultivation> action, long gameTime, long nextTick) {
        this.cultivation = Optional.of(action);
        this.cultivating = true;
        this.cultivateStartedAt = gameTime;
        this.nextCultivateTick = nextTick;
        this.markDirty();
    }

    public void scheduleCultivateTick(long gameTime) {
        this.nextCultivateTick = gameTime;
        this.markDirty();
    }

    public void stopCultivation(Holder<Cultivation> action, long cooldownUntil) {
        if (this.cultivation.filter(action::equals).isPresent()) this.cultivating = false;
        if (cooldownUntil > 0L) this.cultivateCooldowns.put(action, cooldownUntil);
        this.markDirty();
    }

    public boolean isCultivationOnCooldown(Holder<Cultivation> action, long gameTime) {
        return this.cultivateCooldowns.getOrDefault(action, 0L) > gameTime;
    }

    // Back to a mortal: no progress, no realm, no sitting and no cooldown. The identity half (roots, physiques,
    // techniques) is a different attachment and is not touched here.
    public void resetCultivation() {
        this.cultivationProgresses.clear();
        this.realmStages.clear();
        this.cultivation = Optional.empty();
        this.cultivating = false;
        this.cultivateStartedAt = 0L;
        this.nextCultivateTick = 0L;
        this.cultivateCooldowns.clear();
        this.markDirty();
    }
}
