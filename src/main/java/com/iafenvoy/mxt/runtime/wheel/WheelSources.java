package com.iafenvoy.mxt.runtime.wheel;

import com.iafenvoy.mxt.attachment.AbilityAttachment;
import com.iafenvoy.mxt.attachment.WheelLayoutAttachment;
import com.iafenvoy.mxt.compat.CuriosIntegration;
import com.iafenvoy.mxt.data.ability.Ability;
import com.iafenvoy.mxt.data.ability.type.ActiveAbilityType;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.runtime.artifact.ArtifactToggleService;
import com.iafenvoy.mxt.util.HolderHelper;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * What one wheel page holds, read the same way on both sides: the configured page answers from the player's saved
 * layout, a derived page from the ability grants of the equipment it names.
 *
 * <p>A derived page is never stored - its entries are the grants in force at this moment, which is what makes an
 * item's page follow the item. The artifact capabilities of the same equipment are read from the same stacks, which
 * is why both lists are answered here rather than by whoever draws them.
 */
public final class WheelSources {
    private WheelSources() {
    }

    // In id order, so the same moment always gives the same slot. Only active abilities, since they are the only
    // kind the wheel can trigger. Deliberately not cut to one page - how many entries fit is the client's business,
    // and all the server asks is whether one id is in it.
    public static List<Holder<Ability>> abilities(LivingEntity entity, WheelSource source) {
        List<Identifier> sources = source.grantSources(entity);
        if (sources.isEmpty()) return List.of();
        AbilityAttachment holder = entity.getExistingData(MxtAttachments.ABILITY_HOLDER).orElse(null);
        if (holder == null) return List.of();
        return holder.sources().keys().stream()
                .filter(ability -> ability.value().type() instanceof ActiveAbilityType)
                .filter(ability -> holder.sources().of(ability).stream().anyMatch(sources::contains))
                .sorted(Comparator.comparing(ability -> HolderHelper.id(ability).toString()))
                .toList();
    }

    // The configured page reads everything the other pages read, because the player keys it by hand: a capability
    // put on it has to keep working wherever the artifact is worn. These stacks are what an artifact capability is
    // read from; the ability list is read from the grant ledger instead.
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
        };
    }

    // A live reading rather than a stored list, so putting the artifact away takes its cells off the page with it.
    public static List<ArtifactToggleService.Toggle> toggles(LivingEntity entity, WheelSource source) {
        return ArtifactToggleService.of(entity, equipment(entity, source));
    }

    // The check behind every trigger: the id alone is not enough, because the same ability can come from something
    // the player no longer has.
    public static boolean offers(LivingEntity entity, WheelSource source, WheelEntryKind kind, Identifier id) {
        if (kind == null || id == null || !kind.holdsEntry()) return false;
        if (!source.configured()) {
            return switch (kind) {
                case ABILITY -> abilities(entity, source).stream()
                        .anyMatch(ability -> HolderHelper.id(ability).equals(id));
                case ARTIFACT -> ArtifactToggleService.find(toggles(entity, source), id).isPresent();
                default -> false;
            };
        }
        WheelLayout layout = entity.getExistingData(MxtAttachments.WHEEL_LAYOUT)
                .flatMap(WheelLayoutAttachment::layout).orElse(WheelLayout.EMPTY);
        for (int sector = 0; sector < WheelLayout.SLOTS; sector++) {
            WheelSlot slot = layout.slot(sector);
            if (slot.kind() == kind && slot.id().equals(id)) return true;
        }
        return false;
    }
}
