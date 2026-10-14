package com.iafenvoy.mxt.attachment;

import com.iafenvoy.mxt.data.ability.Ability;
import com.iafenvoy.mxt.data.storage.DataStorageHolder;
import com.iafenvoy.mxt.data.storage.builtin.CooldownDataStorage;
import com.iafenvoy.mxt.util.AbstractChangeTrackable;
import com.iafenvoy.mxt.util.SourceLedger;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.*;

/**
 * Ability grants are tracked by source, so removing one source cannot remove another source's ability. The state a
 * granted ability keeps lives here too, in a {@link DataStorageHolder} addressed by the ability's own holder: the
 * values belong to this attachment, so they are saved and synced with it. Revoking the last source drops that state
 * with it - the cooldown excepted, which keeps running so that putting the item back cannot buy the press again - so
 * a re-granted ability does not come back with the charges it had before.
 *
 * <p>Grants and stored state are both addressed by the ability's holder, because that is what an ability is: both
 * name one entry of {@code mxt:ability} and nothing else does. A definition that leaves the pack is therefore
 * dropped at load (one logged line) rather than kept, which is the answer every other holder-keyed index gives. What
 * is <em>not</em> a holder is the source a grant came from - {@code mxt:grant/...} is written by the systems that
 * grant, not by a registry - so the ledger's values stay ids; the channelled ability is a holder for the same reason
 * everything else here is.
 */
public final class AbilityAttachment extends AbstractChangeTrackable {
    public static final MapCodec<AbilityAttachment> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            SourceLedger.codec(Ability.CODEC).lenientOptionalFieldOf("sources", new SourceLedger<>()).forGetter(AbilityAttachment::sources),
            Ability.CODEC.lenientOptionalFieldOf("channelled_ability").forGetter(AbilityAttachment::channelledAbility),
            DataStorageHolder.CODEC.lenientOptionalFieldOf("storage").forGetter(attachment -> Optional.of(attachment.storage))
    ).apply(i, AbilityAttachment::new));
    private final SourceLedger<Holder<Ability>> sources;
    private final DataStorageHolder storage;
    private Optional<Holder<Ability>> channelledAbility;

    public AbilityAttachment() {
        this(new SourceLedger<>(), Optional.empty(), Optional.empty());
    }

    private AbilityAttachment(SourceLedger<Holder<Ability>> sources, Optional<Holder<Ability>> channelledAbility, Optional<DataStorageHolder> storage) {
        this.sources = sources.copy();
        this.channelledAbility = channelledAbility;
        this.storage = storage.orElseGet(DataStorageHolder::new);
    }

    public SourceLedger<Holder<Ability>> sources() {
        return this.sources;
    }

    public DataStorageHolder storage() {
        return this.storage;
    }

    public Optional<Holder<Ability>> channelledAbility() {
        return this.channelledAbility;
    }

    public boolean has(Holder<Ability> ability) {
        return this.sources.holds(ability);
    }

    public void setSources(Holder<Ability> ability, List<Identifier> values) {
        this.sources.drop(ability);
        for (Identifier value : values) this.sources.grant(ability, value);
        this.markDirty();
    }

    public boolean grant(Holder<Ability> ability, Identifier source) {
        if (!this.sources.grant(ability, source)) return false;
        this.markDirty();
        return true;
    }

    // When the revoked source was the last one, the state the ability owned goes with it, except the cooldown: a
    // weapon that leaves the hand must not hand its skill back ready.
    public boolean revoke(Holder<Ability> ability, Identifier source) {
        if (!this.sources.revoke(ability, source)) return false;
        if (!this.sources.holds(ability)) this.storage.clearExcept(ability, CooldownDataStorage.class);
        this.markDirty();
        return true;
    }

    // What the source no longer declares is released and what it declares and does not hold yet is granted; an
    // ability that lost its last source drops its state here too, so this path cleans up exactly like revoke.
    public boolean reconcileSource(Identifier source, Collection<Holder<Ability>> desiredAbilities) {
        Set<Holder<Ability>> released = new LinkedHashSet<>();
        if (!this.sources.reconcile(source, desiredAbilities, released)) return false;
        for (Holder<Ability> ability : released)
            if (!this.sources.holds(ability)) this.storage.clearExcept(ability, CooldownDataStorage.class);
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
