package com.iafenvoy.mxt.attachment;

import com.iafenvoy.mxt.data.cultivation.Technique;
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
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Persisted roots, physiques, learned techniques and their mastery levels. A technique's level is
 * stored only once it has advanced: a technique the holder never climbed has no entry and stands on its own
 * entry level, so a data pack that moves the entry level moves everyone who never advanced.
 *
 * <p>A held root or physique can also be switched off without being given up, which is what the two
 * {@code disabled_*} sets are for: they are the storage half of the enable/disable module, and they name things
 * the entity still holds. Held and active are therefore two different questions, answered by
 * {@link #spiritRoots()} and {@link #activeSpiritRoots()}: ownership reads the first, everything that gives an
 * effect reads the second. Removing a root or physique drops its toggle with it, so a body never keeps state
 * about something it no longer has.</p>
 */
public final class SpiritIdentityAttachment extends ShouldSyncAttachment {
    public static final MapCodec<SpiritIdentityAttachment> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            CollectionCodecs.list(SpiritRoot.CODEC).optionalFieldOf("spirit_roots", List.of()).forGetter(SpiritIdentityAttachment::spiritRoots),
            CollectionCodecs.list(Physique.CODEC).optionalFieldOf("physiques", List.of()).forGetter(SpiritIdentityAttachment::physiques),
            CollectionCodecs.list(Technique.CODEC).optionalFieldOf("learned_techniques", List.of()).forGetter(SpiritIdentityAttachment::learnedTechniques),
            CollectionCodecs.map(Technique.CODEC, SkillStage.CODEC).optionalFieldOf("technique_stages", Map.of()).forGetter(SpiritIdentityAttachment::techniqueStages),
            CollectionCodecs.set(SpiritRoot.CODEC).optionalFieldOf("disabled_spirit_roots", Set.of()).forGetter(SpiritIdentityAttachment::disabledSpiritRoots),
            CollectionCodecs.set(Physique.CODEC).optionalFieldOf("disabled_physiques", Set.of()).forGetter(SpiritIdentityAttachment::disabledPhysiques)
    ).apply(i, SpiritIdentityAttachment::new));

    private final List<Holder<SpiritRoot>> spiritRoots;
    private final List<Holder<Physique>> physiques;
    private final List<Holder<Technique>> learnedTechniques;
    private final Map<Holder<Technique>, Holder<SkillStage>> techniqueStages;
    private final Set<Holder<SpiritRoot>> disabledSpiritRoots;
    private final Set<Holder<Physique>> disabledPhysiques;

    public SpiritIdentityAttachment() {
        this(List.of(), List.of(), List.of(), Map.of(), Set.of(), Set.of());
    }

    private SpiritIdentityAttachment(List<Holder<SpiritRoot>> spiritRoots, List<Holder<Physique>> physiques,
                                     List<Holder<Technique>> learnedTechniques,
                                     Map<Holder<Technique>, Holder<SkillStage>> techniqueStages,
                                     Set<Holder<SpiritRoot>> disabledSpiritRoots,
                                     Set<Holder<Physique>> disabledPhysiques) {
        this.spiritRoots = new LinkedList<>(spiritRoots);
        this.physiques = new LinkedList<>(physiques);
        this.learnedTechniques = new LinkedList<>(learnedTechniques);
        this.techniqueStages = new LinkedHashMap<>(techniqueStages);
        this.disabledSpiritRoots = new LinkedHashSet<>(disabledSpiritRoots);
        this.disabledPhysiques = new LinkedHashSet<>(disabledPhysiques);
    }

    public List<Holder<SpiritRoot>> spiritRoots() {
        return this.spiritRoots;
    }

    public List<Holder<Physique>> physiques() {
        return this.physiques;
    }

    /**
     * The held roots that are switched on, which is what every effect reads. A root that is held but switched
     * off contributes nothing: no element, no cultivation multiplier, no granted ability.
     */
    public List<Holder<SpiritRoot>> activeSpiritRoots() {
        return this.spiritRoots.stream().filter(root -> !this.disabledSpiritRoots.contains(root)).toList();
    }

    public List<Holder<Physique>> activePhysiques() {
        return this.physiques.stream().filter(physique -> !this.disabledPhysiques.contains(physique)).toList();
    }

    public Set<Holder<SpiritRoot>> disabledSpiritRoots() {
        return Set.copyOf(this.disabledSpiritRoots);
    }

    public Set<Holder<Physique>> disabledPhysiques() {
        return Set.copyOf(this.disabledPhysiques);
    }

    public boolean isSpiritRootEnabled(Holder<SpiritRoot> root) {
        return this.spiritRoots.contains(root) && !this.disabledSpiritRoots.contains(root);
    }

    public boolean isPhysiqueEnabled(Holder<Physique> physique) {
        return this.physiques.contains(physique) && !this.disabledPhysiques.contains(physique);
    }

    /**
     * Switches a held root on or off and answers whether that changed anything. A root the entity does not hold
     * has no state to switch, so nothing happens.
     */
    public boolean setSpiritRootEnabled(Holder<SpiritRoot> root, boolean enabled) {
        if (!this.spiritRoots.contains(root)) return false;
        boolean changed = enabled ? this.disabledSpiritRoots.remove(root) : this.disabledSpiritRoots.add(root);
        if (changed) this.markDirty();
        return changed;
    }

    public boolean setPhysiqueEnabled(Holder<Physique> physique, boolean enabled) {
        if (!this.physiques.contains(physique)) return false;
        boolean changed = enabled ? this.disabledPhysiques.remove(physique) : this.disabledPhysiques.add(physique);
        if (changed) this.markDirty();
        return changed;
    }

    public List<Holder<Technique>> learnedTechniques() {
        return this.learnedTechniques;
    }

    public Map<Holder<Technique>, Holder<SkillStage>> techniqueStages() {
        return this.techniqueStages;
    }

    /**
     * The level the holder has advanced to, or {@code null} while it still stands on the entry level.
     */
    public @Nullable Holder<SkillStage> techniqueStage(Holder<Technique> technique) {
        return this.techniqueStages.get(technique);
    }

    public void setTechniqueStage(Holder<Technique> technique, Holder<SkillStage> stage) {
        if (technique == null || stage == null) return;
        this.techniqueStages.put(technique, stage);
        this.markDirty();
    }

    public void setTechniqueStages(Map<Holder<Technique>, Holder<SkillStage>> values) {
        this.techniqueStages.clear();
        this.techniqueStages.putAll(values);
        this.markDirty();
    }

    public void setSpiritRoots(List<Holder<SpiritRoot>> values) {
        this.spiritRoots.clear();
        this.spiritRoots.addAll(values);
        // A toggle about something the body no longer holds is state nothing can read, so it goes with it.
        this.disabledSpiritRoots.retainAll(this.spiritRoots);
        this.markDirty();
    }

    public void setPhysiques(List<Holder<Physique>> values) {
        this.physiques.clear();
        this.physiques.addAll(values);
        this.disabledPhysiques.retainAll(this.physiques);
        this.markDirty();
    }

    public void setLearnedTechniques(List<Holder<Technique>> values) {
        this.learnedTechniques.clear();
        this.learnedTechniques.addAll(values);
        this.markDirty();
    }

    public void addLearnedTechnique(Holder<Technique> value) {
        if (value != null && !this.learnedTechniques.contains(value)) {
            this.learnedTechniques.add(value);
            this.markDirty();
        }
    }
}
