package com.iafenvoy.mxt.data.curse;

import com.iafenvoy.mxt.attachment.CurseHolderData.State;
import com.iafenvoy.mxt.util.codec.CollectionCodecs;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * ItemStack component for curse instances before they are transferred to an entity.
 */
public record CurseContainerData(Map<Holder<Curse>, State> instances) {
    public static final MapCodec<CurseContainerData> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            CollectionCodecs.map(Curse.CODEC, State.CODEC).optionalFieldOf("instances", Map.of()).forGetter(CurseContainerData::instances)
    ).apply(i, CurseContainerData::new));
    public static final Codec<CurseContainerData> CODEC = MAP_CODEC.codec();

    public CurseContainerData() {
        this(Map.of());
    }

    public CurseContainerData with(Holder<Curse> curse, State state) {
        Map<Holder<Curse>, State> values = new LinkedHashMap<>(this.instances);
        values.put(curse, state);
        return new CurseContainerData(values);
    }

    public CurseContainerData without(Holder<Curse> curse) {
        Map<Holder<Curse>, State> values = new LinkedHashMap<>(this.instances);
        values.remove(curse);
        return new CurseContainerData(values);
    }
}
