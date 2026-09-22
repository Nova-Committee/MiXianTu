package com.iafenvoy.mxt.screen.wheel.content;

import com.iafenvoy.mxt.api.WheelMenuEntry;
import com.iafenvoy.mxt.attachment.AbilityAttachment;
import com.iafenvoy.mxt.attachment.WheelLayoutAttachment;
import com.iafenvoy.mxt.data.ability.Ability;
import com.iafenvoy.mxt.data.ability.type.ActiveAbilityType;
import com.iafenvoy.mxt.data.aura.Aura;
import com.iafenvoy.mxt.network.payload.WheelLayoutC2SPayload;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.runtime.artifact.ArtifactToggleService;
import com.iafenvoy.mxt.runtime.cultivation.Elements;
import com.iafenvoy.mxt.runtime.resource.ResourceService;
import com.iafenvoy.mxt.runtime.resource.ResourceUseService;
import com.iafenvoy.mxt.runtime.wheel.WheelEntryKind;
import com.iafenvoy.mxt.runtime.wheel.WheelLayout;
import com.iafenvoy.mxt.runtime.wheel.WheelSlot;
import com.iafenvoy.mxt.runtime.wheel.WheelSource;
import com.iafenvoy.mxt.runtime.wheel.WheelSources;
import com.iafenvoy.mxt.screen.wheel.WheelMenuContent;
import com.iafenvoy.mxt.screen.wheel.WheelMenuProvider;
import com.iafenvoy.mxt.util.HolderHelper;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * The cells a player's wheel page holds: abilities, auras and artifact capabilities normalised into one
 * {@link WheelMenuEntry} list, resolved fresh from the synced attachment and registries so the drawn wheel and
 * the triggered entry can never disagree. Every other source reads what the equipment grants right now.
 */
public final class WheelContent implements WheelMenuProvider {
    public static final WheelContent INSTANCE = new WheelContent();

    private WheelContent() {
    }

    public static void register() {
        WheelMenuContent.register(INSTANCE);
    }

    @Override
    public List<WheelMenuEntry> entries(@Nullable Player player, WheelSource source) {
        if (player == null) return List.of();
        if (!source.configured()) return derived(player, source);
        List<WheelMenuEntry> auras = auras(player);
        List<WheelMenuEntry> options = pool(player);
        WheelLayout layout = layoutFor(player);
        List<WheelMenuEntry> sectors = new ArrayList<>(WheelLayout.SLOTS);
        for (int sector = 0; sector < WheelLayout.SLOTS; sector++)
            sectors.add(find(layout.slot(sector), auras, options));
        return sectors;
    }

    // In id order, and filtered by the same rule the server applies before honouring a burst.
    public static List<WheelMenuEntry> auras(@Nullable Player player) {
        if (player == null) return List.of();
        return MxtDatapackRegistries.holders(player.level().registryAccess(), MxtResourceKeys.AURA)
                .filter(aura -> canBurst(player, aura))
                .sorted(Comparator.comparing(aura -> HolderHelper.id(aura).toString()))
                .<WheelMenuEntry>map(aura -> new AuraWheelEntry(HolderHelper.id(aura), aura))
                .toList();
    }

    public static List<WheelMenuEntry> abilities(@Nullable Player player) {
        if (player == null) return List.of();
        // Read-only: asking what a player could put on their wheel must not create an ability attachment.
        AbilityAttachment holder = player.getExistingData(MxtAttachments.ABILITY_HOLDER).orElse(null);
        if (holder == null) return List.of();
        return holder.sources().keys().stream()
                .filter(ability -> ability.value().type() instanceof ActiveAbilityType)
                .sorted(Comparator.comparing(ability -> HolderHelper.id(ability).toString()))
                .<WheelMenuEntry>map(ability -> new AbilityWheelEntry(HolderHelper.id(ability), ability.value()))
                .toList();
    }

    // Abilities first, then artifact capabilities; each half keeps its own id order, so the pool does not
    // reshuffle between two openings.
    public static List<WheelMenuEntry> pool(@Nullable Player player) {
        if (player == null) return List.of();
        List<WheelMenuEntry> options = new ArrayList<>(abilities(player));
        // The configured page may hold anything the player carries, so this is the same reading its own page does.
        for (ArtifactToggleService.Toggle toggle : WheelSources.toggles(player, WheelSource.CONFIGURED))
            options.add(new ArtifactWheelEntry(toggle));
        return List.copyOf(options);
    }

    // One derived page: what the named equipment grants and declares right now, never stored, so the page
    // follows the gear; a source with more entries than a page gets more pages rather than being cut.
    private static List<WheelMenuEntry> derived(Player player, WheelSource source) {
        List<WheelMenuEntry> entries = new ArrayList<>();
        for (Holder<Ability> ability : WheelSources.abilities(player, source))
            entries.add(new AbilityWheelEntry(HolderHelper.id(ability), ability.value()));
        for (ArtifactToggleService.Toggle toggle : WheelSources.toggles(player, source))
            entries.add(new ArtifactWheelEntry(toggle));
        return List.copyOf(entries);
    }

    // Raw ids rather than resolved entries, so a cell whose id no longer resolves stays visible and clearable.
    // Nothing is filled in for a player who has never saved: what goes on the wheel is the player's decision.
    public static WheelLayout layoutFor(@Nullable Player player) {
        return layout(player).orElse(WheelLayout.EMPTY);
    }

    // Sends the whole layout, then republishes the armed cell: the places it was counted on just changed.
    public static void save(WheelLayout layout) {
        ClientPacketDistributor.sendToServer(new WheelLayoutC2SPayload(layout));
        WheelSelectionSync.republish();
    }

    private static Optional<WheelLayout> layout(@Nullable Player player) {
        if (player == null) return Optional.empty();
        return player.getExistingData(MxtAttachments.WHEEL_LAYOUT).flatMap(WheelLayoutAttachment::layout);
    }

    private static @Nullable WheelMenuEntry find(WheelSlot slot, List<WheelMenuEntry> auras,
                                                 List<WheelMenuEntry> options) {
        if (slot.isEmpty()) return null;
        List<WheelMenuEntry> pool = slot.kind() == WheelEntryKind.AURA ? auras : options;
        for (WheelMenuEntry entry : pool) if (entry.id().equals(slot.id())) return entry;
        return null;
    }

    private static boolean canBurst(Player player, Holder<Aura> aura) {
        Aura profile = aura.value();
        if (!Elements.enabled(profile.auraType()) || !ResourceUseService.canUse(player, aura)) return false;
        double amount = profile.burstAmount().evaluate(
                ResourceService.formulaContext(player, profile.resource(), FormulaContext.of(player)));
        return Double.isFinite(amount) && amount >= 1.0D;
    }
}
