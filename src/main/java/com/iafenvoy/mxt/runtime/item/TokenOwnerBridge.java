package com.iafenvoy.mxt.runtime.item;

import com.iafenvoy.mxt.data.item.TokenComponent;
import com.iafenvoy.mxt.registry.MxtDataComponents;
import net.minecraft.util.StringUtil;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AnvilUpdateEvent;

import java.util.Optional;

/**
 * Renaming a token in an anvil claims it for the player who typed the name. The result can only be
 * rewritten from {@link AnvilUpdateEvent}; the take-time events hand out copies and run after the item
 * has already left the slot.
 */
@EventBusSubscriber
public final class TokenOwnerBridge {
    private TokenOwnerBridge() {
    }

    @SubscribeEvent
    public static void onAnvilUpdate(AnvilUpdateEvent event) {
        // Both logical sides recompute the result, and only the server's slot survives the next sync.
        if (event.getPlayer().level().isClientSide()) return;
        String name = event.getName();
        // Blank means "clear the custom name", and a token that was merely put in the slot arrives as its
        // own name, so only a name that actually differs counts as this rename.
        if (name == null || StringUtil.isBlank(name) || name.equals(event.getLeft().getHoverName().getString())) return;
        ItemStack output = event.getOutput();
        if (output.isEmpty() || !output.has(MxtDataComponents.TOKEN)) return;
        TokenComponent token = output.getOrDefault(MxtDataComponents.TOKEN, TokenComponent.EMPTY);
        output.set(MxtDataComponents.TOKEN, new TokenComponent(token.kind(), token.value(),
                Optional.of(event.getPlayer().getStringUUID())));
    }
}
