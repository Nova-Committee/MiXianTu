package com.iafenvoy.mxt.data.artifact;

import com.iafenvoy.mxt.data.ability.Ability;
import com.iafenvoy.mxt.data.ability.Togglable;
import com.iafenvoy.mxt.data.ability.type.MountAbilityType;
import com.iafenvoy.mxt.data.ability.type.StorageAbilityType;
import com.iafenvoy.mxt.data.ability.type.UpkeepAbilityType;
import com.iafenvoy.mxt.data.aura.Aura;
import com.iafenvoy.mxt.data.cost.Cost;
import com.iafenvoy.mxt.data.cost.builtin.AuraCost;
import com.iafenvoy.mxt.data.cost.builtin.ResourceCost;
import com.iafenvoy.mxt.data.resource.Resource;
import com.iafenvoy.mxt.data.storage.builtin.ContainerDataStorage;
import com.iafenvoy.mxt.runtime.artifact.ArtifactService;
import com.iafenvoy.mxt.runtime.item.ItemStorageService;
import com.iafenvoy.mxt.runtime.spirit.SpiritChargeService;
import com.iafenvoy.mxt.util.DefinitionText;
import com.iafenvoy.mxt.util.HolderHelper;
import com.iafenvoy.mxt.util.PlayerNames;
import com.iafenvoy.mxt.util.TooltipText;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.formula.NumberProvider;
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
 * Tooltip lines for an artifact stack. Every number comes from {@link ArtifactService} so a tooltip can never
 * state a ceiling the game would not honour; client-free so the probe can assert the line keys.
 */
public final class ArtifactDescription {
    private ArtifactDescription() {
    }

