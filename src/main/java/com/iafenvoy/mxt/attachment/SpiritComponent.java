package com.iafenvoy.mxt.attachment;

import com.iafenvoy.mxt.data.Title;
import com.iafenvoy.mxt.data.cultivation.CultivateAction;
import com.iafenvoy.mxt.data.cultivation.CultivationTechnique;
import com.iafenvoy.mxt.data.cultivation.Physique;
import com.iafenvoy.mxt.data.cultivation.RealmStage;
import com.iafenvoy.mxt.data.cultivation.SpiritRoot;
import com.iafenvoy.mxt.util.codec.CollectionCodecs;
import com.mojang.serialization.*;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.*;
import net.minecraft.core.Holder;

import java.util.*;

/**
 * Persistent cultivation identity. Spirit roots and physiques intentionally use separate collections.
 */
public final class SpiritComponent {
    public static final MapCodec<SpiritComponent> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.DOUBLE.optionalFieldOf("cultivation_progress", 0.0D).forGetter(SpiritComponent::cultivationProgress),
            RealmStage.CODEC.optionalFieldOf("realm_stage").forGetter(SpiritComponent::realmStage),
            CollectionCodecs.list(SpiritRoot.CODEC).optionalFieldOf("spirit_roots", List.of()).forGetter(SpiritComponent::spiritRoots),
            CollectionCodecs.list(Physique.CODEC).optionalFieldOf("physiques", List.of()).forGetter(SpiritComponent::physiques),
            CultivationTechnique.CODEC.optionalFieldOf("active_technique").forGetter(SpiritComponent::activeTechnique),
            CollectionCodecs.list(CultivationTechnique.CODEC).optionalFieldOf("learned_techniques", List.of()).forGetter(SpiritComponent::learnedTechniques),
            CollectionCodecs.list(Title.CODEC).optionalFieldOf("titles", List.of()).forGetter(SpiritComponent::titles),
            CultivateAction.CODEC.optionalFieldOf("cultivate_action").forGetter(SpiritComponent::cultivateAction),
            Codec.LONG.optionalFieldOf("cultivate_started_at", 0L).forGetter(SpiritComponent::cultivateStartedAt),
            Codec.LONG.optionalFieldOf("next_cultivate_tick", 0L).forGetter(SpiritComponent::nextCultivateTick),
            CollectionCodecs.longMap(CultivateAction.CODEC).optionalFieldOf("cultivate_cooldowns", Object2LongMaps.emptyMap()).forGetter(SpiritComponent::cultivateCooldowns),
            Codec.LONG.optionalFieldOf("lifespan_remaining", -1L).forGetter(SpiritComponent::lifespanRemaining),
            SoulState.CODEC.optionalFieldOf("soul", SoulState.EMPTY).forGetter(SpiritComponent::soulState)
    ).apply(i, SpiritComponent::new));
    private double cultivationProgress;
    private Optional<Holder<RealmStage>> realmStage;
    private final List<Holder<SpiritRoot>> spiritRoots;
    private final List<Holder<Physique>> physiques;
    private Optional<Holder<CultivationTechnique>> activeTechnique;
    private final List<Holder<CultivationTechnique>> learnedTechniques;
    private final List<Holder<Title>> titles;
    private Optional<Holder<CultivateAction>> cultivateAction;
    private long cultivateStartedAt;
    private long nextCultivateTick;
    private final Object2LongMap<Holder<CultivateAction>> cultivateCooldowns;
    private long lifespanRemaining;
    private SoulState soulState;

    public SpiritComponent() {
        this(0.0D, Optional.empty(), List.of(), List.of(), Optional.empty(), List.of(), List.of(), Optional.empty(), 0L, 0L, Map.of(), -1L, SoulState.EMPTY);
    }

    private SpiritComponent(double cultivationProgress, Optional<Holder<RealmStage>> realmStage, List<Holder<SpiritRoot>> spiritRoots, List<Holder<Physique>> physiques, Optional<Holder<CultivationTechnique>> activeTechnique,
                            List<Holder<CultivationTechnique>> learnedTechniques, List<Holder<Title>> titles, Optional<Holder<CultivateAction>> cultivateAction, long cultivateStartedAt, long nextCultivateTick,
                            Map<Holder<CultivateAction>, Long> cultivateCooldowns, long lifespanRemaining, SoulState soulState) {
        this.cultivationProgress = cultivationProgress;
        this.realmStage = realmStage;
        this.spiritRoots = new LinkedList<>(spiritRoots);
        this.physiques = new LinkedList<>(physiques);
        this.activeTechnique = activeTechnique;
        this.learnedTechniques = new LinkedList<>(learnedTechniques);
        this.titles = new LinkedList<>(titles);
        this.cultivateAction = cultivateAction;
        this.cultivateStartedAt = cultivateStartedAt;
        this.nextCultivateTick = nextCultivateTick;
        this.cultivateCooldowns = new Object2LongOpenHashMap<>(cultivateCooldowns);
        if (lifespanRemaining < -1L)
            throw new IllegalArgumentException("Invalid spirit state");
        this.lifespanRemaining = lifespanRemaining;
        this.soulState = soulState;
    }

    public double cultivationProgress() {
        return this.cultivationProgress;
    }

    public Optional<Holder<RealmStage>> realmStage() {
        return this.realmStage;
    }

    public List<Holder<SpiritRoot>> spiritRoots() {
        return this.spiritRoots;
    }

    public List<Holder<Physique>> physiques() {
        return this.physiques;
    }

    public Optional<Holder<CultivationTechnique>> activeTechnique() {
        return this.activeTechnique;
    }

    public List<Holder<CultivationTechnique>> learnedTechniques() {
        return this.learnedTechniques;
    }

    public List<Holder<Title>> titles() {
        return this.titles;
    }

    public Optional<Holder<CultivateAction>> cultivateAction() {
        return this.cultivateAction;
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

    public long lifespanRemaining() {
        return this.lifespanRemaining;
    }

    private SoulState soulState() {
        return this.soulState;
    }

    public double karma() {
        return this.soulState.karma();
    }

    public double heartDemon() {
        return this.soulState.heartDemon();
    }

    public double soulStrength() {
        return this.soulState.soulStrength();
    }

    public double soulSenseRange() {
        return this.soulState.soulSenseRange();
    }

    public void setLifespanRemaining(long value) {
        if (value < -1L) throw new IllegalArgumentException("Lifespan cannot be less than -1");
        this.lifespanRemaining = value;
    }

    public void setKarma(double value) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException("Karma must be finite");
        this.soulState = new SoulState(value, this.heartDemon(), this.soulStrength(), this.soulSenseRange());
    }

    public void setHeartDemon(double value) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException("Heart demon must be finite");
        this.soulState = new SoulState(this.karma(), value, this.soulStrength(), this.soulSenseRange());
    }

    public void setSoulStrength(double value) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException("Soul strength must be finite");
        this.soulState = new SoulState(this.karma(), this.heartDemon(), value, this.soulSenseRange());
    }

    public void setSoulSenseRange(double value) {
        if (!Double.isFinite(value) || value < 0.0D)
            throw new IllegalArgumentException("Soul sense range must be non-negative");
        this.soulState = new SoulState(this.karma(), this.heartDemon(), this.soulStrength(), value);
    }

    public void setCultivationProgress(double value) {
        if (!Double.isFinite(value) || value < 0.0D)
            throw new IllegalArgumentException("Cultivation progress must be finite and non-negative");
        this.cultivationProgress = value;
    }

    public void setRealmStage(Holder<RealmStage> value) {
        this.realmStage = Optional.ofNullable(value);
    }

    public void setSpiritRoots(List<Holder<SpiritRoot>> values) {
        this.spiritRoots.clear();
        this.spiritRoots.addAll(values);
    }

    public void setPhysiques(List<Holder<Physique>> values) {
        this.physiques.clear();
        this.physiques.addAll(values);
    }

    public void setActiveTechnique(Holder<CultivationTechnique> value) {
        this.activeTechnique = Optional.ofNullable(value);
    }

    public void setLearnedTechniques(List<Holder<CultivationTechnique>> values) {
        this.learnedTechniques.clear();
        this.learnedTechniques.addAll(values);
    }

    public void setTitles(List<Holder<Title>> values) {
        this.titles.clear();
        this.titles.addAll(values);
    }

    public void startCultivateAction(Holder<CultivateAction> action, long gameTime, long nextTick) {
        this.cultivateAction = Optional.of(action);
        this.cultivateStartedAt = gameTime;
        this.nextCultivateTick = nextTick;
    }

    public void scheduleCultivateTick(long gameTime) {
        this.nextCultivateTick = gameTime;
    }

    public void stopCultivateAction(Holder<CultivateAction> action, long cooldownUntil) {
        if (this.cultivateAction.filter(action::equals).isPresent())
            this.cultivateAction = Optional.empty();
        if (cooldownUntil > 0L)
            this.cultivateCooldowns.put(action, cooldownUntil);
    }

    public boolean isCultivateActionOnCooldown(Holder<CultivateAction> action, long gameTime) {
        return this.cultivateCooldowns.getOrDefault(action, 0L) > gameTime;
    }

    private record SoulState(double karma, double heartDemon, double soulStrength, double soulSenseRange) {
        private static final SoulState EMPTY = new SoulState(0.0D, 0.0D, 0.0D, 0.0D);
        private static final Codec<SoulState> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.DOUBLE.optionalFieldOf("karma", 0.0D).forGetter(SoulState::karma),
                Codec.DOUBLE.optionalFieldOf("heart_demon", 0.0D).forGetter(SoulState::heartDemon),
                Codec.DOUBLE.optionalFieldOf("strength", 0.0D).forGetter(SoulState::soulStrength),
                Codec.DOUBLE.optionalFieldOf("sense_range", 0.0D).forGetter(SoulState::soulSenseRange)
        ).apply(i, SoulState::new));

        private SoulState {
            if (!Double.isFinite(karma) || !Double.isFinite(heartDemon) || !Double.isFinite(soulStrength)
                    || !Double.isFinite(soulSenseRange) || soulSenseRange < 0.0D)
                throw new IllegalArgumentException("Invalid soul state");
        }
    }

}
