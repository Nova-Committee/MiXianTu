package com.iafenvoy.mxt.attachment;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import com.iafenvoy.mxt.data.ability.Ability;
import com.iafenvoy.mxt.data.storage.DataStorageHolder;
import com.iafenvoy.mxt.util.HolderHelper;
import com.iafenvoy.mxt.util.ShouldSyncAttachment;
import com.iafenvoy.mxt.util.codec.CollectionCodecs;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongMaps;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;

import java.util.*;
import java.util.Map.Entry;
import java.util.stream.Collectors;

/**
 * Ability grants are tracked by source, so removing one source cannot remove another source's ability.
 *
 * <p>The state a granted ability keeps lives here too, in a {@link DataStorageHolder} addressed by the ability's
 * id: the values belong to the attachment that owns the ability, so they are saved and synced with it rather than
 * in a store every family shares. Revoking the ability's last source drops that state with it, so a re-granted
 * ability does not come back with the charges it had before.</p>
 */
public final class AbilityAttachment extends ShouldSyncAttachment {
    public static final MapCodec<AbilityAttachment> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            CollectionCodecs.multiMap(Ability.CODEC, Identifier.CODEC).optionalFieldOf("sources", ImmutableMultimap.of()).forGetter(AbilityAttachment::sources),
            CollectionCodecs.longMap(Ability.CODEC).optionalFieldOf("cooldowns", Object2LongMaps.emptyMap()).forGetter(AbilityAttachment::cooldowns),
            Ability.CODEC.optionalFieldOf("channelled_ability").forGetter(AbilityAttachment::channelledAbility),
            DataStorageHolder.CODEC.optionalFieldOf("storage").forGetter(attachment -> Optional.of(attachment.storage))
    ).apply(i, AbilityAttachment::new));
    private final Multimap<Holder<Ability>, Identifier> sources;
    private final Object2LongMap<Holder<Ability>> cooldowns;
    private final DataStorageHolder storage;
    private Optional<Holder<Ability>> channelledAbility;

    public AbilityAttachment() {
        this(ArrayListMultimap.create(), Object2LongMaps.emptyMap(), Optional.empty(), Optional.empty());
    }

    private AbilityAttachment(Multimap<Holder<Ability>, Identifier> sources, Object2LongMap<Holder<Ability>> cooldowns,
                              Optional<Holder<Ability>> channelledAbility, Optional<DataStorageHolder> storage) {
        this.sources = ArrayListMultimap.create(sources);
        this.cooldowns = new Object2LongOpenHashMap<>(cooldowns);
        this.channelledAbility = channelledAbility;
        this.storage = storage.orElseGet(DataStorageHolder::new);
        this.storage.ownedBy(this);
    }

    public Multimap<Holder<Ability>, Identifier> sources() {
        return this.sources;
    }

    public Object2LongMap<Holder<Ability>> cooldowns() {
        return this.cooldowns;
    }

    /**
     * The state every granted ability keeps, addressed by the ability's id.
     */
    public DataStorageHolder storage() {
        return this.storage;
    }

    public Optional<Holder<Ability>> channelledAbility() {
        return this.channelledAbility;
    }

    public boolean has(Holder<Ability> ability) {
        return this.sources.containsKey(ability);
    }

    public void setSources(Holder<Ability> ability, List<Identifier> values) {
        if (values.isEmpty()) this.sources.removeAll(ability);
        else {
            this.sources.removeAll(ability);
            this.sources.putAll(ability, values);
        }
        this.markDirty();
    }

    public boolean grant(Holder<Ability> ability, Identifier source) {
        if (this.sources.containsEntry(ability, source)) return false;
        this.sources.put(ability, source);
        this.markDirty();
        return true;
    }

    /**
     * Removes one source of an ability and, when it was the last one, drops the state the ability owned.
     */
    public boolean revoke(Holder<Ability> ability, Identifier source) {
        if (!this.sources.remove(ability, source)) return false;
        if (!this.sources.containsKey(ability)) {
            this.cooldowns.removeLong(ability);
            this.storage.clear(HolderHelper.id(ability));
        }
        this.markDirty();
        return true;
    }

    public boolean reconcileSource(Identifier source, Collection<Holder<Ability>> desiredAbilities) {
        Set<Holder<Ability>> desired = new LinkedHashSet<>(desiredAbilities);
        Set<Holder<Ability>> previous = this.sources.entries().stream().filter(entry -> entry.getValue().equals(source)).map(Entry::getKey).collect(Collectors.toSet());
        boolean changed = false;
        for (Holder<Ability> ability : previous) {
            if (!desired.contains(ability)) changed |= this.revoke(ability, source);
        }
        for (Holder<Ability> ability : desired) {
            if (!previous.contains(ability)) changed |= this.grant(ability, source);
        }
        return changed;
    }

    public boolean isOnCooldown(Holder<Ability> ability, long gameTime) {
        return this.cooldowns.getOrDefault(ability, -1L) > gameTime;
    }

    public void setCooldownUntil(Holder<Ability> ability, long gameTime) {
        this.cooldowns.put(ability, gameTime);
        this.markDirty();
    }

    public void setChannelledAbility(Holder<Ability> ability) {
        this.channelledAbility = Optional.ofNullable(ability);
        this.markDirty();
    }

    /**
     * Creates a detached draft for validation. It is never installed on an entity or synchronised, and its
     * storage is a copy as well, so a rejected sequence of writes leaves the real values alone.
     */
    public AbilityAttachment copy() {
        return new AbilityAttachment(this.sources, this.cooldowns, this.channelledAbility, Optional.of(this.storage.copy()));
    }
}
