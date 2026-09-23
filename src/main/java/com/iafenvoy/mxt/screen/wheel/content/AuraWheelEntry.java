package com.iafenvoy.mxt.screen.wheel.content;

import com.iafenvoy.mxt.api.WheelMenuEntry;
import com.iafenvoy.mxt.attachment.SpiritBurstCooldownAttachment;
import com.iafenvoy.mxt.data.IconReference;
import com.iafenvoy.mxt.data.aura.Aura;
import com.iafenvoy.mxt.data.resource.Resource;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.runtime.resource.ResourceService;
import com.iafenvoy.mxt.runtime.spirit.SpiritBurstService;
import com.iafenvoy.mxt.runtime.wheel.WheelEntryKind;
import com.iafenvoy.mxt.screen.wheel.WheelSelection;
import com.iafenvoy.mxt.util.DefinitionText;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * One aura that can burst as a wheel entry: named and drawn after the {@link Resource} it carries rather than
 * its own id, because an aura is a behaviour laid on a resource.
 */
public record AuraWheelEntry(Identifier id, Holder<Aura> aura) implements WheelMenuEntry {
    private static final int ACCENT = 0xFF62B6E8;

    @Override
    public WheelEntryKind kind() {
        return WheelEntryKind.AURA;
    }

    @Override
    public Component title() {
        return DefinitionText.name(this.aura.value().resource());
    }

    @Override
    public Optional<IconReference> icon() {
        return this.aura.value().resource().value().icon();
    }

    @Override
    public int accentColor() {
        return ACCENT;
    }

    @Override
    public long cooldownTicks(Player player) {
        if (player == null) return 0L;
        // Read-only: an absent entry reads as ready, and asking must not create the attachment.
        SpiritBurstCooldownAttachment cooldowns = player.getExistingData(MxtAttachments.SPIRIT_BURST_COOLDOWNS).orElse(null);
        if (cooldowns == null) return 0L;
        return Math.max(0L, cooldowns.cooldowns().getOrDefault(this.aura, 0L) - player.level().getGameTime());
    }

    // Every burst of one aura waits the same interval, so the length is the constant the server fires on.
    @Override
    public long cooldownLength(Player player) {
        return SpiritBurstService.FIRE_INTERVAL_TICKS;
    }

    @Override
    public List<Component> tooltip(Player player) {
        List<Component> lines = new ArrayList<>(6);
        lines.add(this.kind().displayName().copy().withStyle(ChatFormatting.GRAY));
        lines.add(this.title().copy().withStyle(ChatFormatting.WHITE));
        if (player == null) return lines;
        Aura definition = this.aura.value();
        Holder<Resource> resource = definition.resource();
        FormulaContext context = ResourceService.formulaContext(player, resource, FormulaContext.of(player));
        lines.add(Component.translatable("wheel.mxt.tooltip.burst",
                WheelTooltips.number(definition.burstAmount().evaluate(context))));
        player.getExistingData(MxtAttachments.RESOURCE_HOLDER).ifPresent(holder ->
                ResourceService.resolveBounds(resource.value(), context).ifPresent(bounds ->
                        lines.add(Component.translatable("wheel.mxt.tooltip.amount",
                                WheelTooltips.number(holder.get(resource)), WheelTooltips.number(bounds.max())))));
        definition.auraType().ifPresentOrElse(
                element -> lines.add(Component.translatable("wheel.mxt.tooltip.element", DefinitionText.name(element))),
                () -> lines.add(Component.translatable("wheel.mxt.tooltip.element_none")));
        return lines;
    }

    @Override
    public void onSelected(WheelSelection selection) {
        WheelTrigger.send(this, selection.source());
    }
}
