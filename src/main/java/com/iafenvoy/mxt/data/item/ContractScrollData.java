package com.iafenvoy.mxt.data.item;
import com.iafenvoy.mxt.registry.MxtResourceKeys;

import com.iafenvoy.mxt.data.creature.ContractType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.resources.RegistryFixedCodec;

import java.util.Optional;

/**
 * Datapack-selected contract policy carried by a reusable contract scroll.
 */
public record ContractScrollData(Optional<Holder<ContractType>> contractType) {
    public static final ContractScrollData EMPTY = new ContractScrollData(Optional.empty());
    public static final Codec<ContractScrollData> CODEC = RecordCodecBuilder.create(i -> i.group(
            RegistryFixedCodec.create(MxtResourceKeys.CONTRACT_TYPE).optionalFieldOf("contract_type").forGetter(ContractScrollData::contractType)
    ).apply(i, ContractScrollData::new));
}
