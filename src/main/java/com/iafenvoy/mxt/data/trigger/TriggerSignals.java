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
    public static final Identifier TECHNIQUE_STAGE = id("technique_stage");

    // Ported vanilla triggers: the signal keeps the vanilla id, but MiXianTu's own hooks publish it instead of
    // the call sites vanilla fires from.
    public static final Identifier CONSUME_ITEM = id("consume_item");
    public static final Identifier BREWED_POTION = id("brewed_potion");
    public static final Identifier TAME_ANIMAL = id("tame_animal");
    public static final Identifier BRED_ANIMALS = id("bred_animals");
    public static final Identifier VILLAGER_TRADE = id("villager_trade");
    public static final Identifier USED_TOTEM = id("used_totem");
    public static final Identifier STARTED_RIDING = id("started_riding");
    public static final Identifier CHANGED_DIMENSION = id("changed_dimension");
    public static final Identifier EFFECTS_CHANGED = id("effects_changed");
    public static final Identifier LIGHTNING_STRIKE = id("lightning_strike");
    public static final Identifier PLAYER_HURT_ENTITY = id("player_hurt_entity");
    public static final Identifier ENTITY_HURT_PLAYER = id("entity_hurt_player");
    public static final Identifier PLAYER_KILLED_ENTITY = id("player_killed_entity");
    public static final Identifier ENTITY_KILLED_PLAYER = id("entity_killed_player");
    public static final Identifier SHOT_CROSSBOW = id("shot_crossbow");
    public static final Identifier PLAYER_INTERACTED_WITH_ENTITY = id("player_interacted_with_entity");
    public static final Identifier FISHING_ROD_HOOKED = id("fishing_rod_hooked");
    public static final Identifier THROWN_ITEM_PICKED_UP_BY_PLAYER = id("thrown_item_picked_up_by_player");
    public static final Identifier ENTER_BLOCK = id("enter_block");
    public static final Identifier LOCATION = id("location");
    public static final Identifier LEVITATION = id("levitation");
    public static final Identifier FALL_FROM_HEIGHT = id("fall_from_height");
    public static final Identifier FALL_AFTER_EXPLOSION = id("fall_after_explosion");
    public static final Identifier RIDE_ENTITY_IN_LAVA = id("ride_entity_in_lava");
    public static final Identifier NETHER_TRAVEL = id("nether_travel");
    public static final Identifier INVENTORY_CHANGED = id("inventory_changed");
    public static final Identifier FILLED_BUCKET = id("filled_bucket");
    public static final Identifier ITEM_DURABILITY_CHANGED = id("item_durability_changed");
    public static final Identifier USING_ITEM = id("using_item");
    public static final Identifier SLEPT_IN_BED = id("slept_in_bed");

    private TriggerSignals() {
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, path);
    }
}
