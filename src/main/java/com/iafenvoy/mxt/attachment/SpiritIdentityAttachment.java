package com.iafenvoy.mxt.attachment;

import com.iafenvoy.mxt.data.Title;
import com.iafenvoy.mxt.data.cultivation.CultivationTechnique;
import com.iafenvoy.mxt.data.cultivation.Physique;
import com.iafenvoy.mxt.data.cultivation.SkillStage;
import com.iafenvoy.mxt.data.cultivation.SpiritRoot;
import com.iafenvoy.mxt.util.ShouldSyncAttachment;
import com.iafenvoy.mxt.util.codec.CollectionCodecs;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Persisted roots, physiques, learned techniques, their mastery levels and titles. A technique's level is
 * stored only once it has advanced: a technique the holder never climbed has no entry and stands on its own
 * entry level, so a data pack that moves the entry level moves everyone who never advanced.
 */
public final class SpiritIdentityAttachment extends ShouldSyncAttachment {
    public static final MapCodec<SpiritIdentityAttachment> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            CollectionCodecs.list(SpiritRoot.CODEC).optionalFieldOf("spirit_roots", List.of()).forGetter(SpiritIdentityAttachment::spiritRoots),
            CollectionCodecs.list(Physique.CODEC).optionalFieldOf("physiques", List.of()).forGetter(SpiritIdentityAttachment::physiques),
            CollectionCodecs.list(CultivationTechnique.CODEC).optionalFieldOf("learned_techniques", List.of()).forGetter(SpiritIdentityAttachment::learnedTechniques),
            CollectionCodecs.map(CultivationTechnique.CODEC, SkillStage.CODEC).optionalFieldOf("technique_stages", Map.of()).forGetter(SpiritIdentityAttachment::techniqueStages),
            CollectionCodecs.list(Title.CODEC).optionalFieldOf("titles", List.of()).forGetter(SpiritIdentityAttachment::titles)
    ).apply(i, SpiritIdentityAttachment::new));

    private final List<Holder<SpiritRoot>> spiritRoots;
    private final List<Holder<Physique>> physiques;
    private final List<Holder<CultivationTechnique>> learnedTechniques;
    private final Map<Holder<CultivationTechnique>, Holder<SkillStage>> techniqueStages;
    private final List<Holder<Title>> titles;

    public SpiritIdentityAttachment() {
        this(List.of(), List.of(), List.of(), Map.of(), List.of());
    }

    private SpiritIdentityAttachment(List<Holder<SpiritRoot>> spiritRoots, List<Holder<Physique>> physiques,
                                     List<Holder<CultivationTechnique>> learnedTechniques,
                                     Map<Holder<CultivationTechnique>, Holder<SkillStage>> techniqueStages,
                                     List<Holder<Title>> titles) {
        this.spiritRoots = new LinkedList<>(spiritRoots);
        this.physiques = new LinkedList<>(physiques);
        this.learnedTechniques = new LinkedList<>(learnedTechniques);
        this.techniqueStages = new LinkedHashMap<>(techniqueStages);
        this.titles = new LinkedList<>(titles);
    }

    public List<Holder<SpiritRoot>> spiritRoots() {
        return this.spiritRoots;
    }

    public List<Holder<Physique>> physiques() {
        return this.physiques;
    }

    public List<Holder<CultivationTechnique>> learnedTechniques() {
        return this.learnedTechniques;
    }

    public Map<Holder<CultivationTechnique>, Holder<SkillStage>> techniqueStages() {
        return this.techniqueStages;
    }

    /**
     * The level the holder has advanced to, or {@code null} while it still stands on the entry level.
     */
    public @Nullable Holder<SkillStage> techniqueStage(Holder<CultivationTechnique> technique) {
        return this.techniqueStages.get(technique);
    }

    public List<Holder<Title>> titles() {
        return this.titles;
    }

    public void setTechniqueStage(Holder<CultivationTechnique> technique, Holder<SkillStage> stage) {
        if (technique == null || stage == null) return;
        this.techniqueStages.put(technique, stage);
        this.markDirty();
    }

    public void setTechniqueStages(Map<Holder<CultivationTechnique>, Holder<SkillStage>> values) {
        this.techniqueStages.clear();
        this.techniqueStages.putAll(values);
        this.markDirty();
    }

    public void setSpiritRoots(List<Holder<SpiritRoot>> values) {
        this.spiritRoots.clear();
        this.spiritRoots.addAll(values);
        this.markDirty();
    }

    public void setPhysiques(List<Holder<Physique>> values) {
        this.physiques.clear();
        this.physiques.addAll(values);
        this.markDirty();
    }

    public void setLearnedTechniques(List<Holder<CultivationTechnique>> values) {
        this.learnedTechniques.clear();
        this.learnedTechniques.addAll(values);
        this.markDirty();
    }

    public void addLearnedTechnique(Holder<CultivationTechnique> value) {
        if (value != null && !this.learnedTechniques.contains(value)) {
            this.learnedTechniques.add(value);
            this.markDirty();
        }
    }

    public void setTitles(List<Holder<Title>> values) {
        this.titles.clear();
        this.titles.addAll(values);
        this.markDirty();
    }
}
