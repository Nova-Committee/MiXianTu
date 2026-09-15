package com.iafenvoy.mxt.data.item;

import com.iafenvoy.mxt.config.MxtServerConfig;
import com.iafenvoy.mxt.data.Formation;
import com.iafenvoy.mxt.registry.MxtDataComponents;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.util.DefinitionText;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.Holder.Reference;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryFixedCodec;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item.TooltipContext;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipProvider;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.tooltip.TooltipAppender;
import net.neoforged.neoforge.event.RegisterTooltipAppendersEvent;
import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * What a portable formation controller is allowed to run, and which formation it currently runs.
 *
 * <p>Two independent fields. {@link #allowed()} is an allow list for the <em>item</em>: it is a property
 * of the plate, the same on every copy, and it bounds the set of formations the plate can ever bind to
 * or suggest. {@link #formation()} is the <em>selection</em>: which one this particular copy was bound
 * to, and it must be a member of the allow list for the plate to be usable.</p>
 *
 * <p>The allow list is what keeps the candidate set small. Without it, both binding and any future
 * selection screen have to consider every formation in the registry, and nothing about a plate says
 * which of them it could plausibly run. Entries are either a formation id or a {@code #tag}, so a
 * content pack can group its formations once and hand the same group to several plates.</p>
 *
 * <p>An empty allow list is the ambiguous case and is therefore configurable: by default it means
 * "unrestricted", which is what every plate written before this field existed relies on, and the
 * {@code formation_plate.empty_allows_all} server option can turn it into "nothing is allowed" for
 * packs that would rather be explicit.</p>
 */
@EventBusSubscriber(Dist.CLIENT)
public record FormationPlateComponent(List<Allowed> allowed, Optional<Holder<Formation>> formation) implements TooltipProvider {
    public static final FormationPlateComponent EMPTY = new FormationPlateComponent(List.of(), Optional.empty());
    public static final Codec<FormationPlateComponent> CODEC = RecordCodecBuilder.create(i -> i.group(
            Allowed.CODEC.listOf().optionalFieldOf("allowed", List.of()).forGetter(FormationPlateComponent::allowed),
            RegistryFixedCodec.create(MxtResourceKeys.FORMATION).optionalFieldOf("formation").forGetter(FormationPlateComponent::formation)
    ).apply(i, FormationPlateComponent::new));

    /**
     * One entry of the allow list: a formation id, or a tag when written with a leading {@code #}.
     *
     * <p>The tag form is why this is typed rather than a bare id: the two look identical in JSON apart
     * from the prefix, so which one was written has to be remembered, and a tag also has to be checked
     * through {@link Holder#is} rather than by comparing keys.</p>
     */
    public sealed interface Allowed {
        Codec<Allowed> CODEC = Codec.either(Identifier.CODEC, TagKey.codec(MxtResourceKeys.FORMATION)).xmap(
                either -> either.map(Id::new, Tag::new),
                allowed -> switch (allowed) {
                    case Id(Identifier id) -> Either.left(id);
                    case Tag(TagKey<Formation> tag) -> Either.right(tag);
                });

        boolean matches(Holder<Formation> formation);

        record Id(Identifier id) implements Allowed {
            @Override
            public boolean matches(Holder<Formation> formation) {
                return formation.is(this.id);
            }
        }

        record Tag(TagKey<Formation> tag) implements Allowed {
            @Override
            public boolean matches(Holder<Formation> formation) {
                return formation.is(this.tag);
            }
        }
    }

    /**
     * Whether this plate may run the given formation: an empty allow list follows the server option, and
     * anything listed matches by id or by tag.
     */
    public boolean admits(Holder<Formation> candidate) {
        if (this.allowed.isEmpty()) return MxtServerConfig.emptyPlateAllowsAll();
        return this.allowed.stream().anyMatch(entry -> entry.matches(candidate));
    }

    /**
     * Whether a bound plate may still be used to activate. An unbound plate is inert regardless of its
     * allow list, which the plate item reports as its own case.
     */
    public boolean admitsSelection() {
        return this.formation.map(this::admits).orElse(false);
    }

    /**
     * Every formation in the registry this plate admits, in registry order.
     *
     * <p>This exists so that binding and suggestions do not have to enumerate the registry themselves:
     * the answer is a filter over the registry rather than "the whole registry". Disabled entries are
     * left to the caller, because the activation path rejects them with its own message and filtering
     * them here would make the allow list disagree with the registry for no gain.</p>
     */
    public List<Reference<Formation>> admissible(Registry<Formation> registry) {
        return registry.listElements().filter(this::admits).toList();
    }

    /**
     * The bound formation, or empty when this plate carries none.
     */
    public Optional<Holder<Formation>> selected() {
        return this.formation.filter(this::admits);
    }

    @SubscribeEvent
    public static void registerTooltipAppender(RegisterTooltipAppendersEvent event) {
        event.registerComponentAppenderBeforeAll(MxtDataComponents.FORMATION_PLATE, TooltipAppender.createComponentAppender(MxtDataComponents.FORMATION_PLATE.get()));
    }

    @Override
    public void addToTooltip(@NonNull TooltipContext context, Consumer<Component> consumer, @NonNull TooltipFlag flag, @NonNull DataComponentGetter components) {
        consumer.accept(Component.translatable("tooltip.mxt.formation_plate.formation",
                this.formation.map(value -> DefinitionText.name(value, "formation")).orElse(Component.literal("-"))));
        if (this.allowed.isEmpty()) {
            consumer.accept(Component.translatable("tooltip.mxt.formation_plate.allowed.any"));
            return;
        }
        consumer.accept(Component.translatable("tooltip.mxt.formation_plate.allowed", this.allowed.size()));
    }
}
