package com.iafenvoy.mxt.runtime.creature;

import com.iafenvoy.mxt.data.creature.ContractType;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.util.HolderHelper;
import com.iafenvoy.mxt.util.codec.CollectionCodecs;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryFixedCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Server-wide index of bound creatures, kept on the overworld. What decides anything stays on the creature
 * ({@code mxt:contract}); this list exists so a limit, a listing or a release does not have to walk loaded
 * levels, and so an owner who is offline still loses the row when their beast dies.
 */
public final class BoundBeastsAttachment {
    public static final MapCodec<BoundBeastsAttachment> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            CollectionCodecs.list(Entry.CODEC).lenientOptionalFieldOf("beasts", List.of()).forGetter(BoundBeastsAttachment::stored)
    ).apply(i, BoundBeastsAttachment::new));
    public static final Codec<BoundBeastsAttachment> CODEC = MAP_CODEC.codec();
    private final List<Entry> entries;

    public BoundBeastsAttachment() {
        this(List.of());
    }

    private BoundBeastsAttachment(List<Entry> entries) {
        this.entries = new ArrayList<>(entries);
    }

    public List<Entry> of(UUID owner) {
        return this.entries.stream().filter(entry -> entry.owner().equals(owner)).toList();
    }

    public long count(UUID owner, Holder<ContractType> type) {
        return this.entries.stream().filter(entry -> entry.owner().equals(owner) && sameType(entry.type(), type)).count();
    }

    // One row per beast: binding the same creature again replaces its row instead of doubling the count.
    public void add(Entry entry) {
        this.entries.removeIf(existing -> existing.beast().equals(entry.beast()));
        this.entries.add(entry);
    }

    public boolean remove(UUID beast) {
        return this.entries.removeIf(entry -> entry.beast().equals(beast));
    }

    public void removeOwner(UUID owner) {
        this.entries.removeIf(entry -> entry.owner().equals(owner));
    }

    private List<Entry> stored() {
        return List.copyOf(this.entries);
    }

    // Ids, not holders: the index is read after a world reload, and a holder from another registry instance is
    // not the same object even when it names the same definition.
    private static boolean sameType(Holder<ContractType> left, Holder<ContractType> right) {
        return HolderHelper.id(left).equals(HolderHelper.id(right));
    }

    public record Entry(UUID owner, UUID beast, Holder<ContractType> type, ResourceKey<Level> dimension, long boundAt) {
        public static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
                UUIDUtil.CODEC.fieldOf("owner").forGetter(Entry::owner),
                UUIDUtil.CODEC.fieldOf("beast").forGetter(Entry::beast),
                RegistryFixedCodec.create(MxtResourceKeys.CONTRACT_TYPE).fieldOf("type").forGetter(Entry::type),
                ResourceKey.codec(Registries.DIMENSION).fieldOf("dimension").forGetter(Entry::dimension),
                Codec.LONG.optionalFieldOf("bound_at", -1L).forGetter(Entry::boundAt)
        ).apply(i, Entry::new));
    }
}
