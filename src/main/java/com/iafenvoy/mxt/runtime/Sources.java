package com.iafenvoy.mxt.runtime;

import com.iafenvoy.mxt.MiXianTu;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/**
 * How a source id the framework itself produces is spelled. Grants are counted per source, so the shape lives in
 * one place rather than in whoever happens to be granting.
 *
 * <p>A content pack names its own source in the action and loot fields instead of coming through here; only the
 * ids the framework generates are built here.
 */
public final class Sources {
    // One shared source, so equipping a second charm cannot release the first one's abilities.
    public static final Identifier CURIOS = Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "curios_equipment");
    private static final String EQUIPMENT_PREFIX = "equipment/";
    private static final String GRANT_PREFIX = "grant/";
    private static final String FORMATION_PREFIX = "formation/";

    private Sources() {
    }

    /**
     * The categories a framework grant is counted under: {@code mxt:grant/<category>/<namespace>/<path>}. Learning
     * a definition, reaching a level and signing a contract are all "the body holds this", which is what
     * {@link #isGranted} answers for the wheel.
     */
    public enum Grant {
        SPIRIT_ROOT("spirit_root"), PHYSIQUE("physique"), TECHNIQUE("technique"),
        REALM_STAGE("realm_stage"), CREATURE("creature"), CONTRACT("contract");

        private final String path;

        Grant(String path) {
            this.path = path;
        }

        public String path() {
            return this.path;
        }
    }

    /**
     * The categories a generated passive modifier's id names: which sort of thing declared it. They are part of a
     * stored modifier id, so the strings are fixed.
     */
    public enum Declaration {
        ABILITY("ability"), REALM("realm"), TECHNIQUE("technique"), PHYSIQUE("physique");

        private final String path;

        Declaration(String path) {
            this.path = path;
        }

        public String path() {
            return this.path;
        }
    }

    // An empty slot names air.
    public static Identifier equipment(EquipmentSlot slot, ItemStack stack) {
        Identifier item = stack.isEmpty() ? Identifier.fromNamespaceAndPath("minecraft", "air")
                : BuiltInRegistries.ITEM.getKey(stack.getItem());
        return Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID,
                EQUIPMENT_PREFIX + slot.getName() + "/" + item.getNamespace() + "/" + item.getPath());
    }

    public static Identifier granted(Grant kind, Identifier content) {
        return Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID,
                GRANT_PREFIX + kind.path() + "/" + content.getNamespace() + "/" + content.getPath());
    }

    // Every instance of one formation definition shares this source, so leaving one instance still leaves the
    // others' grants alone.
    public static Identifier formation(Identifier formation) {
        return Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID,
                FORMATION_PREFIX + formation.getNamespace() + "/" + formation.getPath());
    }

    // Held in a hand, so the grant leaves with the item; a charm in a Curios slot has its own source and counts as
    // part of the body instead.
    public static boolean isEquipment(Identifier source) {
        return inMod(source) && source.getPath().startsWith(EQUIPMENT_PREFIX);
    }

    // Granted by a definition the body holds, as opposed to by an item it happens to carry.
    public static boolean isGranted(Identifier source) {
        return inMod(source) && source.getPath().startsWith(GRANT_PREFIX);
    }

    private static boolean inMod(Identifier source) {
        return source.getNamespace().equals(MiXianTu.MOD_ID);
    }
}
