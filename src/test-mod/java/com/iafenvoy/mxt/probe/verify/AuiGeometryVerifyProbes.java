package com.iafenvoy.mxt.probe.verify;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.iafenvoy.mxt.data.rift.RiftShape;
import com.iafenvoy.mxt.data.rift.RiftVertex;
import com.iafenvoy.mxt.probe.Probe;
import com.iafenvoy.mxt.probe.ProbeContext;
import com.iafenvoy.mxt.screen.AuraDisplay;
import com.iafenvoy.mxt.screen.aui.AuiElements;
import com.iafenvoy.mxt.screen.aui.AuiStyles;
import com.iafenvoy.mxt.screen.gui.SpiritCraftingAuraRow;
import com.iafenvoy.saturn.data.IconReference;
import com.iafenvoy.mxt.screen.rift.RiftEditorInputs;
import com.iafenvoy.mxt.screen.rift.RiftEditorToolbar;
import com.iafenvoy.mxt.screen.rift.RiftEditorViewport;
import com.sighs.apricityui.dom.SlotContentRules;
import com.sighs.apricityui.dom.expander.ContainerExpander;
import com.sighs.apricityui.element.Canvas;
import com.sighs.apricityui.element.Container;
import com.sighs.apricityui.element.Input;
import com.sighs.apricityui.element.Item;
import com.sighs.apricityui.element.Option;
import com.sighs.apricityui.element.Select;
import com.sighs.apricityui.element.Slot;
import com.sighs.apricityui.element.Translation;
import com.sighs.apricityui.element.Texture;
import com.sighs.apricityui.event.Event;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.layout.LayoutMeasureCache;
import com.sighs.apricityui.layout.Size;
import com.sighs.apricityui.parser.HTML;
import com.sighs.apricityui.render.GeometryQueryScope;
import com.sighs.apricityui.style.Animation;
import com.sighs.apricityui.style.Background;
import com.sighs.apricityui.style.Style;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class AuiGeometryVerifyProbes {
    private AuiGeometryVerifyProbes() {
    }

    public static List<Probe> probes() {
        return List.of(Probe.of("verify/aui/rift_geometry", AuiGeometryVerifyProbes::riftGeometry),
                Probe.of("verify/aui/slot_geometry", AuiGeometryVerifyProbes::slotGeometry),
                Probe.of("verify/aui/theme_details", AuiGeometryVerifyProbes::themeDetails),
                Probe.of("verify/aui/aura_icons", AuiGeometryVerifyProbes::auraIcons));
    }

    private static void auraIcons(ProbeContext context) {
        Element.register("item", (document, _) -> new Item(document));
        Element.register("texture", (document, _) -> new Texture(document));
        String css = resource("apricityui/apricity/mxt/common/theme.css") + "\n"
                + resource("apricityui/apricity/mxt/spirit_crafting/spirit_crafting.css");
        String html = resource("apricityui/apricity/mxt/spirit_crafting/spirit_crafting.html")
                .replaceAll("<link[^>]*>", "").replace("</head>", "<style>" + css + "</style></head>");
        String path = "mxt_test/aui_aura_icons.html";
        HTML.putTemple(path, html);
        Component name = Component.literal("Aura name");
        AuraDisplay plain = new AuraDisplay(name, Optional.empty(), 0x66CCFF, List.of());
        AuraDisplay texture = new AuraDisplay(name, Optional.of(IconReference.texture(Identifier.parse("mxt_test:textures/item/aura.png"))),
                0x66CCFF, List.of());
        AuraDisplay item = new AuraDisplay(name, IconReference.of(new ItemStack(Items.DIAMOND)), 0x66CCFF, List.of());
        int documents = Document.getAll().size();
        try {
            for (int[] viewport : List.of(new int[]{320, 240}, new int[]{640, 360}, new int[]{960, 540})) {
                Size.setViewportOverride(viewport[0], viewport[1]);
                Document document = Document.create(path);
                if (!context.check("aura icon document created", document != null && document.body != null)) return;
                assert document != null;
                try (Document.ContextScope _ = Document.withContext(document)) {
                    document.registerUaStylesheet(resource("apricityui/apricity/global.css"), "mxt_test/actual-global.css");
                    Element root = document.getElementById("aura_row");
                    SpiritCraftingAuraRow row = new SpiritCraftingAuraRow(root);
                    for (AuraDisplay display : List.of(plain, texture, item, texture, plain, item)) {
                        row.show(10, display, 25, 50);
                        document.flushPendingStyleUpdates();
                        document.commitRenderState();
                        boolean hasIcon = display.icon().isPresent();
                        context.expect("name is the fallback only", root.querySelector(".name").getComputedStyle().display, hasIcon ? "none" : "flex");
                        context.expect("tooltip identifies the aura", row.tooltip().getFirst().getString(), name.getString());
                        context.check("tooltip preserves progress", row.tooltip().get(1).getString().contains("25"));
                        Item icon = (Item) root.querySelector(".aura-item");
                        context.check("item image cannot replace aura tooltip", !icon.canShowItemTooltip());
                        context.expect("item state follows icon kind", icon.resolveDisplayStack().isEmpty(), display != item);
                        context.expect("texture state follows icon kind", root.querySelector(".aura-texture").hasAttribute("src"), display == texture);
                        try (GeometryQueryScope _ = GeometryQueryScope.open()) {
                            Element.DOMRect outer = root.getBoundingClientRect();
                            Element.DOMRect track = root.querySelector(".bar").getBoundingClientRect();
                            Element.DOMRect fill = root.querySelector(".fill").getBoundingClientRect();
                            close(context, "icon reserves space beside progress", track.left - outer.left, hasIcon ? 20 : 0);
                            close(context, "track fits the available width", track.width, hasIcon ? 85 : 105);
                            close(context, "half progress uses the correct interior", fill.width, hasIcon ? 41 : 51);
                            close(context, "progress stays inside row", track.right, outer.right);
                            close(context, "icon keeps compact row height", outer.height, 18);
                        }
                    }
                    Element clone = root.cloneNode(true);
                    clone.removeAttribute("id");
                    clone = root.parentElement.appendChild(clone);
                    SpiritCraftingAuraRow copied = new SpiritCraftingAuraRow(clone);
                    copied.show(136, plain, 50, 50);
                    document.flushPendingStyleUpdates();
                    context.check("cloned icon row resets to a name", !clone.getClassList().contains("has-icon"));
                    context.expect("cloned row has no stale item", ((Item) clone.querySelector(".aura-item")).resolveDisplayStack().isEmpty(), true);
                    row.hide();
                    context.check("hidden row leaves no tooltip", row.tooltip().isEmpty());
                    row.show(10, item, 40, 50);
                    context.check("reused row refreshes tooltip", row.tooltip().get(1).getString().contains("40"));
                } finally {
                    document.remove();
                }
            }
        } finally {
            Size.clearViewportOverride();
        }
        context.expect("aura icon documents are released", Document.getAll().size(), documents);
    }

    private static void themeDetails(ProbeContext context) {
        Element.register("container", (document, _) -> new Container(document));
        Element.register("slot", (document, _) -> new Slot(document));
        int documents = Document.getAll().size();
        try {
            for (String page : List.of("information/information", "spirit_crafting/spirit_crafting")) {
                String css = resource("apricityui/apricity/mxt/common/theme.css") + "\n"
                        + resource("apricityui/apricity/mxt/" + page + ".css");
                String html = resource("apricityui/apricity/mxt/" + page + ".html")
                        .replaceAll("<link[^>]*>", "").replace("</head>", "<style>" + css + "</style></head>");
                String path = "mxt_test/aui_theme_" + page.replace('/', '_') + ".html";
                HTML.putTemple(path, html);
                for (int[] viewport : List.of(new int[]{320, 240}, new int[]{640, 360}, new int[]{960, 540})) {
                    Size.setViewportOverride(viewport[0], viewport[1]);
                    Document document = Document.create(path);
                    if (!context.check("theme page created: " + page, document != null && document.body != null)) return;
                    assert document != null;
                    try (Document.ContextScope _ = Document.withContext(document)) {
                        document.registerUaStylesheet(resource("apricityui/apricity/global.css"), "mxt_test/actual-global.css");
                        ContainerExpander.expand(document);
                        SlotContentRules.normalizeTemplate(document);
                        document.flushPendingStyleUpdates();
                        if (page.startsWith("information")) {
                            for (int index = 0; index < 4; index++) {
                                Element frame = document.getElementById("equip-" + index).parentElement;
                                document.commitRenderState();
                                Element.DOMRect empty = frame.getBoundingClientRect();
                                context.check("empty armor slot has a silhouette " + index,
                                        Background.of(frame).getLayers().stream().anyMatch(layer -> layer.imagePath != null
                                                && layer.imagePath.startsWith("mxt/information/ornaments/")));
                                AuiElements.setClass(frame, "empty", false);
                                document.flushPendingStyleUpdates();
                                document.commitRenderState();
                                Element.DOMRect equipped = frame.getBoundingClientRect();
                                close(context, "equipping keeps slot x", equipped.x, empty.x);
                                close(context, "equipping keeps slot y", equipped.y, empty.y);
                                close(context, "equipping keeps slot width", equipped.width, 24);
                                close(context, "equipping keeps slot height", equipped.height, 24);
                                context.check("equipped slot has no placeholder " + index,
                                        Background.of(frame).getLayers().stream().noneMatch(layer -> layer.imagePath != null
                                                && layer.imagePath.startsWith("mxt/information/ornaments/")));
                            }
                            AuiElements.setClass(document.getElementById("panel"), "page-technique", true);
                        }
                        Element track = page.startsWith("information") ? document.getElementById("tech_bar-0")
                                : document.body.querySelector(".bar");
                        Element fill = track.getFirstElementChild();
                        AuiElements.style(track, "width", "105px");
                        AuiElements.style(fill, "width", AuiStyles.progressWidth(105, 1) + "px");
                        document.flushPendingStyleUpdates();
                        document.commitRenderState();
                        try (GeometryQueryScope _ = GeometryQueryScope.open()) {
                            Element.DOMRect outer = track.getBoundingClientRect(), inner = fill.getBoundingClientRect();
                            close(context, page + " track height", outer.height, 7);
                            close(context, page + " fill height", inner.height, 3);
                            close(context, page + " left cap", inner.left - outer.left, 2);
                            close(context, page + " right cap", outer.right - inner.right, 2);
                            close(context, page + " top cap", inner.top - outer.top, 2);
                            close(context, page + " bottom cap", outer.bottom - inner.bottom, 2);
                        }
                        if (!page.startsWith("spirit_crafting")) continue;
                        Element ornament = document.getElementById("crafting_array"), panel = document.getElementById("panel");
                        context.expect("decoration cannot intercept slots", ornament.getComputedStyle().pointerEvents, "none");
                        close(context, "idle decoration opacity", Double.parseDouble(ornament.getComputedStyle().opacity), 0.45);
                        context.check("title leaves room above crafting grid", document.getElementById("title").getBoundingClientRect().bottom
                                <= document.getElementById("crafting").getBoundingClientRect().top);
                        AuiElements.setClass(ornament, "active", true);
                        document.flushPendingStyleUpdates();
                        Style animated = ornament.getComputedStyle();
                        context.check("recipe activates the breath", animated.animation.contains("mxt-aura-breathe"));
                        Animation.updateStyle(ornament, animated);
                        context.check("AUI runs the decoration animation", Animation.isActive(ornament));
                        close(context, "animation does not fade the panel", Double.parseDouble(panel.getComputedStyle().opacity), 1);
                        close(context, "animation does not fade the title", Double.parseDouble(document.getElementById("title").getComputedStyle().opacity), 1);
                        AuiElements.setClass(ornament, "active", false);
                        document.flushPendingStyleUpdates();
                        Animation.updateStyle(ornament, ornament.getComputedStyle());
                        context.check("removing the recipe stops the breath", !Animation.isActive(ornament));
                        close(context, "idle opacity restores", Double.parseDouble(ornament.getComputedStyle().opacity), 0.45);
                    } finally {
                        document.remove();
                    }
                }
            }
        } finally {
            Size.clearViewportOverride();
        }
        context.expect("theme checks release their documents", Document.getAll().size(), documents);
    }

    private record SlotGrid(String id, int columns, int rows, int left, int top, boolean playerInventory) {
        SlotGrid(String id, int columns, int rows, int left, int top) {
            this(id, columns, rows, left, top, false);
        }
    }

    private record SlotPage(String path, String stylesheet, List<SlotGrid> grids) {
    }

    private static SlotGrid inventory(int top) {
        return new SlotGrid("inventory", 9, 4, 7, top, true);
    }

    private static void slotGeometry(ProbeContext context) {
        Element.register("container", (document, _) -> new Container(document));
        Element.register("slot", (document, _) -> new Slot(document));
        List<SlotPage> pages = List.of(
                new SlotPage("spirit_crafting/spirit_crafting", "spirit_crafting/spirit_crafting.css", List.of(
                        new SlotGrid("crafting", 3, 3, 29, 16), new SlotGrid("result", 1, 1, 123, 34), inventory(83))),
                new SlotPage("economy/cheque", "economy/cheque.css", List.of(
                        new SlotGrid("currency", 5, 3, 7, 17), new SlotGrid("cheque_in", 1, 1, 133, 17),
                        new SlotGrid("cheque_out", 1, 1, 133, 53), inventory(83))),
                new SlotPage("economy/exchange", "economy/exchange.css", List.of(
                        new SlotGrid("input", 1, 1, 19, 32), new SlotGrid("result", 1, 1, 142, 32), inventory(83))),
                new SlotPage("economy/station_customer", "economy/station.css", List.of(
                        new SlotGrid("costs", 4, 3, 7, 17), new SlotGrid("rewards", 4, 3, 97, 17), inventory(83))),
                new SlotPage("economy/station_owner", "economy/station.css", List.of(
                        new SlotGrid("costs", 4, 3, 7, 15), new SlotGrid("rewards", 4, 3, 97, 15),
                        new SlotGrid("stock", 7, 3, 7, 71), new SlotGrid("display", 1, 1, 151, 71), inventory(139))),
                new SlotPage("economy/trade", "economy/trade.css", List.of(
                        new SlotGrid("offer", 4, 5, 7, 17), new SlotGrid("partner", 4, 5, 97, 17), inventory(139))));
        int documents = Document.getAll().size();
        try {
            for (SlotPage page : pages) {
                String css = resource("apricityui/apricity/mxt/common/theme.css") + "\n"
                        + resource("apricityui/apricity/mxt/" + page.stylesheet());
                String html = resource("apricityui/apricity/mxt/" + page.path() + ".html")
                        .replaceAll("<link[^>]*>", "").replace("</head>", "<style>" + css + "</style></head>");
                String path = "mxt_test/aui_slots_" + page.path().replace('/', '_') + ".html";
                HTML.putTemple(path, html);
                for (int[] viewport : List.of(new int[]{320, 240}, new int[]{640, 360}, new int[]{960, 540})) {
                    Size.setViewportOverride(viewport[0], viewport[1]);
                    Document document = Document.create(path);
                    if (!context.check("slot page created: " + page.path(), document != null && document.body != null)) return;
                    assert document != null;
                    try (Document.ContextScope _ = Document.withContext(document)) {
                        document.registerUaStylesheet(resource("apricityui/apricity/global.css"), "mxt_test/actual-global.css");
                        ContainerExpander.expand(document);
                        SlotContentRules.normalizeTemplate(document);
                        document.flushPendingStyleUpdates();
                        document.commitRenderState();
                        try (GeometryQueryScope _ = GeometryQueryScope.open()) {
                            for (SlotGrid grid : page.grids()) slotGrid(context, document, page.path(), grid);
                        }
                        Slot hovered = (Slot) document.getElementById(page.grids().getFirst().id()).getFirstElementChild();
                        hovered.setHover(true);
                        document.flushPendingStyleUpdates();
                        document.commitRenderState();
                        try (GeometryQueryScope _ = GeometryQueryScope.open()) {
                            slotItem(context, hovered, page.path() + " hovered item");
                        }
                    } finally {
                        document.remove();
                    }
                }
            }
        } finally {
            Size.clearViewportOverride();
        }
        context.expect("slot geometry releases documents", Document.getAll().size(), documents);
    }

    private static void slotGrid(ProbeContext context, Document document, String page, SlotGrid grid) {
        String label = page + " #" + grid.id();
        Element container = document.getElementById(grid.id());
        if (!context.check(label + " container exists", container instanceof Container)) return;
        List<Slot> slots = container.getChildren().stream().filter(Slot.class::isInstance).map(Slot.class::cast).toList();
        context.expect(label + " expanded slot count", slots.size(), grid.columns() * grid.rows());
        context.expect(label + " unique indices", slots.stream().map(Slot::getSlotIndex).distinct().count(), (long) slots.size());
        Element.DOMRect panel = document.getElementById("panel").getBoundingClientRect();
        for (int index = 0; index < slots.size(); index++) {
            Slot slot = slots.get(index);
            int expectedIndex = grid.playerInventory() ? (index + 9) % 36 : index;
            context.expect(label + " menu slot order " + index, slot.getSlotIndex(), expectedIndex);
            int y = grid.playerInventory() && index >= 27 ? 58 : index / grid.columns() * 18;
            Element.DOMRect rect = slot.getBoundingClientRect();
            close(context, label + " slot " + expectedIndex + " x", rect.x - panel.x, grid.left() + index % grid.columns() * 18);
            close(context, label + " slot " + expectedIndex + " y", rect.y - panel.y, grid.top() + y);
            close(context, label + " slot " + expectedIndex + " width", rect.width, 18);
            close(context, label + " slot " + expectedIndex + " height", rect.height, 18);
            slotItem(context, slot, label + " slot " + expectedIndex + " item");
            context.check(label + " slot " + expectedIndex + " is inside the panel",
                    rect.x >= panel.x && rect.y >= panel.y && rect.x + rect.width <= panel.x + panel.width
                            && rect.y + rect.height <= panel.y + panel.height);
        }
    }

    private static void slotItem(ProbeContext context, Slot slot, String label) {
        Element item = SlotContentRules.getDisplayItem(slot);
        if (!context.check(label + " exists", item != null)) return;
        assert item != null;
        Element.DOMRect outer = slot.getBoundingClientRect(), inner = item.getBoundingClientRect();
        close(context, label + " x inset", inner.x - outer.x, 1);
        close(context, label + " y inset", inner.y - outer.y, 1);
        close(context, label + " width", inner.width, 16);
        close(context, label + " height", inner.height, 16);
    }

    private static String resource(String path) {
        return AuiTestResources.read(path);
    }

    private static void riftGeometry(ProbeContext context) {
        // Dedicated servers do not register AUI's client controls automatically.
        Element.register("canvas", (document, _) -> new Canvas(document));
        Element.register("input", (document, _) -> new Input(document));
        Element.register("select", (document, _) -> new Select(document));
        Element.register("option", (document, _) -> new Option(document));
        Element.register("translation", (document, _) -> new Translation(document));
        String path = "mxt_test/aui_rift_geometry_probe.html";
        String css = resource("apricityui/apricity/mxt/common/theme.css") + "\n" + resource("apricityui/apricity/mxt/rift/editor.css");
        String html = resource("apricityui/apricity/mxt/rift/editor.html").replaceAll("<link[^>]*>", "")
                .replace("</head>", "<style>" + css + "</style></head>");
        HTML.putTemple(path, html);
        int documents = Document.getAll().size();
        Language original = Language.getInstance();
        try {
            for (String language : List.of("zh_cn", "en_us")) {
                JsonObject labels = JsonParser.parseString(resource("mxt/lang/" + language + ".json")).getAsJsonObject();
                Language.inject(language(labels, original));
                for (int[] size : List.of(new int[]{768, 454}, new int[]{640, 360}, new int[]{480, 270}, new int[]{960, 540},
                        new int[]{320, 240}, new int[]{1280, 720})) {
                    Size.setViewportOverride(size[0], size[1]);
                    Document document = Document.create(path);
                    if (!context.check("editor document created", document != null && document.body != null)) {
                        if (document != null) document.remove();
                        return;
                    }
                    assert document != null;
                    try (Document.ContextScope _ = Document.withContext(document)) {
                        for (Element element : document.getElements()) {
                            if (element instanceof Translation translation) {
                                String key = translation.getTextContent();
                                context.check("static translation key exists: " + key, labels.has(key));
                                context.expect("static translation follows " + language, translation.getTranslatedText(), labels.get(key).getAsString());
                            } else if (element.id != null && element.id.startsWith("option_")) {
                                element.setTextContent(labels.get("screen.mxt.rift." + element.id).getAsString());
                            }
                        }
                        document.getElementById("editor_mode").setTextContent(labels.get("screen.mxt.rift.mode_new").getAsString());
                        document.getElementById("toggle_properties").setTextContent(labels.get("screen.mxt.rift.hide_properties").getAsString());
                        document.getElementById("status").setTextContent(labels.get("screen.mxt.rift.hint_apply").getAsString());
                        Canvas canvas = (Canvas) document.getElementById("canvas");
                        canvas.setWidth(320);
                        canvas.setHeight(256);
                        tab(document, "shape", "generate");
                        context.check("input query starts outside layout scope", !LayoutMeasureCache.isActive());
                        Element.DOMRect first = canvas.getBoundingClientRect();
                        context.check("cold canvas fills the majority of the window", first.width >= size[0] * 0.55);
                        context.check("cold canvas has finite geometry", Double.isFinite(first.height) && first.height > 0);
                        context.check("cold query closes layout scope", !LayoutMeasureCache.isActive());
                        document.flushPendingStyleUpdates();
                        document.invalidateFontMetrics();
                        Element.DOMRect refreshed = canvas.getBoundingClientRect();
                        close(context, "invalidated canvas x", first.x, refreshed.x);
                        close(context, "invalidated canvas y", first.y, refreshed.y);
                        close(context, "invalidated canvas width", first.width, refreshed.width);
                        close(context, "invalidated canvas height", first.height, refreshed.height);
                        toolbar(context, document, size[0], size[1]);
                        paired(context, document, "width", "height");
                        inputs(context, document);
                        for (String shape : List.of("generate", "vertices", "transform", "curves")) {
                            tab(document, "shape", shape);
                            layout(context, document, size[0], size[1]);
                            Element.DOMRect active = document.getElementById("shape_" + shape).getBoundingClientRect();
                            context.check("active shape tool group has content", active.width > 0 && active.height > 0);
                            for (String other : List.of("generate", "vertices", "transform", "curves"))
                                if (!other.equals(shape)) close(context, "inactive shape group " + other, document.getElementById("shape_" + other).getBoundingClientRect().height, 0);
                        }
                        for (String section : List.of("placement", "target", "appearance")) {
                            tab(document, section, "generate");
                            layout(context, document, size[0], size[1]);
                            if (section.equals("placement")) {
                                paired(context, document, "origin_x", "origin_y");
                                paired(context, document, "origin_y", "origin_z");
                                context.check("coordinate inputs fit property width", document.getElementById("origin_z").getBoundingClientRect().right
                                        <= document.getElementById("properties").getBoundingClientRect().right);
                            }
                        }
                        for (String menu : List.of("blueprint", "edit", "properties")) {
                            if (menu.equals("properties") && document.getElementById("menu_properties").getChildren().isEmpty()) continue;
                            Element.DOMRect before = canvas.getBoundingClientRect();
                            display(document, "menu_" + menu, true);
                            document.commitRenderState();
                            inside(context, document, "menu_" + menu, "panel");
                            close(context, "popup leaves canvas width unchanged", canvas.getBoundingClientRect().width, before.width);
                            close(context, "popup leaves canvas height unchanged", canvas.getBoundingClientRect().height, before.height);
                            if (menu.equals("blueprint")) {
                                Element file = document.getElementById("file_scroll");
                                try (GeometryQueryScope _ = GeometryQueryScope.open()) {
                                    boolean scrollable = file.hasVerticalScrollRange();
                                    if (size[1] <= 360) context.check("short file menu can scroll to export and save", scrollable);
                                    if (scrollable) {
                                        file.setScrollTop(10000);
                                        document.stepScrollRender();
                                        context.check("file menu scrolls independently", file.getScrollTop() > 0);
                                    } else inside(context, document, "save_file", "file_scroll");
                                }
                            }
                            display(document, "menu_" + menu, false);
                        }
                        viewport(context, document);
                        tab(document, "placement", "generate");
                        document.commitRenderState();
                        Element scroll = document.getElementById("scroll");
                        Element.DOMRect footer = document.getElementById("footer").getBoundingClientRect();
                        Element.DOMRect header = document.getElementById("header").getBoundingClientRect();
                        try (GeometryQueryScope _ = GeometryQueryScope.open()) {
                            boolean scrollable = scroll.hasVerticalScrollRange();
                            if (size[1] <= 454) context.check("long properties in short windows have a scroll range", scrollable);
                            if (scrollable) {
                                scroll.setScrollTop(10000);
                                document.stepScrollRender();
                                context.check("properties scroll down", scroll.getScrollTop() > 0);
                            } else inside(context, document, "section_placement", "scroll");
                        }
                        close(context, "scroll keeps footer fixed", document.getElementById("footer").getBoundingClientRect().y, footer.y);
                        close(context, "scroll keeps header fixed", document.getElementById("header").getBoundingClientRect().y, header.y);
                        Element panel = document.getElementById("panel");
                        double height = panel.getBoundingClientRect().height;
                        display(document, "body", false);
                        display(document, "toolbar", false);
                        panel.getClassList().add("world-preview");
                        document.flushPendingStyleUpdates();
                        document.commitRenderState();
                        Element.DOMRect preview = panel.getBoundingClientRect();
                        context.check("preview panel collapses", preview.height > 0 && preview.height < height);
                        inside(context, document, "footer", "panel");
                        panel.getClassList().remove("world-preview");
                        display(document, "body", true);
                        display(document, "toolbar", true);
                        document.flushPendingStyleUpdates();
                        document.commitRenderState();
                        close(context, "leaving preview restores panel height", panel.getBoundingClientRect().height, height);
                        context.check("panel query closes layout scope", !LayoutMeasureCache.isActive());
                        document.getElementById("apply").setTextContent(labels.get("screen.mxt.rift.apply_item").getAsString());
                        document.getElementById("editor_mode").setTextContent(labels.get("screen.mxt.rift.mode_item").getAsString());
                        document.getElementById("hint_plane").setTextContent(labels.get("screen.mxt.rift.hint_item_placement").getAsString());
                        display(document, "blueprint", false);
                        RiftEditorToolbar.layout(document);
                        document.flushPendingStyleUpdates();
                        layout(context, document, size[0], size[1]);
                        display(document, "menu_blueprint", true);
                        document.commitRenderState();
                        inside(context, document, "menu_blueprint", "panel");
                    } finally {
                        document.remove();
                    }
                    context.expect("reopening does not retain documents", Document.getAll().size(), documents);
                }
            }
        } finally {
            Language.inject(original);
            Size.clearViewportOverride();
        }
    }

    private static Language language(JsonObject labels, Language fallback) {
        return new Language() {
            @Override
            public @NonNull String getOrDefault(@NonNull String key, @NonNull String defaultValue) {
                return labels.has(key) ? labels.get(key).getAsString() : fallback.getOrDefault(key, defaultValue);
            }

            @Override
            public boolean has(@NonNull String key) {
                return labels.has(key) || fallback.has(key);
            }

            @Override
            public boolean isDefaultRightToLeft() {
                return false;
            }

            @Override
            public @NonNull FormattedCharSequence getVisualOrder(@NonNull FormattedText text) {
                return fallback.getVisualOrder(text);
            }
        };
    }

    private static void display(Document document, String id, boolean visible) {
        document.getElementById(id).setInlineStyleProperty("display", visible ? "flex" : "none");
    }

    private static void tab(Document document, String tab, String shape) {
        for (String section : List.of("shape", "placement", "target", "appearance"))
            display(document, "section_" + section, section.equals(tab));
        display(document, "shape_tabs", tab.equals("shape"));
        for (String group : List.of("generate", "vertices", "transform", "curves"))
            display(document, "shape_" + group, group.equals(shape));
        display(document, "target_position", true);
        display(document, "target_secret_realm", false);
        display(document, "hint_return", false);
        document.flushPendingStyleUpdates();
    }

    private static void toolbar(ProbeContext context, Document document, int width, int height) {
        List<String> ids = List.of("tab_shape", "tab_placement", "tab_target", "tab_appearance", "toggle_properties", "preview", "camera");
        List<Element> actions = ids.stream().map(document::getElementById).toList();
        Element shortcuts = document.getElementById("property_shortcuts"), overflow = document.getElementById("menu_properties");
        int[] clicks = {0};
        actions.getLast().addEventListener("click", _ -> clicks[0]++);
        int expectedVisible = -1, fired = 0;
        for (int[] size : List.of(new int[]{width, height}, new int[]{320, 240}, new int[]{1280, 720}, new int[]{width, height})) {
            Size.setViewportOverride(size[0], size[1]);
            document.applyViewport(true);
            boolean folded = RiftEditorToolbar.layout(document);
            document.flushPendingStyleUpdates();
            document.commitRenderState();
            context.check("overflow exists only when needed", folded == !overflow.getChildren().isEmpty());
            context.expect("all property actions retained", shortcuts.getChildren().size() + overflow.getChildren().size(), ids.size());
            if (size[0] == 320) context.check("small windows fold the property actions", folded);
            if (size[0] == 1280) context.check("large windows expose all property actions", !folded);
            if (expectedVisible < 0) expectedVisible = shortcuts.getChildren().size();
            inside(context, document, "menus", "header");
            inside(context, document, "footer", "header");
            context.check("menus do not overlap the save buttons", document.getElementById("menus").getBoundingClientRect().right
                    <= document.getElementById("footer").getBoundingClientRect().left);
            Element previous = null;
            for (int i = 0; i < ids.size(); i++) {
                String id = ids.get(i);
                Element action = document.getElementById(id);
                context.check("resizing preserves action identity", action == actions.get(i));
                context.expect("resizing keeps exactly one action node", (int) document.getElements().stream().filter(e -> id.equals(e.id)).count(), 1);
                if (action.parentElement == shortcuts) {
                    inside(context, document, ids.get(i), "header");
                    close(context, "compact button height", action.getBoundingClientRect().height, 16);
                    if (previous != null) context.check("inline actions never overlap", previous.getBoundingClientRect().right <= action.getBoundingClientRect().left);
                    previous = action;
                }
            }
            if (folded) {
                display(document, "menu_properties", true);
                document.commitRenderState();
                inside(context, document, "menu_properties", "panel");
                for (Element action : overflow.getChildren()) {
                    inside(context, document, action.id, "menu_properties");
                    close(context, "compact popup button height", action.getBoundingClientRect().height, 16);
                }
                close(context, "popup fits its visible buttons", overflow.getBoundingClientRect().height, overflow.getChildren().size() * 19 + 5);
                display(document, "menu_properties", false);
            }
            Event.tiggerEvent(new Event(actions.getLast(), "click", true));
            context.expect("moving a button keeps one click listener", clicks[0], ++fired);
            context.check("toolbar queries close their layout scopes", !LayoutMeasureCache.isActive());
        }
        context.expect("restoring the viewport restores inline actions", shortcuts.getChildren().size(), expectedVisible);
        document.flushPendingStyleUpdates();
        document.commitRenderState();
    }

    private static void layout(ProbeContext context, Document document, int width, int height) {
        document.commitRenderState();
        Element.DOMRect panel = document.getElementById("panel").getBoundingClientRect();
        context.check("panel fits " + width + "x" + height, panel.left >= 0 && panel.top >= 0 && panel.right <= width && panel.bottom <= height);
        for (String part : List.of("header", "toolbar", "body", "feedback")) inside(context, document, part, "panel");
        inside(context, document, "canvas", "canvas_stage");
        inside(context, document, "canvas_stage", "workspace");
        inside(context, document, "scroll", "properties");
        for (String tool : List.of("select", "draw", "edge", "move")) inside(context, document, "tool_" + tool, "tools");
        context.check("workspace and properties do not overlap", document.getElementById("properties").getBoundingClientRect().right <= document.getElementById("workspace").getBoundingClientRect().left);
        List<String> rows = List.of("header", "toolbar", "body", "feedback");
        for (int i = 1; i < rows.size(); i++)
            context.check(rows.get(i - 1) + " stays above " + rows.get(i), document.getElementById(rows.get(i - 1)).getBoundingClientRect().bottom
                    <= document.getElementById(rows.get(i)).getBoundingClientRect().top);
        inside(context, document, "footer", "header");
        for (String button : List.of("close", "apply")) inside(context, document, button, "footer");
        for (String id : List.of("tool_select", "tool_draw", "tool_edge", "tool_move", "fit_view", "close")) {
            Element button = document.getElementById(id);
            context.check("translated button keeps its layout container: " + id, !(button instanceof Translation));
            Element label = button.getChildren().getFirst();
            context.check("button keeps its translation child: " + id, label instanceof Translation);
            Element.DOMRect bounds = button.getBoundingClientRect(), text = label.getBoundingClientRect();
            close(context, "button translation is horizontally centered: " + id, text.x + text.width / 2, bounds.x + bounds.width / 2);
            close(context, "button translation is vertically centered: " + id, text.y + text.height / 2, bounds.y + bounds.height / 2);
        }
        Element.DOMRect canvas = document.getElementById("canvas").getBoundingClientRect(), body = document.getElementById("body").getBoundingClientRect();
        context.check("canvas takes most of the working area", canvas.width >= body.width * 0.58);
        close(context, "canvas takes the entire working height", canvas.height, body.height);
    }

    private static void viewport(ProbeContext context, Document document) {
        Canvas canvas = (Canvas) document.getElementById("canvas");
        Element.DOMRect before = canvas.getBoundingClientRect();
        RiftEditorViewport view = new RiftEditorViewport();
        context.check("viewport follows canvas size", view.resize(before.width, before.height));
        close(context, "viewport pixel width", view.width(), Math.round(before.width));
        close(context, "viewport pixel height", view.height(), Math.round(before.height));
        context.check("unchanged canvas avoids buffer reallocation", !view.resize(before.width, before.height));
        canvas.setWidth(view.width());
        canvas.setHeight(view.height());
        RiftVertex point = new RiftVertex(-2.125, 3.375);
        RiftVertex projected = view.local(view.x(point.u()), view.y(point.v()));
        close(context, "wide canvas horizontal round trip", projected.u(), point.u());
        close(context, "wide canvas vertical round trip", projected.v(), point.v());
        RiftVertex center = view.local(view.width() / 2.0, view.height() / 2.0);
        display(document, "properties", false);
        document.commitRenderState();
        Element.DOMRect expanded = canvas.getBoundingClientRect();
        context.check("hiding sidebar widens canvas", expanded.width > before.width + 100);
        close(context, "hidden sidebar gives full working width", expanded.width, document.getElementById("body").getBoundingClientRect().width);
        view.resize(expanded.width, expanded.height);
        close(context, "resize retains horizontal view center", view.local(view.width() / 2.0, view.height() / 2.0).u(), center.u());
        close(context, "resize retains vertical view center", view.local(view.width() / 2.0, view.height() / 2.0).v(), center.v());
        view.pan(new RiftVertex(0, 0), new RiftVertex(1, -2));
        close(context, "pan horizontal distance", view.local(view.width() / 2.0, view.height() / 2.0).u(), center.u() - 1);
        close(context, "pan vertical distance", view.local(view.width() / 2.0, view.height() / 2.0).v(), center.v() + 2);
        view.zoom(1000);
        close(context, "zoom upper bound", view.scale(), 512);
        view.zoom(-1000);
        close(context, "zoom lower bound", view.scale(), 4);
        RiftShape shape = new RiftShape(List.of(new RiftVertex(-1, -1), new RiftVertex(1, -1), new RiftVertex(1, 1), new RiftVertex(-1, 1)));
        view.fit(shape);
        for (RiftVertex vertex : shape.vertices())
            context.check("fit keeps outline inside canvas", view.x(vertex.u()) >= 15 && view.x(vertex.u()) <= view.width() - 15
                    && view.y(vertex.v()) >= 15 && view.y(vertex.v()) <= view.height() - 15);
        context.check("zero and NaN geometry do not resize", !view.resize(0, 100) && !view.resize(Double.NaN, 100));
        view.resize(10000, 5000);
        context.expect("large backing buffer width capped", view.width(), 4096);
        context.expect("large backing buffer preserves aspect", view.height(), 2048);
        display(document, "properties", true);
        document.commitRenderState();
        close(context, "sidebar restores canvas width", canvas.getBoundingClientRect().width, before.width);
    }

    private static void inside(ProbeContext context, Document document, String child, String parent) {
        Element.DOMRect a = document.getElementById(child).getBoundingClientRect(), b = document.getElementById(parent).getBoundingClientRect();
        context.check(child + " fits " + parent + " (" + a.left + "," + a.top + "," + a.right + "," + a.bottom + ")",
                a.width > 0 && a.height > 0 && a.left >= b.left - 0.01 && a.top >= b.top - 0.01 && a.right <= b.right + 0.01 && a.bottom <= b.bottom + 0.01);
    }

    private static void close(ProbeContext context, String name, double actual, double expected) {
        context.check(name + ": " + actual + " / " + expected, Math.abs(actual - expected) < 1e-7);
    }

    private static void paired(ProbeContext context, Document document, String left, String right) {
        // AUI commits geometry on its tick and a headless document never ticks, so an uncommitted layout still
        // answers with the last committed rect. Commit the pending layout first, the way Document#hitTest does.
        document.commitRenderState();
        Element.DOMRect a = document.getElementById(left).getBoundingClientRect(), b = document.getElementById(right).getBoundingClientRect();
        context.check(left + " / " + right + " share a row", Math.abs(a.top - b.top) < 1e-7 && a.width > 0 && b.width > 0);
        context.check(left + " / " + right + " do not overlap (" + a.x + ".." + a.right + " vs " + b.x + ".." + b.right + ")", a.right <= b.left);
    }

    private static void inputs(ProbeContext context, Document document) {
        RiftEditorInputs fields = new RiftEditorInputs();
        for (String name : List.of("origin_x", "origin_y", "origin_z", "snap", "enabled", "nearby", "target_type", "style", "yaw", "count"))
            fields.bind(name, document.getElementById(name));
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.GERMANY);
            Vec3 exact = new Vec3(-1234.123456789, 64.9999999, 0.000123456);
            fields.vector("origin", exact);
            context.expect("coordinate display uses three decimal places", fields.value("origin_x"), "-1234.123");
            context.expect("untouched vector retains exact precision", fields.vector("origin"), exact);
            context.expect("snap display uses three decimal places", fields.value("snap"), "0.063");
            context.check("rounded snap keeps 1/16 spacing", fields.number("snap") == 0.0625);
            Event.tiggerEvent(new Event(document.getElementById("snap"), "input", true));
            context.check("editing rounded text discards original precision", fields.number("snap") == 0.063);
            document.getElementById("origin_x").setValue("-2.345678");
            Event.tiggerEvent(new Event(document.getElementById("origin_x"), "blur", false));
            context.expect("edited coordinate reformats on blur", fields.value("origin_x"), "-2.346");
            context.check("edited coordinate keeps entered precision", fields.number("origin_x") == -2.345678);
            context.check("editing one axis preserves the others", fields.vector("origin").y == exact.y && fields.vector("origin").z == exact.z);
            document.getElementById("origin_x").setValue("4.500");
            context.check("new text overrides cached precision", fields.number("origin_x") == 4.5);
            fields.number("yaw", -0.00001);
            context.expect("small negative value displays without negative zero", fields.value("yaw"), "0.000");
            fields.value("yaw", "");
            context.expect("optional angle stays empty", fields.value("yaw"), "");
            context.expect("integer count stays integer", fields.value("count"), "16");
            fields.checked("enabled", true);
            fields.checked("nearby", false);
            context.check("checkbox reads checked state", fields.checked("enabled") && !fields.checked("nearby"));
            context.check("checkbox is native AUI input", document.getElementById("enabled") instanceof Input && "checkbox".equals(document.getElementById("enabled").getType()));
            fields.value("target_type", "secret_realm");
            fields.value("style", "portal");
            context.expect("destination dropdown retains codec value", fields.value("target_type"), "secret_realm");
            context.expect("style dropdown retains codec value", fields.value("style"), "portal");
            document.getElementById("origin_x").setValue("NaN");
            boolean rejected = false;
            try {
                fields.vector("origin");
            } catch (IllegalArgumentException expected) {
                rejected = true;
            }
            context.check("non-finite axis rejected", rejected);
        } finally {
            Locale.setDefault(previous);
            fields.clear();
        }
    }
}
