package com.iafenvoy.mxt.data.quality;

import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.saturn.data.condition.EntityCondition;
import com.iafenvoy.saturn.data.cost.Cost;
import com.iafenvoy.saturn.util.chain.ChainNode;
import com.iafenvoy.saturn.util.codec.ExtraEnumCodecs;
import com.iafenvoy.saturn.util.codec.MiscCodecs;
import com.iafenvoy.saturn.util.codec.OptionalCodecs;
import com.iafenvoy.saturn.util.formula.NumberProvider;
import com.iafenvoy.saturn.util.formula.number.Constant;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryFixedCodec;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * One tier of item quality: what it is called, what its economic, forging and alchemy modifiers are, and where it
 * sits on a ladder. The ladder is declared here rather than in a registry of its own - {@code next} is the tier
 * above this one, {@code quality} names the ladder this tier belongs to, and {@code upgrade_costs} /
 * {@code upgrade_condition} are the price of that one step. The order, the entry tier and the membership rule are
 * all derived from those links at runtime, so a pack writes each tier once.
 *
 * <p>Each modifier's own {@code description} is a field of that modifier and is shown only when a pack writes one.
 * The tier itself carries no text: its name and description are always the keys generated from its own id.
 *
 * <p>{@code color} is optional and is the pack's own answer to "what colour is this tier": every place that names
 * a quality tints with it, and a quality without one is drawn exactly as it always was.
 */
public record ItemQuality(OptionalInt color, Modifier valueMultiplier, Modifier forgingModifier,
                          Modifier alchemyModifier, EntityCondition condition, Optional<Holder<ItemQuality>> next,
                          List<Cost> upgradeCosts, EntityCondition upgradeCondition,
                          Optional<Identifier> quality,
                          List<TooltipModifier> tooltipOrder) implements ChainNode<ItemQuality> {
    public static final Codec<Holder<ItemQuality>> CODEC = RegistryFixedCodec.create(MxtResourceKeys.ITEM_QUALITY);
    public static final Codec<ItemQuality> DIRECT_CODEC = RecordCodecBuilder.create(i -> i.group(
            OptionalCodecs.rgbColorCodec("color").forGetter(ItemQuality::color),
            Modifier.CODEC.optionalFieldOf("value_multiplier", Modifier.DEFAULT).forGetter(ItemQuality::valueMultiplier),
            Modifier.CODEC.optionalFieldOf("forging_modifier", Modifier.DEFAULT).forGetter(ItemQuality::forgingModifier),
            Modifier.CODEC.optionalFieldOf("alchemy_modifier", Modifier.DEFAULT).forGetter(ItemQuality::alchemyModifier),
            EntityCondition.optionalCodec("condition").forGetter(ItemQuality::condition),
            CODEC.optionalFieldOf("next").forGetter(ItemQuality::next),
            Cost.LIST_CODEC.optionalFieldOf("upgrade_costs", List.of()).forGetter(ItemQuality::upgradeCosts),
            EntityCondition.optionalCodec("upgrade_condition").forGetter(ItemQuality::upgradeCondition),
            Identifier.CODEC.optionalFieldOf("quality").forGetter(ItemQuality::quality),
            TooltipModifier.ORDER_CODEC.optionalFieldOf("tooltip_order", TooltipModifier.DEFAULT_ORDER).forGetter(ItemQuality::tooltipOrder)
    ).apply(i, ItemQuality::new));

    public ItemQuality {
        if (!tooltipOrder.equals(TooltipModifier.DEFAULT_ORDER))
            tooltipOrder = Stream.concat(tooltipOrder.stream(), TooltipModifier.DEFAULT_ORDER.stream()).distinct().toList();
        else tooltipOrder = TooltipModifier.DEFAULT_ORDER;
    }

    public void appendModifierDescriptions(Consumer<Component> output) {
        for (TooltipModifier entry : this.tooltipOrder) {
            Component description = entry.modifier.apply(this).description();
            if (!description.getString().isBlank()) output.accept(description);
        }
    }

    public enum TooltipModifier {
        VALUE_MULTIPLIER(ItemQuality::valueMultiplier),
        FORGING_MODIFIER(ItemQuality::forgingModifier),
        ALCHEMY_MODIFIER(ItemQuality::alchemyModifier);

        private static final List<TooltipModifier> DEFAULT_ORDER = List.of(values());
        private static final Codec<List<TooltipModifier>> ORDER_CODEC = ExtraEnumCodecs.enumCodec(TooltipModifier.class)
                .listOf(0, DEFAULT_ORDER.size())
                .validate(order -> order.stream().distinct().count() == order.size()
                        ? DataResult.success(order)
                        : DataResult.error(() -> "tooltip_order contains duplicate quality modifiers"));

        private final Function<ItemQuality, Modifier> modifier;

        TooltipModifier(Function<ItemQuality, Modifier> modifier) {
            this.modifier = modifier;
        }
    }

    public record Modifier(Component description, NumberProvider modifier) {
        public static final Modifier DEFAULT = new Modifier(Component.empty(), new Constant(1.0D));
        public static final Codec<Modifier> CODEC = RecordCodecBuilder.create(i -> i.group(
                MiscCodecs.TRANSLATABLE_COMPONENT.optionalFieldOf("description", Component.empty()).forGetter(Modifier::description),
                NumberProvider.CODEC.optionalFieldOf("modifier", new Constant(1.0D)).forGetter(Modifier::modifier)
        ).apply(i, Modifier::new));
    }
}
