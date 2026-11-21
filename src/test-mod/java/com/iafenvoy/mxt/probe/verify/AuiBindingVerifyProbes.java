package com.iafenvoy.mxt.probe.verify;

import com.google.gson.JsonElement;
import com.iafenvoy.mxt.attachment.TechniqueAttachment;
import com.iafenvoy.mxt.compat.jei.SpiritJeiText;
import com.iafenvoy.mxt.data.aura.Aura;
import com.iafenvoy.mxt.data.cost.builtin.AuraCost;
import com.iafenvoy.saturn.util.formula.number.Constant;
import com.iafenvoy.saturn.util.formula.number.Expression;
import com.iafenvoy.mxt.data.cultivation.Technique;
import com.iafenvoy.mxt.probe.Probe;
import com.iafenvoy.mxt.probe.ProbeContext;
import com.iafenvoy.mxt.probe.ProbeDefinitions;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.screen.aui.AuiElements;
import com.iafenvoy.mxt.screen.aui.AuiStyles;
import com.iafenvoy.mxt.screen.aui.AuiWrappedScreen;
import com.iafenvoy.mxt.screen.information.MxtPlayerInformation;
import com.iafenvoy.mxt.screen.information.MxtPlayerView;
import com.iafenvoy.saturn.util.DefinitionText;
import com.mojang.serialization.JsonOps;
import com.sighs.apricityui.event.Event;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.style.Text;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.NoSuchElementException;

public final class AuiBindingVerifyProbes {
    private AuiBindingVerifyProbes() {
    }

    public static List<Probe> probes() {
        return List.of(Probe.of("verify/aui/bindings", AuiBindingVerifyProbes::bindings),
                Probe.of("verify/aui/styled_text", AuiBindingVerifyProbes::styledText),
                Probe.of("verify/aui/paper_colors", AuiBindingVerifyProbes::paperColors),
                Probe.of("verify/aui/information_lines", AuiBindingVerifyProbes::informationLines),
                Probe.of("verify/aui/progress_tracks", AuiBindingVerifyProbes::progressTracks),
                Probe.of("verify/jei/aura_entries", AuiBindingVerifyProbes::auraEntries));
    }

    private static void auraEntries(ProbeContext context) {
        Holder<Aura> icon = context.require(MxtResourceKeys.AURA, ProbeDefinitions.SPIRIT_POWER);
        Holder<Aura> plain = context.require(MxtResourceKeys.AURA, ProbeDefinitions.QI);
        List<SpiritJeiText.AuraEntry> entries = SpiritJeiText.auraEntries(List.of(new AuraCost(icon, new Constant(1.25)),
                new AuraCost(plain, new Expression("amount * 2")), new AuraCost(icon, new Constant(3)),
                new AuraCost(plain, new Constant(4))));
        context.expect("all costs remain available beyond three rows", entries.size(), 4);
        context.expect("resource supplies the aura icon", entries.getFirst().display().icon(), icon.value().resource().value().icon());
        context.check("configured icon is present", entries.getFirst().display().icon().isPresent());
        context.check("unconfigured aura keeps its name", entries.get(1).display().icon().isEmpty());
        context.expect("fallback uses the aura name", entries.get(1).display().name().getString(), DefinitionText.nameOrId(plain).getString());
        context.expect("fractional requirement keeps decimals", entries.getFirst().amount(), "1.25");
        context.expect("dynamic requirement is not evaluated", entries.get(1).amount(), "amount * 2");
        context.expect("tooltip identifies the aura", entries.getFirst().tooltip().getFirst().getString(), DefinitionText.nameOrId(icon).getString());
        context.expect("tooltip preserves the full value", entries.getFirst().tooltip().get(1).getString(), "× 1.25");
        context.check("free recipe has no label", SpiritJeiText.auraEntries(List.of()).isEmpty());
    }

    private static void informationLines(ProbeContext context) {
        ServerPlayer player = context.player();
        TechniqueAttachment attachment = player.getData(MxtAttachments.TECHNIQUE);
        attachment.setLearnedTechniques(List.of());
        context.check("empty technique list leaves no row", !MxtPlayerView.capture(player).containsKey(MxtPlayerInformation.TECHNIQUES));
        Holder<Technique> first = context.require(MxtResourceKeys.TECHNIQUE, ProbeDefinitions.TECHNIQUE);
        Holder<Technique> second = context.require(MxtResourceKeys.TECHNIQUE, ProbeDefinitions.SWORD_MANUAL);
        attachment.setLearnedTechniques(List.of(first, second));
        MxtPlayerView.Row row = MxtPlayerView.capture(player).get(MxtPlayerInformation.TECHNIQUES);
        context.expect("each technique occupies a value line", row.values().size(), 2);
        context.expect("first line preserves learning order", row.values().getFirst().getString(), DefinitionText.nameOrId(first).getString());
        context.expect("second line preserves learning order", row.values().get(1).getString(), DefinitionText.nameOrId(second).getString());
        JsonElement encoded = MxtPlayerView.Row.CODEC.encodeStart(JsonOps.INSTANCE, row).getOrThrow();
        MxtPlayerView.Row decoded = MxtPlayerView.Row.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow();
        context.expect("snapshot round trip preserves separate lines", decoded.values().stream().map(Component::getString).toList(),
                row.values().stream().map(Component::getString).toList());
        attachment.setLearnedTechniques(List.of(second));
        context.expect("removing a technique removes its line", MxtPlayerView.capture(player).get(MxtPlayerInformation.TECHNIQUES).values().size(), 1);
    }

