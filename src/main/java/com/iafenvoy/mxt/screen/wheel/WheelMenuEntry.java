package com.iafenvoy.mxt.screen.wheel;

import com.iafenvoy.mxt.data.IconReference;
import com.iafenvoy.mxt.runtime.wheel.WheelEntryKind;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;

import java.util.List;
import java.util.Optional;

/**
 * One thing the wheel can choose: its name, its icon and what choosing it does. Its sector comes from the
 * player's saved layout ({@link WheelMenuProvider}), never from the entry; the kind is part of the contract
 * because one ring mixes abilities and auras.
 */
public interface WheelMenuEntry {
    /** Which registry {@link #id()} belongs to. */
    WheelEntryKind kind();

    /** Stable identity: the saved layout addresses this entry by it, and it looks the definition back up. */
    Identifier id();

    /** What the middle of the wheel shows while this sector is under the pointer. */
    Component title();

    /** Drawn inside the sector, or nothing; drawing is {@code IconRenderer}'s job. */
    default Optional<IconReference> icon() {
        return Optional.empty();
    }

    /** The colour marking this entry's kind in the configuration screen's slot row. */
    default int accentColor() {
        return 0xFF7E8799;
    }

    /** The lines shown when the player asks what this entry is; rebuilt, since they depend on the player. */
    default List<Component> tooltip(Player player) {
        return List.of(this.title(), this.kind().displayName());
    }

    /** Ticks this entry is still on cooldown, {@code 0} when it is ready. */
    default long cooldownTicks(Player player) {
        return 0L;
    }

    /** Whether choosing it would do anything. Only dims the sector - the trigger is still sent. */
    default boolean usable(Player player) {
        return this.cooldownTicks(player) <= 0L;
    }

    /** Called on the client once the entry was used; the wheel stays open across it. */
    void onSelected(WheelSelection selection);
}
