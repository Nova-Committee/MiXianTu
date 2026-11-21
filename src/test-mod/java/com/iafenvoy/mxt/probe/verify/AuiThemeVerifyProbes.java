package com.iafenvoy.mxt.probe.verify;

import com.google.gson.JsonElement;
import com.iafenvoy.jupiter.config.entry.EnumEntry;
import com.iafenvoy.mxt.config.MxtClientConfig;
import com.iafenvoy.mxt.probe.Probe;
import com.iafenvoy.mxt.probe.ProbeContext;
import com.iafenvoy.mxt.screen.aui.AuiElements;
import com.iafenvoy.mxt.screen.aui.AuiStyles;
import com.iafenvoy.mxt.screen.aui.AuiTheme;
import com.iafenvoy.mxt.screen.aui.AuiWrappedScreen;
import com.mojang.serialization.JsonOps;
import com.sighs.apricityui.dom.SlotContentRules;
import com.sighs.apricityui.dom.expander.ContainerExpander;
import com.sighs.apricityui.element.Container;
import com.sighs.apricityui.element.Item;
import com.sighs.apricityui.element.Slot;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.layout.Size;
import com.sighs.apricityui.parser.Color;
import com.sighs.apricityui.parser.HTML;
import com.sighs.apricityui.render.GeometryQueryScope;
import com.sighs.apricityui.style.Background;
import com.sighs.apricityui.style.Text;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public final class AuiThemeVerifyProbes {
    private AuiThemeVerifyProbes() {
    }

    public static List<Probe> probes() {
        return List.of(Probe.of("verify/aui/themes", AuiThemeVerifyProbes::themes),
                Probe.of("verify/aui/theme_text", AuiThemeVerifyProbes::themeText),
                Probe.of("verify/aui/theme_config", AuiThemeVerifyProbes::themeConfig));
    }

    private static Document page(String page) {
        String stylesheet = page.startsWith("economy/station_") ? "economy/station" : page;
        String css = AuiTestResources.read("apricityui/apricity/mxt/common/theme.css") + "\n"
                + AuiTestResources.read("apricityui/apricity/mxt/" + stylesheet + ".css");
        String html = AuiTestResources.read("apricityui/apricity/mxt/" + page + ".html")
                .replaceAll("<link[^>]*>", "").replace("</head>", "<style>" + css + "</style></head>");
        String path = "mxt_test/theme_" + page.replace('/', '_') + ".html";
        HTML.putTemple(path, html);
        Document document = Document.create(path);
        if (document == null || document.body == null) throw new IllegalStateException("Missing theme page: " + page);
        document.registerUaStylesheet(AuiTestResources.read("apricityui/apricity/global.css"), "mxt_test/global.css");
        return document;
    }

    private static void themes(ProbeContext context) {
        Element.register("container", (document, _) -> new Container(document));
        Element.register("slot", (document, _) -> new Slot(document));
        int documents = Document.getAll().size();
        try {
            for (String page : List.of("information/information", "spirit_crafting/spirit_crafting", "rift/editor",
                    "profiler/profiler", "economy/cheque", "economy/exchange", "economy/station_customer",
                    "economy/station_owner", "economy/trade")) {
                for (int[] viewport : List.of(new int[]{320, 240}, new int[]{640, 360}, new int[]{960, 540})) {
                    Size.setViewportOverride(viewport[0], viewport[1]);
                    Document document = page(page);
                    try (Document.ContextScope _ = Document.withContext(document)) {
                        ContainerExpander.expand(document);
                        SlotContentRules.normalizeTemplate(document);
                        List<Rect> baseline = null;
                        for (AuiTheme theme : List.of(AuiTheme.PARCHMENT, AuiTheme.JADE, AuiTheme.INK_GOLD, AuiTheme.PARCHMENT)) {
                            theme.apply(document);
                            document.flushPendingStyleUpdates();
                            document.commitRenderState();
                            int ink = switch (theme) {
                                case PARCHMENT -> 0xFF3A322A;
                                case JADE -> 0xFF243F38;
                                case INK_GOLD -> 0xFFE5DCC8;
                            };
                            context.expect(page + " body ink " + theme, Text.getFontColor(document.body), ink);
                            context.expect(page + " canvas palette follows CSS " + theme, AuiStyles.palette(document.body).ink(), ink);
                            Element panel = document.getElementById("panel");
                            context.check(page + " has themed corner art " + theme, Background.of(panel).getLayers().stream()
                                    .anyMatch(layer -> layer.imagePath != null && layer.imagePath.contains("corner-tl.svg")));
                            List<Rect> actual = new ArrayList<>();
                            try (GeometryQueryScope _ = GeometryQueryScope.open()) {
                                geometry(panel, actual);
                            }
                            if (baseline == null) baseline = actual;
                            else context.expect(page + " theme preserves panel and item geometry", actual, baseline);
                        }
                    } finally {
                        document.remove();
                    }
                }
            }
        } finally {
            Size.clearViewportOverride();
        }
        context.expect("theme pages release documents", Document.getAll().size(), documents);
    }

    private record Rect(double x, double y, double width, double height) {
    }

    private static void geometry(Element element, List<Rect> rectangles) {
        if (element instanceof Slot || element instanceof Item || "panel".equals(element.getAttribute("id"))) {
            Element.DOMRect rect = element.getBoundingClientRect();
            rectangles.add(new Rect(rect.x, rect.y, rect.width, rect.height));
        }
        for (Element child : element.getChildren()) geometry(child, rectangles);
    }

    private static void themeText(ProbeContext context) {
        Size.setViewportOverride(640, 360);
        Document document = page("spirit_crafting/spirit_crafting");
        try (Document.ContextScope _ = Document.withContext(document)) {
            Element text = document.body.appendChild(new Element(document, "div"));
            for (AuiTheme theme : AuiTheme.values()) {
                theme.apply(document);
                document.flushPendingStyleUpdates();
                AuiElements.setText(text, "ordinary <&> text");
                context.expect("plain div inherits theme " + theme, Text.getFontColor(text), AuiStyles.palette(document.body).ink());
                int background = AuiStyles.color(document.body, "--surface-deep");
                for (int red = 0; red < 256; red += 17)
                    for (int green = 0; green < 256; green += 17)
                        for (int blue = 0; blue < 256; blue += 17) {
                            int rgb = (red << 16) | (green << 8) | blue;
                            int rendered = Color.parse(AuiStyles.textColor(text, rgb));
                            if (!context.check("data text has 4.5:1 contrast: " + theme + "/" + rgb,
                                    contrast(rendered, background) >= 4.5 - 0.00001)) return;
                        }
                Component colored = Component.literal("water ").withColor(0x0000FF)
                        .append(Component.literal("fire").withColor(0xFF3300));
                AuiElements.setText(text, colored);
                document.flushPendingStyleUpdates();
                context.expect("styled div keeps content", text.getTextContent(), "water fire");
                context.check("data colors retain distinct hues", Text.getFontColor(text.getFirstElementChild())
                        != Text.getFontColor(text.getLastElementChild()));
                for (Element run : text.getChildren())
                    context.check("styled runs are readable", contrast(Text.getFontColor(run), background) >= 4.5 - 0.00001);
                AuiElements.setText(text, Component.literal("ordinary"));
                document.flushPendingStyleUpdates();
                context.expect("reused div clears data color", Text.getFontColor(text.getFirstElementChild()), AuiStyles.palette(document.body).ink());
                AuiElements.setText(text, "ordinary");
                context.check("plain div clears rich runs", text.getChildren().isEmpty());
            }
        } finally {
            document.remove();
            Size.clearViewportOverride();
        }
    }

    private static double contrast(int first, int second) {
        double a = luminance(first), b = luminance(second);
        return (Math.max(a, b) + 0.05) / (Math.min(a, b) + 0.05);
    }

    private static double luminance(int rgb) {
        double[] weights = {0.2126, 0.7152, 0.0722};
        double value = 0;
        for (int index = 0; index < 3; index++) {
            double channel = ((rgb >> (16 - index * 8)) & 255) / 255.0;
            value += weights[index] * (channel <= 0.04045 ? channel / 12.92 : Math.pow((channel + 0.055) / 1.055, 2.4));
        }
        return value;
    }

    private static void themeConfig(ProbeContext context) {
        EnumEntry<AuiTheme> entry = MxtClientConfig.INSTANCE.appearance.theme;
        AuiTheme previous = entry.getValue();
        context.expect("default theme is parchment", entry.getDefaultValue(), AuiTheme.PARCHMENT);
        Size.setViewportOverride(640, 360);
        Document first = page("spirit_crafting/spirit_crafting"), second = page("spirit_crafting/spirit_crafting");
        try {
            for (AuiTheme theme : AuiTheme.values()) {
                JsonElement encoded = entry.getCodec().encodeStart(JsonOps.INSTANCE, theme).getOrThrow();
                context.expect("config enum roundtrip " + theme, entry.getCodec().parse(JsonOps.INSTANCE, encoded).getOrThrow(), theme);
                entry.setValue(theme);
                Host host = new Host(first);
                host.auiPageOpened();
                context.expect("theme is applied before binding", host.themeAtBind, theme.id());
                context.expect("theme styles are resolved before binding", host.inkAtBind, switch (theme) {
                    case PARCHMENT -> 0xFF3A322A;
                    case JADE -> 0xFF243F38;
                    case INK_GOLD -> 0xFFE5DCC8;
                });
                host.auiClose();
                context.check("theme does not leak to another document", second.body.getAttribute("data-mxt-theme") == null);
            }
            entry.setValue(AuiTheme.JADE);
            Host host = new Host(second);
            host.auiPageOpened();
            entry.setValue(AuiTheme.INK_GOLD);
            host.auiRebind();
            context.expect("current page stays stable until rebuilt", second.body.getAttribute("data-mxt-theme"), "jade");
            context.expect("another page keeps its own theme", first.body.getAttribute("data-mxt-theme"), "ink-gold");
            host.auiClose();
            host.auiPageOpened();
            context.expect("reopened page uses new config", second.body.getAttribute("data-mxt-theme"), "ink-gold");
            host.auiClose();
        } finally {
            first.remove();
            second.remove();
            Size.clearViewportOverride();
            entry.setValue(previous);
        }
    }

    private static final class Host implements AuiWrappedScreen {
        private final State state = new State();
        private final Document document;
        private String themeAtBind;
        private int inkAtBind;

        private Host(Document document) {
            this.document = document;
        }

        @Override
        public State auiState() {
            return this.state;
        }

        @Override
        public Document getLinkedDocument() {
            return this.document;
        }

        @Override
        public void bindPage() {
            this.themeAtBind = this.document.body.getAttribute("data-mxt-theme");
            this.inkAtBind = Text.getFontColor(this.document.body);
        }
    }
}
