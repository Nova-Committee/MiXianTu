package com.iafenvoy.mxt.attachment;

import com.iafenvoy.mxt.data.cultivation.Technique;
import com.iafenvoy.mxt.data.progression.ProgressionHolder;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.util.AbstractChangeTrackable;
import com.iafenvoy.mxt.util.codec.CollectionCodecs;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.resources.RegistryFixedCodec;

import java.util.LinkedList;
import java.util.List;

/**
 * The techniques a body learned and how far each of them has been climbed. The two belong together because the
 * levels of a technique the body no longer learned are state nothing can read: giving one up drops its record in
 * the same write, which only holds while both live in one attachment.
 */
public final class TechniqueAttachment extends AbstractChangeTrackable {
    public static final MapCodec<TechniqueAttachment> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            CollectionCodecs.list(Technique.CODEC).lenientOptionalFieldOf("learned_techniques", List.of()).forGetter(TechniqueAttachment::learnedTechniques),
            // The absent-field default is one instance every decode shares, so the constructor copies it.
            ProgressionHolder.codec(RegistryFixedCodec.create(MxtResourceKeys.TECHNIQUE)).lenientOptionalFieldOf("technique_levels", new ProgressionHolder<>()).forGetter(TechniqueAttachment::techniqueLevels)
    ).apply(i, TechniqueAttachment::new));
    private final List<Holder<Technique>> learnedTechniques;
    private final ProgressionHolder<Technique> techniqueLevels;

    public TechniqueAttachment() {
        this(List.of(), new ProgressionHolder<>());
    }

    private TechniqueAttachment(List<Holder<Technique>> learnedTechniques, ProgressionHolder<Technique> techniqueLevels) {
        this.learnedTechniques = new LinkedList<>(learnedTechniques);
        this.techniqueLevels = new ProgressionHolder<>(techniqueLevels);
    }

    @Override
    public boolean checkDirty() {
        if (this.techniqueLevels.checkDirty()) this.markDirty();
        return super.checkDirty();
    }

    public List<Holder<Technique>> learnedTechniques() {
        return this.learnedTechniques;
    }

    // Where each learned technique stands in its own chain.
    public ProgressionHolder<Technique> techniqueLevels() {
        return this.techniqueLevels;
    }

    public void setLearnedTechniques(List<Holder<Technique>> values) {
        this.learnedTechniques.clear();
        this.learnedTechniques.addAll(values);
        // A record for a technique the body no longer learned is state nothing can read, so it goes with it.
        this.techniqueLevels.retainOnly(this.learnedTechniques);
        this.markDirty();
    }

    public void addLearnedTechnique(Holder<Technique> value) {
        if (value != null && !this.learnedTechniques.contains(value)) {
            this.learnedTechniques.add(value);
            this.markDirty();
        }
    }
}
