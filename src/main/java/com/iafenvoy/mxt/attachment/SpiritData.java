package com.iafenvoy.mxt.attachment;

import com.iafenvoy.mxt.data.Title;
import com.iafenvoy.mxt.data.cultivation.CultivateAction;
import com.iafenvoy.mxt.data.cultivation.CultivationTechnique;
import com.iafenvoy.mxt.data.cultivation.Physique;
import com.iafenvoy.mxt.data.cultivation.RealmStage;
import com.iafenvoy.mxt.data.cultivation.SpiritRoot;
import com.iafenvoy.mxt.data.aura.ItemAura;
import com.iafenvoy.mxt.util.codec.CollectionCodecs;
import com.mojang.serialization.*;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.*;
import net.minecraft.core.Holder;

import java.util.*;

/**
 * Persistent cultivation identity. Spirit roots and physiques intentionally use separate collections.
 */
public final class SpiritData {
    public static final MapCodec<SpiritData> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.DOUBLE.optionalFieldOf("cultivation_progress", 0.0D).forGetter(SpiritData::cultivationProgress),
            RealmStage.CODEC.optionalFieldOf("realm_stage").forGetter(SpiritData::realmStage),
            CollectionCodecs.list(SpiritRoot.CODEC).optionalFieldOf("spirit_roots", List.of()).forGetter(SpiritData::spiritRoots),
            CollectionCodecs.list(Physique.CODEC).optionalFieldOf("physiques", List.of()).forGetter(SpiritData::physiques),
            CultivationTechnique.CODEC.optionalFieldOf("active_technique").forGetter(SpiritData::activeTechnique),
            CollectionCodecs.list(CultivationTechnique.CODEC).optionalFieldOf("learned_techniques", List.of()).forGetter(SpiritData::learnedTechniques),
            CollectionCodecs.list(Title.CODEC).optionalFieldOf("titles", List.of()).forGetter(SpiritData::titles),
            CultivateAction.CODEC.optionalFieldOf("cultivate_action").forGetter(SpiritData::cultivateAction),
            Codec.LONG.optionalFieldOf("cultivate_started_at", 0L).forGetter(SpiritData::cultivateStartedAt),
            Codec.LONG.optionalFieldOf("next_cultivate_tick", 0L).forGetter(SpiritData::nextCultivateTick),
            CollectionCodecs.longMap(CultivateAction.CODEC).optionalFieldOf("cultivate_cooldowns", Object2LongMaps.emptyMap()).forGetter(SpiritData::cultivateCooldowns),
            Codec.LONG.optionalFieldOf("lifespan_remaining", -1L).forGetter(SpiritData::lifespanRemaining),
            SoulState.CODEC.optionalFieldOf("soul", SoulState.EMPTY).forGetter(SpiritData::soulState),
            ItemAuraState.CODEC.optionalFieldOf("item_aura_state", ItemAuraState.EMPTY).forGetter(SpiritData::itemAuraState)
    ).apply(i, SpiritData::new));
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
    private ItemAuraState itemAuraState;

    public SpiritData() {
        this(0.0D, Optional.empty(), List.of(), List.of(), Optional.empty(), List.of(), List.of(), Optional.empty(), 0L, 0L, Map.of(), -1L, SoulState.EMPTY, ItemAuraState.EMPTY);
    }

    private SpiritData(double cultivationProgress, Optional<Holder<RealmStage>> realmStage, List<Holder<SpiritRoot>> spiritRoots, List<Holder<Physique>> physiques, Optional<Holder<CultivationTechnique>> activeTechnique,
                       List<Holder<CultivationTechnique>> learnedTechniques, List<Holder<Title>> titles, Optional<Holder<CultivateAction>> cultivateAction, long cultivateStartedAt, long nextCultivateTick,
                       Map<Holder<CultivateAction>, Long> cultivateCooldowns, long lifespanRemaining, SoulState soulState,
                       ItemAuraState itemAuraState) {
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
        this.itemAuraState = itemAuraState;
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

    public Optional<Holder<ItemAura>> itemAura() {
        return this.itemAuraState.aura();
    }

    public double itemAuraRemaining() {
        return this.itemAuraState.remaining();
    }

    public double itemAuraMaximum() {
        return this.itemAuraState.maximum();
    }

    public ItemAuraState itemAuraState() {
        return this.itemAuraState;
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

    public void chargeItemAura(Holder<ItemAura> holder, double amount) {
        if (!Double.isFinite(amount) || amount <= 0.0D)
            throw new IllegalArgumentException("Item aura amount must be positive");
        this.itemAuraState = new ItemAuraState(Optional.of(holder), amount, amount);
    }

    public void setItemAuraRemaining(double value) {
        if (!Double.isFinite(value) || value < 0.0D || value > this.itemAuraMaximum())
            throw new IllegalArgumentException("Invalid item aura remaining amount");
        this.itemAuraState = new ItemAuraState(this.itemAura(), value, this.itemAuraMaximum());
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

    public record ItemAuraState(Optional<Holder<ItemAura>> aura, double remaining, double maximum) {
        private static final ItemAuraState EMPTY = new ItemAuraState(Optional.empty(), 0.0D, 0.0D);
        private static final Codec<ItemAuraState> CODEC = RecordCodecBuilder.<ItemAuraState>create(i -> i.group(
                ItemAura.CODEC.optionalFieldOf("aura").forGetter(ItemAuraState::aura),
                Codec.DOUBLE.optionalFieldOf("remaining", 0.0D).forGetter(ItemAuraState::remaining),
                Codec.DOUBLE.optionalFieldOf("maximum", 0.0D).forGetter(ItemAuraState::maximum)
        ).apply(i, ItemAuraState::new)).validate(ItemAuraState::validate);

        public ItemAuraState {
            if (!Double.isFinite(remaining) || remaining < 0.0D || !Double.isFinite(maximum) || maximum < 0.0D || remaining > maximum)
                throw new IllegalArgumentException("Invalid item aura state");
            if (aura.isPresent() != (maximum > 0.0D))
                throw new IllegalArgumentException("Item aura holder must match its maximum amount");
        }

        private static DataResult<ItemAuraState> validate(ItemAuraState state) {
            return state.remaining <= state.maximum
                    ? DataResult.success(state)
                    : DataResult.error(() -> "Item aura remaining cannot exceed maximum");
        }
    }
}
