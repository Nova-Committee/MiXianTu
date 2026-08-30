package com.iafenvoy.mxt.attachment;

import com.iafenvoy.mxt.data.cultivation.CultivateAction;
import com.iafenvoy.mxt.data.cultivation.RealmStage;
import com.iafenvoy.mxt.data.resource.Resource;
import com.iafenvoy.mxt.util.ShouldSyncAttachment;
import com.iafenvoy.mxt.util.codec.CollectionCodecs;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.Object2DoubleMap;
import it.unimi.dsi.fastutil.objects.Object2DoubleMaps;
import it.unimi.dsi.fastutil.objects.Object2DoubleOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongMaps;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import net.minecraft.core.Holder;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** Persisted cultivation progress and the currently selected cultivation action. */
public final class CultivationAttachment extends ShouldSyncAttachment {
    public static final MapCodec<CultivationAttachment> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            CollectionCodecs.doubleMap(Resource.CODEC).optionalFieldOf("cultivation_progress", Object2DoubleMaps.emptyMap()).forGetter(CultivationAttachment::cultivationProgresses),
            CollectionCodecs.map(Resource.CODEC, RealmStage.CODEC).optionalFieldOf("realm_stages", Map.of()).forGetter(CultivationAttachment::realmStages),
            CultivateAction.CODEC.optionalFieldOf("cultivate_action").forGetter(CultivationAttachment::cultivateAction),
            Codec.BOOL.optionalFieldOf("cultivating", false).forGetter(CultivationAttachment::cultivating),
            Codec.LONG.optionalFieldOf("cultivate_started_at", 0L).forGetter(CultivationAttachment::cultivateStartedAt),
            Codec.LONG.optionalFieldOf("next_cultivate_tick", 0L).forGetter(CultivationAttachment::nextCultivateTick),
            CollectionCodecs.longMap(CultivateAction.CODEC).optionalFieldOf("cultivate_cooldowns", Object2LongMaps.emptyMap()).forGetter(CultivationAttachment::cultivateCooldowns)
    ).apply(i, CultivationAttachment::new));

    private final Object2DoubleMap<Holder<Resource>> cultivationProgresses;
    private final Map<Holder<Resource>, Holder<RealmStage>> realmStages;
    private Optional<Holder<CultivateAction>> cultivateAction;
    private boolean cultivating;
    private long cultivateStartedAt, nextCultivateTick;
    private final Object2LongMap<Holder<CultivateAction>> cultivateCooldowns;

    public CultivationAttachment() {
        this(Object2DoubleMaps.emptyMap(), Map.of(), Optional.empty(), false, 0L, 0L, Object2LongMaps.emptyMap());
    }

    private CultivationAttachment(Object2DoubleMap<Holder<Resource>> cultivationProgresses, Map<Holder<Resource>, Holder<RealmStage>> realmStages,
                                  Optional<Holder<CultivateAction>> cultivateAction, boolean cultivating,
                                  long cultivateStartedAt, long nextCultivateTick,
                                  Map<Holder<CultivateAction>, Long> cultivateCooldowns) {
        this.cultivationProgresses = new Object2DoubleOpenHashMap<>(cultivationProgresses);
        this.realmStages = new LinkedHashMap<>(realmStages);
        this.cultivateAction = cultivateAction;
        this.cultivating = cultivating;
        this.cultivateStartedAt = cultivateStartedAt;
        this.nextCultivateTick = nextCultivateTick;
        this.cultivateCooldowns = new Object2LongOpenHashMap<>(cultivateCooldowns);
    }

    public Object2DoubleMap<Holder<Resource>> cultivationProgresses() { return this.cultivationProgresses; }
    public double cultivationProgress(Holder<Resource> resource) { return this.cultivationProgresses.getDouble(resource); }
    public Map<Holder<Resource>, Holder<RealmStage>> realmStages() { return this.realmStages; }
    /**
     * Returns the stage currently associated with the resource, or {@code null}
     * when the player has not entered a realm chain for that resource yet.
     */
    public @Nullable Holder<RealmStage> realmStage(Holder<Resource> resource) { return this.realmStages.get(resource); }
    public Optional<Holder<CultivateAction>> cultivateAction() { return this.cultivateAction; }
    public boolean cultivating() { return this.cultivating; }
    public long cultivateStartedAt() { return this.cultivateStartedAt; }
    public long nextCultivateTick() { return this.nextCultivateTick; }
    public Object2LongMap<Holder<CultivateAction>> cultivateCooldowns() { return this.cultivateCooldowns; }

    public void setCultivationProgress(Holder<Resource> resource, double value) {
        if (!Double.isFinite(value) || value < 0.0D) throw new IllegalArgumentException("Cultivation progress must be finite and non-negative");
        this.cultivationProgresses.put(resource, value);
        this.markDirty();
    }

    public void setRealmStage(Holder<RealmStage> value) {
        if (value == null) return;
        this.realmStages.put(value.value().resource(), value);
        this.markDirty();
    }

    public void setRealmStages(Map<Holder<Resource>, Holder<RealmStage>> values) {
        this.realmStages.clear();
        this.realmStages.putAll(values);
        this.markDirty();
    }

    public void startCultivateAction(Holder<CultivateAction> action, long gameTime, long nextTick) {
        this.cultivateAction = Optional.of(action);
        this.cultivating = true;
        this.cultivateStartedAt = gameTime;
        this.nextCultivateTick = nextTick;
        this.markDirty();
    }

    public void scheduleCultivateTick(long gameTime) { this.nextCultivateTick = gameTime; this.markDirty(); }

    public void stopCultivateAction(Holder<CultivateAction> action, long cooldownUntil) {
        if (this.cultivateAction.filter(action::equals).isPresent()) this.cultivating = false;
        if (cooldownUntil > 0L) this.cultivateCooldowns.put(action, cooldownUntil);
        this.markDirty();
    }

    public boolean isCultivateActionOnCooldown(Holder<CultivateAction> action, long gameTime) {
        return this.cultivateCooldowns.getOrDefault(action, 0L) > gameTime;
    }
}
