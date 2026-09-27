package com.iafenvoy.mxt.data.alchemy;

import com.iafenvoy.mxt.api.NamedDefinition;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.util.DefinitionText;
import com.iafenvoy.mxt.util.codec.ContextNameCodec;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryFixedCodec;

/**
 * A pack-defined medicinal identity. It is not an element, an aura, or a resource.
 */
public record MedicinalProperty(Component name, Component description) implements NamedDefinition {
    private static final String CATEGORY = DefinitionText.category(MxtResourceKeys.MEDICINAL_PROPERTY.identifier());
    public static final Codec<Holder<MedicinalProperty>> CODEC = RegistryFixedCodec.create(MxtResourceKeys.MEDICINAL_PROPERTY);
    public static final Codec<MedicinalProperty> DIRECT_CODEC = RecordCodecBuilder.create(i -> i.group(
            ContextNameCodec.name(CATEGORY).forGetter(MedicinalProperty::name),
            ContextNameCodec.description(CATEGORY).forGetter(MedicinalProperty::description)
    ).apply(i, MedicinalProperty::new));
}