    private static void progressTracks(ProbeContext context) {
        context.expect("empty track has no fill", AuiStyles.progressWidth(105, 0), 0);
        context.expect("one percent is visible inside the frame", AuiStyles.progressWidth(105, 0.01), 1);
        context.expect("half progress uses the inner width", AuiStyles.progressWidth(104, 0.5), 50);
        context.expect("full progress leaves both end caps intact", AuiStyles.progressWidth(105, 1), 101);
        context.expect("overfill is clamped", AuiStyles.progressWidth(105, 2), 101);
        context.expect("negative progress is clamped", AuiStyles.progressWidth(105, -1), 0);
        context.expect("narrow track cannot overflow", AuiStyles.progressWidth(3, 1), 0);
        context.expect("negative track cannot overflow", AuiStyles.progressWidth(-1, 1), 0);
        context.expect("NaN is empty", AuiStyles.progressWidth(105, Double.NaN), 0);
        context.expect("infinity is empty", AuiStyles.progressWidth(105, Double.POSITIVE_INFINITY), 0);
    }

    private static void paperColors(ProbeContext context) {
        context.expect("dark data colour is unchanged", AuiStyles.readableOnPaper(0x123456), 0x123456);
        context.expect("white data colour is legible", AuiStyles.readableOnPaper(0xFFFFFF), 0x5C5C5C);
        context.expect("green hue is preserved", AuiStyles.readableOnPaper(0x55FF55), 0x1F5C1F);
        context.expect("black avoids division by zero", AuiStyles.readableOnPaper(0), 0);
        context.expect("alpha is excluded from CSS text", AuiStyles.hex(AuiStyles.readableOnPaper(0x80123456)), "#123456");
        for (int red = 0; red <= 255; red += 17)
            for (int green = 0; green <= 255; green += 17)
                for (int blue = 0; blue <= 255; blue += 17) {
                    int rgb = (red << 16) | (green << 8) | blue;
                    int readable = AuiStyles.readableOnPaper(rgb);
                    int brightest = Math.max(readable >> 16, Math.max((readable >> 8) & 255, readable & 255));
                    if (brightest > 92 || AuiStyles.readableOnPaper(readable) != readable
                            || (luminance(0xE2D6BD) + 0.05) / (luminance(readable) + 0.05) < 4.5) {
                        context.check("all sampled colours are readable and stable", false);
                        return;
                    }
                }
        context.check("4096 sampled colours have 4.5:1 contrast on the darkest paper and are stable", true);
    }

