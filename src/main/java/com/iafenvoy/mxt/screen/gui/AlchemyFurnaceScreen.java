package com.iafenvoy.mxt.screen.gui;

import com.iafenvoy.mxt.network.payload.AlchemyActionC2SPayload;
import com.iafenvoy.mxt.network.payload.AlchemyActionC2SPayload.Action;
import com.iafenvoy.mxt.screen.aui.AuiContainerScreen;
import com.iafenvoy.mxt.screen.aui.AuiElements;
import com.iafenvoy.mxt.screen.menu.AlchemyFurnaceMenu;
import com.iafenvoy.mxt.screen.menu.AlchemyFurnaceMenu.TemperatureAck;
import com.iafenvoy.mxt.screen.menu.AlchemyFurnaceView;
import com.sighs.apricityui.element.Container;
import com.sighs.apricityui.element.Input;
import com.sighs.apricityui.element.Slot;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/**
 * The four furnace views, one page each; see {@link AuiContainerScreen} for the page contract.
 */
public final class AlchemyFurnaceScreen extends AuiContainerScreen<AlchemyFurnaceMenu> {
    private static final int PLAYER_SLOTS = 36;

    @Nullable
    private Element title, temperature, limit, status, progress, apply, start, abort;
    @Nullable
    private Input target;
    private boolean draftDirty, writingField;
    @Nullable
    private String fieldText;
    private int observedEpoch;
    private int inflight;
    private double submitted = Double.NaN;
    @Nullable
    private Component rejection;
    private boolean startDisabled, abortDisabled;
    private float shownProgress = Float.NaN;
    private long shownTemperatureBits = Long.MIN_VALUE;
    private long shownLimitBits = Long.MIN_VALUE;
    @Nullable
    private Component shownTitle, shownQuality, shownStatus;

