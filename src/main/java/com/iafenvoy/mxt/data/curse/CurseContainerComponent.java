package com.iafenvoy.mxt.data.curse;

import com.iafenvoy.mxt.attachment.CurseHolderAttachment.State;
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
public record CurseContainerComponent(Map<Holder<Curse>, State> instances) {
    public static final MapCodec<CurseContainerComponent> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            CollectionCodecs.map(Curse.CODEC, State.CODEC).optionalFieldOf("instances", Map.of()).forGetter(CurseContainerComponent::instances)
    ).apply(i, CurseContainerComponent::new));
    public static final Codec<CurseContainerComponent> CODEC = MAP_CODEC.codec();

    public CurseContainerComponent() {
        this(Map.of());
    }

    public CurseContainerComponent with(Holder<Curse> curse, State state) {
        Map<Holder<Curse>, State> values = new LinkedHashMap<>(this.instances);
        values.put(curse, state);
        return new CurseContainerComponent(values);
    }

    public CurseContainerComponent without(Holder<Curse> curse) {
        Map<Holder<Curse>, State> values = new LinkedHashMap<>(this.instances);
        values.remove(curse);
        return new CurseContainerComponent(values);
    }
}
