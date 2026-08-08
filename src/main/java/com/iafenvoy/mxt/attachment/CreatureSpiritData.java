package com.iafenvoy.mxt.attachment;

import com.iafenvoy.mxt.data.creature.CreatureProfile;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryFixedCodec;

import java.util.Optional;

/**
 * Persistent creature-profile state; it is separate from a creature's optional player-like SpiritData.
 */
public final class CreatureSpiritData {
    public static final MapCodec<CreatureSpiritData> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            RegistryFixedCodec.create(MxtDatapackRegistries.CREATURE_PROFILE).optionalFieldOf("profile").forGetter(CreatureSpiritData::profile),
            Codec.DOUBLE.optionalFieldOf("intelligence", 0.0D).forGetter(CreatureSpiritData::intelligence),
            Identifier.CODEC.optionalFieldOf("inner_core").forGetter(CreatureSpiritData::innerCore),
            Identifier.CODEC.optionalFieldOf("loot_table").forGetter(CreatureSpiritData::lootTable)
    ).apply(instance, CreatureSpiritData::new));

    private Holder<CreatureProfile> profile;
    private double intelligence;
    private Identifier innerCore;
    private Identifier lootTable;

    public CreatureSpiritData() {
        this(Optional.empty(), 0.0D, Optional.empty(), Optional.empty());
    }

    private CreatureSpiritData(Optional<Holder<CreatureProfile>> profile, double intelligence, Optional<Identifier> innerCore, Optional<Identifier> lootTable) {
        this.profile = profile.orElse(null);
        if (!Double.isFinite(intelligence) || intelligence < 0.0D)
            throw new IllegalArgumentException("Creature intelligence must be finite and non-negative");
        this.intelligence = intelligence;
        this.innerCore = innerCore.orElse(null);
        this.lootTable = lootTable.orElse(null);
    }

    public Optional<Holder<CreatureProfile>> profile() {
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

    public void apply(Holder<CreatureProfile> profile, double intelligence, Optional<Identifier> innerCore, Optional<Identifier> lootTable) {
        if (!Double.isFinite(intelligence) || intelligence < 0.0D)
            throw new IllegalArgumentException("Creature intelligence must be finite and non-negative");
        this.profile = profile;
        this.intelligence = intelligence;
        this.innerCore = innerCore.orElse(null);
        this.lootTable = lootTable.orElse(null);
    }
}
