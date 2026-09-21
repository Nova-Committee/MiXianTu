package com.iafenvoy.mxt.config;

import com.iafenvoy.jupiter.config.container.AutoInitConfigContainer;
import com.iafenvoy.jupiter.config.entry.MapStringEntry;
import com.iafenvoy.mxt.MiXianTu;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.Map;

/**
 * Where every movable HUD element sits, in a file of its own.
 *
 * <p>It is kept out of {@link MxtClientConfig} on purpose. Everything else in the client config is a decision
 * the player makes about how the mod behaves - one value at a time, edited on a config screen. A layout is
 * different in kind: it is written continuously while a player drags things around, it is one row per HUD
 * element rather than one row per setting, and losing it means nothing except that the elements go back to
 * where they started. Keeping it separate means a broken layout cannot take the mod's actual settings with
 * it, and it stays readable on its own.</p>
 *
 * <p>Values are {@code x,y,visible}, with the position a ratio of the window and always naming the top-left
 * corner of the element, so a layout stays put across resolutions. A key that is absent means "never placed",
 * and an element in that state uses the position its own class suggests. The key carries a version because the
 * meaning of a stored position has changed once already: a value written before elements could be anchored
 * anywhere had no anchor to speak of, and re-reading it under the new rules could move somebody's column. An
 * old key is simply never read, so those layouts fall back to their defaults instead.</p>
 *
 * <p>It writes {@code config/mxt/mxt-hud.json} - the file keeps the mod prefix inside the mod's own directory,
 * so moving it here did not have to rename the files a player may already have.</p>
 */
public final class MxtHudConfig extends AutoInitConfigContainer {
    public static final MxtHudConfig INSTANCE = new MxtHudConfig();

    public final Hud hud = new Hud();

    private MxtHudConfig() {
        super(Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "hud_layout"), "config.mxt.hud", "./config/mxt/mxt-hud.json");
    }

    public static final class Hud extends AutoInitConfigCategoryBase {
        /**
         * The whole layout table: one entry rather than one per HUD element, because the set of elements is
         * not known here - a module that registers an element only picks a layout key, and the framework does
         * the rest. Not meant to be edited by hand; the drag editor and this field are the only writers.
         *
         * <p>Public, unlike a plain field: a category's entries are collected by reflection over its public
         * fields, so a private one is silently dropped and the file comes out as {@code "hud": {}} - which is
         * exactly what happened the first time this class was written.</p>
         */
        public final MapStringEntry layout = hudLayoutEntry();

        private Hud() {
            super("hud", "config.mxt.hud");
        }

        /**
         * The raw stored value for one layout key, or {@code null} when that element has never been placed.
         */
        public String stored(String layoutKey) {
            return this.layout.getValue().get(layoutKey);
        }

        /**
         * Stores one placement and writes the file. A drag has no other moment at which it could be saved:
         * the edit screen writes while the mouse moves, and there is no close-time flush that a crash or a
         * kill would spare.
         */
        public void store(String layoutKey, String value) {
            Map<String, String> values = new HashMap<>(this.layout.getValue());
            values.put(layoutKey, value);
            this.layout.setValue(values);
            MxtHudConfig.INSTANCE.save();
        }

        /**
         * Forgets one placement and writes the file, which is what a reset does. Writing here rather than
         * only clearing the entry's copy is the point: a value left in the file would come back on the next
         * launch and make the reset look like it had not been saved.
         */
        public void remove(String layoutKey) {
            Map<String, String> values = new HashMap<>(this.layout.getValue());
            if (values.remove(layoutKey) == null) return;
            this.layout.setValue(values);
            MxtHudConfig.INSTANCE.save();
        }

        private static MapStringEntry hudLayoutEntry() {
            // Held in a local first: the tooltip call makes the builder chain lose its type argument, and this
            // way the inferred type stays concrete instead of a raw type warning.
            var builder = MapStringEntry.builder("config.mxt.hud.layout_v2", Map.<String, String>of());
            builder.key("layout_v2").tooltip("config.mxt.hud.layout.tooltip");
            return builder.build();
        }
    }
}