    // advanced adds the definition's own id, and the owner's, under the lines they belong to.
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
        appendAbilities(lines, registries, stack, formula);
        appendHold(lines, registries, stack, artifact, player, formula);
        // Nothing is said about Curios here: the slots an item fits are Curios' own tooltip, and repeating them
        // would only be a second list to keep in step with the first.
        return List.copyOf(lines);
    }

    // Searched inside the line, not only at its root: the ownership line appends its sentence to a coloured mark,
    // and falling back to rendered text is what this exists to avoid.
    public static List<String> keys(List<Component> lines) {
        return lines.stream().map(ArtifactDescription::keyOrText).toList();
    }

    private static String keyOrText(Component line) {
        String key = key(line);
        return key == null ? line.getString() : key;
    }

    private static String key(Component line) {
        if (line.getContents() instanceof TranslatableContents translatable) return translatable.getKey();
        for (Component sibling : line.getSiblings()) {
            String nested = key(sibling);
            if (nested != null) return nested;
        }
        return null;
    }

    // A fresh artifact that merely has not been refined says nothing; a name is preferred over the raw uuid.
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

    // Read through ArtifactService so the ceiling is the warmed-up one rather than the raw declaration.
    private static void appendAuras(List<Component> lines, Provider registries, ItemStack stack, Artifact artifact,
                                    FormulaContext formula) {
        artifact.spiritCapacity().keySet().forEach(aura -> {
            int capacity = ArtifactService.capacity(registries, stack, aura, 0.0D, formula);
            double stored = ArtifactService.stored(stack, aura);
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

    // Order is the definition's. Every entry is either named or described by numbers: the three item-side types
    // say what they do with their own line, and everything else is named after the ability it references.
    private static void appendAbilities(List<Component> lines, Provider registries, ItemStack stack,
                                        FormulaContext formula) {
        for (Holder<Ability> entry : ArtifactService.abilities(registries, stack)) {
            Ability ability = entry.value();
            if (ability.hidden()) continue;
            if (ability.type() instanceof MountAbilityType mount) {
                appendMount(lines, mount, ability.costs(), formula);
                continue;
            }
            if (ability.type() instanceof StorageAbilityType) {
                appendStorage(lines, stack, entry, formula);
                continue;
            }
            if (ability.type() instanceof UpkeepAbilityType upkeep) {
                appendUpkeep(lines, upkeep, ability.costs(), formula);
                continue;
            }
            boolean pressable = ability.type() instanceof Togglable;
            lines.add(Component.translatable(pressable ? "tooltip.mxt.artifact.active" : "tooltip.mxt.artifact.passive",
                            ability.name())
                    .withStyle(pressable ? ChatFormatting.AQUA : ChatFormatting.BLUE));
        }
    }

    private static void appendUpkeep(List<Component> lines, UpkeepAbilityType upkeep, List<Cost> costs,
                                     FormulaContext formula) {
        if (costs.isEmpty()) return;
        lines.add(Component.translatable("tooltip.mxt.artifact.upkeep",
                        TooltipText.number(upkeep.interval().evaluate(formula)), costs(costs, formula))
                .withStyle(ChatFormatting.DARK_AQUA));
    }

    // Tooltips have no payer to ask, so the price is read straight off the entry: only the two resource-shaped
    // channels can be spelled out as a number, anything else is named as "other".
    private static MutableComponent costs(List<Cost> costs, FormulaContext formula) {
        List<Component> parts = new ArrayList<>(costs.size() + 1);
        boolean other = false;
        for (Cost cost : costs) {
            if (cost instanceof ResourceCost(
                    Holder<Resource> resource,
                    NumberProvider amount1
            ))
                parts.add(Component.translatable("tooltip.mxt.artifact.resource_cost",
                        TooltipText.number(amount1.evaluate(formula)),
                        DefinitionText.name(resource, "resource")));
            else if (cost instanceof AuraCost(
                    Holder<Aura> aura, NumberProvider amount
            ))
                parts.add(Component.translatable("tooltip.mxt.artifact.resource_cost",
                        TooltipText.number(amount.evaluate(formula)),
                        DefinitionText.name(aura, "aura")));
            else other = true;
        }
        if (other) parts.add(Component.translatable("tooltip.mxt.artifact.resource_cost_other"));
        return TooltipText.join(parts);
    }

    private static void appendMount(List<Component> lines, MountAbilityType mount, List<Cost> costs,
                                    FormulaContext formula) {
        double speed = mount.speed().evaluate(formula);
        Component pose = Component.translatable(mount.sit() ? "tooltip.mxt.artifact.pose_sit" : "tooltip.mxt.artifact.pose_stand");
        if (costs.isEmpty()) {
            lines.add(Component.translatable("tooltip.mxt.artifact.mount", TooltipText.number(speed), mount.seats(), pose)
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
            return;
        }
        lines.add(Component.translatable("tooltip.mxt.artifact.mount_costs",
                        TooltipText.number(speed), mount.seats(), pose, costs(costs, formula))
                .withStyle(ChatFormatting.LIGHT_PURPLE));
    }

    private static void appendStorage(List<Component> lines, ItemStack stack, Holder<Ability> ability,
                                      FormulaContext formula) {
        int slots = ArtifactService.storageSlots(stack, ability, formula);
        // A slot count that evaluates to nothing declares no inventory, so there is nothing to announce.
        if (slots <= 0) return;
        ContainerDataStorage contents = ItemStorageService.get(stack, HolderHelper.id(ability), ContainerDataStorage.class).orElse(null);
        int used = contents == null ? 0 : (int) contents.contents().stream().filter(value -> !value.isEmpty()).count();
        lines.add(Component.translatable("tooltip.mxt.artifact.storage", used, slots).withStyle(ChatFormatting.GOLD));
    }

    // The two halves are mutually exclusive because the gesture is: an unclaimed artifact is claimed by holding
    // it, and only its owner is offered the pour. hold_ticks: 0 advertises nothing, since nothing would answer.
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

    private static MutableComponent indented(Object id) {
        return Component.literal("   " + id).withStyle(ChatFormatting.DARK_GRAY);
    }
}
