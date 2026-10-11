package com.iafenvoy.mxt.recipe;

/**
 * Which of the furnace's three input roles an item or a slot plays. Shared by the recipe shape, the slot map and
 * the potency resolver, so neither side owns the other's enum.
 */
public enum AlchemyRole {
    MAIN, AUXILIARY, CATALYST
}
