package com.iafenvoy.mxt.render.overlay.hotbar;

import com.iafenvoy.mxt.network.payload.SpiritBurstC2SPayload;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

import java.util.Optional;

/**
 * Hotbar entry backed by one resource that can emit a spirit burst.
 */
public record SpiritHotbarEntry(Identifier id) implements HotbarEntry {
    @Override
    public Component name() {
        return Component.literal(this.id.getPath());
    }

    @Override
    public int accentColor() {
        return 0xFF62B6E8;
    }

    @Override
    public void onPress(Player player) {
        ClientPacketDistributor.sendToServer(new SpiritBurstC2SPayload(true, Optional.of(this.id)));
    }

    @Override
    public void onRelease(Player player) {
        ClientPacketDistributor.sendToServer(new SpiritBurstC2SPayload(false, Optional.of(this.id)));
    }
}
