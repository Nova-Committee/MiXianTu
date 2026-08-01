package com.iafenvoy.mxt.attachment;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;

import java.util.Optional;
import java.util.UUID;

/**
 * Contract state attached to the contracted creature; policy remains in contract_type definitions.
 */
public final class ContractData {
    public static final MapCodec<ContractData> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Identifier.CODEC.optionalFieldOf("contract_type").forGetter(ContractData::contractType), UUIDUtil.CODEC.optionalFieldOf("owner").forGetter(ContractData::owner),
            Codec.LONG.optionalFieldOf("bound_at", -1L).forGetter(ContractData::boundAt), Codec.BOOL.optionalFieldOf("recalled", false).forGetter(ContractData::recalled)
    ).apply(instance, ContractData::decode));
    public static final Codec<ContractData> CODEC = MAP_CODEC.codec();
    private Optional<Identifier> contractType;
    private Optional<UUID> owner;
    private long boundAt;
    private boolean recalled;

    public ContractData() {
        this(Optional.empty(), Optional.empty(), -1L, false);
    }

    private ContractData(Optional<Identifier> contractType, Optional<UUID> owner, long boundAt, boolean recalled) {
        this.contractType = contractType;
        this.owner = owner;
        this.boundAt = boundAt;
        this.recalled = recalled;
    }

    private static ContractData decode(Optional<Identifier> contractType, Optional<UUID> owner, long boundAt, boolean recalled) {
        return new ContractData(contractType, owner, boundAt, recalled);
    }

    public Optional<Identifier> contractType() {
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

    public void bind(Identifier type, UUID owner, long gameTime) {
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
