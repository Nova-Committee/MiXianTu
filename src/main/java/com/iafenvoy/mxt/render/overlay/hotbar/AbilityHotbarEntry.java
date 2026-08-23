package com.iafenvoy.mxt.render.overlay.hotbar;

import com.iafenvoy.mxt.network.payload.AbilityActionC2SPayload;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * Hotbar entry backed by one resolved, data-driven ability.
 */
public record AbilityHotbarEntry(Identifier id, int accentColor) implements HotbarEntry {
    public AbilityHotbarEntry(Identifier id) {
        this(id, 0xFF7E8799);
    }

    @Override
    public Component name() {
        return Component.literal(this.id.getPath());
    }

    @Override
    public void onPress(Player player) {
        ClientPacketDistributor.sendToServer(AbilityActionC2SPayload.use(this.id));
    }

    @Override
    public void onRelease(Player player) {
        if (player != null && AbilityHotbarClient.shouldCancel(player, this.id))
            ClientPacketDistributor.sendToServer(AbilityActionC2SPayload.cancel(this.id));
    }
}
