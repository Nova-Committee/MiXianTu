package com.iafenvoy.mxt.render.overlay.hotbar;

import com.iafenvoy.mxt.network.payload.AbilityActionC2SPayload;
import com.iafenvoy.mxt.attachment.AbilityHolderComponent;
import com.iafenvoy.mxt.data.HotbarIcon;
import com.iafenvoy.mxt.data.ability.Ability;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.util.HolderHelper;
import com.iafenvoy.mxt.util.DefinitionText;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

import java.util.Optional;

/**
 * Hotbar entry backed by one resolved, data-driven ability.
 */
public record AbilityHotbarEntry(Identifier id, Ability definition) implements HotbarEntry {

    @Override
    public Component name() {
        return DefinitionText.name(this.id, "ability");
    }

    @Override
    public Optional<HotbarIcon> icon() {
        return this.definition.icon();
    }

    @Override
    public int accentColor() {
        return 0xFF7E8799;
    }

    @Override
    public float cooldown(Player player) {
        if (player == null) return 0.0F;
        AbilityHolderComponent holder = player.getData(MxtAttachments.ABILITY_HOLDER);
        Holder<Ability> ability = holder.sources().keySet().stream()
                .filter(value -> HolderHelper.id(value).equals(this.id)).findFirst().orElse(null);
        if (ability == null) return 0.0F;
        long remaining = holder.cooldowns().getOrDefault(ability, -1L) - player.level().getGameTime();
        if (remaining <= 0L) return 0.0F;
        double duration = holder.componentState(ability, "cooldown_duration")
                .map(state -> state.value()).orElse(0.0D);
        if (!Double.isFinite(duration) || duration <= 0.0D) return 0.0F;
        return (float) Math.max(0.0D, Math.min(1.0D, remaining / duration));
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
