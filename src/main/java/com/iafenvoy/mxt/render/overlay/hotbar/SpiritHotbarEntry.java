package com.iafenvoy.mxt.render.overlay.hotbar;

import com.iafenvoy.mxt.network.payload.SpiritBurstC2SPayload;
import com.iafenvoy.mxt.attachment.SpiritBurstCooldownAttachment;
import com.iafenvoy.mxt.data.HotbarIcon;
import com.iafenvoy.mxt.data.resource.Resource;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.runtime.spirit.SpiritBurstService;
import com.iafenvoy.mxt.util.DefinitionText;
import net.minecraft.core.Holder;
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
        return DefinitionText.name(this.id, "resource");
    }

    @Override
    public int accentColor() {
        return 0xFF62B6E8;
    }

    @Override
    public Optional<HotbarIcon> icon() {
        return MxtDatapackRegistries.holder(MxtResourceKeys.RESOURCE, this.id).flatMap(resource -> resource.value().icon());
    }

    @Override
    public float cooldown(Player player) {
        if (player == null) return 0.0F;
        Holder<Resource> resource = MxtDatapackRegistries.holder(MxtResourceKeys.RESOURCE, this.id).orElse(null);
        if (resource == null) return 0.0F;
        SpiritBurstCooldownAttachment cooldowns = player.getData(MxtAttachments.SPIRIT_BURST_COOLDOWNS);
        long remaining = cooldowns.cooldowns().getOrDefault(resource, -1L) - player.level().getGameTime();
        return remaining <= 0L ? 0.0F : Math.min(1.0F, remaining / (float) SpiritBurstService.FIRE_INTERVAL_TICKS);
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
