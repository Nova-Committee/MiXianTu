package com.iafenvoy.mxt.runtime.ability;

import com.iafenvoy.mxt.MiXianTu;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/**
 * The ids abilities are granted under. A grant is counted by source ({@link com.iafenvoy.mxt.util.SourceLedger}),
 * so the source an equipment slot contributes is what says "this ability comes from what is worn there"; the
 * convention therefore lives in one place rather than in whoever happens to be granting.
 *
 * <p>An id names the slot <em>and</em> the item in it, so swapping the item swaps the source: what the old item
 * granted is released and what the new one grants is added, without either touching another slot's grants.</p>
 */
public final class AbilitySources {
    /** Curios gear shares one source, so equipping a second charm cannot release the first one's abilities. */
    public static final Identifier CURIOS = Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "curios_equipment");

    private AbilitySources() {
    }

    /** The source one equipment slot contributes while {@code stack} is in it; an empty slot names air. */
    public static Identifier equipment(EquipmentSlot slot, ItemStack stack) {
        Identifier item = stack.isEmpty() ? Identifier.fromNamespaceAndPath("minecraft", "air") : BuiltInRegistries.ITEM.getKey(stack.getItem());
        return Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "equipment/" + slot.getName() + "/" + item.getNamespace() + "/" + item.getPath());
    }
}
