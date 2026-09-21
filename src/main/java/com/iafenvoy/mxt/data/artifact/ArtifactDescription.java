package com.iafenvoy.mxt.data.artifact;

import com.iafenvoy.mxt.data.ability.Ability;
import com.iafenvoy.mxt.data.artifact.ability.ArtifactAbility;
import com.iafenvoy.mxt.data.artifact.ability.FlightArtifactAbility;
import com.iafenvoy.mxt.data.artifact.ability.GrantArtifactAbility;
import com.iafenvoy.mxt.data.artifact.ability.StorageArtifactAbility;
import com.iafenvoy.mxt.data.artifact.ability.UpkeepArtifactAbility;
import com.iafenvoy.mxt.data.resource.ResourceCost;
import com.iafenvoy.mxt.registry.MxtDataComponents;
import com.iafenvoy.mxt.runtime.artifact.ArtifactService;
import com.iafenvoy.mxt.runtime.spirit.SpiritChargeService;
import com.iafenvoy.mxt.util.DefinitionText;
import com.iafenvoy.mxt.util.HolderHelper;
import com.iafenvoy.mxt.util.PlayerNames;
import com.iafenvoy.mxt.util.TooltipText;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.Holder.Reference;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The lines an artifact explains itself with: which definition claims the stack, how much of each aura it holds
 * against which ceiling, what its {@code abilities} list grants, whether it has been bound to an owner, and what
 * holding it down would do.
 *
 * <p>Kept apart from the tooltip that draws it so the same list can be read where there is no client - the probe
 * asserts its shape - and so the appender stays a registration. Every number comes from
 * {@link ArtifactService}, which is what flight, storage and the item conditions read, so a tooltip can never
 * state a ceiling the game would not honour.</p>
 *
 * <p>A state is reported the way a described binding condition already is (green {@code ✔} when it holds, red
 * {@code ✖} when it does not): ownership is the reason flight, storage and {@code mxt:owned_by} refuse, so it
 * belongs with the conditions rather than with the numbers.</p>
 */
public final class ArtifactDescription {
    private ArtifactDescription() {
    }

