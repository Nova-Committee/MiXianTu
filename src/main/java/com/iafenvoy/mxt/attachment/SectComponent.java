package com.iafenvoy.mxt.attachment;
import com.iafenvoy.mxt.registry.MxtResourceKeys;

import com.iafenvoy.mxt.data.Sect;
import com.iafenvoy.mxt.util.codec.CollectionCodecs;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryFixedCodec;

import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

public final class SectComponent {
    public static final MapCodec<SectComponent> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            RegistryFixedCodec.create(MxtResourceKeys.SECT).optionalFieldOf("sect").forGetter(SectComponent::sect), Codec.STRING.optionalFieldOf("rank", "").forGetter(SectComponent::rank), Codec.INT.optionalFieldOf("contribution", 0).forGetter(SectComponent::contribution),
            CollectionCodecs.set(Identifier.CODEC).optionalFieldOf("completed_tasks", Set.of()).forGetter(SectComponent::completedTasks)
    ).apply(i, SectComponent::new));
    private Holder<Sect> sect;
    private String rank;
    private int contribution;
    private final Set<Identifier> completedTasks;

    public SectComponent() {
        this(Optional.empty(), "", 0, Set.of());
    }

    private SectComponent(Optional<Holder<Sect>> sect, String rank, int contribution, Set<Identifier> completedTasks) {
        this.sect = sect.orElse(null);
        this.rank = rank;
        this.contribution = contribution;
        this.completedTasks = new LinkedHashSet<>(completedTasks);
    }
    
    public Optional<Holder<Sect>> sect() {
        return Optional.ofNullable(this.sect);
    }

    public String rank() {
        return this.rank;
    }

    public int contribution() {
        return this.contribution;
    }

    public Set<Identifier> completedTasks() {
        return this.completedTasks;
    }

    public boolean member() {
        return this.sect != null;
    }

    public void join(Holder<Sect> sect, String rank) {
        this.sect = sect;
        this.rank = rank;
        this.contribution = 0;
        this.completedTasks.clear();
    }

    public void leave() {
        this.sect = null;
        this.rank = "";
        this.contribution = 0;
        this.completedTasks.clear();
    }

    public void addContribution(int amount) {
        if (amount < 0 || this.contribution > Integer.MAX_VALUE - amount)
            throw new IllegalArgumentException("Invalid contribution");
        this.contribution += amount;
    }

    public void setRank(String rank) {
        this.rank = rank;
    }

    public boolean completeTask(Identifier task) {
        return this.completedTasks.add(task);
    }

    public boolean hasCompletedTask(Identifier task) {
        return this.completedTasks.contains(task);
    }

    public boolean consumeContribution(int amount) {
        if (amount < 0 || this.contribution < amount) return false;
        this.contribution -= amount;
        return true;
    }
}
