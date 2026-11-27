package com.iafenvoy.mxt.screen.wheel.content;

import com.iafenvoy.mxt.data.ability.AbilityAffinity;
import com.iafenvoy.mxt.data.aura.Aura;
import com.iafenvoy.mxt.data.quality.ItemQuality;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.registry.cache.RegistryCaches;
import com.iafenvoy.mxt.runtime.artifact.ArtifactService;
import com.iafenvoy.mxt.runtime.creature.ContractBells;
import com.iafenvoy.mxt.runtime.item.QualityService;
import com.iafenvoy.mxt.runtime.spirit.SpiritBurstService;
import com.iafenvoy.mxt.runtime.wheel.MxtWheelSources;
import com.iafenvoy.saturn.api.wheel.WheelMenuEntry;
import com.iafenvoy.saturn.runtime.Sources;
import com.iafenvoy.saturn.runtime.wheel.WheelSources;
import com.iafenvoy.saturn.screen.wheel.WheelMenuContent;
import com.iafenvoy.saturn.screen.wheel.content.AbilityWheelTooltips;
import com.iafenvoy.saturn.screen.wheel.content.WheelContent;
import com.iafenvoy.saturn.util.DefinitionText;
import com.iafenvoy.saturn.util.HolderHelper;
import com.iafenvoy.saturn.util.formula.number.Constant;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;

import java.util.Comparator;
import java.util.List;

public final class MxtWheelContent {
    private MxtWheelContent() {
    }

    public static void register() {
        WheelContent.register(MxtWheelSources.AURA, MxtWheelContent::auras);
        WheelMenuContent.register(MxtWheelSources.CONTRACT, (player, source) -> player == null ? List.of() :
                ContractBells.selection(player).map(selection -> selection.orders().stream()
                        .<WheelMenuEntry>map(ContractWheelEntry::new).toList()).orElse(List.of()));
        AbilityWheelTooltips.register(Identifier.fromNamespaceAndPath("mxt", "ability"), (player, ability, carrier, lines) -> {
            AbilityAffinity affinity = RegistryCaches.ABILITY_AFFINITY.of(ability);
            if (affinity != null && !affinity.isEmpty())
                lines.add(Component.translatable("wheel.mxt.tooltip.element", WheelTooltips.elements(affinity.elements())));
            if (WheelSources.sources(player, ability).stream().anyMatch(Sources::isGranted))
                lines.add(Component.translatable("wheel.saturn.tooltip.source.learned"));
            else if (carrier != null && !carrier.isEmpty()) {
                RegistryAccess registries = player.registryAccess();
                Component name = ArtifactService.definition(registries, carrier).<Component>map(DefinitionText::nameOrId).orElseGet(carrier::getHoverName);
                Holder<ItemQuality> quality = QualityService.find(registries, carrier).filter(holder -> holder.value().color().isPresent()).orElse(null);
                name = quality == null ? name.copy().withStyle(ChatFormatting.GOLD) : QualityService.coloredName(quality, name);
                lines.add(Component.translatable("wheel.saturn.tooltip.source.item", name));
            }
        });
    }

    public static List<WheelMenuEntry> auras(Player player) {
        return MxtDatapackRegistries.holders(player.registryAccess(), MxtResourceKeys.AURA)
                .filter(aura -> supportsBurst(aura.value()))
                .sorted(Comparator.comparing(aura -> HolderHelper.id(aura).toString()))
                .<WheelMenuEntry>map(aura -> new AuraWheelEntry(HolderHelper.id(aura), aura)).toList();
    }

    private static boolean supportsBurst(Aura aura) {
        if (aura.auraType().isEmpty()) return false;
        // A formula can become positive after the player or their surroundings change.
        return !(aura.burstAmount() instanceof Constant(double value)) || SpiritBurstService.validBurstAmount(value);
    }
}
