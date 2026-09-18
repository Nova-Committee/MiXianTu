package com.iafenvoy.mxt.screen.overlay.hotbar;

import com.iafenvoy.mxt.data.aura.Aura;
import com.iafenvoy.mxt.network.payload.SpiritBurstC2SPayload;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.runtime.resource.ResourceService;
import com.iafenvoy.mxt.runtime.resource.ResourceUseService;
import com.iafenvoy.mxt.util.HolderHelper;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.core.Holder.Reference;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Client-side selection state for hold-to-fire spirit bursts.
 */
public final class SpiritBurstClient {
    public static final int MAX_SLOTS = 9;
    private static int selectedIndex;

    private SpiritBurstClient() {
    }

    public static List<Reference<Aura>> auras(Player player) {
        return aurasAvailable(player).stream().limit(MAX_SLOTS).toList();
    }

    /**
     * Every aura available for selection; the runtime hotbar still uses nine.
     */
    public static List<Reference<Aura>> aurasAvailable(Player player) {
        return MxtDatapackRegistries.holders(player.level().registryAccess(), MxtResourceKeys.AURA)
                .filter(aura -> canBurst(player, aura))
                .sorted(Comparator.comparing(aura -> HolderHelper.id(aura).toString()))
                .toList();
    }

    /**
     * {@code aura_type} selects the elemental relation, while {@code burst_amount} opts an aura in.
     */
    private static boolean canBurst(Player player, Reference<Aura> aura) {
        Aura profile = aura.value();
        if (profile.auraType().isEmpty() || !ResourceUseService.canUse(player, aura)) return false;
        double amount = profile.burstAmount().evaluate(
                ResourceService.formulaContext(player, profile.resource(), FormulaContext.of(player)));
        return Double.isFinite(amount) && amount >= 1.0D;
    }

    public static int selectedIndex() {
        return selectedIndex;
    }

    public static Optional<Holder<Aura>> selected(Player player) {
        List<Reference<Aura>> auras = auras(player);
        if (auras.isEmpty()) return Optional.empty();
        selectedIndex = Math.max(0, Math.min(selectedIndex, auras.size() - 1));
        return Optional.of(auras.get(selectedIndex));
    }

    public static void select(int index, boolean firing) {
        if (index < 0 || index >= MAX_SLOTS) return;
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (player == null) return;
        List<Reference<Aura>> auras = auras(player);
        if (index >= auras.size()) return;
        selectedIndex = index;
        Identifier id = HolderHelper.id(auras.get(index));
        ClientPacketDistributor.sendToServer(new SpiritBurstC2SPayload(firing, Optional.of(id)));
    }

    public static Optional<Identifier> selectedId(Player player) {
        return selected(player).map(HolderHelper::id);
    }
}
