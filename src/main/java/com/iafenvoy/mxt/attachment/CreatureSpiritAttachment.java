package com.iafenvoy.mxt.attachment;

import com.iafenvoy.mxt.data.creature.CreatureProfile;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.util.ShouldSyncAttachment;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryFixedCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.storage.loot.LootTable;

import java.util.Optional;

/**
 * Persistent creature-profile state; it is separate from player cultivation and identity attachments.
 */
public final class CreatureSpiritAttachment extends ShouldSyncAttachment {
    public static final MapCodec<CreatureSpiritAttachment> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            RegistryFixedCodec.create(MxtResourceKeys.CREATURE_PROFILE).lenientOptionalFieldOf("profile").forGetter(CreatureSpiritAttachment::profile),
            Codec.DOUBLE.lenientOptionalFieldOf("intelligence", 0.0D).forGetter(CreatureSpiritAttachment::intelligence),
            BuiltInRegistries.ITEM.holderByNameCodec().lenientOptionalFieldOf("inner_core").forGetter(CreatureSpiritAttachment::innerCore),
            ResourceKey.codec(Registries.LOOT_TABLE).lenientOptionalFieldOf("loot_table").forGetter(CreatureSpiritAttachment::lootTable)
    ).apply(i, CreatureSpiritAttachment::new));
    private Holder<CreatureProfile> profile;
    private double intelligence;
    // The inner core is a static-registry item, so a holder is safe to keep. A loot table is not: /reload swaps
    // the reloadable loot registries, so what is kept is the key it is looked up by, the way vanilla save data does.
    private Holder<Item> innerCore;
    private ResourceKey<LootTable> lootTable;

    public CreatureSpiritAttachment() {
        this(Optional.empty(), 0.0D, Optional.empty(), Optional.empty());
    }

    private CreatureSpiritAttachment(Optional<Holder<CreatureProfile>> profile, double intelligence, Optional<Holder<Item>> innerCore, Optional<ResourceKey<LootTable>> lootTable) {
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

    public Optional<Holder<Item>> innerCore() {
        return Optional.ofNullable(this.innerCore);
    }

    public Optional<ResourceKey<LootTable>> lootTable() {
        return Optional.ofNullable(this.lootTable);
    }

    public void apply(Holder<CreatureProfile> profile, double intelligence, Optional<Holder<Item>> innerCore, Optional<ResourceKey<LootTable>> lootTable) {
        if (!Double.isFinite(intelligence) || intelligence < 0.0D)
            throw new IllegalArgumentException("Creature intelligence must be finite and non-negative");
        this.profile = profile;
        this.intelligence = intelligence;
        this.innerCore = innerCore.orElse(null);
        this.lootTable = lootTable.orElse(null);
    }
}
