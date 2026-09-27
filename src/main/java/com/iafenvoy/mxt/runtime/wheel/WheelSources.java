package com.iafenvoy.mxt.runtime.wheel;

import com.iafenvoy.mxt.api.WheelEntryKind;
import com.iafenvoy.mxt.api.WheelSource;
import com.iafenvoy.mxt.attachment.AbilityAttachment;
import com.iafenvoy.mxt.attachment.WheelLayoutAttachment;
import com.iafenvoy.mxt.data.ability.Abilities;
import com.iafenvoy.mxt.data.ability.Ability;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.runtime.ability.AbilityActivationService;
import com.iafenvoy.mxt.runtime.ability.AbilitySources;
import com.iafenvoy.mxt.runtime.artifact.ArtifactService;
import com.iafenvoy.mxt.util.HolderHelper;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * What one wheel page holds, read the same way on both sides: the configured page answers from the player's saved
 * layout, a derived page from the abilities the equipment it names grants right now.
 *
 * <p>The page itself says which stacks it is read from and whether one of its entries is reachable
 * ({@link WheelSource}), so this class is the shared arithmetic behind those answers rather than a list of pages:
 * resolving a definition walks the artifact registry, and that is the walk worth doing once per page rather than
 * once per cell.
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

    // Every pressable ability the player holds, whatever granted it: the ledger the editor's pool and the saved
    // cells' resolution list are both built from, read here rather than from the equipment so neither depends on
    // where the thing that grants it happens to be.
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

    // Where one ability's grant comes from right now; empty once nothing holds it, which is a different answer
    // from "held by something the tooltip cannot name".
    public static Set<Identifier> sources(LivingEntity entity, Identifier abilityId) {
        AbilityAttachment holder = entity.getExistingData(MxtAttachments.ABILITY_HOLDER).orElse(null);
        return holder == null ? Set.of() : holder.sources().of(abilityId);
    }

    // True when a hand is the only thing holding it: such a skill belongs on that item's own page, because the
    // hand is whatever is picked up next. A charm in a Curios slot stays with the body and counts as the player's.
    public static boolean handOnly(LivingEntity entity, Holder<Ability> ability) {
        Set<Identifier> sources = sources(entity, HolderHelper.id(ability));
        return !sources.isEmpty() && sources.stream().allMatch(AbilitySources::isEquipment);
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
        for (ItemStack stack : source.equipment(entity)) {
            if (stack.isEmpty()) continue;
            for (Holder<Ability> ability : ArtifactService.abilities(entity.level().registryAccess(), stack))
                found.putIfAbsent(HolderHelper.id(ability), stack);
        }
        return found;
    }

    // The check behind every trigger: the id alone is not enough, because the same ability can come from something
    // the player no longer has. Which page answers that question is the page's own business.
    public static boolean offers(LivingEntity entity, WheelSource source, WheelEntryKind kind, Identifier id) {
        if (kind == null || id == null || !kind.holdsEntry()) return false;
        return source.offers(entity, kind, id);
    }

    // Whether the player's saved layout holds that cell, which is what the configured page answers with.
    static boolean inLayout(LivingEntity entity, WheelEntryKind kind, Identifier id) {
        WheelLayout layout = entity.getExistingData(MxtAttachments.WHEEL_LAYOUT)
                .flatMap(WheelLayoutAttachment::layout).orElse(WheelLayout.EMPTY);
        for (int sector = 0; sector < WheelLayout.SLOTS; sector++) {
            WheelSlot slot = layout.slot(sector);
            if (slot.kind() == kind && slot.id().equals(id)) return true;
        }
        return false;
    }

    // Whether a derived page's own grants currently offer that ability, which is the answer every equipment page
    // and the Curios page give.
    static boolean granted(LivingEntity entity, WheelSource source, WheelEntryKind kind, Identifier id) {
        return kind == WheelEntryKinds.ABILITY
                && abilities(entity, source).stream().anyMatch(ability -> HolderHelper.id(ability).equals(id));
    }
}
