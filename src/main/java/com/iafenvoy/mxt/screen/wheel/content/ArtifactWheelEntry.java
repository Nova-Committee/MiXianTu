package com.iafenvoy.mxt.screen.wheel.content;

import com.iafenvoy.mxt.api.WheelMenuEntry;
import com.iafenvoy.mxt.runtime.artifact.ArtifactService;
import com.iafenvoy.mxt.runtime.artifact.ArtifactToggleService;
import com.iafenvoy.mxt.runtime.wheel.WheelEntryKind;
import com.iafenvoy.mxt.screen.wheel.WheelSelection;
import com.iafenvoy.mxt.util.DefinitionText;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * One artifact capability as a wheel entry: something the player presses for, either a switch that stays on or a
 * one-shot like opening the storage. The cell draws the capability's name, not the artifact's item, and never
 * says which way a press goes: the server reads the state and decides.
 */
public record ArtifactWheelEntry(ArtifactToggleService.Toggle toggle) implements WheelMenuEntry {
    private static final int ACCENT_ON = 0xFF7BD37B;
    private static final int ACCENT_OFF = 0xFF8A8F9A;
    private static final int ACCENT_READY = 0xFFB08CE8;

    @Override
    public WheelEntryKind kind() {
        return WheelEntryKind.ARTIFACT;
    }

    // The artifact plus the capability's key: this is what the stored cell and the trigger both name.
    @Override
    public Identifier id() {
        return this.toggle.id();
    }

    @Override
    public Component title() {
        return this.toggle.ability().displayName();
    }

    @Override
    public int accentColor() {
        return this.toggle.state().map(on -> on ? ACCENT_ON : ACCENT_OFF).orElse(ACCENT_READY);
    }

    // A one-shot has no state line at all, because it has no state to report.
    @Override
    public List<Component> tooltip(@Nullable Player player) {
        List<Component> lines = new ArrayList<>(4);
        lines.add(this.kind().displayName().copy().withStyle(ChatFormatting.GRAY));
        lines.add(this.title().copy().withStyle(ChatFormatting.WHITE));
        lines.add(Component.translatable("wheel.mxt.tooltip.artifact", DefinitionText.name(this.toggle.artifact())));
        this.toggle.state().ifPresent(on -> lines.add(Component.translatable(
                on ? "wheel.mxt.tooltip.state_on" : "wheel.mxt.tooltip.state_off")));
        return lines;
    }

    // The same gate flight and the storage ask use.
    @Override
    public boolean usable(@Nullable Player player) {
        return player != null && ArtifactService.mayUse(this.toggle.stack(), this.toggle.artifact(), player.getUUID());
    }

    @Override
    public void onSelected(WheelSelection selection) {
        WheelTrigger.send(this, selection.source());
    }
}
