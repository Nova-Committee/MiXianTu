package com.iafenvoy.mxt.attachment;

import com.iafenvoy.mxt.data.creature.CreatureProfile;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.util.ShouldSyncAttachment;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.resources.RegistryFixedCodec;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

/**
 * Persistent creature-profile state; it is separate from player cultivation and identity attachments.
 */
public final class CreatureSpiritAttachment extends ShouldSyncAttachment {
    public static final MapCodec<CreatureSpiritAttachment> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            RegistryFixedCodec.create(MxtResourceKeys.CREATURE_PROFILE).lenientOptionalFieldOf("profile").forGetter(CreatureSpiritAttachment::profile),
            Codec.DOUBLE.lenientOptionalFieldOf("intelligence", 0.0D).forGetter(CreatureSpiritAttachment::intelligence),
            ItemStack.CODEC.lenientOptionalFieldOf("inner_core", ItemStack.EMPTY).forGetter(CreatureSpiritAttachment::innerCore)
    ).apply(i, CreatureSpiritAttachment::new));
    private Holder<CreatureProfile> profile;
    private double intelligence;
    private ItemStack innerCore;

    public CreatureSpiritAttachment() {
        this(Optional.empty(), 0.0D, ItemStack.EMPTY);
    }

    private CreatureSpiritAttachment(Optional<Holder<CreatureProfile>> profile, double intelligence, ItemStack innerCore) {
        this.profile = profile.orElse(null);
        if (!Double.isFinite(intelligence) || intelligence < 0.0D)
            throw new IllegalArgumentException("Creature intelligence must be finite and non-negative");
        this.intelligence = intelligence;
        this.innerCore = innerCore;
        this.markDirty();
    }

    public Optional<Holder<CreatureProfile>> profile() {
        return Optional.ofNullable(this.profile);
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
        this.profile = profile;
        this.intelligence = intelligence;
        this.innerCore = innerCore;
        this.markDirty();
    }
}
