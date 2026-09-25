package com.iafenvoy.mxt.api;

import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

/**
 * What kind of thing one wheel sector holds: the kind says which registry, list or code-side table an id has to
 * resolve in, which is what lets one twelve-cell wheel mix abilities, auras and anything a content mod adds.
 *
 * <p>A content mod adds a kind by implementing this and registering it (from its mod setup) with
 * {@code WheelEntryKinds#register}: the kind then takes part in stored layouts and in the pool the configuration
 * screen offers, and it decides what pressing one of its cells does. The first registration of an id wins.
 */
public interface WheelEntryKind {
    Identifier id();

    /**
     * The kind's name in the configuration screen. A built-in kind returns its own translation key; a content mod
     * may return whatever component it likes.
     */
    Component displayName();

    /**
     * Whether a cell of this kind holds an entry at all. One kind answers no, and it is how an empty cell is
     * spelled, so a saved layout is always twelve cells.
     */
    boolean holdsEntry();

    /**
     * Whether an id still names something a cell may keep. Each side passes its own registry access, and a definition
     * that no longer resolves is what takes its cells off every wheel.
     */
    boolean exists(RegistryAccess access, Identifier id);

    /**
     * What pressing a cell of this kind does, server-side. The page the request named has already been re-read, and
     * answering {@code false} is how a refusal is reported to the player by the implementation itself.
     */
    boolean trigger(ServerPlayer player, WheelSource source, Identifier id);

    /**
     * The directed form of the same request: a screen or a script naming the state it wants instead of letting the
     * server read the current one. Only a stateful entry has a state to ask for, so the default refuses.
     */
    default boolean directed(ServerPlayer player, WheelSource source, Identifier id, boolean wanted) {
        return false;
    }
}
