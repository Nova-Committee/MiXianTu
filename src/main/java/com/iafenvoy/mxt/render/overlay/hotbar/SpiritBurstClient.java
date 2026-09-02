package com.iafenvoy.mxt.render.overlay.hotbar;

import com.iafenvoy.mxt.data.resource.Resource;
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

    public static List<Reference<Resource>> resources(Player player) {
        return resourcesAvailable(player).stream().limit(MAX_SLOTS).toList();
    }

    /**
     * Returns every resource available for selection; the runtime hotbar still uses nine.
     */
    public static List<Reference<Resource>> resourcesAvailable(Player player) {
        return MxtDatapackRegistries.holders(player.level().registryAccess(), MxtResourceKeys.RESOURCE)
                .filter(resource -> canBurst(player, resource))
                .sorted(Comparator.comparing(resource -> HolderHelper.id(resource).toString()))
                .toList();
    }

    /**
     * {@code aura_type} selects the elemental relation, while {@code burst_amount}
     * explicitly opts a resource into the spirit-burst hotbar.
     */
    private static boolean canBurst(Player player, Reference<Resource> resource) {
        if (resource.value().auraType().isEmpty() || !ResourceUseService.canUse(player, resource)) return false;
        double amount = resource.value().burstAmount().evaluate(
                ResourceService.formulaContext(player, resource, FormulaContext.of(player)));
        return Double.isFinite(amount) && amount >= 1.0D;
    }

    public static int selectedIndex() {
        return selectedIndex;
    }

    public static Optional<Holder<Resource>> selected(Player player) {
        List<Reference<Resource>> resources = resources(player);
        if (resources.isEmpty()) return Optional.empty();
        selectedIndex = Math.max(0, Math.min(selectedIndex, resources.size() - 1));
        return Optional.of(resources.get(selectedIndex));
    }

    public static void select(int index, boolean firing) {
        if (index < 0 || index >= MAX_SLOTS) return;
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (player == null) return;
        List<Reference<Resource>> resources = resources(player);
        if (index >= resources.size()) return;
        selectedIndex = index;
        Identifier id = HolderHelper.id(resources.get(index));
        ClientPacketDistributor.sendToServer(new SpiritBurstC2SPayload(firing, Optional.of(id)));
    }

    public static Optional<Identifier> selectedId(Player player) {
        return selected(player).map(HolderHelper::id);
    }
}
