package com.iafenvoy.mxt.runtime.wheel;

import com.iafenvoy.mxt.attachment.AbilityAttachment;
import com.iafenvoy.mxt.attachment.WheelLayoutAttachment;
import com.iafenvoy.mxt.compat.CuriosIntegration;
import com.iafenvoy.mxt.data.ability.Abilities;
import com.iafenvoy.mxt.data.ability.Ability;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.runtime.ability.AbilityActivationService;
import com.iafenvoy.mxt.runtime.artifact.ArtifactService;
import com.iafenvoy.mxt.runtime.creature.ContractBells;
import com.iafenvoy.mxt.util.HolderHelper;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * What one wheel page holds, read the same way on both sides: the configured page answers from the player's saved
 * layout, a derived page from the abilities the equipment it names grants right now.
 *
 * <p>A derived page is never stored - its entries are the grants in force at this moment, which is what makes an
 * item's page follow the item. Which item an ability came from is answered here too, because a press has to hand
 * the carrier to the ability that acts on it.
 */
public final class WheelSources {
    private WheelSources() {
    }

    // In id order, so the same moment always gives the same slot. Only abilities that can be pressed, since they
    // are the only kind the wheel can trigger. Deliberately not cut to one page - how many entries fit is the
    // client's business, and all the server asks is whether one id is in it.
    public static List<Holder<Ability>> abilities(LivingEntity entity, WheelSource source) {
        List<Identifier> sources = source.grantSources(entity);
        if (sources.isEmpty()) return List.of();
        AbilityAttachment holder = entity.getExistingData(MxtAttachments.ABILITY_HOLDER).orElse(null);
        if (holder == null) return List.of();
        return abilities(entity).stream()
                .filter(ability -> holder.sources().of(HolderHelper.id(ability)).stream().anyMatch(sources::contains))
                .toList();
    }

    // Every pressable ability the player holds, whatever granted it. This is what the configured page's pool
    // offers, and it is read from the ledger rather than from the equipment, so a cell the player pinned stays
    // valid wherever the thing that grants it happens to be.
    public static List<Holder<Ability>> abilities(LivingEntity entity) {
        AbilityAttachment holder = entity.getExistingData(MxtAttachments.ABILITY_HOLDER).orElse(null);
        if (holder == null) return List.of();
        LinkedHashSet<Holder<Ability>> found = new LinkedHashSet<>();
        holder.sources().keys().stream()
                .sorted(Comparator.comparing(Identifier::toString))
                .forEach(id -> Abilities.resolve(entity.level().registryAccess(), id)
                        .filter(AbilityActivationService::togglable)
                        .ifPresent(found::add));
        return List.copyOf(found);
    }

    // The stack an ability acts on, for one page: the first item the page names that offers it. Empty when the
    // ability came from somewhere else entirely (a book, a command), which an item-side ability reports as
    // NO_CARRIER instead of guessing.
    public static Optional<ItemStack> carrier(LivingEntity entity, WheelSource source, Identifier abilityId) {
        return Optional.ofNullable(carriers(entity, source).get(abilityId));
    }

    // Every ability this page's equipment offers, mapped to the stack offering it, in one pass over the stacks:
    // resolving a definition walks the artifact registry, so a lookup per ability would repeat that walk for
    // every cell of the page.
    public static Map<Identifier, ItemStack> carriers(LivingEntity entity, WheelSource source) {
        Map<Identifier, ItemStack> found = new LinkedHashMap<>();
        for (ItemStack stack : equipment(entity, source)) {
            if (stack.isEmpty()) continue;
            for (Holder<Ability> ability : ArtifactService.abilities(entity.level().registryAccess(), stack))
                found.putIfAbsent(HolderHelper.id(ability), stack);
        }
        return found;
    }

    // These stacks are what an item-side ability is read from; the ability list is read from the grant ledger.
    public static List<ItemStack> equipment(LivingEntity entity, WheelSource source) {
        return switch (source) {
            case CONFIGURED -> {
                List<ItemStack> carried = new ArrayList<>();
                carried.add(entity.getMainHandItem());
                carried.add(entity.getOffhandItem());
                carried.addAll(CuriosIntegration.equipped(entity));
                yield List.copyOf(carried);
            }
            case MAIN_HAND -> List.of(entity.getMainHandItem());
            case OFF_HAND -> List.of(entity.getOffhandItem());
            case CURIOS -> CuriosIntegration.equipped(entity);
            // The bell itself, which is what the contract page is read from; it grants no abilities.
            case CONTRACT -> List.of(entity.getMainHandItem(), entity.getOffhandItem());
        };
    }

    // The check behind every trigger: the id alone is not enough, because the same ability can come from something
    // the player no longer has. An order is checked against the bell's tuned beast the same way - the creature it
    // names is what decides whether the order is still reachable, and the service behind it checks the record.
    public static boolean offers(LivingEntity entity, WheelSource source, WheelEntryKind kind, Identifier id) {
        if (kind == null || id == null || !kind.holdsEntry()) return false;
        if (source == WheelSource.CONTRACT)
            return kind == WheelEntryKind.BEHAVIOR
                    && ContractBells.selection(entity).map(selection -> selection.offers(id)).orElse(false);
        if (!source.configured())
            return kind == WheelEntryKind.ABILITY
                    && abilities(entity, source).stream().anyMatch(ability -> HolderHelper.id(ability).equals(id));
        WheelLayout layout = entity.getExistingData(MxtAttachments.WHEEL_LAYOUT)
                .flatMap(WheelLayoutAttachment::layout).orElse(WheelLayout.EMPTY);
        for (int sector = 0; sector < WheelLayout.SLOTS; sector++) {
            WheelSlot slot = layout.slot(sector);
            if (slot.kind() == kind && slot.id().equals(id)) return true;
        }
        return false;
    }
}
