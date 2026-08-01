package com.iafenvoy.mxt.attachment;

import com.mojang.serialization.*;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

import java.util.*;

/**
 * Persistent cultivation identity. Spirit roots and physiques intentionally use separate collections.
 */
public final class SpiritData {
    private static final MapCodec<SpiritData> PAYLOAD_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.DOUBLE.optionalFieldOf("cultivation_progress", 0.0D).forGetter(SpiritData::cultivationProgress),
            Identifier.CODEC.optionalFieldOf("realm_stage").forGetter(SpiritData::realmStage),
            Identifier.CODEC.listOf().optionalFieldOf("spirit_roots", List.of()).forGetter(SpiritData::spiritRoots),
            Identifier.CODEC.listOf().optionalFieldOf("physiques", List.of()).forGetter(SpiritData::physiques),
            Identifier.CODEC.optionalFieldOf("active_technique").forGetter(SpiritData::activeTechnique),
            Identifier.CODEC.listOf().optionalFieldOf("learned_techniques", List.of()).forGetter(SpiritData::learnedTechniques),
            Identifier.CODEC.listOf().optionalFieldOf("titles", List.of()).forGetter(SpiritData::titles),
            Identifier.CODEC.optionalFieldOf("cultivate_action").forGetter(SpiritData::cultivateAction),
            Codec.LONG.optionalFieldOf("cultivate_started_at", 0L).forGetter(SpiritData::cultivateStartedAt),
            Codec.LONG.optionalFieldOf("next_cultivate_tick", 0L).forGetter(SpiritData::nextCultivateTick),
            Codec.unboundedMap(Identifier.CODEC, Codec.LONG).optionalFieldOf("cultivate_cooldowns", Map.of()).forGetter(SpiritData::cultivateCooldowns),
            Codec.LONG.optionalFieldOf("lifespan_remaining", -1L).forGetter(SpiritData::lifespanRemaining),
            Codec.DOUBLE.optionalFieldOf("karma", 0.0D).forGetter(SpiritData::karma),
            Codec.DOUBLE.optionalFieldOf("heart_demon", 0.0D).forGetter(SpiritData::heartDemon),
            Codec.DOUBLE.optionalFieldOf("soul_strength", 0.0D).forGetter(SpiritData::soulStrength),
            Codec.DOUBLE.optionalFieldOf("soul_sense_range", 0.0D).forGetter(SpiritData::soulSenseRange)
    ).apply(instance, SpiritData::new));
    public static final MapCodec<SpiritData> MAP_CODEC = PAYLOAD_CODEC;
    public static final Codec<SpiritData> CODEC = MAP_CODEC.codec();

    private double cultivationProgress;
    private Optional<Identifier> realmStage;
    private final List<Identifier> spiritRoots;
    private final List<Identifier> physiques;
    private Optional<Identifier> activeTechnique;
    private final List<Identifier> learnedTechniques;
    private final List<Identifier> titles;
    private Optional<Identifier> cultivateAction;
    private long cultivateStartedAt;
    private long nextCultivateTick;
    private final Map<Identifier, Long> cultivateCooldowns;
    private long lifespanRemaining;
    private double karma;
    private double heartDemon;
    private double soulStrength;
    private double soulSenseRange;

    public SpiritData() {
        this(0.0D, Optional.empty(), List.of(), List.of(), Optional.empty(), List.of(), List.of(), Optional.empty(), 0L, 0L, Map.of(), -1L, 0.0D, 0.0D, 0.0D, 0.0D);
    }

    private SpiritData(double cultivationProgress, Optional<Identifier> realmStage, List<Identifier> spiritRoots, List<Identifier> physiques, Optional<Identifier> activeTechnique,
                       List<Identifier> learnedTechniques, List<Identifier> titles, Optional<Identifier> cultivateAction, long cultivateStartedAt, long nextCultivateTick,
                       Map<Identifier, Long> cultivateCooldowns, long lifespanRemaining, double karma, double heartDemon,
                       double soulStrength, double soulSenseRange) {
        this.cultivationProgress = cultivationProgress;
        this.realmStage = realmStage;
        this.spiritRoots = new ArrayList<>(spiritRoots);
        this.physiques = new ArrayList<>(physiques);
        this.activeTechnique = activeTechnique;
        this.learnedTechniques = new ArrayList<>(learnedTechniques);
        this.titles = new ArrayList<>(titles);
        this.cultivateAction = cultivateAction;
        this.cultivateStartedAt = cultivateStartedAt;
        this.nextCultivateTick = nextCultivateTick;
        this.cultivateCooldowns = new LinkedHashMap<>(cultivateCooldowns);
        if (lifespanRemaining < -1L || !Double.isFinite(karma) || !Double.isFinite(heartDemon) || !Double.isFinite(soulStrength) || !Double.isFinite(soulSenseRange))
            throw new IllegalArgumentException("Invalid spirit state");
        this.lifespanRemaining = lifespanRemaining;
        this.karma = karma;
        this.heartDemon = heartDemon;
        this.soulStrength = soulStrength;
        this.soulSenseRange = soulSenseRange;
    }

    public double cultivationProgress() {
        return this.cultivationProgress;
    }

    public Optional<Identifier> realmStage() {
        return this.realmStage;
    }

    public List<Identifier> spiritRoots() {
        return List.copyOf(this.spiritRoots);
    }

    public List<Identifier> physiques() {
        return List.copyOf(this.physiques);
    }

    public Optional<Identifier> activeTechnique() {
        return this.activeTechnique;
    }

    public List<Identifier> learnedTechniques() {
        return List.copyOf(this.learnedTechniques);
    }

    public List<Identifier> titles() {
        return List.copyOf(this.titles);
    }

    public Optional<Identifier> cultivateAction() {
        return this.cultivateAction;
    }

    public long cultivateStartedAt() {
        return this.cultivateStartedAt;
    }

    public long nextCultivateTick() {
        return this.nextCultivateTick;
    }

    public Map<Identifier, Long> cultivateCooldowns() {
        return Map.copyOf(this.cultivateCooldowns);
    }

    public long lifespanRemaining() {
        return this.lifespanRemaining;
    }

    public double karma() {
        return this.karma;
    }

    public double heartDemon() {
        return this.heartDemon;
    }

    public double soulStrength() {
        return this.soulStrength;
    }

    public double soulSenseRange() {
        return this.soulSenseRange;
    }

    public void setLifespanRemaining(long value) {
        if (value < -1L) throw new IllegalArgumentException("Lifespan cannot be less than -1");
        this.lifespanRemaining = value;
    }

    public void setKarma(double value) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException("Karma must be finite");
        this.karma = value;
    }

    public void setHeartDemon(double value) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException("Heart demon must be finite");
        this.heartDemon = value;
    }

    public void setSoulStrength(double value) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException("Soul strength must be finite");
        this.soulStrength = value;
    }

    public void setSoulSenseRange(double value) {
        if (!Double.isFinite(value) || value < 0.0D)
            throw new IllegalArgumentException("Soul sense range must be non-negative");
        this.soulSenseRange = value;
    }

    public void setCultivationProgress(double value) {
        if (!Double.isFinite(value) || value < 0.0D)
            throw new IllegalArgumentException("Cultivation progress must be finite and non-negative");
        this.cultivationProgress = value;
    }

    public void setRealmStage(Identifier value) {
        this.realmStage = Optional.ofNullable(value);
    }

    public void setSpiritRoots(List<Identifier> values) {
        this.spiritRoots.clear();
        this.spiritRoots.addAll(List.copyOf(values));
    }

    public void setPhysiques(List<Identifier> values) {
        this.physiques.clear();
        this.physiques.addAll(List.copyOf(values));
    }

    public void setActiveTechnique(Identifier value) {
        this.activeTechnique = Optional.ofNullable(value);
    }

    public void setLearnedTechniques(List<Identifier> values) {
        this.learnedTechniques.clear();
        this.learnedTechniques.addAll(List.copyOf(values));
    }

    public void setTitles(List<Identifier> values) {
        this.titles.clear();
        this.titles.addAll(List.copyOf(values));
    }

    public void startCultivateAction(Identifier action, long gameTime, long nextTick) {
        this.cultivateAction = Optional.of(action);
        this.cultivateStartedAt = gameTime;
        this.nextCultivateTick = nextTick;
    }

    public void scheduleCultivateTick(long gameTime) {
        this.nextCultivateTick = gameTime;
    }

    public void stopCultivateAction(Identifier action, long cooldownUntil) {
        if (this.cultivateAction.filter(action::equals).isPresent()) this.cultivateAction = Optional.empty();
        if (cooldownUntil > 0L) this.cultivateCooldowns.put(action, cooldownUntil);
    }

    public boolean isCultivateActionOnCooldown(Identifier action, long gameTime) {
        return this.cultivateCooldowns.getOrDefault(action, 0L) > gameTime;
    }
}
