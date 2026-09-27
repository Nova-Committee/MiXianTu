package com.iafenvoy.mxt.screen.gui;

import com.iafenvoy.mxt.network.payload.AlchemyActionC2SPayload;
import com.iafenvoy.mxt.network.payload.AlchemyActionC2SPayload.Action;
import com.iafenvoy.mxt.screen.AlchemyUiTemplates;
import com.iafenvoy.mxt.screen.menu.AlchemyFurnaceMenu;
import com.iafenvoy.mxt.screen.menu.AlchemyFurnaceMenu.TemperatureAck;
import com.iafenvoy.mxt.screen.menu.AlchemyFurnaceView;
import com.lowdragmc.lowdraglib2.gui.holder.IModularUIHolderMenu;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.UITemplate;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ItemSlot;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ProgressBar;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.Optional;

/**
 * Binds a freshly loaded native template. A missing required node binds no slots.
 */
public final class AlchemyFurnaceScreen extends AbstractContainerScreen<AlchemyFurnaceMenu> {
    private static final String[] PLAYER_SLOTS = playerSlots();

    @Nullable
    private UI ui;
    @Nullable
    private Component templateError;
    private List<FormattedCharSequence> errorLines = List.of();
    private int errorWidth = -1;

    @Nullable
    private Label title;
    @Nullable
    private Label temperature;
    @Nullable
    private Label limit;
    @Nullable
    private Label status;
    @Nullable
    private ProgressBar progress;
    @Nullable
    private TextField target;
    @Nullable
    private Button start;
    @Nullable
    private Button abort;
    @Nullable
    private Button apply;

    private boolean draftDirty;
    private boolean writingField;
    private int observedEpoch;
    private int inflight;
    private double submitted = Double.NaN;
    @Nullable
    private Component rejection;
    private long tooltipTargetBits = Long.MIN_VALUE;
    private long tooltipMaximumBits = Long.MIN_VALUE;
    private int tooltipInflight = -1;
    @Nullable
    private Component tooltipRejection;

    private boolean startActive;
    private boolean abortActive;
    private float shownProgress = Float.NaN;
    private long shownTemperatureBits = Long.MIN_VALUE;
    private long shownHeatTooltip = Long.MIN_VALUE;
    private long shownLimitBits = Long.MIN_VALUE;
    private long shownLimitTooltip = Long.MIN_VALUE;
    @Nullable
    private Component shownTitle;
    @Nullable
    private Component shownQuality;
    @Nullable
    private Component shownStatus;

    public AlchemyFurnaceScreen(AlchemyFurnaceMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.bind(inventory);
    }

    private void bind(Inventory inventory) {
        UITemplate template = AlchemyUiTemplates.load(this.menu.view());
        if (template == null) {
            this.templateError = Component.translatable("screen.mxt.alchemy.template_missing", AlchemyUiTemplates.name(this.menu.view()));
            return;
        }
        UI loaded = template.createUI();
        String missing = this.missing(loaded);
        if (missing != null) {
            this.templateError = Component.translatable("screen.mxt.alchemy.template_invalid", AlchemyUiTemplates.name(this.menu.view()), missing);
            return;
        }
        IModularUIHolderMenu holder = (IModularUIHolderMenu) (Object) this.menu;
        this.bindSlots(loaded, holder);
        holder.setModularUI(new ModularUI(loaded, inventory.player));
        this.ui = loaded;
        this.capture(loaded);
        this.bindControls(loaded);
    }

    private void bindSlots(UI loaded, IModularUIHolderMenu holder) {
        for (String id : this.machineIds()) this.bindSlot(loaded, holder, id);
        for (String id : PLAYER_SLOTS) this.bindSlot(loaded, holder, id);
    }

    private void bindSlot(UI loaded, IModularUIHolderMenu holder, String id) {
        int index = this.menu.menuIndex(id);
        ItemSlot slot = loaded.selectId(id, ItemSlot.class).findFirst().orElseThrow();
        slot.bind(this.menu.getSlot(index));
        if (id.startsWith("inventory_")) slot.slotStyle(style -> style.isPlayerSlot(true));
        holder.ldlib2$addSlot(slot);
    }

