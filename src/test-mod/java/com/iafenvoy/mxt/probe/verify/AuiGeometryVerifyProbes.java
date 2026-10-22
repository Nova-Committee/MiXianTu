package com.iafenvoy.mxt.probe.verify;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.probe.Probe;
import com.iafenvoy.mxt.probe.ProbeContext;
import com.iafenvoy.mxt.screen.rift.RiftEditorInputs;
import com.sighs.apricityui.element.Input;
import com.sighs.apricityui.element.Option;
import com.sighs.apricityui.element.Select;
import com.sighs.apricityui.event.Event;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.layout.LayoutMeasureCache;
import com.sighs.apricityui.layout.Size;
import com.sighs.apricityui.parser.HTML;
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
        Element.register("input", (document, _) -> new Input(document));
        Element.register("select", (document, _) -> new Select(document));
        Element.register("option", (document, _) -> new Option(document));
        String path = "mxt_test/aui_rift_geometry_probe.html";
        String css = resource("apricityui/apricity/mxt/common/theme.css") + "\n" + resource("apricityui/apricity/mxt/rift/editor.css");
        String html = resource("apricityui/apricity/mxt/rift/editor.html").replaceAll("<link[^>]*>", "")
                .replace("</head>", "<style>" + css + "</style></head>");
        JsonObject labels = JsonParser.parseString(resource("mxt/lang/zh_cn.json")).getAsJsonObject();
        HTML.putTemple(path, html);
        int documents = Document.getAll().size();
        Size.setViewportOverride(768, 454);
        try {
            for (int opening = 0; opening < 6; opening++) {
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
                    document.getElementById("section_shape").setInlineStyleProperty("display", "flex");
                    Element canvas = document.getElementById("canvas"), panel = document.getElementById("panel");
                    context.check("input query starts outside layout scope", !LayoutMeasureCache.isActive());
                    Element.DOMRect first = canvas.getBoundingClientRect();
                    context.check("cold canvas width", Math.abs(first.width - 320) < 1e-7);
                    context.check("cold canvas height", Math.abs(first.height - 256) < 1e-7);
                    context.check("cold query closes layout scope", !LayoutMeasureCache.isActive());
                    document.flushPendingStyleUpdates();
                    document.invalidateFontMetrics();
                    Element.DOMRect refreshed = canvas.getBoundingClientRect();
                    context.check("invalidated canvas origin", Math.abs(first.x - refreshed.x) < 1e-7 && Math.abs(first.y - refreshed.y) < 1e-7);
                    context.check("invalidated canvas size", Math.abs(first.width - refreshed.width) < 1e-7 && Math.abs(first.height - refreshed.height) < 1e-7);
                    paired(context, document, "width", "height");
                    inputs(context, document);
                    document.getElementById("section_shape").setInlineStyleProperty("display", "none");
                    document.getElementById("section_placement").setInlineStyleProperty("display", "flex");
                    paired(context, document, "origin_x", "origin_y");
                    paired(context, document, "origin_y", "origin_z");
                    context.check("coordinate inputs fit property width", document.getElementById("origin_z").getBoundingClientRect().right
                            <= document.getElementById("properties").getBoundingClientRect().right);
                    double height = panel.getBoundingClientRect().height;
                    document.getElementById("body").setInlineStyleProperty("display", "none");
                    panel.getClassList().add("world-preview");
                    // AUI applies class-selector changes during its tick, before committing render geometry.
                    document.flushPendingStyleUpdates();
                    Element.DOMRect preview = panel.getBoundingClientRect();
                    context.check("preview panel uses refreshed geometry: " + preview.height + " / " + height,
                            Double.isFinite(preview.height) && preview.height > 0 && preview.height < height);
                    context.check("panel query closes layout scope", !LayoutMeasureCache.isActive());
                } finally {
                    document.remove();
                }
                context.expect("reopening does not retain documents", Document.getAll().size(), documents);
            }
        } finally {
            Size.clearViewportOverride();
        }
    }

    private static void paired(ProbeContext context, Document document, String left, String right) {
        Element.DOMRect a = document.getElementById(left).getBoundingClientRect(), b = document.getElementById(right).getBoundingClientRect();
        context.check(left + " / " + right + " share a row", Math.abs(a.top - b.top) < 1e-7 && a.width > 0 && b.width > 0);
        context.check(left + " / " + right + " do not overlap", a.right <= b.left);
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
