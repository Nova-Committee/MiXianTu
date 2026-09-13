package com.iafenvoy.mxt.attachment;

import com.iafenvoy.mxt.data.cultivation.CultivateAction;
import com.iafenvoy.mxt.data.cultivation.CultivationProfile;
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
 * Persisted cultivation progress and the currently selected cultivation action.
 *
 * <p>Progress and the current realm are keyed by the cultivation profile: a chain belongs to the
 * profile, so storing the profile is the whole identity. The value a profile stores is only read
 * through it, which is what keeps the stored state from duplicating what the data already says.</p>
 */
public final class CultivationAttachment extends ShouldSyncAttachment {
    public static final MapCodec<CultivationAttachment> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            CollectionCodecs.doubleMap(CultivationProfile.CODEC).optionalFieldOf("cultivation_progress", Object2DoubleMaps.emptyMap()).forGetter(CultivationAttachment::cultivationProgresses),
            CollectionCodecs.map(CultivationProfile.CODEC, RealmStage.CODEC).optionalFieldOf("realm_stages", Map.of()).forGetter(CultivationAttachment::realmStages),
            CultivateAction.CODEC.optionalFieldOf("cultivate_action").forGetter(CultivationAttachment::cultivateAction),
            Codec.BOOL.optionalFieldOf("cultivating", false).forGetter(CultivationAttachment::cultivating),
            Codec.LONG.optionalFieldOf("cultivate_started_at", 0L).forGetter(CultivationAttachment::cultivateStartedAt),
            Codec.LONG.optionalFieldOf("next_cultivate_tick", 0L).forGetter(CultivationAttachment::nextCultivateTick),
            CollectionCodecs.longMap(CultivateAction.CODEC).optionalFieldOf("cultivate_cooldowns", Object2LongMaps.emptyMap()).forGetter(CultivationAttachment::cultivateCooldowns)
    ).apply(i, CultivationAttachment::new));

    private final Object2DoubleMap<Holder<CultivationProfile>> cultivationProgresses;
    private final Map<Holder<CultivationProfile>, Holder<RealmStage>> realmStages;
    private Optional<Holder<CultivateAction>> cultivateAction;
    private boolean cultivating;
    private long cultivateStartedAt, nextCultivateTick;
    private final Object2LongMap<Holder<CultivateAction>> cultivateCooldowns;

    public CultivationAttachment() {
        this(Object2DoubleMaps.emptyMap(), Map.of(), Optional.empty(), false, 0L, 0L, Object2LongMaps.emptyMap());
    }

    private CultivationAttachment(Object2DoubleMap<Holder<CultivationProfile>> cultivationProgresses, Map<Holder<CultivationProfile>, Holder<RealmStage>> realmStages,
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

    public Object2DoubleMap<Holder<CultivationProfile>> cultivationProgresses() {
        return this.cultivationProgresses;
    }

    public double cultivationProgress(Holder<CultivationProfile> cultivation) {
        return this.cultivationProgresses.getDouble(cultivation);
    }

    public Map<Holder<CultivationProfile>, Holder<RealmStage>> realmStages() {
        return this.realmStages;
    }

    /**
     * Returns the stage currently associated with the chain, or {@code null} when the holder has not
     * entered it yet.
     */
    public @Nullable Holder<RealmStage> realmStage(Holder<CultivationProfile> cultivation) {
        return this.realmStages.get(cultivation);
    }

    public Optional<Holder<CultivateAction>> cultivateAction() {
        return this.cultivateAction;
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

    public Object2LongMap<Holder<CultivateAction>> cultivateCooldowns() {
        return this.cultivateCooldowns;
    }

    public void setCultivationProgress(Holder<CultivationProfile> cultivation, double value) {
        if (!Double.isFinite(value) || value < 0.0D)
            throw new IllegalArgumentException("Cultivation progress must be finite and non-negative");
        this.cultivationProgresses.put(cultivation, value);
        this.markDirty();
    }

    public void setRealmStage(Holder<RealmStage> value) {
        if (value == null) return;
        this.realmStages.put(value.value().cultivation(), value);
        this.markDirty();
    }

    public void setRealmStages(Map<Holder<CultivationProfile>, Holder<RealmStage>> values) {
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

    public void scheduleCultivateTick(long gameTime) {
        this.nextCultivateTick = gameTime;
        this.markDirty();
    }

    public void stopCultivateAction(Holder<CultivateAction> action, long cooldownUntil) {
        if (this.cultivateAction.filter(action::equals).isPresent()) this.cultivating = false;
        if (cooldownUntil > 0L) this.cultivateCooldowns.put(action, cooldownUntil);
        this.markDirty();
    }

    public boolean isCultivateActionOnCooldown(Holder<CultivateAction> action, long gameTime) {
        return this.cultivateCooldowns.getOrDefault(action, 0L) > gameTime;
    }
}
