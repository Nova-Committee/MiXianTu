package com.iafenvoy.mxt.screen.wheel.content;

import com.iafenvoy.mxt.api.WheelMenuEntry;
import com.iafenvoy.mxt.data.IconReference;
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
import java.util.Optional;

/**
 * One artifact capability as a wheel entry: something the player presses for, either a switch that stays on or a
 * one-shot like opening the storage.
 *
 * <p>The cell draws the capability's name rather than the artifact's item on purpose: one artifact can offer
 * several capabilities, and two identical sword icons would say nothing about which is which. Which artifact a
 * cell belongs to is in its tooltip, where there is room to say it.</p>
 *
 * <p>The entry never says which way a press goes. It reports the state the implementation gives it, the server
 * reads the same state and decides, so the cell can never ask for something impossible.</p>
 */
public record ArtifactWheelEntry(ArtifactToggleService.Toggle toggle) implements WheelMenuEntry {
    /** A switch that is on, drawn as something running. */
    private static final int ACCENT_ON = 0xFF7BD37B;
    /** A switch that is off, drawn as the grey the wheel already uses for "idle". */
    private static final int ACCENT_OFF = 0xFF8A8F9A;
    /** A one-shot that is ready: nothing to report, so it gets a colour of its own instead of a state. */
    private static final int ACCENT_READY = 0xFFB08CE8;

    @Override
    public WheelEntryKind kind() {
        return WheelEntryKind.ARTIFACT;
    }

    /** The artifact plus the capability's key, which is what both the stored cell and the trigger name. */
    @Override
    public Identifier id() {
        return this.toggle.id();
    }

    @Override
    public Component title() {
        return this.toggle.ability().displayName();
    }

    /** No icon of its own: see the class doc - the name says which capability, the tooltip says which artifact. */
    @Override
    public Optional<IconReference> icon() {
        return Optional.empty();
    }

    @Override
    public int accentColor() {
        return this.toggle.state().map(on -> on ? ACCENT_ON : ACCENT_OFF).orElse(ACCENT_READY);
    }

    /**
     * Kind, what it is, which artifact declares it, and - for a switch - whether it is on. A one-shot has no
     * state line at all, because it has no state to report.
     */
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

    /** An artifact nobody may use is drawn dimmed, which is the same gate flight and the storage ask. */
    @Override
    public boolean usable(@Nullable Player player) {
        return player != null && ArtifactService.mayUse(this.toggle.stack(), this.toggle.artifact(), player.getUUID());
    }

    @Override
    public void onSelected(WheelSelection selection) {
        WheelTrigger.send(this, selection.source());
    }
}
