package com.iafenvoy.mxt.screen.profiling;

import com.iafenvoy.mxt.network.payload.ProfilerGuiS2CPayload;
import com.iafenvoy.mxt.network.payload.ProfilerSnapshotS2CPayload;
import com.iafenvoy.mxt.network.payload.ProfilerSubscriptionC2SPayload;
import com.iafenvoy.mxt.screen.aui.*;
import com.iafenvoy.mxt.util.profiling.*;
import com.sighs.apricityui.element.Canvas;
import com.sighs.apricityui.init.Element;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.jspecify.annotations.NonNull;

import java.util.*;

public final class ProfilerScreen extends AuiScreen {
    private static final int ROW_WIDTH = 472;
    private static final int COMPACT_ROW_WIDTH = 288;
    private static final int ROW_HEIGHT = 38;
    private static final int COMPACT_ROW_HEIGHT = 64;
    private static final int ROWS = 24;
    private final UUID token;
    private final boolean local;
    private final ClientPacketListener connection;
    private final MinecraftServer server;
    private final UUID player;
    private final List<Element> sortButtons = new ArrayList<>();
    private ProfilerFrame frame = ProfilerFrame.EMPTY;
    private List<Row> rows = List.of();
    private AuiScrollList<Row> list;
    private Element panel, status, direction, legend;
    private Sort sort = Sort.AVERAGE;
    private boolean descending = true;
    private boolean subscribed;
    private boolean closed;
    private int ticks;

    private ProfilerScreen(UUID token, boolean local) {
        super(AuiPages.profilerPage());
        Minecraft minecraft = Minecraft.getInstance();
        this.token = token;
        this.local = local;
        this.connection = minecraft.getConnection();
        this.server = local ? minecraft.getSingleplayerServer() : null;
        this.player = minecraft.player.getUUID();
    }

