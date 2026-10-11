package com.iafenvoy.mxt.data.alchemy;

import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.Holder;
import net.minecraft.resources.RegistryFixedCodec;

/**
 * A pack-defined medicinal identity, not an element, an aura or a resource: it declares no data, only its id.
 * The codec must build a fresh instance per decode; a shared one collides on the second entry and aborts the load.
 */
public record MedicinalProperty() {
    public static final Codec<Holder<MedicinalProperty>> CODEC = RegistryFixedCodec.create(MxtResourceKeys.MEDICINAL_PROPERTY);
    public static final Codec<MedicinalProperty> DIRECT_CODEC = MapCodec.unit(MedicinalProperty::new).codec();
}
