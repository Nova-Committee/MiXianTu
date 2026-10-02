package com.iafenvoy.mxt.screen.gui;

import com.iafenvoy.mxt.network.payload.*;
import com.iafenvoy.mxt.recipe.TalismanDrawingRecipe;
import com.iafenvoy.mxt.runtime.talisman.BrushPigmentService;
import com.iafenvoy.mxt.runtime.talisman.TalismanDrawingScorer;
import com.iafenvoy.mxt.runtime.talisman.TalismanDrawingScorer.Point;
import com.iafenvoy.mxt.screen.aui.AuiContainerScreen;
import com.iafenvoy.mxt.screen.aui.AuiElements;
import com.iafenvoy.mxt.screen.aui.AuiPages;
import com.iafenvoy.mxt.screen.aui.AuiStyles;
import com.iafenvoy.mxt.screen.menu.TalismanWorkstationMenu;
import com.sighs.apricityui.element.Canvas;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.layout.Position;
import com.sighs.apricityui.layout.Size;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;

import java.awt.*;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * The drawing screen: the page owns the layout, the two paper canvases and the formula list, while the strokes
 * are collected here and never leave this side until a whole one is sent.
 *
 * <p>The paper is painted by Java (the ground and the ink are the open formula's two colours, the reference layer is
 * a faded shadow of its own) because the two layers must not share one surface: the reference is drawn once per
 * session, the ink once per stroke.
 *
 * <p>Slot clicks still belong to vanilla: a press is only taken as a stroke while a drawing is open, the pointer
 * is over the paper and the cursor holds the brush, and every other press goes to {@code super}.
 */
public final class TalismanWorkstationScreen extends AuiContainerScreen<TalismanWorkstationMenu> {
    private static final int PANEL_WIDTH = 178, PANEL_HEIGHT = 306;
    private static final int ROWS = 8;
    private static final int CANVAS_WIDTH = (int) TalismanDrawingScorer.CANVAS_WIDTH, CANVAS_HEIGHT = (int) TalismanDrawingScorer.CANVAS_HEIGHT;
    /**
     * Faded brown, the reference a player traces over - deliberately neither the ground nor the ink, or it would
     * read as one of them.
     */
    private static final Color GUIDE = new Color(93, 64, 28, 89);
    private static final int GUIDE_ALPHA_IDLE = 89, GUIDE_ALPHA_TRACING = 38;
    private static final float INK_WIDTH = 2.0F;
    private static final long FADE_RESTORE_MS = 2_000L;
    private static final long PREVIEW_INTERVAL_MS = 200L;
    /**
     * A stroke point closer than this to the previous one is dropped, the same first step the scorer takes.
     */
    private static final double MIN_POINT_DISTANCE = 1.0D;

    private final List<Element> rows = new ArrayList<>();
    private final List<Boolean> rowAffordable = new ArrayList<>();
    private final List<TalismanDrawingScorer.Stroke> referenceStrokes = new ArrayList<>();
    /**
     * The drawing as this side has it, in bitmap pixels; the server keeps its own copy of every accepted stroke.
     */
    private final List<List<Point>> strokes = new ArrayList<>();
    private final List<String> rowIds = new ArrayList<>();

    @Nullable
    private Element paperFrame, resultLine, previewLine, submitButton, cancelButton;
    @Nullable
    private Canvas guideCanvas, inkCanvas;
    @Nullable
    private List<Point> currentStroke;
    @Nullable
    private Identifier drawingRecipe;
    private int handledAck = -1;
    private boolean settlementShown;
    private boolean referenceVisible = true;
    private boolean emptyListHintShown;
    private long lastStrokeAt;
    private long lastPreviewAt;
    private String shownPreview = "";
    /**
     * The ground the paper is painted on and the ink the strokes are drawn in: the open formula's two colours, and
     * the recipe's defaults until one is open.
     */
    private int paperColor = TalismanDrawingRecipe.DEFAULT_BACKGROUND_COLOR;
    private int inkColor = TalismanDrawingRecipe.DEFAULT_FOREGROUND_COLOR;

