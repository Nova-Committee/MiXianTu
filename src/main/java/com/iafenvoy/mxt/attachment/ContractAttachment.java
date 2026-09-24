package com.iafenvoy.mxt.attachment;

import com.iafenvoy.mxt.data.creature.ContractBehavior;
import com.iafenvoy.mxt.data.creature.ContractBehaviors;
import com.iafenvoy.mxt.data.creature.ContractType;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.util.ShouldSyncAttachment;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryFixedCodec;

import java.util.Optional;

/**
 * The contract record of one creature, and the only copy of it: policy stays in {@code contract_type}
 * definitions, and the owner-side list of bound creatures is an index, not a second record.
 *
 * <p>The owner is not here. Who owns a contracted creature is answered by the creature's own
 * {@code OwnableEntity} implementation - the framework keeps no owner of its own - so this record only says
 * which contract is in force and where its recall latch stands.</p>
 *
 * <p>{@code recalled} is a one-shot latch rather than a state: the bell sets it, the creature's next tick
 * consumes it, and the framework asks {@code ContractOperations#recall} in between. The order the owner picked is
 * a state, and this is its only copy: stored as an id so a record survives a behaviour class being replaced.</p>
 */
public final class ContractAttachment extends ShouldSyncAttachment {
    public static final MapCodec<ContractAttachment> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            RegistryFixedCodec.create(MxtResourceKeys.CONTRACT_TYPE).lenientOptionalFieldOf("contract_type").forGetter(ContractAttachment::contractType),
            Codec.LONG.lenientOptionalFieldOf("bound_at", -1L).forGetter(ContractAttachment::boundAt),
            Codec.BOOL.lenientOptionalFieldOf("recalled", false).forGetter(ContractAttachment::recalled),
            Codec.LONG.lenientOptionalFieldOf("recall_at", -1L).forGetter(ContractAttachment::recallAt),
            Identifier.CODEC.lenientOptionalFieldOf("behavior").forGetter(ContractAttachment::storedBehavior)
    ).apply(i, ContractAttachment::new));
    private Optional<Holder<ContractType>> contractType;
    private long boundAt;
    private boolean recalled;
    // When the latch was set, which is what the contract type's cooldown is measured from. A stamp rather than
    // a countdown so a reload or a restart cannot freeze or lose it.
    private long recallAt;
    private Optional<Identifier> behavior;

    public ContractAttachment() {
        this(Optional.empty(), -1L, false, -1L, Optional.empty());
    }

    private ContractAttachment(Optional<Holder<ContractType>> contractType, long boundAt, boolean recalled, long recallAt,
                               Optional<Identifier> behavior) {
        this.contractType = contractType;
        this.boundAt = boundAt;
        this.recalled = recalled;
        this.recallAt = recallAt;
        this.behavior = behavior;
    }

    public Optional<Holder<ContractType>> contractType() {
        return this.contractType;
    }

    public long boundAt() {
        return this.boundAt;
    }

    public boolean recalled() {
        return this.recalled;
    }

    public long recallAt() {
        return this.recallAt;
    }

    // The order in force. A record written before orders existed, or one naming a behaviour the content mod no
    // longer provides, reads as the default the framework has always driven rather than as nothing.
    public ContractBehavior behavior() {
        return this.behavior.flatMap(ContractBehaviors::byId).orElse(ContractBehaviors.FOLLOW);
    }

    private Optional<Identifier> storedBehavior() {
        return this.behavior;
    }

    public boolean bound() {
        return this.contractType.isPresent();
    }

    public void bind(Holder<ContractType> type, long gameTime) {
        this.contractType = Optional.of(type);
        this.boundAt = gameTime;
        this.recalled = false;
        this.recallAt = -1L;
        this.behavior = Optional.empty();
        this.markDirty();
    }

    public void setBehavior(ContractBehavior behavior) {
        if (!this.bound()) throw new IllegalStateException("Cannot order an unbound creature");
        this.behavior = Optional.of(behavior.id());
        this.markDirty();
    }

    // Zero or negative cooldown means the bell may be rung as often as the player likes.
    public boolean canRecall(long gameTime, int cooldown) {
        return cooldown <= 0 || this.recallAt < 0L || gameTime - this.recallAt >= cooldown;
    }

    public void requestRecall(long gameTime) {
        if (!this.bound()) throw new IllegalStateException("Cannot recall an unbound creature");
        this.recalled = true;
        this.recallAt = gameTime;
        this.markDirty();
    }

    public void completeRecall() {
        if (!this.recalled) return;
        this.recalled = false;
        this.markDirty();
    }

    public void clear() {
        this.contractType = Optional.empty();
        this.boundAt = -1L;
        this.recalled = false;
        this.recallAt = -1L;
        this.behavior = Optional.empty();
        this.markDirty();
    }
}