    private void capture(UI loaded) {
        this.title = this.label(loaded, "title");
        if (this.menu.view() != AlchemyFurnaceMenu.View.MONITOR) {
            this.text(this.title, this.getTitle());
            return;
        }
        this.temperature = this.label(loaded, "temperature");
        this.limit = this.label(loaded, "limit");
        this.status = this.label(loaded, "status");
        this.progress = loaded.selectId("progress", ProgressBar.class).findFirst().orElseThrow();
        this.target = loaded.selectId("target", TextField.class).findFirst().orElseThrow();
        this.start = loaded.selectId("start", Button.class).findFirst().orElseThrow();
        this.abort = loaded.selectId("abort", Button.class).findFirst().orElseThrow();
        this.apply = loaded.selectId("apply", Button.class).findFirst().orElseThrow();
    }

    private void bindControls(UI loaded) {
        this.click(loaded, "start", () -> this.send(Action.START, 0));
        this.click(loaded, "abort", () -> this.send(Action.ABORT, 0));
        this.click(loaded, "apply", this::applyTemperature);
        if (this.target != null) {
            TextField field = this.target;
            // Template number mode installs a 0.1 quantizer; string mode keeps the typed characters.
            field.setAnyString();
            field.setTextResponder(value -> {
                if (this.writingField) return;
                this.draftDirty = true;
                this.rejection = null;
            });
        }
        if (this.start != null) this.start.setActive(false);
        if (this.abort != null) this.abort.setActive(false);
    }

    @Nullable
    private String missing(UI loaded) {
        String missing = this.missingLabel(loaded, "title");
        if (missing != null) return missing;
        missing = this.missingElement(loaded, "player_inventory");
        if (missing != null) return missing;
        for (String id : PLAYER_SLOTS) {
            missing = this.missingSlot(loaded, id);
            if (missing != null) return missing;
        }
        for (String id : this.machineIds()) {
            missing = this.missingSlot(loaded, id);
            if (missing != null) return missing;
        }
        if (this.menu.view() != AlchemyFurnaceMenu.View.MONITOR) return null;
        for (String id : new String[]{"temperature", "limit", "status"}) {
            missing = this.missingLabel(loaded, id);
            if (missing != null) return missing;
        }
        missing = this.missingElement(loaded, "monitor");
        if (missing != null) return missing;
        missing = this.missingType(loaded, "progress", ProgressBar.class, "progress-bar");
        if (missing != null) return missing;
        missing = this.missingType(loaded, "target", TextField.class, "text-field");
        if (missing != null) return missing;
        for (String id : new String[]{"apply", "start", "abort"}) {
            missing = this.missingType(loaded, id, Button.class, "button");
            if (missing != null) return missing;
        }
        return null;
    }

    @Nullable
    private String missingSlot(UI loaded, String id) {
        return this.missingType(loaded, id, ItemSlot.class, "item-slot");
    }

    @Nullable
    private String missingLabel(UI loaded, String id) {
        return this.missingType(loaded, id, Label.class, "label");
    }

    @Nullable
    private String missingElement(UI loaded, String id) {
        return this.missingType(loaded, id, UIElement.class, "element");
    }

    @Nullable
    private String missingType(UI loaded, String id, Class<? extends UIElement> type, String typeName) {
        return loaded.selectId(id, type).findFirst().isEmpty() ? id + " (" + typeName + ")" : null;
    }

    private String[] machineIds() {
        return switch (this.menu.view()) {
            case MONITOR -> new String[]{AlchemyFurnaceMenu.FIRE};
            case MAIN -> AlchemyFurnaceMenu.MAIN_SLOTS;
            case AUXILIARY -> AlchemyFurnaceMenu.AUXILIARY_SLOTS;
            case OUTPUT -> AlchemyFurnaceMenu.OUTPUT_SLOTS;
        };
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (this.ui == null) {
            this.hoveredSlot = null;
            return;
        }
        this.refresh();
    }

    private void refresh() {
        if (this.menu.view() != AlchemyFurnaceMenu.View.MONITOR) return;
        this.pollTemperature();
        AlchemyFurnaceView view = this.menu.viewSnapshot();
        AlchemyFurnaceView.Numbers numbers = view.numbers();
        this.showTitle(view.status().furnace(), view.status().quality());
        this.showExact(this.temperature, "screen.mxt.alchemy.temperature", numbers.temperature(), true, numbers);
        this.showExact(this.limit, "screen.mxt.alchemy.maximum", numbers.maximum(), false, numbers);
        this.showStatus(view.status().message());
        long total = numbers.total();
        float progress = total > 0 && view.status().locked()
                ? (float) (total - numbers.remaining()) / total : 0;
        if (this.progress != null && Float.floatToIntBits(progress) != Float.floatToIntBits(this.shownProgress)) {
            this.progress.setProgress(progress);
            this.shownProgress = progress;
        }
        boolean canStart = view.status().canStart();
        if (this.start != null && canStart != this.startActive) {
            this.start.setActive(canStart);
            this.startActive = canStart;
        }
        boolean canAbort = "WARMING".equals(view.status().phase()) || "RUNNING".equals(view.status().phase());
        if (this.abort != null && canAbort != this.abortActive) {
            this.abort.setActive(canAbort);
            this.abortActive = canAbort;
        }
        this.syncDraft(numbers.target());
        this.showFieldTooltip(numbers);
    }

