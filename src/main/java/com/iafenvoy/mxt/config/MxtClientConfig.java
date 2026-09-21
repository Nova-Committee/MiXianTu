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
 * Client-only controls for the mod's own overlays, laid out one tab per feature. Names stay short and the
 * explanation lives in the tooltip, the same way the server config is written; call sites read the entry
 * itself through {@link #INSTANCE}, and the serialised keys are short for the same reason: the old full
 * paths are translated on load.
 *
 * <p>The HUD layout is not here: it lives in {@link MxtHudConfig}, in its own file, because it is written by
 * dragging rather than by filling in a setting. The wheel's sectors are not here either: they are the player's
 * own layout, kept on the server, which is the side that validates them (see {@code WheelLayoutAttachment}).</p>
 */
public final class MxtClientConfig extends AutoInitConfigContainer {
    public static final MxtClientConfig INSTANCE = new MxtClientConfig();

    public final ResourceBars resourceBars = new ResourceBars();
    public final Information information = new Information();
    public final Techniques techniques = new Techniques();
    public final Rifts rifts = new Rifts();
    public final Wheel wheel = new Wheel();

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
        /**
         * Ticks between two rebuilds of the information panel.
         */
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
        /**
         * Draws rifts with the mod's own pipeline, which layers a drifting pattern over the points, links and
         * fills a rift is built from. Off switches to plain translucent colour, which is there to fall back on
         * when a shader pack or a driver disagrees with the custom pipeline.
         */
        public final BooleanEntry shaders = BooleanEntry.builder("config.mxt.client.rifts.shaders", true)
                .key("shaders")
                .tooltip("config.mxt.client.rifts.shaders.tooltip")
                .build();
        /**
         * How thick a rift is drawn: the side of the cube at each node, the diameter of a link and the thickness
         * of the slab a triangle is filled with, all one measurement. The floor keeps a rift visible when the
         * player wants it subtle; the ceiling keeps one block from overfilling its own cube.
         */
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
     * How the wheel menu is driven. One entry, because the only choice left is whether the key holds the wheel
     * up or toggles it; spending the selection is the use key's job, not a setting.
     */
    public static final class Wheel extends AutoInitConfigCategoryBase {
        public final EnumEntry<WheelMode> mode = EnumEntry.builder("config.mxt.client.wheel.mode", WheelMode.HOLD)
                .key("mode")
                .tooltip("config.mxt.client.wheel.mode.tooltip")
                .nameProvider(value -> Component.translatable("config.mxt.client.wheel.mode." + value.name().toLowerCase()))
                .build();

        private Wheel() {
            super("wheel", "config.mxt.client.wheel");
        }
    }

    public enum WheelMode {
        HOLD, TOGGLE
    }
}
