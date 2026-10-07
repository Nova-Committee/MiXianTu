package com.iafenvoy.mxt.data;

import com.iafenvoy.mxt.api.NamedDefinition;
import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.data.cost.Cost;
import com.iafenvoy.mxt.data.talisman.TalismanType;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.util.DefinitionText;
import com.iafenvoy.mxt.util.codec.ContextNameCodec;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryFixedCodec;

import java.util.List;

/**
 * One inscribed talisman a carrier can hold. What every invocation shares lives here - how it is named, the gate its
 * holder has to pass, what it costs and how many invocations' worth of aura the carrier stores for it - while the
 * effect itself belongs to the {@code type}: a type owns the target set it lands on and its own parameters, so this
 * record declares no action field.
 *
 * <p>{@code capacity} is a multiplier of one invocation's aura entries, at least one, and it is a property of the
 * carrier rather than of a use, so it stays here. An aura entry of {@code costs} is drawn from that store - which is
 * what lets a carrier written with room for several invocations fire again without being poured - while every other
 * entry is paid by the holder, and the multiplier is capped by the wear the carrier has left. {@code condition} is
 * asked of the holder before {@code costs} are planned, so an inscription that says "not now" refuses the carrier
 * before anything is paid for it. {@code type} is required: an inscription with no use at all is not something a
 * pack should be able to write by accident, and {@code mxt:empty} is there for one that means to do nothing.
 */
public record Talisman(Component name, Component description, TalismanType type, double capacity,
                       EntityCondition condition, List<Cost> costs) implements NamedDefinition {
    private static final String CATEGORY = DefinitionText.category(MxtResourceKeys.TALISMAN.identifier());
    public static final Codec<Holder<Talisman>> CODEC = RegistryFixedCodec.create(MxtResourceKeys.TALISMAN);
    public static final Codec<Talisman> DIRECT_CODEC = RecordCodecBuilder.create(i -> i.group(
            ContextNameCodec.name(CATEGORY).forGetter(Talisman::name),
            ContextNameCodec.description(CATEGORY).forGetter(Talisman::description),
            TalismanType.CODEC.forGetter(Talisman::type),
            Codec.doubleRange(1.0D, Double.MAX_VALUE).optionalFieldOf("capacity", 1.0D).forGetter(Talisman::capacity),
            EntityCondition.optionalCodec("condition").forGetter(Talisman::condition),
            Cost.LIST_CODEC.optionalFieldOf("costs", List.of()).forGetter(Talisman::costs)
    ).apply(i, Talisman::new));
}