    private void showTitle(Component furnace, Component quality) {
        if (this.title == null || furnace.equals(this.shownTitle) && quality.equals(this.shownQuality)) return;
        this.text(this.title, furnace);
        this.tooltip(this.title, furnace, quality);
        this.shownTitle = furnace;
        this.shownQuality = quality;
    }

    private void showStatus(Component message) {
        if (this.status == null || message.equals(this.shownStatus)) return;
        this.text(this.status, message);
        this.tooltip(this.status, message);
        this.shownStatus = message;
    }


    private void showExact(@Nullable Label label, String key, double value, boolean heat, AlchemyFurnaceView.Numbers numbers) {
        if (label == null) return;
        long bits = Double.doubleToLongBits(value);
        long tooltipBits = heat
                ? mix(numbers.temperature(), numbers.target(), numbers.maximum())
                : mix(numbers.maximum(), numbers.recipeTarget(), numbers.tolerance());
        long shown = heat ? this.shownTemperatureBits : this.shownLimitBits;
        long shownTooltip = heat ? this.shownHeatTooltip : this.shownLimitTooltip;
        if (bits == shown && tooltipBits == shownTooltip) return;
        if (bits != shown) this.text(label, Component.translatable(key, roundTrip(value)));
        if (tooltipBits != shownTooltip) {
            this.tooltip(label, heat
                    ? Component.translatable("screen.mxt.alchemy.temperature_exact", roundTrip(numbers.temperature()), roundTrip(numbers.target()), roundTrip(numbers.maximum()))
                    : Component.translatable("screen.mxt.alchemy.limit_exact", roundTrip(numbers.maximum()), roundTrip(numbers.recipeTarget()), roundTrip(numbers.tolerance())));
        }
        if (heat) {
            this.shownTemperatureBits = bits;
            this.shownHeatTooltip = tooltipBits;
        } else {
            this.shownLimitBits = bits;
            this.shownLimitTooltip = tooltipBits;
        }
    }

    private static long mix(double first, double second, double third) {
        return Double.doubleToLongBits(first) ^ Long.rotateLeft(Double.doubleToLongBits(second), 21) ^ Long.rotateLeft(Double.doubleToLongBits(third), 42);
    }

    private void syncDraft(double authoritative) {
        if (this.target == null || this.draftDirty || this.inflight > 0 || this.target.isFocused()) return;
        String exact = roundTrip(authoritative);
        if (!exact.equals(this.target.getRawText())) this.writeField(exact);
    }

    private void showFieldTooltip(AlchemyFurnaceView.Numbers numbers) {
        if (this.target == null) return;
        long targetBits = Double.doubleToLongBits(numbers.target());
        long maximumBits = Double.doubleToLongBits(numbers.maximum());
        if (this.rejection == this.tooltipRejection && this.inflight == this.tooltipInflight
                && targetBits == this.tooltipTargetBits && maximumBits == this.tooltipMaximumBits) return;
        Component tooltip = this.rejection != null ? this.rejection
                : this.inflight > 0 ? Component.translatable("screen.mxt.alchemy.temperature_pending", roundTrip(this.submitted))
                : Component.translatable("screen.mxt.alchemy.temperature_draft", roundTrip(numbers.target()), roundTrip(numbers.maximum()));
        this.tooltip(this.target, tooltip);
        if (this.apply != null) this.tooltip(this.apply, tooltip);
        this.tooltipRejection = this.rejection;
        this.tooltipInflight = this.inflight;
        this.tooltipTargetBits = targetBits;
        this.tooltipMaximumBits = maximumBits;
    }

    private void pollTemperature() {
        TemperatureAck ack = this.menu.temperatureAck();
        if (ack.epoch() == this.observedEpoch) return;
        int steps = epochSteps(this.observedEpoch, ack.epoch());
        this.observedEpoch = ack.epoch();
        if (this.inflight <= 0) return;
        this.inflight = Math.max(0, this.inflight - steps);
        if (this.inflight > 0) return;
        this.finishTemperature(ack);
    }

