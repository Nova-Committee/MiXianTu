package com.iafenvoy.mxt.attachment;

import com.iafenvoy.mxt.util.ShouldSyncAttachment;
import com.iafenvoy.mxt.registry.MxtResourceKeys;

import com.iafenvoy.mxt.data.creature.CreatureProfile;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryFixedCodec;

import java.util.Optional;

/**
 * Persistent creature-profile state; it is separate from player cultivation and identity attachments.
 */
public final class CreatureSpiritAttachment extends ShouldSyncAttachment {
    public static final MapCodec<CreatureSpiritAttachment> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            RegistryFixedCodec.create(MxtResourceKeys.CREATURE_PROFILE).optionalFieldOf("profile").forGetter(CreatureSpiritAttachment::profile),
            Codec.DOUBLE.optionalFieldOf("intelligence", 0.0D).forGetter(CreatureSpiritAttachment::intelligence),
            Identifier.CODEC.optionalFieldOf("inner_core").forGetter(CreatureSpiritAttachment::innerCore),
            Identifier.CODEC.optionalFieldOf("loot_table").forGetter(CreatureSpiritAttachment::lootTable)
    ).apply(i, CreatureSpiritAttachment::new));
    private Holder<CreatureProfile> profile;
    private double intelligence;
    private Identifier innerCore, lootTable;

    public CreatureSpiritAttachment() {
        this(Optional.empty(), 0.0D, Optional.empty(), Optional.empty());
    }

    private CreatureSpiritAttachment(Optional<Holder<CreatureProfile>> profile, double intelligence, Optional<Identifier> innerCore, Optional<Identifier> lootTable) {
        this.profile = profile.orElse(null);
        if (!Double.isFinite(intelligence) || intelligence < 0.0D)
            throw new IllegalArgumentException("Creature intelligence must be finite and non-negative");
        this.intelligence = intelligence;
        this.innerCore = innerCore.orElse(null);
        this.lootTable = lootTable.orElse(null);
        this.markDirty();
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
