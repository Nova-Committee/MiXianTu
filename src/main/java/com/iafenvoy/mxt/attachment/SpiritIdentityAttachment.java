package com.iafenvoy.mxt.attachment;

import com.iafenvoy.mxt.data.cultivation.Physique;
import com.iafenvoy.mxt.data.cultivation.SpiritRoot;
import com.iafenvoy.mxt.util.AbstractChangeTrackable;
import com.iafenvoy.mxt.util.codec.CollectionCodecs;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;

import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

/**
 * The spirit roots and physiques one body holds, and which of them are switched off. The two {@code disabled_*}
 * sets name things the entity still holds, so held and active are different questions: ownership reads
 * {@link #spiritRoots()}, every effect reads {@link #activeSpiritRoots()}.
 */
public final class SpiritIdentityAttachment extends AbstractChangeTrackable {
    public static final MapCodec<SpiritIdentityAttachment> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            CollectionCodecs.list(SpiritRoot.CODEC).lenientOptionalFieldOf("spirit_roots", List.of()).forGetter(SpiritIdentityAttachment::spiritRoots),
            CollectionCodecs.list(Physique.CODEC).lenientOptionalFieldOf("physiques", List.of()).forGetter(SpiritIdentityAttachment::physiques),
            CollectionCodecs.set(SpiritRoot.CODEC).lenientOptionalFieldOf("disabled_spirit_roots", Set.of()).forGetter(SpiritIdentityAttachment::disabledSpiritRoots),
            CollectionCodecs.set(Physique.CODEC).lenientOptionalFieldOf("disabled_physiques", Set.of()).forGetter(SpiritIdentityAttachment::disabledPhysiques)
    ).apply(i, SpiritIdentityAttachment::new));
    private final List<Holder<SpiritRoot>> spiritRoots;
    private final List<Holder<Physique>> physiques;
    private final Set<Holder<SpiritRoot>> disabledSpiritRoots;
    private final Set<Holder<Physique>> disabledPhysiques;

    public SpiritIdentityAttachment() {
        this(List.of(), List.of(), Set.of(), Set.of());
    }

    private SpiritIdentityAttachment(List<Holder<SpiritRoot>> spiritRoots, List<Holder<Physique>> physiques,
                                     Set<Holder<SpiritRoot>> disabledSpiritRoots,
                                     Set<Holder<Physique>> disabledPhysiques) {
        this.spiritRoots = new LinkedList<>(spiritRoots);
        this.physiques = new LinkedList<>(physiques);
        this.disabledSpiritRoots = new LinkedHashSet<>(disabledSpiritRoots);
        this.disabledPhysiques = new LinkedHashSet<>(disabledPhysiques);
    }

    public List<Holder<SpiritRoot>> spiritRoots() {
        return this.spiritRoots;
    }

    public List<Holder<Physique>> physiques() {
        return this.physiques;
    }

    // What every effect reads: a root that is held but switched off contributes nothing.
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

    // A root the entity does not hold has no state to switch.
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
}
