package com.iafenvoy.mxt.config;

import com.iafenvoy.jupiter.config.container.AutoInitConfigContainer;
import com.iafenvoy.jupiter.config.entry.BooleanEntry;
import com.iafenvoy.jupiter.config.entry.DoubleEntry;
import com.iafenvoy.jupiter.config.entry.EnumEntry;
import com.iafenvoy.jupiter.config.entry.IntegerEntry;
import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.runtime.cultivation.TechniqueProgress.Mode;
import com.iafenvoy.mxt.runtime.rift.RiftMesh;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * Client-only controls for the mod's own overlays, one tab per feature; an entry's name stays short and its
 * tooltip carries the explanation, which also lives in both lang files ({@code config.mxt.client.*}), so
 * adding or renaming one means editing {@code zh_cn.json} and {@code en_us.json} too.
 */
public final class MxtClientConfig extends AutoInitConfigContainer {
    public static final MxtClientConfig INSTANCE = new MxtClientConfig();

    public final ResourceBars resourceBars = new ResourceBars();
    public final Information information = new Information();
    public final Techniques techniques = new Techniques();
    public final Rifts rifts = new Rifts();
    public final Wheel wheel = new Wheel();
    public final Tooltips tooltips = new Tooltips();

    private MxtClientConfig() {
        super(Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "client"), "config.mxt.client", "./config/mxt/mxt-client.json");
        this.dataFixer.registerKeyRule("^config\\.mxt\\.client\\.[a-z_]+\\.([a-z_]+)$", key -> key.substring(key.lastIndexOf('.') + 1));
    }

    public static final class ResourceBars extends AutoInitConfigCategoryBase {
        public final BooleanEntry showNames = BooleanEntry.builder("config.mxt.client.resource_bars.show_names", false)
                .key("show_names")
                .tooltip("config.mxt.client.resource_bars.show_names.tooltip")
                .build();
        public final EnumEntry<ResourceBarIconLayout> iconLayout = EnumEntry.builder("config.mxt.client.resource_bars.icon_layout", ResourceBarIconLayout.SIDES)
                .key("icon_layout")
                .tooltip("config.mxt.client.resource_bars.icon_layout.tooltip")
                .nameProvider(value -> Component.translatable("config.mxt.client.resource_bars.icon_layout." + value.name().toLowerCase()))
                .build();

        private ResourceBars() {
            super("resource_bars", "config.mxt.client.resource_bars");
        }
    }

    public enum ResourceBarIconLayout {
        SIDES, CENTER
    }

    public static final class Information extends AutoInitConfigCategoryBase {
        // Ticks between two rebuilds of the information panel.
        public final IntegerEntry refreshInterval = IntegerEntry.builder("config.mxt.client.information.refresh_interval", 20)
                .key("refresh_interval")
                .tooltip("config.mxt.client.information.refresh_interval.tooltip")
                .range(1, 1200).build();

        private Information() {
            super("information", "config.mxt.client.information");
        }
    }

    public static final class Techniques extends AutoInitConfigCategoryBase {
        public final EnumEntry<Mode> progressMode = EnumEntry.builder("config.mxt.client.techniques.progress_mode", Mode.ABSOLUTE)
                .key("progress_mode")
                .tooltip("config.mxt.client.techniques.progress_mode.tooltip")
                .nameProvider(value -> Component.translatable("config.mxt.client.techniques.progress_mode." + value.name().toLowerCase()))
                .build();

        private Techniques() {
            super("techniques", "config.mxt.client.techniques");
        }
    }

    public static final class Rifts extends AutoInitConfigCategoryBase {
        // Off switches to plain translucent colour, which is what a shader pack or driver that disagrees with
        // the custom pipeline falls back on.
        public final BooleanEntry shaders = BooleanEntry.builder("config.mxt.client.rifts.shaders", true)
                .key("shaders")
                .tooltip("config.mxt.client.rifts.shaders.tooltip")
                .build();
        // One measurement for a node's cube, a link's diameter and a fill slab's thickness: the floor keeps a
        // subtle rift visible and the ceiling keeps one block from overfilling its own cube.
        public final DoubleEntry thickness = DoubleEntry.builder("config.mxt.client.rifts.thickness", RiftMesh.DEFAULT_THICKNESS)
                .key("thickness")
                .tooltip("config.mxt.client.rifts.thickness.tooltip")
                .range(RiftMesh.MIN_THICKNESS, RiftMesh.MAX_THICKNESS)
                .build();

        private Rifts() {
            super("rifts", "config.mxt.client.rifts");
        }
    }

    /**
     * How the wheel menu is driven: whether the key holds the wheel up or toggles it. Spending the selection is
     * the use key's job, not a setting.
     */
    public static final class Wheel extends AutoInitConfigCategoryBase {
        public final EnumEntry<WheelMode> mode = EnumEntry.builder("config.mxt.client.wheel.mode", WheelMode.HOLD)
                .key("mode")
                .tooltip("config.mxt.client.wheel.mode.tooltip")
                .nameProvider(value -> Component.translatable("config.mxt.client.wheel.mode." + value.name().toLowerCase()))
                .build();
        // On by default: every page looks the same on the ring, and the wheel is the one screen where the mouse is
        // already in hand.
        public final BooleanEntry scrollSwitch = BooleanEntry.builder("config.mxt.client.wheel.scroll_switch", true)
                .key("scroll_switch")
                .tooltip("config.mxt.client.wheel.scroll_switch.tooltip")
                .build();
        // Wrapping is what the wheel has always done, so it stays the default; off parks the page at either end.
        public final BooleanEntry wrapPages = BooleanEntry.builder("config.mxt.client.wheel.wrap_pages", true)
                .key("wrap_pages")
                .tooltip("config.mxt.client.wheel.wrap_pages.tooltip")
                .build();

        private Wheel() {
            super("wheel", "config.mxt.client.wheel");
        }
    }

    public enum WheelMode {
        HOLD, TOGGLE
    }

    /**
     * Item tooltip switches. The name line is the one line no appender can reach, so whether a quality is
     * allowed to tint it is a player's choice rather than part of what the quality draws.
     */
    public static final class Tooltips extends AutoInitConfigCategoryBase {
        public final BooleanEntry tintItemName = BooleanEntry.builder("config.mxt.client.tooltips.tint_item_name", true)
                .key("tint_item_name")
                .tooltip("config.mxt.client.tooltips.tint_item_name.tooltip")
                .build();

        private Tooltips() {
            super("tooltips", "config.mxt.client.tooltips");
        }
    }
}
