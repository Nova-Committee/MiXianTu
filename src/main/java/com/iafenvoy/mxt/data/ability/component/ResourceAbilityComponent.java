package com.iafenvoy.mxt.data.ability.component;

import com.iafenvoy.mxt.data.ability.AbilityComponent;
import com.iafenvoy.mxt.data.resource.Resource;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.Holder;

public record ResourceAbilityComponent(
        Holder<Resource> resource) implements AbilityComponent {
    public static final MapCodec<ResourceAbilityComponent> CODEC = Resource.CODEC.fieldOf("resource").xmap(ResourceAbilityComponent::new, ResourceAbilityComponent::resource);

    @Override
    public MapCodec<ResourceAbilityComponent> codec() {
        return CODEC;
    }
}