    private void finishTemperature(TemperatureAck ack) {
        boolean diverged = this.draftDiverged();
        if (ack.accepted() && !diverged) {
            this.rejection = null;
            this.draftDirty = false;
            this.writeField(roundTrip(ack.target()));
        } else if (!ack.accepted() && !diverged) {
            this.rejection = Component.translatable("screen.mxt.alchemy.temperature_rejected", roundTrip(this.submitted), roundTrip(ack.target()));
        }
        this.submitted = Double.NaN;
    }

    private boolean draftDiverged() {
        if (this.target == null || Double.isNaN(this.submitted)) return true;
        Optional<Double> parsed = parseFinite(this.target.getRawText());
        return parsed.isEmpty() || Double.doubleToLongBits(parsed.get()) != Double.doubleToLongBits(this.submitted);
    }

    private void writeField(String text) {
        if (this.target == null) return;
        if (text.equals(this.target.getRawText())) return;
        this.writingField = true;
        this.target.setText(text, false);
        this.writingField = false;
    }

    private void applyTemperature() {
        if (this.target == null) return;
        String draft = this.target.getRawText();
        Optional<Double> parsed = parseFinite(draft);
        if (parsed.isEmpty()) {
            this.rejection = Component.translatable("screen.mxt.alchemy.temperature_invalid", draft);
            this.tooltipRejection = null;
            return;
        }
        this.submitted = parsed.get();
        this.inflight++;
        this.draftDirty = true;
        this.rejection = null;
        this.tooltipRejection = null;
        this.send(Action.TEMPERATURE, this.submitted);
    }

    private static Optional<Double> parseFinite(String raw) {
        if (raw == null || raw.isBlank()) return Optional.empty();
        try {
            double value = Double.parseDouble(raw.trim());
            return Double.isFinite(value) ? Optional.of(value) : Optional.empty();
        } catch (NumberFormatException exception) {
            return Optional.empty();
        }
    }

    private static String roundTrip(double value) {
        return Double.toString(value);
    }

    private static int epochSteps(int from, int to) {
        return (to - from) & 0xFFFF;
    }

    private void text(@Nullable Label label, Component text) {
        if (label != null && !text.equals(label.getText())) label.setText(text);
    }

    private void tooltip(UIElement element, Component... lines) {
        element.style(style -> style.tooltips(lines));
    }

    private void click(UI loaded, String id, Runnable action) {
        loaded.selectId(id, Button.class).findFirst().ifPresent(button -> button.setOnClick(event -> action.run()));
    }

    @Nullable
    private Label label(UI loaded, String id) {
        return loaded.selectId(id, Label.class).findFirst().orElse(null);
    }

    private void send(Action action, double temperature) {
        ClientPacketDistributor.sendToServer(new AlchemyActionC2SPayload(this.menu.containerId, action, temperature));
    }

    @Override
    public boolean mouseClicked(@NonNull MouseButtonEvent event, boolean doubleClick) {
        if (this.ui == null) return true;
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseReleased(@NonNull MouseButtonEvent event) {
        if (this.ui == null) return true;
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseDragged(@NonNull MouseButtonEvent event, double deltaX, double deltaY) {
        if (this.ui == null) return true;
        return super.mouseDragged(event, deltaX, deltaY);
    }

    @Override
    public boolean keyPressed(@NonNull KeyEvent event) {
        if (this.ui == null) return event.isEscape() && super.keyPressed(event);
        return super.keyPressed(event);
    }

    @Override
    public void extractBackground(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        if (this.ui != null || this.templateError == null) return;
        if (this.errorWidth != this.width) {
            this.errorLines = this.font.split(this.templateError, Math.max(40, this.width - 40));
            this.errorWidth = this.width;
        }
        int y = this.height / 2 - this.errorLines.size() * 5;
        for (FormattedCharSequence line : this.errorLines) {
            graphics.text(this.font, line, (this.width - this.font.width(line)) / 2, y, 0xFFFF5555, false);
            y += 10;
        }
    }

    @Override
    protected void extractSlot(@NonNull GuiGraphicsExtractor graphics, net.minecraft.world.inventory.Slot slot, int mouseX, int mouseY) {
        if (this.ui == null) return;
        super.extractSlot(graphics, slot, mouseX, mouseY);
    }

    @Override
    protected void extractLabels(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
    }

    private static String[] playerSlots() {
        String[] ids = new String[36];
        for (int index = 0; index < ids.length; index++) ids[index] = "inventory_" + index;
        return ids;
    }
}