    public AlchemyFurnaceScreen(AlchemyFurnaceMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    public void bindPage() {
        this.panel = this.getOrThrow("panel");
        this.title = this.getOrThrow("title");

        List<Element> playerCells = this.getOrThrow("player_inventory", Container.class).getChildren();
        // The monitor has no machine cell: its heat source is a block in the world, not a slot.
        if (this.menu.view() == AlchemyFurnaceMenu.View.MONITOR) this.bindMonitor();
        else this.bindMachine(this.getOrThrow("machine", Container.class).getChildren());
        if (playerCells.size() != PLAYER_SLOTS)
            throw this.missing("player_inventory (" + playerCells.size() + " slots)");
        this.text(this.title, this.getTitle());
        this.text(this.getOrThrow("inventory_label"), Component.translatable("container.inventory"));
    }

    private void bindMachine(List<Element> machineCells) {
        int machineSlots = this.menu.view().getMachineSlots();
        if (machineCells.size() != machineSlots) throw this.missing("machine (" + machineCells.size() + " slots)");
        for (Element cell : machineCells) {
            int index = cell instanceof Slot slot ? slot.getSlotIndex() : -1;
            if (index < 0 || index >= machineSlots) {
                throw this.missing("machine (slot-index " + index + ")");
            }
            this.tooltip(cell, () -> this.machineHint(index));
        }
    }

    private void bindMonitor() {
        this.temperature = this.getOrThrow("temperature");
        this.limit = this.getOrThrow("limit");
        this.status = this.getOrThrow("status");
        this.progress = this.getOrThrow("progress_fill");
        this.target = this.getOrThrow("target", Input.class);
        this.apply = this.getOrThrow("apply");
        this.start = this.getOrThrow("start");
        this.abort = this.getOrThrow("abort");
        // The page draws bare boxes; the labels come from Java, like every other button of this screen.
        this.text(this.apply, Component.translatable("screen.mxt.alchemy.apply"));
        this.text(this.start, Component.translatable("screen.mxt.alchemy.start"));
        this.text(this.abort, Component.translatable("screen.mxt.alchemy.abort"));

        this.click(this.apply, this::applyTemperature);
        this.click(this.start, () -> this.send(Action.START, 0));
        this.click(this.abort, () -> this.send(Action.ABORT, 0));
        this.tooltip(this.title, this::titleTooltip);
        this.tooltip(this.temperature, this::heatTooltip);
        this.tooltip(this.limit, this::limitTooltip);
        this.tooltip(this.target, this::fieldTooltip);
        this.tooltip(this.apply, this::fieldTooltip);
    }

    @Override
    public void onBindingsCleared() {
        this.title = null;
        this.temperature = null;
        this.limit = null;
        this.status = null;
        this.progress = null;
        this.target = null;
        this.apply = null;
        this.start = null;
        this.abort = null;
        this.fieldText = null;
        this.draftDirty = false;
        this.writingField = false;
        this.rejection = null;
        this.startDisabled = false;
        this.abortDisabled = false;
        this.shownProgress = Float.NaN;
        this.shownTemperatureBits = Long.MIN_VALUE;
        this.shownLimitBits = Long.MIN_VALUE;
        this.shownTitle = null;
        this.shownQuality = null;
        this.shownStatus = null;
    }

    @Override
    public void onPageBound() {
        this.refresh();
    }

    @Override
    protected void refresh() {
        if (this.menu.view() != AlchemyFurnaceMenu.View.MONITOR) return;
        // A rebuilt DOM is stale until syncPage rebinds it.
        Document current = this.getLinkedDocument();
        if (current != null && current.getRefreshGeneration() != this.boundGeneration()) return;
        this.pollTemperature();
        AlchemyFurnaceView view = this.menu.viewSnapshot();
        AlchemyFurnaceView.Numbers numbers = view.numbers();
        this.showTitle(view.status().furnace(), view.status().quality());
        this.showExact(this.temperature, "screen.mxt.alchemy.temperature", numbers.temperature(), true);
        this.showExact(this.limit, "screen.mxt.alchemy.maximum", numbers.maximum(), false);
        this.showStatus(view.status().message());
        long total = numbers.total();
        float progress = total > 0 && view.status().locked()
                ? (float) (total - numbers.remaining()) / total : 0;
        if (this.progress != null && Float.floatToIntBits(progress) != Float.floatToIntBits(this.shownProgress)) {
            AuiElements.style(this.progress, "width", Math.round(progress * 100) + "%");
            this.shownProgress = progress;
        }
        boolean canStart = view.status().canStart();
        if (this.start != null && canStart == this.startDisabled) {
            this.start.setDisabled(!canStart);
            this.startDisabled = !canStart;
        }
        boolean canAbort = "WARMING".equals(view.status().phase()) || "RUNNING".equals(view.status().phase());
        if (this.abort != null && canAbort == this.abortDisabled) {
            this.abort.setDisabled(!canAbort);
            this.abortDisabled = !canAbort;
        }
        this.syncDraft(numbers.target());
    }

    private void showTitle(Component furnace, Component quality) {
        if (this.title == null || furnace.equals(this.shownTitle) && quality.equals(this.shownQuality)) return;
        this.text(this.title, furnace);
        this.shownTitle = furnace;
        this.shownQuality = quality;
    }

    private void showStatus(Component message) {
        if (this.status == null || message.equals(this.shownStatus)) return;
        this.text(this.status, message);
        this.shownStatus = message;
    }

    private void showExact(@Nullable Element label, String key, double value, boolean heat) {
        if (label == null) return;
        long bits = Double.doubleToLongBits(value);
        if (bits == (heat ? this.shownTemperatureBits : this.shownLimitBits)) return;
        this.text(label, Component.translatable(key, roundTrip(value)));
        if (heat) this.shownTemperatureBits = bits;
        else this.shownLimitBits = bits;
    }

    private List<Component> titleTooltip() {
        AlchemyFurnaceView.Status status = this.menu.viewSnapshot().status();
        return List.of(status.furnace().copy().append(" / ").append(status.quality()));
    }

    private List<Component> heatTooltip() {
        AlchemyFurnaceView.Numbers numbers = this.menu.viewSnapshot().numbers();
        return List.of(Component.translatable("screen.mxt.alchemy.temperature_exact",
                roundTrip(numbers.temperature()), roundTrip(numbers.target()), roundTrip(numbers.maximum())));
    }

    private List<Component> limitTooltip() {
        AlchemyFurnaceView.Numbers numbers = this.menu.viewSnapshot().numbers();
        return List.of(Component.translatable("screen.mxt.alchemy.limit_exact",
                        roundTrip(numbers.maximum()), roundTrip(numbers.recipeTarget()), roundTrip(numbers.tolerance())),
                Component.translatable("screen.mxt.alchemy.heat_cell"));
    }

    private List<Component> fieldTooltip() {
        AlchemyFurnaceView.Numbers numbers = this.menu.viewSnapshot().numbers();
        Component text = this.rejection != null ? this.rejection
                : this.inflight > 0 ? Component.translatable("screen.mxt.alchemy.temperature_pending", roundTrip(this.submitted))
                : Component.translatable("screen.mxt.alchemy.temperature_draft", roundTrip(numbers.target()), roundTrip(numbers.maximum()));
        // The field has no room for a label of its own, so the tooltip's first line names it.
        return List.of(Component.translatable("screen.mxt.alchemy.target"), text);
    }

    private void syncDraft(double authoritative) {
        if (this.target == null) return;
        String raw = this.target.getValue();
        if (!this.writingField && this.fieldText != null && !raw.equals(this.fieldText)) {
            this.draftDirty = true;
            this.rejection = null;
        }
        if (this.draftDirty || this.inflight > 0 || this.fieldFocused()) return;
        String exact = roundTrip(authoritative);
        if (!exact.equals(raw)) this.writeField(exact);
    }

    private boolean fieldFocused() {
        Document current = this.getLinkedDocument();
        return current != null && current.getFocusedElement() == this.target;
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
        Optional<Double> parsed = parseFinite(this.target.getValue());
        return parsed.isEmpty() || Double.doubleToLongBits(parsed.get()) != Double.doubleToLongBits(this.submitted);
    }

    private void writeField(String text) {
        if (this.target == null) return;
        // Remember what Java wrote even when the value already matches, or the next frame reads it as a user edit.
        this.fieldText = text;
        if (text.equals(this.target.getValue())) return;
        this.writingField = true;
        this.target.setValue(text);
        this.writingField = false;
    }

    private void applyTemperature() {
        if (this.target == null) return;
        String draft = this.target.getValue();
        Optional<Double> parsed = parseFinite(draft);
        if (parsed.isEmpty()) {
            this.rejection = Component.translatable("screen.mxt.alchemy.temperature_invalid", draft);
            return;
        }
        this.submitted = parsed.get();
        this.inflight++;
        this.draftDirty = true;
        this.rejection = null;
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

    /**
     * The hint for a machine cell: what goes in it. An occupied cell answers nothing, so the item's own tooltip -
     * which the vanilla pass has already queued for this frame - is the one that shows.
     */
    private List<Component> machineHint(int index) {
        if (this.menu.getSlot(index).hasItem()) return List.of();
        String key = switch (this.menu.view()) {
            case MAIN -> "screen.mxt.alchemy.main";
            case AUXILIARY -> index == 2 ? "screen.mxt.alchemy.catalyst" : "screen.mxt.alchemy.auxiliary";
            default -> "screen.mxt.alchemy.output";
        };
        return List.of(Component.translatable(key));
    }

    private void send(Action action, double temperature) {
        ClientPacketDistributor.sendToServer(new AlchemyActionC2SPayload(this.menu.containerId, action, temperature));
    }
}