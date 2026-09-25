package com.iafenvoy.mxt.screen.wheel.content;

import com.iafenvoy.mxt.api.WheelMenuEntry;
import com.iafenvoy.mxt.api.WheelSource;
import com.iafenvoy.mxt.attachment.WheelLayoutAttachment;
import com.iafenvoy.mxt.data.ability.Ability;
import com.iafenvoy.mxt.data.ability.Togglable;
import com.iafenvoy.mxt.data.aura.Aura;
import com.iafenvoy.mxt.network.payload.WheelLayoutC2SPayload;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.runtime.ability.AbilityActivationService;
import com.iafenvoy.mxt.runtime.creature.ContractBells;
import com.iafenvoy.mxt.runtime.cultivation.Elements;
import com.iafenvoy.mxt.runtime.resource.ResourceService;
import com.iafenvoy.mxt.runtime.resource.ResourceUseService;
import com.iafenvoy.mxt.runtime.wheel.*;
import com.iafenvoy.mxt.screen.wheel.WheelMenuContent;
import com.iafenvoy.mxt.screen.wheel.WheelMenuProvider;
import com.iafenvoy.mxt.util.HolderHelper;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The cells a player's wheel page holds: pressable abilities and auras normalised into one {@link WheelMenuEntry}
 * list, resolved fresh from the synced attachment and registries so the drawn wheel and the triggered entry can
 * never disagree. Every other source reads what the equipment grants right now.
 */
public final class WheelContent implements WheelMenuProvider {
    public static final WheelContent INSTANCE = new WheelContent();

    private WheelContent() {
    }

    // One provider for the built-in pages: each is registered under its own source, so a content mod registering a
    // page of its own adds to the wheel instead of replacing what is here.
    public static void register() {
        for (WheelSource source : WheelSourceTypes.BUILT_IN) WheelMenuContent.register(source, INSTANCE);
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

    // Everything the player could put on the wheel right now, whoever granted it: the pool a player drags cells
    // from, which reads the grant ledger rather than the equipment so a pinned cell keeps working wherever the
    // thing that grants it happens to be.
    public static List<WheelMenuEntry> pool(@Nullable Player player) {
        if (player == null) return List.of();
        List<WheelMenuEntry> options = new ArrayList<>();
        // One pass over what the player carries, rather than a lookup per ability: resolving a definition walks
        // the artifact registry, and this list is rebuilt every client tick.
        Map<Identifier, ItemStack> carriers = WheelSources.carriers(player, WheelSourceTypes.CONFIGURED);
        for (Holder<Ability> ability : WheelSources.abilities(player))
            options.add(entry(player, ability, carriers.get(HolderHelper.id(ability))));
        return List.copyOf(options);
    }

    // One derived page: what the named equipment grants and declares right now, never stored, so the page follows
    // the gear; a source with more entries than a page gets more pages rather than being cut.
    private static List<WheelMenuEntry> derived(Player player, WheelSource source) {
        if (source == WheelSourceTypes.CONTRACT) return orders(player);
        List<WheelMenuEntry> entries = new ArrayList<>();
        Map<Identifier, ItemStack> carriers = WheelSources.carriers(player, source);
        for (Holder<Ability> ability : WheelSources.abilities(player, source))
            entries.add(entry(player, ability, carriers.get(HolderHelper.id(ability))));
        return List.copyOf(entries);
    }

    // The orders of the beast the held taming bell is tuned to. Read from the bell, which carries the creature's
    // own answer, so nothing here has to find a creature that may not be loaded on this side.
    private static List<WheelMenuEntry> orders(Player player) {
        return ContractBells.selection(player)
                .map(selection -> selection.orders().stream().<WheelMenuEntry>map(ContractWheelEntry::new).toList())
                .orElse(List.of());
    }

    // The state is asked here rather than read off the definition, because a switch answers from wherever it keeps
    // its state and the entry only carries the answer.
    private static AbilityWheelEntry entry(Player player, Holder<Ability> ability, @Nullable ItemStack carrier) {
        Optional<Boolean> state = ability.value().type() instanceof Togglable
                ? AbilityActivationService.state(player, ability) : Optional.empty();
        return new AbilityWheelEntry(ability, carrier, state);
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
        List<WheelMenuEntry> pool = slot.kind() == WheelEntryKinds.AURA ? auras : options;
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