    private static double luminance(int rgb) {
        double[] channels = {(rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255};
        for (int index = 0; index < channels.length; index++) {
            double channel = channels[index] / 255.0;
            channels[index] = channel <= 0.04045 ? channel / 12.92 : Math.pow((channel + 0.055) / 1.055, 2.4);
        }
        return channels[0] * 0.2126 + channels[1] * 0.7152 + channels[2] * 0.0722;
    }

    private static void styledText(ProbeContext context) {
        TestDocument document = new TestDocument();
        Element value = new Element(document, "div");
        AuiElements.style(value, "--surface-deep", "#E2D6BD");
        AuiElements.style(value, "color", "#E0E4EC");
        AuiElements.setText(value, "previous row");
        Component text = Component.literal("root ").withColor(0x123456)
                .append(Component.literal("fire").withStyle(Style.EMPTY.withColor(0xFF5500).withBold(true)))
                .append(" / ")
                .append(Component.literal("water").withStyle(Style.EMPTY.withColor(0x3366FF).withItalic(true)))
                .append(Component.literal(" <&>"));
        AuiElements.setText(value, text);
        context.expect("styled text keeps spaces and literal markup", value.getTextContent(), "root fire / water <&>");
        context.expect("each component segment has its own run", value.getChildren().size(), 5);
        List<Element> runs = List.copyOf(value.getChildren());
        context.expect("root RGB color reaches the rendered run", Text.getFontColor(runs.get(0)), 0xFF123456);
        context.expect("nested fire color overrides the root", Text.getFontColor(runs.get(1)), 0xFF5C1F00);
        context.expect("uncolored separator inherits the component root", Text.getFontColor(runs.get(2)), 0xFF123456);
        context.expect("nested water color survives", Text.getFontColor(runs.get(3)), 0xFF12255C);
        context.check("bold is rendered", Text.of(runs.get(1)).isBold());
        context.check("italic is rendered", Text.of(runs.get(3)).isOblique());
        AuiElements.setText(value, text.copy());
        context.check("unchanged layout reuses every run", runs.equals(value.getChildren()));

        AuiElements.setText(value, Component.literal(text.getString()).withColor(0x55FF55));
        context.expect("same text can change color without retaining old segments", value.getChildren().size(), 1);
        context.expect("refreshed ready color reaches the renderer", Text.getFontColor(value.getFirstElementChild()), 0xFF1F5C1F);
        AuiElements.setText(value, Component.literal("ordinary row"));
        context.expect("reused row falls back to its own default color", Text.getFontColor(value.getFirstElementChild()), 0xFFE0E4EC);
        context.check("reused row does not keep bold or italic", !Text.of(value.getFirstElementChild()).isBold()
                && !Text.of(value.getFirstElementChild()).isOblique());
        AuiElements.setText(value, "ordinary row");
        context.check("plain text clears styled children even when the text matches", value.getChildren().isEmpty());
        context.expect("plain text remains intact", value.getTextContent(), "ordinary row");
        AuiElements.setText(value, Component.empty());
        context.check("empty text clears the cell", value.getTextContent().isEmpty() && value.getChildren().isEmpty());
    }

    private static void bindings(ProbeContext context) {
        Host host = new Host();
        host.auiPageOpened();
        context.expect("initial page binds once", host.binds, 1);
        for (int frame = 0; frame < 600; frame++) {
            context.check("bound frame remains writable", host.auiPageWritable());
            host.auiRebind();
        }
        context.expect("frames do not append more listeners", host.binds, 1);
        host.clickButton();
        context.expect("one click invokes one action", host.clicks, 1);

        host.document.reload();
        context.check("reload defers frame writes", !host.auiPageWritable());
        context.check("reloaded page becomes writable", host.auiPageWritable());
        context.expect("reload binds once", host.binds, 2);
        host.clickButton();
        context.expect("reload retains one action per click", host.clicks, 2);

        host.document = new TestDocument();
        host.document.reload();
        context.check("replacement document with equal generation rebinds", !host.auiPageWritable());
        context.expect("replacement binds once", host.binds, 3);
        host.clickButton();
        context.expect("replacement has one listener", host.clicks, 3);
        host.auiClose();
        context.check("close clears bound state", !host.auiBound());
        context.expect("close drops cached button", host.button, null);

        Host reopened = new Host();
        reopened.auiPageOpened();
        reopened.clickButton();
        context.expect("new screen has no previous actions", reopened.clicks, 1);

        reopened.missing = true;
        reopened.document.reload();
        context.check("invalid page is not writable", !reopened.auiPageWritable());
        int attempts = reopened.binds;
        for (int frame = 0; frame < 10; frame++) {
            reopened.auiRebind();
            context.check("invalid page stays unwritable", !reopened.auiPageWritable());
        }
        context.expect("invalid contract is not retried every frame", reopened.binds, attempts);
        reopened.missing = false;
        reopened.document.reload();
        reopened.auiPageWritable();
        context.check("fresh valid DOM restores binding", reopened.auiPageWritable());
        reopened.auiClose();
    }

    private static final class TestDocument extends Document {
        private long generation;
        private Element button = new Element(this, "div");

        TestDocument() {
            super("mxt_test/aui_binding_probe.html", false);
        }

        @Override
        public long getRefreshGeneration() {
            return this.generation;
        }

        void reload() {
            this.generation++;
            this.button = new Element(this, "div");
        }
    }

    private static final class Host implements AuiWrappedScreen {
        private final State state = new State();
        private TestDocument document = new TestDocument();
        private Element button;
        private int binds, clicks;
        private boolean missing;

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
            this.binds++;
            if (this.missing) throw new NoSuchElementException("probe button");
            this.button = this.document.button;
            this.click(this.button, () -> this.clicks++);
        }

        @Override
        public void onBindingsCleared() {
            this.button = null;
        }

        void clickButton() {
            this.button.triggerEvent(listener -> {
                if (listener.type().equals("click") && !listener.internal())
                    listener.listener().accept(new Event(this.button, "click", false));
            });
        }
    }
}
