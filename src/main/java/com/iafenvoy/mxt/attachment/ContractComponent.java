package com.iafenvoy.mxt.attachment;
import com.iafenvoy.mxt.registry.MxtResourceKeys;

import com.iafenvoy.mxt.data.creature.ContractType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.RegistryFixedCodec;

import java.util.Optional;
import java.util.UUID;

/**
 * Contract state attached to the contracted creature; policy remains in contract_type definitions.
 */
public final class ContractComponent {
    public static final MapCodec<ContractComponent> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            RegistryFixedCodec.create(MxtResourceKeys.CONTRACT_TYPE).optionalFieldOf("contract_type").forGetter(ContractComponent::contractType), UUIDUtil.CODEC.optionalFieldOf("owner").forGetter(ContractComponent::owner),
            Codec.LONG.optionalFieldOf("bound_at", -1L).forGetter(ContractComponent::boundAt), Codec.BOOL.optionalFieldOf("recalled", false).forGetter(ContractComponent::recalled)
    ).apply(i, ContractComponent::new));
    private Optional<Holder<ContractType>> contractType;
    private Optional<UUID> owner;
    private long boundAt;
    private boolean recalled;

    public ContractComponent() {
        this(Optional.empty(), Optional.empty(), -1L, false);
    }

    private ContractComponent(Optional<Holder<ContractType>> contractType, Optional<UUID> owner, long boundAt, boolean recalled) {
        this.contractType = contractType;
        this.owner = owner;
        this.boundAt = boundAt;
        this.recalled = recalled;
    }

    public Optional<Holder<ContractType>> contractType() {
        return this.contractType;
    }

    public Optional<UUID> owner() {
        return this.owner;
    }

    public long boundAt() {
        return this.boundAt;
    }

    public boolean recalled() {
        return this.recalled;
    }

    public boolean bound() {
        return this.contractType.isPresent() && this.owner.isPresent();
    }

    public void bind(Holder<ContractType> type, UUID owner, long gameTime) {
        this.contractType = Optional.of(type);
        this.owner = Optional.of(owner);
        this.boundAt = gameTime;
        this.recalled = false;
    }

    public void clear() {
        this.contractType = Optional.empty();
        this.owner = Optional.empty();
        this.boundAt = -1L;
        this.recalled = false;
    }

    public void setRecalled(boolean value) {
        if (!this.bound()) throw new IllegalStateException("Cannot recall an unbound creature");
        this.recalled = value;
    }
}
