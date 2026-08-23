package com.iafenvoy.mxt.render.overlay.hotbar;

import com.iafenvoy.mxt.attachment.AbilityHolderComponent;
import com.iafenvoy.mxt.data.ability.Ability;
import com.iafenvoy.mxt.data.ability.type.ActiveAbilityType;
import com.iafenvoy.mxt.network.payload.AbilityActionC2SPayload;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.util.HolderHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Client-side slot resolution for the data-driven ability hotbar.
 */
public final class AbilityHotbarClient {
    public static final int MAX_SLOTS = 9;

    private AbilityHotbarClient() {
    }

    public static void sendSlot(String slot, boolean pressed) {
        Player player = Minecraft.getInstance().player;
        if (player == null) return;
        resolve(player, slot).ifPresent(ability -> {
            if (pressed) {
                ClientPacketDistributor.sendToServer(AbilityActionC2SPayload.use(ability.id()));
            } else if (shouldCancel(player, ability.id())) {
                ClientPacketDistributor.sendToServer(AbilityActionC2SPayload.cancel(ability.id()));
            }
        });
    }

    public static void sendIndex(int index, boolean pressed) {
        if (index < 0 || index >= MAX_SLOTS) return;
        Player player = Minecraft.getInstance().player;
        if (player == null) return;
        all(player).stream().skip(index).findFirst().ifPresent(ability -> {
            if (pressed) {
                ClientPacketDistributor.sendToServer(AbilityActionC2SPayload.use(ability.id()));
            } else if (shouldCancel(player, ability.id())) {
                ClientPacketDistributor.sendToServer(AbilityActionC2SPayload.cancel(ability.id()));
            }
        });
    }

    public static Optional<ResolvedAbility> resolve(Player player, String slot) {
        AbilityHolderComponent holder = player.getData(MxtAttachments.ABILITY_HOLDER);
        return holder.sources().keySet().stream()
                .filter(ability -> ability.value().type() instanceof ActiveAbilityType(
                        String slot1
                ) && slot1.equals(slot))
                .map(ability -> new ResolvedAbility(HolderHelper.id(ability), ability.value())).min(Comparator.comparing(value -> value.id().toString()));
    }

    public static List<ResolvedAbility> all(Player player) {
        AbilityHolderComponent holder = player.getData(MxtAttachments.ABILITY_HOLDER);
        return holder.sources().keySet().stream()
                .filter(ability -> ability.value().type() instanceof ActiveAbilityType)
                .map(ability -> new ResolvedAbility(HolderHelper.id(ability), ability.value()))
                .sorted(Comparator.comparing(value -> value.id().toString()))
                .limit(MAX_SLOTS)
                .toList();
    }

    public static boolean shouldCancel(Player player, Identifier id) {
        AbilityHolderComponent holder = player.getData(MxtAttachments.ABILITY_HOLDER);
        Optional<Holder<Ability>> ability = holder.sources().keySet().stream().filter(value -> HolderHelper.id(value).equals(id)).findFirst();
        if (ability.isEmpty()) return false;
        if (holder.channelledAbility().filter(ability.get()::equals).isPresent()) return true;
        return holder.componentState(ability.get(), "cast_ends_at")
                .map(state -> state.value() < Double.MAX_VALUE)
                .orElse(false);
    }

    public record ResolvedAbility(Identifier id, Ability definition) {
    }
}
