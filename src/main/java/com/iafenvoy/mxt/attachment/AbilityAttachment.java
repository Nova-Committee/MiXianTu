package com.iafenvoy.mxt.attachment;

import com.iafenvoy.mxt.data.storage.DataStorageHolder;
import com.iafenvoy.mxt.util.ShouldSyncAttachment;
import com.iafenvoy.mxt.util.SourceLedger;
import com.iafenvoy.mxt.util.codec.CollectionCodecs;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongMaps;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import net.minecraft.resources.Identifier;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Ability grants are tracked by source, so removing one source cannot remove another source's ability. The state a
 * granted ability keeps lives here too, in a {@link DataStorageHolder} addressed by the ability's id: the values
 * belong to this attachment, so they are saved and synced with it. Revoking the last source drops that state with
 * it, so a re-granted ability does not come back with the charges it had before.
 *
 * <p>The id is the whole address, which is what lets one ability be granted, cooled down and stored the same way
 * whoever handed it out. An id that no longer resolves is kept rather than dropped, so revoking a definition that
 * was deleted still takes it off.
 */
public final class AbilityAttachment extends ShouldSyncAttachment {
    public static final MapCodec<AbilityAttachment> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            SourceLedger.codec(Identifier.CODEC).optionalFieldOf("sources", new SourceLedger<>()).forGetter(AbilityAttachment::sources),
            CollectionCodecs.longMap(Identifier.CODEC).optionalFieldOf("cooldowns", Object2LongMaps.emptyMap()).forGetter(AbilityAttachment::cooldowns),
            Identifier.CODEC.optionalFieldOf("channelled_ability").forGetter(AbilityAttachment::channelledAbility),
            DataStorageHolder.CODEC.optionalFieldOf("storage").forGetter(attachment -> Optional.of(attachment.storage))
    ).apply(i, AbilityAttachment::new));
    private final SourceLedger<Identifier> sources;
    private final Object2LongMap<Identifier> cooldowns;
    private final DataStorageHolder storage;
    private Optional<Identifier> channelledAbility;

    public AbilityAttachment() {
        this(new SourceLedger<>(), Object2LongMaps.emptyMap(), Optional.empty(), Optional.empty());
    }

    private AbilityAttachment(SourceLedger<Identifier> sources, Object2LongMap<Identifier> cooldowns,
                              Optional<Identifier> channelledAbility, Optional<DataStorageHolder> storage) {
        this.sources = sources.copy();
        this.cooldowns = new Object2LongOpenHashMap<>(cooldowns);
        this.channelledAbility = channelledAbility;
        this.storage = storage.orElseGet(DataStorageHolder::new);
        this.storage.ownedBy(this);
    }

    public SourceLedger<Identifier> sources() {
        return this.sources;
    }

    public Object2LongMap<Identifier> cooldowns() {
        return this.cooldowns;
    }

    public DataStorageHolder storage() {
        return this.storage;
    }

    public Optional<Identifier> channelledAbility() {
        return this.channelledAbility;
    }

    public boolean has(Identifier ability) {
        return this.sources.holds(ability);
    }

    public void setSources(Identifier ability, List<Identifier> values) {
        this.sources.drop(ability);
        for (Identifier value : values) this.sources.grant(ability, value);
        this.markDirty();
    }

    public boolean grant(Identifier ability, Identifier source) {
        if (!this.sources.grant(ability, source)) return false;
        this.markDirty();
        return true;
    }

    // When the revoked source was the last one, the state the ability owned goes with it.
    public boolean revoke(Identifier ability, Identifier source) {
        if (!this.sources.revoke(ability, source)) return false;
        if (!this.sources.holds(ability)) {
            this.cooldowns.removeLong(ability);
            this.storage.clear(ability);
        }
        this.markDirty();
        return true;
    }

    // The same rule curses follow: what the source no longer declares is released, what it declares and does not
    // hold yet is granted.
    public boolean reconcileSource(Identifier source, Collection<Identifier> desiredAbilities) {
        if (!this.sources.reconcile(source, desiredAbilities)) return false;
        this.markDirty();
        return true;
    }

    public boolean isOnCooldown(Identifier ability, long gameTime) {
        return this.cooldowns.getOrDefault(ability, -1L) > gameTime;
    }

    public void setCooldownUntil(Identifier ability, long gameTime) {
        this.cooldowns.put(ability, gameTime);
        this.markDirty();
    }

    public void setChannelledAbility(Identifier ability) {
        this.channelledAbility = Optional.ofNullable(ability);
        this.markDirty();
    }

    // A detached draft for validation: never installed on an entity or synchronised, and its storage is a copy as
    // well, so a rejected sequence of writes leaves the real values alone.
    public AbilityAttachment copy() {
        return new AbilityAttachment(this.sources, this.cooldowns, this.channelledAbility, Optional.of(this.storage.copy()));
    }
}
