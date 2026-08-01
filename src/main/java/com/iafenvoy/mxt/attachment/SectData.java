package com.iafenvoy.mxt.attachment;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

public final class SectData {
    public static final MapCodec<SectData> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Identifier.CODEC.optionalFieldOf("sect").forGetter(SectData::sect), Codec.STRING.optionalFieldOf("rank", "").forGetter(SectData::rank), Codec.INT.optionalFieldOf("contribution", 0).forGetter(SectData::contribution),
            Identifier.CODEC.listOf().optionalFieldOf("completed_tasks", List.of()).forGetter(SectData::completedTasks)
    ).apply(instance, SectData::decode));
    public static final Codec<SectData> CODEC = MAP_CODEC.codec();
    private Identifier sect;
    private String rank;
    private int contribution;
    private final LinkedHashSet<Identifier> completedTasks;

    public SectData() {
        this(Optional.empty(), "", 0, List.of());
    }

    private SectData(Optional<Identifier> sect, String rank, int contribution, List<Identifier> completedTasks) {
        this.sect = sect.orElse(null);
        this.rank = rank;
        this.contribution = contribution;
        this.completedTasks = new LinkedHashSet<>(completedTasks);
    }

    private static SectData decode(Optional<Identifier> sect, String rank, int contribution, List<Identifier> completedTasks) {
        return new SectData(sect, rank, contribution, completedTasks);
    }

    public Optional<Identifier> sect() {
        return Optional.ofNullable(this.sect);
    }

    public String rank() {
        return this.rank;
    }

    public int contribution() {
        return this.contribution;
    }

    public List<Identifier> completedTasks() {
        return List.copyOf(this.completedTasks);
    }

    public boolean member() {
        return this.sect != null;
    }

    public void join(Identifier id, String rank) {
        this.sect = id;
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
