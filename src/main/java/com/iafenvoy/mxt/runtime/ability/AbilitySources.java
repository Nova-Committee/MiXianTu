package com.iafenvoy.mxt.runtime.ability;

import com.iafenvoy.mxt.MiXianTu;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/**
 * The ids abilities are granted under. Grants are counted by source, so the convention lives in one place rather
 * than in whoever happens to be granting: an equipment id names the slot <em>and</em> the item in it, so swapping
 * the item releases what the old one granted and adds what the new one grants.
 */
public final class AbilitySources {
    // One shared source, so equipping a second charm cannot release the first one's abilities.
    public static final Identifier CURIOS = Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "curios_equipment");
    /**
     * The learning pipeline's prefix ({@code mxt:grant/<kind>/<namespace>/<path>}): what a body was taught, as
     * opposed to what an item declares. It lives here so the shape has one home rather than one per granter.
     */
    public static final String GRANT_PREFIX = "grant/";
    private static final String EQUIPMENT_PREFIX = "equipment/";

    private AbilitySources() {
    }

    // An empty slot names air.
    public static Identifier equipment(EquipmentSlot slot, ItemStack stack) {
        Identifier item = stack.isEmpty() ? Identifier.fromNamespaceAndPath("minecraft", "air") : BuiltInRegistries.ITEM.getKey(stack.getItem());
        return Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, EQUIPMENT_PREFIX + slot.getName() + "/" + item.getNamespace() + "/" + item.getPath());
    }

    // Held in a hand, so the grant leaves with the item; a charm in a Curios slot has its own source and counts as
    // part of the body instead.
    public static boolean isEquipment(Identifier source) {
        return source.getNamespace().equals(MiXianTu.MOD_ID) && source.getPath().startsWith(EQUIPMENT_PREFIX);
    }

    // Taught by the cultivation pipeline: a technique, a spirit root, a physique or a reached minor stage.
    public static boolean isLearned(Identifier source) {
        return source.getNamespace().equals(MiXianTu.MOD_ID) && source.getPath().startsWith(GRANT_PREFIX);
    }
}
