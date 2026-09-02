package com.iafenvoy.mxt.data.trigger;

import com.iafenvoy.mxt.MiXianTu;
import net.minecraft.resources.Identifier;

/**
 * Canonical signal identifiers emitted by MiXianTu's event bridges.
 */
public final class TriggerSignals {
    public static final Identifier TICK = id("tick");
    public static final Identifier ATTACK = id("attack");
    public static final Identifier HURT = id("hurt");
    public static final Identifier KILL = id("kill");
    public static final Identifier BLOCK_BREAK = id("block_break");
    public static final Identifier BLOCK_USE = id("block_use");
    public static final Identifier ITEM_USE = id("item_use");
    public static final Identifier EQUIP = id("equip");
    public static final Identifier DEATH = id("death");
    public static final Identifier BREAKTHROUGH = id("breakthrough");

    private TriggerSignals() {
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, path);
    }
}
