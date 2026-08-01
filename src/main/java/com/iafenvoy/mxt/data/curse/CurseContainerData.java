package com.iafenvoy.mxt.data.curse;

import com.iafenvoy.mxt.attachment.CurseHolderData;
import com.iafenvoy.mxt.attachment.CurseHolderData.State;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * ItemStack component for curse instances before they are transferred to an entity.
 */
public record CurseContainerData(Map<Identifier, State> instances) {
    public static final MapCodec<CurseContainerData> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.unboundedMap(Identifier.CODEC, State.CODEC).optionalFieldOf("instances", Map.of()).forGetter(CurseContainerData::instances)
    ).apply(instance, CurseContainerData::decode));
    public static final Codec<CurseContainerData> CODEC = MAP_CODEC.codec();

    public CurseContainerData() {
        this(Map.of());
    }

    public CurseContainerData(Map<Identifier, State> instances) {
        this.instances = new LinkedHashMap<>(instances);
    }

    private static CurseContainerData decode(Map<Identifier, State> instances) {
        return new CurseContainerData(instances);
    }

    @Override
    public Map<Identifier, State> instances() {
        return Map.copyOf(this.instances);
    }

    public CurseContainerData with(Identifier curse, State state) {
        Map<Identifier, State> values = new LinkedHashMap<>(this.instances);
        values.put(curse, state);
        return new CurseContainerData(values);
    }

    public CurseContainerData without(Identifier curse) {
        Map<Identifier, State> values = new LinkedHashMap<>(this.instances);
        values.remove(curse);
        return new CurseContainerData(values);
    }
}
