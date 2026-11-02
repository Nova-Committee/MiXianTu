package com.iafenvoy.mxt.probe.verify;

import com.iafenvoy.mxt.probe.Probe;
import com.iafenvoy.mxt.probe.ProbeContext;
import com.iafenvoy.mxt.screen.aui.AuiElements;
import com.iafenvoy.mxt.screen.aui.AuiWrappedScreen;
import com.sighs.apricityui.event.Event;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.style.Text;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;

import java.util.List;
import java.util.NoSuchElementException;

public final class AuiBindingVerifyProbes {
    private AuiBindingVerifyProbes() {
    }

    public static List<Probe> probes() {
        return List.of(Probe.of("verify/aui/bindings", AuiBindingVerifyProbes::bindings),
                Probe.of("verify/aui/styled_text", AuiBindingVerifyProbes::styledText));
    }

    private static void styledText(ProbeContext context) {
        TestDocument document = new TestDocument();
        Element value = new Element(document, "div");
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
        context.expect("nested fire color overrides the root", Text.getFontColor(runs.get(1)), 0xFFFF5500);
        context.expect("uncolored separator inherits the component root", Text.getFontColor(runs.get(2)), 0xFF123456);
        context.expect("nested water color survives", Text.getFontColor(runs.get(3)), 0xFF3366FF);
        context.check("bold is rendered", Text.of(runs.get(1)).isBold());
        context.check("italic is rendered", Text.of(runs.get(3)).isOblique());
        AuiElements.setText(value, text.copy());
        context.check("unchanged layout reuses every run", runs.equals(value.getChildren()));

        AuiElements.setText(value, Component.literal(text.getString()).withColor(0x55FF55));
        context.expect("same text can change color without retaining old segments", value.getChildren().size(), 1);
        context.expect("refreshed ready color reaches the renderer", Text.getFontColor(value.getFirstElementChild()), 0xFF55FF55);
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
