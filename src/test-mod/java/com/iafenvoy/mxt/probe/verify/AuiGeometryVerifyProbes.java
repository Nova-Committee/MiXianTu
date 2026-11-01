package com.iafenvoy.mxt.probe.verify;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.data.rift.RiftShape;
import com.iafenvoy.mxt.data.rift.RiftVertex;
import com.iafenvoy.mxt.probe.Probe;
import com.iafenvoy.mxt.probe.ProbeContext;
import com.iafenvoy.mxt.screen.rift.RiftEditorInputs;
import com.iafenvoy.mxt.screen.rift.RiftEditorToolbar;
import com.iafenvoy.mxt.screen.rift.RiftEditorViewport;
import com.sighs.apricityui.element.Canvas;
import com.sighs.apricityui.element.Input;
import com.sighs.apricityui.element.Option;
import com.sighs.apricityui.element.Select;
import com.sighs.apricityui.event.Event;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.layout.LayoutMeasureCache;
import com.sighs.apricityui.layout.Size;
import com.sighs.apricityui.parser.HTML;
import com.sighs.apricityui.render.GeometryQueryScope;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;

public final class AuiGeometryVerifyProbes {
    private AuiGeometryVerifyProbes() {
    }

    public static List<Probe> probes() {
        return List.of(Probe.of("verify/aui/rift_geometry", AuiGeometryVerifyProbes::riftGeometry));
    }

    private static String resource(String path) {
        try (InputStream input = MiXianTu.class.getResourceAsStream("/assets/" + path)) {
            if (input == null) throw new NoSuchElementException(path);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    private static void riftGeometry(ProbeContext context) {
        // Dedicated servers do not register AUI's client controls automatically.
        Element.register("canvas", (document, _) -> new Canvas(document));
        Element.register("input", (document, _) -> new Input(document));
        Element.register("select", (document, _) -> new Select(document));
        Element.register("option", (document, _) -> new Option(document));
        String path = "mxt_test/aui_rift_geometry_probe.html";
        String css = resource("apricityui/apricity/mxt/common/theme.css") + "\n" + resource("apricityui/apricity/mxt/rift/editor.css");
        String html = resource("apricityui/apricity/mxt/rift/editor.html").replaceAll("<link[^>]*>", "")
                .replace("</head>", "<style>" + css + "</style></head>");
        HTML.putTemple(path, html);
        int documents = Document.getAll().size();
        try {
            for (String language : List.of("zh_cn", "en_us")) {
                JsonObject labels = JsonParser.parseString(resource("mxt/lang/" + language + ".json")).getAsJsonObject();
                for (int[] size : List.of(new int[]{768, 454}, new int[]{640, 360}, new int[]{480, 270}, new int[]{960, 540},
                        new int[]{320, 240}, new int[]{1280, 720})) {
                    Size.setViewportOverride(size[0], size[1]);
                    Document document = Document.create(path);
                    if (!context.check("editor document created", document != null && document.body != null)) {
                        if (document != null) document.remove();
                        return;
                    }
                    try (Document.ContextScope _ = Document.withContext(document)) {
                        for (Element element : document.getElements()) {
                            if (element.id == null) continue;
                            String key = "screen.mxt.rift." + (element.id.startsWith("label_") ? "field_" + element.id.substring(6) : element.id);
                            if (labels.has(key)) element.setTextContent(labels.get(key).getAsString());
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
                                try (var _ = GeometryQueryScope.open()) {
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
                        try (var _ = GeometryQueryScope.open()) {
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
            Size.clearViewportOverride();
        }
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
