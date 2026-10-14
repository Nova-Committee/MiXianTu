package com.iafenvoy.mxt.attachment;

import com.iafenvoy.mxt.data.creature.CreatureProfile;
import com.iafenvoy.mxt.data.progression.ProgressionHolder;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.util.AbstractChangeTrackable;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.resources.RegistryFixedCodec;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

/**
 * Persistent creature-profile state; it is separate from player cultivation and identity attachments. How far the
 * creature climbed its profile's chain belongs to the profile, so the levels live here rather than in a shared
 * attachment, and a profile and a technique that share an id cannot read each other's records.
 */
public final class CreatureSpiritAttachment extends AbstractChangeTrackable {
    public static final MapCodec<CreatureSpiritAttachment> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            RegistryFixedCodec.create(MxtResourceKeys.CREATURE_PROFILE).lenientOptionalFieldOf("profile").forGetter(CreatureSpiritAttachment::profile),
            Codec.DOUBLE.lenientOptionalFieldOf("intelligence", 0.0D).forGetter(CreatureSpiritAttachment::intelligence),
            ItemStack.CODEC.lenientOptionalFieldOf("inner_core", ItemStack.EMPTY).forGetter(CreatureSpiritAttachment::innerCore),
            ProgressionHolder.codec(RegistryFixedCodec.create(MxtResourceKeys.CREATURE_PROFILE)).lenientOptionalFieldOf("profile_levels", new ProgressionHolder<>()).forGetter(CreatureSpiritAttachment::profileLevels)
    ).apply(i, CreatureSpiritAttachment::new));
    private final ProgressionHolder<CreatureProfile> profileLevels;
    private Holder<CreatureProfile> profile;
    private double intelligence;
    private ItemStack innerCore;

    public CreatureSpiritAttachment() {
        this(Optional.empty(), 0.0D, ItemStack.EMPTY, new ProgressionHolder<>());
    }

    private CreatureSpiritAttachment(Optional<Holder<CreatureProfile>> profile, double intelligence, ItemStack innerCore, ProgressionHolder<CreatureProfile> profileLevels) {
        this.profile = profile.orElse(null);
        if (!Double.isFinite(intelligence) || intelligence < 0.0D)
            throw new IllegalArgumentException("Creature intelligence must be finite and non-negative");
        this.intelligence = intelligence;
        this.innerCore = innerCore;
        this.profileLevels = profileLevels;
        this.markDirty();
    }

    @Override
    public boolean checkDirty() {
        if (this.profileLevels.checkDirty()) this.markDirty();
        return super.checkDirty();
    }

    public Optional<Holder<CreatureProfile>> profile() {
        return Optional.ofNullable(this.profile);
    }

    // Which profile owns a chain is the profile's own business, so only the one this body holds is ever an owner.
    public ProgressionHolder<CreatureProfile> profileLevels() {
        return this.profileLevels;
    }

    public double intelligence() {
        return this.intelligence;
    }

    public ItemStack innerCore() {
        return this.innerCore;
    }

    public void apply(Holder<CreatureProfile> profile, double intelligence, ItemStack innerCore) {
        if (!Double.isFinite(intelligence) || intelligence < 0.0D)
            throw new IllegalArgumentException("Creature intelligence must be finite and non-negative");
        // A profile that is replaced leaves its climb behind: the new profile has its own chain and entry level.
        if (profile != null && !profile.equals(this.profile)) this.profileLevels.clear();
        this.profile = profile;
        this.intelligence = intelligence;
        this.innerCore = innerCore;
        this.markDirty();
    }
}
