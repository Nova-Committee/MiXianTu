package com.iafenvoy.mxt.screen.wheel.content;

import com.iafenvoy.mxt.runtime.wheel.WheelEntryKinds;
import com.iafenvoy.mxt.api.WheelMenuEntry;
import com.iafenvoy.mxt.data.creature.ContractBehavior;
import com.iafenvoy.mxt.api.WheelEntryKind;
import com.iafenvoy.mxt.screen.wheel.WheelSelection;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;

import java.util.List;

/**
 * One order of the tuned beast as a wheel entry. The page it sits on is read from the bell rather than from the
 * creature, so this only has to say which order it is and how to send it.
 */
public record ContractWheelEntry(ContractBehavior behavior) implements WheelMenuEntry {
    // An order that stays in force and one that happens once are worth telling apart at a glance.
    private static final int ACCENT_STATE = 0xFF7BD37B;
    private static final int ACCENT_MOMENTARY = 0xFFFFD24A;

    @Override
    public WheelEntryKind kind() {
        return WheelEntryKinds.BEHAVIOR;
    }

    @Override
    public Identifier id() {
        return this.behavior.id();
    }

    @Override
    public Component title() {
        return this.behavior.name();
    }

    @Override
    public int accentColor() {
        return this.behavior.momentary() ? ACCENT_MOMENTARY : ACCENT_STATE;
    }

    @Override
    public List<Component> tooltip(Player player) {
        return List.of(this.kind().displayName().copy().withStyle(ChatFormatting.GRAY),
                this.title().copy().withStyle(ChatFormatting.WHITE),
                Component.translatable(this.behavior.momentary()
                        ? "wheel.mxt.tooltip.behavior_momentary" : "wheel.mxt.tooltip.behavior_state"));
    }

    @Override
    public void onSelected(WheelSelection selection) {
        WheelTrigger.send(this, selection.source());
    }
}