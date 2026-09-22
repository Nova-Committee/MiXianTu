package com.iafenvoy.mxt.screen.wheel.content;

import com.iafenvoy.mxt.attachment.AbilityAttachment;
import com.iafenvoy.mxt.attachment.WheelLayoutAttachment;
import com.iafenvoy.mxt.data.ability.type.ActiveAbilityType;
import com.iafenvoy.mxt.data.aura.Aura;
import com.iafenvoy.mxt.network.payload.WheelLayoutC2SPayload;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.runtime.cultivation.Elements;
import com.iafenvoy.mxt.runtime.resource.ResourceService;
import com.iafenvoy.mxt.runtime.resource.ResourceUseService;
import com.iafenvoy.mxt.runtime.wheel.WheelEntryKind;
import com.iafenvoy.mxt.runtime.wheel.WheelLayout;
import com.iafenvoy.mxt.runtime.wheel.WheelSlot;
import com.iafenvoy.mxt.screen.wheel.WheelMenuContent;
import com.iafenvoy.mxt.screen.wheel.WheelMenuEntry;
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
 * The twelve sectors a player's wheel holds: abilities and auras normalised into one {@link WheelMenuEntry}
 * list, resolved fresh from the synced attachment and registries so the drawn wheel and the triggered entry
 * can never disagree.
 */
public final class WheelContent implements WheelMenuProvider {
    public static final WheelContent INSTANCE = new WheelContent();

    private WheelContent() {
    }

    public static void register() {
        WheelMenuContent.register(INSTANCE);
    }

    @Override
    public List<WheelMenuEntry> entries(@Nullable Player player) {
        List<WheelMenuEntry> auras = auras(player);
        List<WheelMenuEntry> abilities = abilities(player);
        WheelLayout layout = layoutFor(player);
        List<WheelMenuEntry> sectors = new ArrayList<>(WheelLayout.SLOTS);
        for (int sector = 0; sector < WheelLayout.SLOTS; sector++)
            sectors.add(find(layout.slot(sector), auras, abilities));
        return sectors;
    }

    /** Every aura this player can actually burst, in id order; the filter is the one the server applies. */
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

    /**
     * The twelve sectors as stored: the player's own layout, or twelve empty ones when they have never saved
     * a wheel. Raw ids rather than resolved entries, so a sector whose id no longer resolves stays visible
     * and clearable.
     *
     * <p>Nothing is filled in for a new player on purpose: what goes on the wheel is a decision, and picking
     * "the first six of each pool" for them put entries they never chose there - and kept moving them until
     * the first save, because the pool is derived from what is available right now.
     */
    public static WheelLayout layoutFor(@Nullable Player player) {
        return layout(player).orElse(WheelLayout.EMPTY);
    }

    /** Sends the whole layout, then republishes the armed selection: it is stored as what a sector holds. */
    public static void save(WheelLayout layout) {
        ClientPacketDistributor.sendToServer(new WheelLayoutC2SPayload(layout));
        WheelSelectionSync.republish();
    }

    private static Optional<WheelLayout> layout(@Nullable Player player) {
        if (player == null) return Optional.empty();
        return player.getExistingData(MxtAttachments.WHEEL_LAYOUT).flatMap(WheelLayoutAttachment::layout);
    }

    /** The entry a sector names, or {@code null} when its id no longer resolves in that pool. */
    private static @Nullable WheelMenuEntry find(WheelSlot slot, List<WheelMenuEntry> auras,
                                                 List<WheelMenuEntry> abilities) {
        if (slot.isEmpty()) return null;
        List<WheelMenuEntry> pool = slot.kind() == WheelEntryKind.AURA ? auras : abilities;
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