    /**
     * Every line for one stack, or none when no definition claims it. {@code advanced} adds the ids an author
     * needs - the definition's own, and the owner's - under the lines they belong to.
     */
    public static List<Component> describe(Provider registries, ItemStack stack, @Nullable Player player, boolean advanced) {
        if (stack.isEmpty()) return List.of();
        Reference<Artifact> holder = ArtifactService.definition(registries, stack).orElse(null);
        if (holder == null) return List.of();
        Artifact artifact = holder.value();
        FormulaContext formula = player == null ? FormulaContext.EMPTY : FormulaContext.of(player);
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("tooltip.mxt.artifact.header", DefinitionText.name(holder)).withStyle(ChatFormatting.GOLD));
        if (advanced) lines.add(indented(HolderHelper.id(holder)));
        appendOwnership(lines, artifact, ArtifactService.state(stack), advanced);
        appendAuras(lines, registries, stack, artifact, formula);
        appendAbilities(lines, registries, stack, artifact, formula);
        appendHold(lines, registries, stack, artifact, player, formula);
        // Nothing is said about Curios here: the slots an item fits are Curios' own tooltip, and repeating them
        // would only be a second list to keep in step with the first.
        return List.copyOf(lines);
    }

    /**
     * The translation key of every line, in order. It is what a check can assert against without a client: the
     * rendered text is a language file's business, the set of lines is this module's.
     *
     * <p>A line is not always one component: the ownership line is a coloured mark with the sentence appended to
     * it, so the key is looked for inside the line rather than only at its root - otherwise such a line would
     * report its own rendered text, which is exactly what this method exists to avoid depending on.</p>
     */
    public static List<String> keys(List<Component> lines) {
        return lines.stream().map(ArtifactDescription::keyOrText).toList();
    }

    /**
     * The key of a line, or its rendered text when it holds no translation at all. Kept apart from
     * {@link #key} so the recursive search can tell "this sibling has no key" from "this line has none".
     */
    private static String keyOrText(Component line) {
        String key = key(line);
        return key == null ? line.getString() : key;
    }

    /** The first translation key anywhere in this component, or {@code null} when it holds none. */
    private static String key(Component line) {
        if (line.getContents() instanceof TranslatableContents translatable) return translatable.getKey();
        for (Component sibling : line.getSiblings()) {
            String nested = key(sibling);
            if (nested != null) return nested;
        }
        return null;
    }

    /**
     * Ownership, and only when it earns a line: an artifact that has an owner says whose it is, one that asks for
     * an owner and has none says why it will refuse, and one that merely has not been refined - the ordinary state
     * of a fresh artifact - says nothing at all.
     *
     * <p>Whose it is is said with a name, because that is what a reader can use: the name recorded when the stack
     * was claimed, or - for a stack claimed before names were kept - one the running server or the current
     * connection still knows. The raw id is the last resort rather than the first, and it stays reachable under
     * advanced tooltips whenever the line above it managed a name.</p>
     */
    private static void appendOwnership(List<Component> lines, Artifact artifact, ArtifactStateComponent state,
                                        boolean advanced) {
        String owner = state.ownerUuid().orElse(null);
        if (owner == null && !artifact.requireOwner()) return;
        MutableComponent mark = Component.literal(owner == null ? "✖ " : "✔ ")
                .withStyle(owner == null ? ChatFormatting.RED : ChatFormatting.GREEN);
        if (owner == null) {
            lines.add(mark.append(Component.translatable("tooltip.mxt.artifact.unowned")
                    .withStyle(ChatFormatting.DARK_RED)));
            return;
        }
        Optional<String> name = state.ownerName().or(() -> PlayerNames.resolve(owner));
        lines.add(mark.append(Component.translatable("tooltip.mxt.artifact.owned", name.orElse(owner))
                .withStyle(ChatFormatting.GRAY)));
        if (advanced && name.isPresent()) lines.add(indented(owner));
    }

    /**
     * One line per declared aura, read through {@link ArtifactService} so the ceiling is the warmed-up one rather
     * than the raw declaration, plus the warmth itself once there is any.
     */
    private static void appendAuras(List<Component> lines, Provider registries, ItemStack stack, Artifact artifact,
                                    FormulaContext formula) {
        artifact.spiritCapacity().keySet().forEach(aura -> {
            int capacity = ArtifactService.capacity(registries, stack, aura, 0.0D, formula);
            int stored = ArtifactService.stored(stack, aura);
            int percentage = capacity <= 0 ? 0 : Math.clamp(Math.round(stored * 100.0D / capacity), 0, 100);
            Component amount = Component.literal(TooltipText.number(stored) + " / " + TooltipText.number(capacity))
                    .withColor(SpiritChargeService.color(percentage));
            lines.add(Component.translatable("tooltip.mxt.artifact.aura", DefinitionText.name(aura, "aura"), amount)
                    .withStyle(ChatFormatting.GRAY));
        });
        double nourishment = ArtifactService.state(stack).nourishment();
        if (nourishment > 0.0D)
            lines.add(Component.translatable("tooltip.mxt.artifact.nourishment", TooltipText.number(nourishment * 100.0D))
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
    }

    /**
     * What the entries grant, in the order the definition writes them. {@code mxt:empty} and any later entry that
     * only marks a slot contribute no line, which is what makes it a placeholder.
     *
     * <p>One entry is one line: an ability that carries numbers of its own keeps them in the same sentence as the
     * ability itself, so a reader counts abilities by counting lines. The one exception is a grant, where each
     * granted ability is the ability being reported and therefore earns its own line.</p>
     */
    private static void appendAbilities(List<Component> lines, Provider registries, ItemStack stack, Artifact artifact,
                                        FormulaContext formula) {
        for (ArtifactAbility ability : artifact.abilities()) {
            if (ability instanceof GrantArtifactAbility grant) appendGrant(lines, registries, grant);
            else if (ability instanceof FlightArtifactAbility flight) appendFlight(lines, flight, formula);
            else if (ability instanceof StorageArtifactAbility) appendStorage(lines, registries, stack, formula);
            else if (ability instanceof UpkeepArtifactAbility upkeep) appendUpkeep(lines, upkeep, formula);
        }
    }

    /**
     * What carrying the artifact costs over time, as one line. An entry that names no resource charges nothing,
     * so it says nothing - the same reading the service takes.
     */
    private static void appendUpkeep(List<Component> lines, UpkeepArtifactAbility upkeep, FormulaContext formula) {
        if (upkeep.costs().isEmpty()) return;
        lines.add(Component.translatable("tooltip.mxt.artifact.upkeep",
                        TooltipText.number(upkeep.interval().evaluate(formula)), costs(upkeep.costs(), formula))
                .withStyle(ChatFormatting.DARK_AQUA));
    }

    /**
     * A price list inside one line: the entries are joined by a translatable separator rather than a hardcoded
     * one, because a list is punctuated differently in every language and this is the only place that has to
     * know how.
     */
    private static MutableComponent costs(List<ResourceCost> costs, FormulaContext formula) {
        MutableComponent result = Component.empty();
        for (int index = 0; index < costs.size(); index++) {
            if (index > 0) result.append(Component.translatable("tooltip.mxt.separator"));
            ResourceCost cost = costs.get(index);
            result.append(Component.translatable("tooltip.mxt.artifact.resource_cost",
                    TooltipText.number(cost.amount().evaluate(formula)), DefinitionText.name(cost.resource(), "resource")));
        }
        return result;
    }

    private static void appendGrant(List<Component> lines, Provider registries, GrantArtifactAbility grant) {
        boolean active = grant.intent() == GrantArtifactAbility.Intent.ACTIVE;
        for (Holder<Ability> granted : ArtifactService.resolveAbilities(registries, grant.abilities()))
            lines.add(Component.translatable(active ? "tooltip.mxt.artifact.active" : "tooltip.mxt.artifact.passive",
                    DefinitionText.name(granted, "ability")).withStyle(active ? ChatFormatting.AQUA : ChatFormatting.BLUE));
    }

    /**
     * Flight as one line: the speed and what riding costs are one sentence, because they are one ability. A
     * definition that declares no price says only the speed.
     */
    private static void appendFlight(List<Component> lines, FlightArtifactAbility flight, FormulaContext formula) {
        double speed = flight.speed().evaluate(formula);
        if (flight.costs().isEmpty()) {
            lines.add(Component.translatable("tooltip.mxt.artifact.flight", TooltipText.number(speed))
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
            return;
        }
        lines.add(Component.translatable("tooltip.mxt.artifact.flight_costs",
                        TooltipText.number(speed), costs(flight.costs(), formula))
                .withStyle(ChatFormatting.LIGHT_PURPLE));
    }

    private static void appendStorage(List<Component> lines, Provider registries, ItemStack stack, FormulaContext formula) {
        int slots = ArtifactService.storageSlots(registries, stack, formula);
        // A slot count that evaluates to nothing declares no inventory, so there is nothing to announce.
        if (slots <= 0) return;
        ArtifactStorageComponent contents = stack.get(MxtDataComponents.ARTIFACT_STORAGE);
        int used = contents == null ? 0 : (int) contents.contents().stream().filter(value -> !value.isEmpty()).count();
        lines.add(Component.translatable("tooltip.mxt.artifact.storage", used, slots).withStyle(ChatFormatting.GOLD));
    }

    /**
     * What the long press does, which is the one thing no number above it states. Both halves are mutually
     * exclusive because the gesture is: an unclaimed artifact is claimed by holding it, and only its owner is
     * offered the pour. A definition that declares no gesture - or one a pack turned off with
     * {@code hold_ticks: 0} - advertises nothing, since nothing would answer the hold. A claim with no declared
     * price says so rather than quoting a zero.
     */
    private static void appendHold(List<Component> lines, Provider registries, ItemStack stack, Artifact artifact,
                                   @Nullable Player player, FormulaContext formula) {
        if (ArtifactService.holdTicks(artifact, formula) <= 0) return;
        if (!ArtifactService.hasOwner(stack)) {
            double cost = ArtifactService.claimHealthCost(registries, stack, formula);
            lines.add(cost > 0.0D
                    ? Component.translatable("tooltip.mxt.artifact.hold_claim", TooltipText.number(cost)).withStyle(ChatFormatting.GRAY)
                    : Component.translatable("tooltip.mxt.artifact.hold_claim_free").withStyle(ChatFormatting.GRAY));
            return;
        }
        if (player != null && ArtifactService.isOwner(stack, player.getUUID()) && !artifact.spiritCapacity().isEmpty())
            lines.add(Component.translatable("tooltip.mxt.artifact.hold_pour").withStyle(ChatFormatting.GRAY));
    }

    /**
     * An id under the line it belongs to, indented to that line's own column and only with advanced tooltips on.
     */
    private static MutableComponent indented(Object id) {
        return Component.literal("   " + id).withStyle(ChatFormatting.DARK_GRAY);
    }
}