    public static void control(ProfilerGuiS2CPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!payload.open()) {
            if (minecraft.screen instanceof ProfilerScreen screen && screen.token.equals(payload.token()))
                screen.onClose();
            return;
        }
        if (minecraft.player == null || !ProfilerSubscriptions.mayView(minecraft.player.permissions())) return;
        if (payload.local() && minecraft.getSingleplayerServer() == null) return;
        minecraft.setScreen(new ProfilerScreen(payload.token(), payload.local()));
    }

    public static void receive(ProfilerSnapshotS2CPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof ProfilerScreen screen && !screen.local && screen.authorized()
                && screen.token.equals(payload.token())) screen.accept(payload.frame());
    }

    private boolean authorized() {
        return !this.closed && this.minecraft.getConnection() == this.connection && this.minecraft.player != null
                && this.player.equals(this.minecraft.player.getUUID())
                && ProfilerSubscriptions.mayView(this.minecraft.player.permissions());
    }

    @Override
    protected void init() {
        super.init();
        if (!this.subscribed) {
            this.subscribed = true;
            this.subscription(true);
        }
    }

    private void subscription(boolean open) {
        if (this.local && this.server != null) {
            this.server.execute(() -> {
                ServerPlayer player = this.server.getPlayerList().getPlayer(this.player);
                if (player != null) ProfilerSubscriptions.update(player, this.token, open);
            });
        } else if (this.minecraft.getConnection() == this.connection)
            ClientPacketDistributor.sendToServer(new ProfilerSubscriptionC2SPayload(this.token, open));
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.authorized()) {
            this.onClose();
            return;
        }
        if (++this.ticks % ProfilerSubscriptions.HEARTBEAT_TICKS == 0) this.subscription(true);
    }

    private void accept(ProfilerFrame frame) {
        if (frame.sequence() <= this.frame.sequence()) return;
        this.frame = frame;
        List<Row> rows = new ArrayList<>();
        for (ProfilerWindowSnapshot.Producer producer : frame.window().producers())
            for (ProfilerSource source : frame.window().sources()) {
                ProfilerWindowSnapshot.Summary summary = producer.summary(source);
                double ceiling = Math.max(1D, Math.ceil(summary.maxNanos() / 1_000_000D));
                rows.add(new Row(source, producer, summary,
                        sourceName(source),
                        millis(summary.minNanos()) + " / " + millis(summary.averageNanos()) + " / " + millis(summary.maxNanos()),
                        String.format(Locale.ROOT, "%.0f", ceiling) + " ms", ceiling));
            }
        this.rows = rows;
        this.reorder();
        this.showStatus();
    }

    private static String sourceName(ProfilerSource source) {
        for (ServerProfiling.Source registered : ServerProfiling.Source.values())
            if (registered.id().equals(source.id()))
                return Component.translatableWithFallback(registered.translationKey(), source.id()).getString();
        return source.id();
    }

    @Override
    public void bindPage() {
        this.panel = this.getOrThrow("panel");
        this.status = this.getOrThrow("status");
        this.direction = this.getOrThrow("direction");
        this.text(this.getOrThrow("title"), Component.translatable("screen.mxt.profiler.title"));
        this.text(this.getOrThrow("columns"), Component.translatable("screen.mxt.profiler.columns"));
        this.legend = this.getOrThrow("legend");
        Element close = this.getOrThrow("close");
        this.text(close, Component.translatable("gui.done"));
        this.click(close, this::onClose);
        for (Sort sort : Sort.values()) {
            Element button = this.getOrThrow("sort_" + sort.name().toLowerCase(Locale.ROOT));
            this.sortButtons.add(button);
            this.text(button, Component.translatable(sort.key));
            this.click(button, () -> {
                this.sort = sort;
                this.reorder();
            });
        }
        this.click(this.direction, () -> {
            this.descending = !this.descending;
            this.reorder();
        });
        this.list = this.scrollList("source", "sources", ROWS, new RowBinding());
        this.list.attachScrollbar(this.getOrThrow("scroll_track"), this.getOrThrow("scroll_thumb"));
    }

    @Override
    public void onPageBound() {
        this.reorder();
        this.showStatus();
        this.layout();
    }

    @Override
    public void onBindingsCleared() {
        this.panel = this.status = this.direction = this.legend = null;
        this.list = null;
        this.sortButtons.clear();
    }

    private void reorder() {
        Comparator<Row> comparator = switch (this.sort) {
            case SOURCE -> Comparator.comparing(row -> row.name);
            case MIN -> Comparator.comparingLong(row -> row.summary.minNanos());
            case AVERAGE -> Comparator.comparingDouble(row -> row.summary.averageNanos());
            case MAX -> Comparator.comparingLong(row -> row.summary.maxNanos());
        };
        if (this.descending) comparator = comparator.reversed();
        this.rows = this.rows.stream().sorted(comparator.thenComparing(row -> row.source.id())
                .thenComparingLong(row -> row.producer.id())).toList();
        if (this.list != null) this.list.rebuild(this.font, this.rows);
        this.text(this.direction, Component.translatable(this.descending ? "screen.mxt.profiler.descending" : "screen.mxt.profiler.ascending"));
        for (int index = 0; index < this.sortButtons.size(); index++)
            AuiElements.setClass(this.sortButtons.get(index), "selected", Sort.values()[index] == this.sort);
    }

    private void showStatus() {
        String key = this.frame.sequence() == 0L ? "screen.mxt.profiler.waiting"
                : !this.frame.enabled() ? "screen.mxt.profiler.disabled"
                : this.rows.isEmpty() ? "command.mxt.debug.no_data" : "screen.mxt.profiler.status";
        this.text(this.status, Component.translatable(key, Profiler.WINDOW_TICKS, this.frame.window().droppedBatches()));
    }

    private void layout() {
        int width = (this.width >= ROW_WIDTH + 48 ? ROW_WIDTH : COMPACT_ROW_WIDTH) + 32;
        int height = Math.max(180, Math.min(680, this.height - 16));
        AuiElements.style(this.panel, "width", width + "px");
        AuiElements.style(this.panel, "height", height + "px");
        AuiElements.setText(this.legend, this.font.plainSubstrByWidth(
                Component.translatable("screen.mxt.profiler.legend").getString(), width - 80));
        if (this.list != null) {
            AuiElements.style(this.list.root, "width", (width - 32) + "px");
            AuiElements.style(this.list.root, "height", (height - 116) + "px");
            this.list.layout(this.font, width - 32, height - 116);
        }
    }

    @Override
    public void extractRenderState(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        boolean bound = this.auiPageWritable();
        if (bound) {
            if (this.local && this.authorized()) {
                ProfilerFrame frame = ProfilerSubscriptions.readLocal(this.server, this.player, this.token);
                if (frame != null) this.accept(frame);
            }
            this.layout();
        }
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(@NonNull MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0 && this.auiBound() && this.list != null
                && this.list.scrollbarClicked(event.x(), event.y(), this.font)) return true;
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(@NonNull MouseButtonEvent event, double deltaX, double deltaY) {
        if (event.button() == 0 && this.auiBound() && this.list != null
                && this.list.scrollbarDragged(event.x(), event.y(), this.font)) return true;
        return super.mouseDragged(event, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(@NonNull MouseButtonEvent event) {
        if (this.list != null) this.list.scrollbarReleased();
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (this.auiBound() && this.list != null
                && (this.list.contains(mouseX, mouseY) || this.list.scrollbarContains(mouseX, mouseY))) {
            this.list.scrollBy(-scrollY * this.list.rowHeight(), this.font);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void removed() {
        if (!this.closed) {
            this.closed = true;
            this.subscription(false);
        }
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static String millis(double nanos) {
        return String.format(Locale.ROOT, "%.3f", nanos / 1_000_000D);
    }

    private enum Sort {
        SOURCE("screen.mxt.profiler.sort.source"), MIN("screen.mxt.profiler.sort.min"),
        AVERAGE("screen.mxt.profiler.sort.avg"), MAX("screen.mxt.profiler.sort.max");

        private final String key;

        Sort(String key) {
            this.key = key;
        }
    }

    private record Row(ProfilerSource source, ProfilerWindowSnapshot.Producer producer,
                       ProfilerWindowSnapshot.Summary summary,
                       String name, String values, String axis, double ceiling) {
    }

    private static final class RowBinding implements AuiScrollList.Binding<Row> {
        private boolean compact;

        @Override
        public List<AuiScrollList.Cell> bind(AuiWrappedScreen screen, String prefix, int count) {
            List<AuiScrollList.Cell> cells = new ArrayList<>();
            for (int index = 0; index < count; index++)
                cells.add(new AuiScrollList.Cell(screen.getOrThrow(prefix + "_row-" + index),
                        new Element[]{screen.getOrThrow(prefix + "_name-" + index), screen.getOrThrow(prefix + "_values-" + index),
                                screen.getOrThrow(prefix + "_axis-" + index), screen.getOrThrow(prefix + "_chart-" + index, Canvas.class)}));
            return cells;
        }

        @Override
        public void prepare(Font font, List<Row> entries, int width) {
            this.compact = width < ROW_WIDTH;
        }

        @Override
        public int rowHeight() {
            return this.compact ? COMPACT_ROW_HEIGHT : ROW_HEIGHT;
        }

        @Override
        public void clear(AuiScrollList.Cell cell) {
            cell.hide();
        }

        @Override
        public void show(Font font, AuiScrollList.Cell cell, Row row, int index, int left, int top, int width) {
            boolean changed = cell.entry() != row;
            cell.show(row.source.id() + "/" + row.producer.id(), row, left, top, width);
            cell.setClass("compact", this.compact);
            int textWidth = this.compact ? COMPACT_ROW_WIDTH : 180;
            AuiElements.setText(cell.part(0), font.plainSubstrByWidth(row.name, textWidth - 4));
            AuiElements.setText(cell.part(1), font.plainSubstrByWidth(row.values, textWidth - 4));
            AuiElements.setText(cell.part(2), row.axis);
            if (!changed) return;
            Canvas chart = (Canvas) cell.part(3);
            chart.renderOperation(graphics -> ProfilerChart.draw(graphics, row.producer, row.source, row.ceiling));
        }
    }
}
