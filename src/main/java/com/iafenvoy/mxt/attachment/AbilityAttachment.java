package com.iafenvoy.mxt.attachment;

import com.iafenvoy.mxt.data.ability.Ability;
import com.iafenvoy.mxt.data.storage.DataStorageHolder;
import com.iafenvoy.mxt.util.ShouldSyncAttachment;
import com.iafenvoy.mxt.util.SourceLedger;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Ability grants are tracked by source, so removing one source cannot remove another source's ability. The state a
 * granted ability keeps lives here too, in a {@link DataStorageHolder} addressed by the ability's id: the values
 * belong to this attachment, so they are saved and synced with it. Revoking the last source drops that state with
 * it, so a re-granted ability does not come back with the charges it had before.
 *
 * <p>Grants and stored state both address an ability by id and keep an id that no longer resolves rather than
 * dropping it, so revoking a definition that was deleted still takes it off. The channelled ability is the one
 * running right now, so it is kept as a holder instead: a definition that was deleted stops the channel on load.
 */
public final class AbilityAttachment extends ShouldSyncAttachment {
    public static final MapCodec<AbilityAttachment> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            SourceLedger.codec(Identifier.CODEC).lenientOptionalFieldOf("sources", new SourceLedger<>()).forGetter(AbilityAttachment::sources),
            Ability.CODEC.lenientOptionalFieldOf("channelled_ability").forGetter(AbilityAttachment::channelledAbility),
            DataStorageHolder.CODEC.lenientOptionalFieldOf("storage").forGetter(attachment -> Optional.of(attachment.storage))
    ).apply(i, AbilityAttachment::new));
    private final SourceLedger<Identifier> sources;
    private final DataStorageHolder storage;
    private Optional<Holder<Ability>> channelledAbility;

    public AbilityAttachment() {
        this(new SourceLedger<>(), Optional.empty(), Optional.empty());
    }

    private AbilityAttachment(SourceLedger<Identifier> sources, Optional<Holder<Ability>> channelledAbility,
                              Optional<DataStorageHolder> storage) {
        this.sources = sources.copy();
        this.channelledAbility = channelledAbility;
        this.storage = storage.orElseGet(DataStorageHolder::new);
    }

    public SourceLedger<Identifier> sources() {
        return this.sources;
    }

    public DataStorageHolder storage() {
        return this.storage;
    }

    public Optional<Holder<Ability>> channelledAbility() {
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
        if (!this.sources.holds(ability)) this.storage.clear(ability);
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

    public void setChannelledAbility(@Nullable Holder<Ability> ability) {
        this.channelledAbility = Optional.ofNullable(ability);
        this.markDirty();
    }

    // A detached draft for validation: never installed on an entity or synchronised, and its storage is a copy as
    // well, so a rejected sequence of writes leaves the real values alone.
    public AbilityAttachment copy() {
        return new AbilityAttachment(this.sources, this.channelledAbility, Optional.of(this.storage.copy()));
    }
}