    public TalismanWorkstationScreen(TalismanWorkstationMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, PANEL_WIDTH, PANEL_HEIGHT);
    }

    @Override
    protected String pagePath() {
        return AuiPages.talismanPage();
    }

    @Override
    public void bindPage() {
        this.panel = this.getOrThrow("panel");
        this.paperFrame = this.getOrThrow("paper_frame");
        this.guideCanvas = this.getOrThrow("guide", Canvas.class);
        this.inkCanvas = this.getOrThrow("paper", Canvas.class);
        this.resultLine = this.getOrThrow("result");
        this.previewLine = this.getOrThrow("preview");
        this.submitButton = this.getOrThrow("submit");
        this.cancelButton = this.getOrThrow("cancel");

        this.bindCells("station", TalismanWorkstationMenu.PAPER_SLOT);
        this.bindInventoryCells("inventory");

        this.rows.clear();
        for (int index = 0; index < ROWS; index++) {
            this.rows.add(this.getOrThrow("row-" + index));
        }
        assert this.guideCanvas != null;
        this.guideCanvas.setWidth(CANVAS_WIDTH);
        this.guideCanvas.setHeight(CANVAS_HEIGHT);
        assert this.inkCanvas != null;
        this.inkCanvas.setWidth(CANVAS_WIDTH);
        this.inkCanvas.setHeight(CANVAS_HEIGHT);
        this.text(this.submitButton, Component.translatable("screen.mxt.talisman.submit"));
        this.text(this.cancelButton, Component.translatable("screen.mxt.talisman.cancel"));
        for (int index = 0; index < this.rows.size(); index++) {
            int slot = index;
            this.click(this.rows.get(index), () -> this.select(slot));
        }
        this.click(this.submitButton, this::submit);
        this.click(this.cancelButton, this::cancel);
    }

    // Every element this screen remembered is dead after a rebind; the drawing itself stays, so a hot reload
    // redraws it instead of losing it.
    @Override
    public void onBindingsCleared() {
        this.rows.clear();
        this.paperFrame = null;
        this.guideCanvas = null;
        this.inkCanvas = null;
        this.resultLine = null;
        this.previewLine = null;
        this.submitButton = null;
        this.cancelButton = null;
        this.currentStroke = null;
    }

    @Override
    public void onPageBound() {
        this.handledAck = Math.max(0, this.handledAck);
        this.showPaperColor();
        this.redrawGuide(GUIDE_ALPHA_IDLE);
        this.redrawInk();
        this.shownPreview = "";
        this.applyButtons();
    }

    // ------------------------------------------------------------------ per tick

    @Override
    protected void refresh() {
        this.applyRows();
        this.applyDrawing();
        this.applyAck();
        this.applyResult();
        this.applyPreview();
        this.applyButtons();
    }

    private void applyRows() {
        List<TalismanDrawingListS2CPayload.Row> source = this.menu.rows();
        TalismanDrawingStartS2CPayload open = this.menu.drawing();
        this.rowIds.clear();
        this.rowAffordable.clear();
        // An empty list is indistinguishable from a page that failed to fill its rows, so it says so once.
        if (source.isEmpty()) {
            if (!this.emptyListHintShown) {
                this.emptyListHintShown = true;
                this.hint(Component.translatable("screen.mxt.talisman.hint.no_formula"));
            }
        } else {
            this.emptyListHintShown = false;
        }
        for (int index = 0; index < this.rows.size(); index++) {
            Element row = this.rows.get(index);
            if (index >= source.size()) {
                this.text(row, Component.empty());
                AuiElements.setClass(row, "dim", false);
                AuiElements.setClass(row, "selected", false);
                this.rowIds.add("");
                continue;
            }
            TalismanDrawingListS2CPayload.Row entry = source.get(index);
            this.text(row, entry.name());
            AuiElements.setClass(row, "dim", !entry.affordable());
            // The open formula is marked, because until the first stroke the player may still pick another one.
            AuiElements.setClass(row, "selected", open != null && entry.id().equals(open.recipeId()));
            this.rowIds.add(entry.id().toString());
            this.rowAffordable.add(entry.affordable());
        }
    }

    private void applyDrawing() {
        TalismanDrawingStartS2CPayload drawing = this.menu.drawing();
        if (drawing == null) {
            if (this.drawingRecipe != null) {
                this.drawingRecipe = null;
                this.referenceStrokes.clear();
                this.strokes.clear();
                this.showColors(TalismanDrawingRecipe.DEFAULT_BACKGROUND_COLOR,
                        TalismanDrawingRecipe.DEFAULT_FOREGROUND_COLOR);
                this.redrawGuide(GUIDE_ALPHA_IDLE);
                this.redrawInk();
            }
            return;
        }
        if (drawing.recipeId().equals(this.drawingRecipe)) return;
        this.drawingRecipe = drawing.recipeId();
        this.referenceStrokes.clear();
        this.referenceStrokes.addAll(drawing.strokes());
        this.showColors(drawing.backgroundColor(), drawing.foregroundColor());
        this.strokes.clear();
        this.currentStroke = null;
        this.handledAck = -1;
        this.settlementShown = false;
        this.lastPreviewAt = 0L;
        this.redrawGuide(GUIDE_ALPHA_IDLE);
        this.redrawInk();
        this.text(this.previewLine, Component.empty());
    }

    private void applyAck() {
        TalismanStrokeAckS2CPayload ack = this.menu.lastAck();
        if (ack == null || ack.index() < this.handledAck) return;
        this.handledAck = ack.index() + 1;
        if (ack.accepted()) return;
        // A refused stroke has to go: the settlement reconciles the whole drawing against what arrived, so a
        // stroke the server never took would fail it.
        if (!this.strokes.isEmpty()) this.strokes.removeLast();
        this.redrawInk();
        this.text(this.previewLine, Component.translatable(refusalKey(ack.refusal())));
        this.shownPreview = "";
    }

    private void applyResult() {
        TalismanResultS2CPayload result = this.menu.result();
        if (result == null) {
            this.settlementShown = false;
            return;
        }
        // The same payload stays on the menu until the next drawing, so it is only shown once.
        if (this.settlementShown) return;
        this.settlementShown = true;
        this.strokes.clear();
        this.currentStroke = null;
        this.redrawInk();
        Component text;
        if (result.kind() == TalismanResultS2CPayload.CANCELLED)
            text = Component.translatable("screen.mxt.talisman.result.cancelled");
        else if (result.kind() == TalismanResultS2CPayload.ABANDONED)
            text = Component.translatable("screen.mxt.talisman.result.abandoned");
        else if (result.success())
            text = Component.translatable("screen.mxt.talisman.result.success", percent(result.completion()));
        else if (result.kind() == TalismanResultS2CPayload.FAILED)
            text = Component.translatable("screen.mxt.talisman.result.refused");
        else
            text = Component.translatable("screen.mxt.talisman.result.failure", percent(result.completion()));
        this.text(this.resultLine, text);
    }

    private void applyPreview() {
        TalismanDrawingStartS2CPayload drawing = this.menu.drawing();
        Element line = this.previewLine;
        if (line == null || drawing == null || !drawing.judgement().preview()) return;
        if (this.strokes.isEmpty()) {
            if (!this.referenceVisible) {
                this.referenceVisible = true;
                this.redrawGuide(GUIDE_ALPHA_IDLE);
            }
            return;
        }
        long now = System.currentTimeMillis();
        if (now - this.lastStrokeAt > FADE_RESTORE_MS && !this.referenceVisible) {
            this.referenceVisible = true;
            this.redrawGuide(GUIDE_ALPHA_IDLE);
        }
        if (now - this.lastPreviewAt < PREVIEW_INTERVAL_MS) return;
        this.lastPreviewAt = now;
        List<TalismanDrawingScorer.Stroke> drawn = new ArrayList<>();
        for (List<Point> stroke : this.strokes) drawn.add(new TalismanDrawingScorer.Stroke(stroke));
        TalismanDrawingScorer.Score score = TalismanDrawingScorer.score(drawing.strokes(), List.copyOf(drawn),
                drawing.tolerance(), drawing.judgement());
        String text = percent(score.completion());
        if (text.equals(this.shownPreview)) return;
        this.shownPreview = text;
        this.text(line, Component.translatable("screen.mxt.talisman.preview", text));
    }

    private void applyButtons() {
        // What each button does, not what it was last told: AuiElements.setDisabled compares with the element, and
        // the presses check the same state themselves, so no copy of it is kept here.
        AuiElements.setDisabled(this.submitButton, this.drawingRecipe == null || this.strokes.isEmpty());
        AuiElements.setDisabled(this.cancelButton, this.strokes.isEmpty());
    }

    // ------------------------------------------------------------------ actions

    private void select(int row) {
        if (row >= this.rowIds.size()) return;
        String id = this.rowIds.get(row);
        if (id == null || id.isEmpty()) return;
        // The formula can be changed for free until the first stroke lands; after that it is fixed.
        if (!this.strokes.isEmpty()) {
            this.hint(Component.translatable("screen.mxt.talisman.hint.drawn"));
            return;
        }
        // A dim row says why instead of swallowing the click.
        if (row < this.rowAffordable.size() && !this.rowAffordable.get(row)) {
            this.hint(this.unavailableReason());
            return;
        }
        Identifier recipe = Identifier.tryParse(id);
        if (recipe == null) return;
        ClientPacketDistributor.sendToServer(new TalismanSelectC2SPayload(this.menu.containerId, recipe));
    }

    /**
     * Why a row is dim, out of what this side can see. The brush is not part of it: it is charged per stroke, so
     * it is missing from the paper press instead.
     */
    private Component unavailableReason() {
        if (this.menu.slots.get(TalismanWorkstationMenu.PAPER_SLOT).getItem().isEmpty())
            return Component.translatable("screen.mxt.talisman.hint.need_paper");
        return Component.translatable("screen.mxt.talisman.hint.not_affordable");
    }

    /**
     * Why a stroke cannot start: the brush the ink comes from is missing, or has nothing left in it.
     */
    private Component brushReason() {
        return Component.translatable(BrushPigmentService.isBrush(this.menu.getCarried())
                ? "screen.mxt.talisman.hint.need_pigment" : "screen.mxt.talisman.hint.need_brush");
    }

    /**
     * One line on the preview row; the next preview or settlement overwrites it.
     */
    private void hint(Component text) {
        this.text(this.previewLine, text);
        this.shownPreview = "";
    }

    private void submit() {
        if (this.drawingRecipe == null || this.strokes.isEmpty()) return;
        List<TalismanDrawingScorer.Stroke> sent = new ArrayList<>();
        for (List<Point> stroke : this.strokes) sent.add(new TalismanDrawingScorer.Stroke(stroke));
        // Nothing is cleared here: the result decides, and until it arrives the drawing has to stay on the paper.
        ClientPacketDistributor.sendToServer(
                new TalismanSubmitC2SPayload(this.menu.containerId, this.drawingRecipe, List.copyOf(sent)));
    }

    private void cancel() {
        if (this.strokes.isEmpty()) return;
        ClientPacketDistributor.sendToServer(new TalismanCancelC2SPayload(this.menu.containerId));
    }

    // ------------------------------------------------------------------ strokes

    @Override
    public boolean mouseClicked(@NonNull MouseButtonEvent event, boolean doubleClick) {
        if (this.beginStroke(event)) return true;
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(@NonNull MouseButtonEvent event, double deltaX, double deltaY) {
        if (this.continueStroke(event)) return true;
        return super.mouseDragged(event, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(@NonNull MouseButtonEvent event) {
        if (this.finishStroke()) return true;
        return super.mouseReleased(event);
    }

    private boolean beginStroke(MouseButtonEvent event) {
        if (event.button() != 0) return false;
        Point point = this.paperPoint(event.x(), event.y());
        if (point == null) return false;
        // Without a formula there is no session to draw into, so the press is answered instead of dropped.
        if (this.drawingRecipe == null) {
            this.hint(Component.translatable("screen.mxt.talisman.hint.pick_formula"));
            return false;
        }
        if (!BrushPigmentService.isReady(this.menu.getCarried())) {
            this.hint(this.brushReason());
            return false;
        }
        this.currentStroke = new ArrayList<>(List.of(point));
        return true;
    }

    private boolean continueStroke(MouseButtonEvent event) {
        List<Point> stroke = this.currentStroke;
        if (stroke == null) return false;
        Point point = this.paperPoint(event.x(), event.y());
        if (point == null) return true;
        Point last = stroke.getLast();
        if (Math.hypot(point.x() - last.x(), point.y() - last.y()) < MIN_POINT_DISTANCE) return true;
        stroke.add(point);
        // Incremental: only the new segment is drawn, so a long stroke does not repaint the layer per point.
        this.drawInkSegment(last, point);
        return true;
    }

    private boolean finishStroke() {
        List<Point> stroke = this.currentStroke;
        if (stroke == null) return false;
        this.currentStroke = null;
        if (stroke.size() < 2) {
            this.redrawInk();
            return true;
        }
        this.strokes.add(List.copyOf(stroke));
        this.lastStrokeAt = System.currentTimeMillis();
        if (this.referenceVisible) {
            this.referenceVisible = false;
            this.redrawGuide(GUIDE_ALPHA_TRACING);
        }
        ClientPacketDistributor.sendToServer(new TalismanStrokeC2SPayload(this.menu.containerId, this.drawingRecipe,
                new TalismanDrawingScorer.Stroke(List.copyOf(stroke))));
        return true;
    }

    /**
     * The pointer in bitmap pixels, when it is over the paper at all.
     */
    @Nullable
    private Point paperPoint(double mouseX, double mouseY) {
        Document document = this.getLinkedDocument();
        Element frame = this.paperFrame;
        if (document == null || frame == null) return null;
        Size size = Size.of(frame);
        if (size.width() <= 0 || size.height() <= 0) return null;
        Position origin = Position.of(frame);
        Position pointer = document.screenToDocumentPosition(new Position(mouseX, mouseY));
        if (pointer.x < origin.x || pointer.y < origin.y
                || pointer.x >= origin.x + size.width() || pointer.y >= origin.y + size.height()) return null;
        double x = (pointer.x - origin.x) / size.width() * CANVAS_WIDTH;
        double y = (pointer.y - origin.y) / size.height() * CANVAS_HEIGHT;
        return new Point(Math.clamp(x, 0.0D, CANVAS_WIDTH), Math.clamp(y, 0.0D, CANVAS_HEIGHT));
    }

    // ------------------------------------------------------------------ painting

    private void redrawGuide(int alpha) {
        Canvas canvas = this.guideCanvas;
        if (canvas == null) return;
        List<List<Point>> strokes = new ArrayList<>();
        for (TalismanDrawingScorer.Stroke stroke : this.referenceStrokes) {
            List<Point> points = new ArrayList<>();
            for (Point point : stroke.points())
                points.add(new Point(point.x() * CANVAS_WIDTH, point.y() * CANVAS_HEIGHT));
            strokes.add(points);
        }
        this.repaint(canvas, graphics -> {
            graphics.setColor(new Color(GUIDE.getRed(), GUIDE.getGreen(), GUIDE.getBlue(),
                    Math.clamp(alpha, 0, 255)));
            graphics.setStroke(new BasicStroke(INK_WIDTH, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            for (List<Point> stroke : strokes)
                for (int index = 1; index < stroke.size(); index++)
                    graphics.draw(new Line2D.Double(stroke.get(index - 1).x(), stroke.get(index - 1).y(),
                            stroke.get(index).x(), stroke.get(index).y()));
        });
    }

    private void redrawInk() {
        Canvas canvas = this.inkCanvas;
        if (canvas == null) return;
        List<List<Point>> committed = List.copyOf(this.strokes);
        List<Point> active = this.currentStroke == null ? List.of() : List.copyOf(this.currentStroke);
        Color ink = color(this.inkColor, 255);
        this.repaint(canvas, graphics -> {
            graphics.setColor(ink);
            graphics.setStroke(new BasicStroke(INK_WIDTH, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            for (List<Point> stroke : committed) drawStroke(graphics, stroke);
            drawStroke(graphics, active);
        });
    }

    /**
     * Takes the open formula's two colours. The ground is written here; the layers are repainted by the caller that
     * also changed the strokes.
     */
    private void showColors(int paper, int ink) {
        this.paperColor = paper;
        this.inkColor = ink;
        this.showPaperColor();
    }

    /**
     * The paper's ground as the formula named it. The page's stylesheet deliberately leaves this one colour out, so
     * there is a single source for it.
     */
    private void showPaperColor() {
        AuiElements.style(this.paperFrame, "background-color", AuiStyles.hex(this.paperColor));
    }

    /**
     * Clears one layer and draws all of it again; only a rebind, an erased stroke or a settlement needs that.
     */
    private void repaint(Canvas canvas, Consumer<Graphics2D> operation) {
        canvas.clearSurfaceRect(0, 0, CANVAS_WIDTH, CANVAS_HEIGHT);
        canvas.renderOperation(operation);
    }

    /**
     * Adds one segment of ink to the ink layer and leaves the rest of it alone. Clearing the layer here would erase
     * the very stroke being drawn, and marking the whole surface dirty would re-upload 90x210 pixels per pointer move:
     * the bounds say which rectangle the texture upload actually has to cover.
     */
    private void drawInkSegment(Point from, Point to) {
        Canvas canvas = this.inkCanvas;
        if (canvas == null) return;
        Color ink = color(this.inkColor, 255);
        canvas.renderOperation(graphics -> {
            graphics.setColor(ink);
            graphics.setStroke(new BasicStroke(INK_WIDTH, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            graphics.draw(new Line2D.Double(from.x(), from.y(), to.x(), to.y()));
        }, segmentBounds(from, to, INK_WIDTH));
    }

    /**
     * A formula colour, as the page and the canvas API want it: plain RGB plus the alpha the layer draws at.
     */
    private static Color color(int rgb, int alpha) {
        return new Color((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, Math.clamp(alpha, 0, 255));
    }

    /**
     * What one segment can touch: its box, padded for the stroke width and the antialiased fringe.
     */
    private static Rectangle2D segmentBounds(Point from, Point to, float width) {
        double padding = width + 1.0D;
        return new Rectangle2D.Double(Math.min(from.x(), to.x()) - padding, Math.min(from.y(), to.y()) - padding,
                Math.abs(to.x() - from.x()) + padding * 2.0D, Math.abs(to.y() - from.y()) + padding * 2.0D);
    }

    private static void drawStroke(Graphics2D graphics, List<Point> stroke) {
        for (int index = 1; index < stroke.size(); index++)
            graphics.draw(new Line2D.Double(stroke.get(index - 1).x(), stroke.get(index - 1).y(),
                    stroke.get(index).x(), stroke.get(index).y()));
    }

    private static String percent(double completion) {
        return String.format(Locale.ROOT, "%.0f%%", Math.clamp(completion, 0.0D, 1.0D) * 100.0D);
    }

    private static String refusalKey(int refusal) {
        TalismanWorkstationRefusal kind = TalismanWorkstationRefusal.byOrdinal(refusal);
        return kind == null ? "screen.mxt.talisman.refused.session_over" : kind.key();
    }

    /**
     * The refusal reasons the server reports, in the order of the enum it sends.
     */
    private enum TalismanWorkstationRefusal {
        TOO_FAST("screen.mxt.talisman.refused.too_fast"),
        NO_PIGMENT("screen.mxt.talisman.refused.no_pigment"),
        BRUSH_GONE("screen.mxt.talisman.refused.brush_gone"),
        TOO_MANY_POINTS("screen.mxt.talisman.refused.too_many_points"),
        SESSION_OVER("screen.mxt.talisman.refused.session_over");

        private final String key;

        TalismanWorkstationRefusal(String key) {
            this.key = key;
        }

        String key() {
            return this.key;
        }

        @Nullable
        static TalismanWorkstationRefusal byOrdinal(int ordinal) {
            TalismanWorkstationRefusal[] values = values();
            return ordinal < 0 || ordinal >= values.length ? null : values[ordinal];
        }
    }
}