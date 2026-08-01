package com.iafenvoy.mxt.attachment;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

import java.util.Optional;

/**
 * Persistent creature-profile state; it is separate from a creature's optional player-like SpiritData.
 */
public final class CreatureSpiritData {
    public static final MapCodec<CreatureSpiritData> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Identifier.CODEC.optionalFieldOf("profile").forGetter(CreatureSpiritData::profile),
            Codec.DOUBLE.optionalFieldOf("intelligence", 0.0D).forGetter(CreatureSpiritData::intelligence),
            Identifier.CODEC.optionalFieldOf("inner_core").forGetter(CreatureSpiritData::innerCore),
            Identifier.CODEC.optionalFieldOf("loot_table").forGetter(CreatureSpiritData::lootTable)
    ).apply(instance, CreatureSpiritData::decode));
    public static final Codec<CreatureSpiritData> CODEC = MAP_CODEC.codec();

    private Identifier profile;
    private double intelligence;
    private Identifier innerCore;
    private Identifier lootTable;

    public CreatureSpiritData() {
        this(Optional.empty(), 0.0D, Optional.empty(), Optional.empty());
    }

    private CreatureSpiritData(Optional<Identifier> profile, double intelligence, Optional<Identifier> innerCore, Optional<Identifier> lootTable) {
        this.profile = profile.orElse(null);
        if (!Double.isFinite(intelligence) || intelligence < 0.0D)
            throw new IllegalArgumentException("Creature intelligence must be finite and non-negative");
        this.intelligence = intelligence;
        this.innerCore = innerCore.orElse(null);
        this.lootTable = lootTable.orElse(null);
    }

    private static CreatureSpiritData decode(Optional<Identifier> profile, double intelligence, Optional<Identifier> innerCore, Optional<Identifier> lootTable) {
        return new CreatureSpiritData(profile, intelligence, innerCore, lootTable);
    }

    public Optional<Identifier> profile() {
        return Optional.ofNullable(this.profile);
    }

    public double intelligence() {
        return this.intelligence;
    }

    public Optional<Identifier> innerCore() {
        return Optional.ofNullable(this.innerCore);
    }

    public Optional<Identifier> lootTable() {
        return Optional.ofNullable(this.lootTable);
    }

    public void apply(Identifier profile, double intelligence, Optional<Identifier> innerCore, Optional<Identifier> lootTable) {
        if (!Double.isFinite(intelligence) || intelligence < 0.0D)
            throw new IllegalArgumentException("Creature intelligence must be finite and non-negative");
        this.profile = profile;
        this.intelligence = intelligence;
        this.innerCore = innerCore.orElse(null);
        this.lootTable = lootTable.orElse(null);
    }
}
