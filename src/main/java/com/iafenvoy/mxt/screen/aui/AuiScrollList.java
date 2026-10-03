package com.iafenvoy.mxt.screen.aui;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.layout.Position;
import net.minecraft.client.gui.Font;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.NoSuchElementException;

/**
 * One scrollable list on a page: a fixed set of row elements, the entries behind them and the pixel offset the
 * vanilla lists keep. Rows that would fall outside the list are hidden rather than clipped, because clipping a box
 * in ApricityUI paints a dark artefact.
 * <p>
 * A host resolves one through {@link AuiWrappedScreen#scrollList}, which is also where a page that does not carry
 * the rows fails the bind; the hit test asks that same host for the page instead of caching a document that a hot
 * reload replaces.
 */
public final class AuiScrollList<E> {
    /**
     * The list's own element; the host writes its rectangle into the page.
     */
    public final Element root;
    /**
     * The row elements, in page order.
     */
    public final List<Cell> cells;
    private final AuiWrappedScreen host;
    private final Binding<E> binding;
    private List<E> entries = List.of();
    private double scroll;
    private int width;
    private int height;
    /**
     * The identity the picked row carries; re-applied after every layout, because the entry behind a cell
     * changes when the list scrolls or its contents are rebuilt.
     */
    @Nullable
    private String selectedKey;
    /**
     * The width the binding's columns were last prepared for; only a change re-runs that pass.
     */
    private int preparedWidth;

    AuiScrollList(AuiWrappedScreen host, Element root, List<Cell> cells, Binding<E> binding) {
        this.host = host;
        this.root = root;
        this.cells = cells;
        this.binding = binding;
    }

    public void rebuild(Font font, List<E> entries) {
        this.entries = entries;
        this.prepare(font, this.width);
        this.layout(font, this.width, this.height);
    }

    /**
     * Prepares the binding's columns for one width. The first rebuild runs before the panel has written its
     * geometry, so its width is still zero there and the columns come out degenerate - the rows then draw no
     * text until the next rebuild, which the refresh interval puts a second away. {@link #layout} therefore
     * prepares again as soon as the real width arrives.
     */
    private void prepare(Font font, int width) {
        this.preparedWidth = width;
        this.binding.prepare(font, this.entries, width);
    }

    public int rowHeight() {
        return this.binding.rowHeight();
    }

    private int columns() {
        return Math.max(1, this.binding.columns());
    }

    private double maxScroll() {
        int rows = (this.entries.size() + this.columns() - 1) / this.columns();
        return Math.max(0, rows * this.rowHeight() + 4 - this.height);
    }

    /**
     * Adds to the pixel offset; {@link #layout} clamps it, so this is the only place the window is written.
     */
    public void scrollBy(double delta, Font font) {
        this.scroll += delta;
        this.layout(font, this.width, this.height);
    }

    public void layout(Font font, int width, int height) {
        this.width = width;
        this.height = height;
        if (width != this.preparedWidth) this.prepare(font, width);
        this.scroll = Math.clamp(this.scroll, 0.0D, this.maxScroll());
        int rowHeight = this.rowHeight();
        int columns = this.columns();
        int visibleRows = Math.max(0, height / rowHeight);
        int firstRow = (int) (this.scroll / rowHeight);
        int offset = (int) Math.round(this.scroll) - firstRow * rowHeight;
        int gap = this.binding.columnGap();
        int cellWidth = Math.max(1, (Math.max(1, width) - gap * (columns - 1)) / columns);
        int first = firstRow * columns;
        for (int index = 0; index < this.cells.size(); index++) {
            Cell cell = this.cells.get(index);
            int entryIndex = first + index;
            if (entryIndex >= this.entries.size() || index >= visibleRows * columns) {
                cell.hide();
                continue;
            }
            int left = index % columns * (cellWidth + gap);
            int top = index / columns * rowHeight - offset;
            this.binding.show(font, cell, this.entries.get(entryIndex), entryIndex, left, top, cellWidth);
        }
        // The highlight belongs to the entry, not to the row element: a scrolled or refreshed list moves
        // entries between cells, so which cell carries it is only known after the window was written.
        this.applySelection();
    }

    public void markSelected(@Nullable String key) {
        this.selectedKey = key;
        this.applySelection();
    }

    private void applySelection() {
        for (Cell cell : this.cells) {
            cell.setSelected(this.selectedKey != null && this.selectedKey.equals(cell.key));
        }
    }

    public boolean contains(double mouseX, double mouseY) {
        Position pointer = this.local(mouseX, mouseY);
        return pointer != null && this.inside(pointer);
    }

    /**
     * The row cell under the pointer, or null. Measured against the rectangles the last layout wrote, so only a
     * drawn row can be hit.
     */
    @Nullable
    public Cell hoveredCell(double mouseX, double mouseY) {
        Position pointer = this.local(mouseX, mouseY);
        if (pointer == null || !this.inside(pointer)) return null;
        for (Cell cell : this.cells) {
            if (!cell.visible || cell.entry == null) continue;
            if (pointer.x >= cell.left && pointer.x < cell.left + cell.width
                    && pointer.y >= cell.top && pointer.y < cell.top + this.rowHeight()) {
                return cell;
            }
        }
        return null;
    }

    private boolean inside(Position pointer) {
        return pointer.x >= 0 && pointer.x < this.width && pointer.y >= 0 && pointer.y < this.height;
    }

    /**
     * The pointer in the list's own coordinates, or null while no page is bound to convert it. One place for the
     * conversion: both hit tests are reached every frame and have to agree with each other.
     */
    @Nullable
    private Position local(double mouseX, double mouseY) {
        Document document = this.host.getLinkedDocument();
        if (document == null) return null;
        // The pointer arrives in screen coordinates while element positions are document coordinates.
        Position pointer = document.screenToDocumentPosition(new Position(mouseX, mouseY));
        Position position = Position.of(this.root);
        return new Position(pointer.x - position.x, pointer.y - position.y);
    }

    /**
     * One row of a list: its root element, whatever elements its binding named, the identity that binding derived
     * and the rectangle the last layout wrote into the page. The row writes its own element; the binding only says
     * what it should say.
     */
    public static final class Cell {
        private final Element root;
        private final Element[] parts;
        @Nullable
        private String key;
        @Nullable
        private Object entry;
        @Nullable
        private String iconKey;
        private boolean visible;
        private int left;
        private int top;
        private int width;

        public Cell(Element root, Element[] parts) {
            this.root = root;
            this.parts = parts;
        }

        /**
         * The nth element the binding named, in the order it named them.
         */
        public Element part(int index) {
            return this.parts[index];
        }

        /**
         * The identity the binding derived; null while the row is hidden.
         */
        @Nullable
        public String key() {
            return this.key;
        }

        /**
         * The entry currently shown, for tooltips that have to recompute from live state.
         */
        @Nullable
        public Object entry() {
            return this.entry;
        }

        /**
         * The icon signature the row was last drawn with, so a stack is only pushed into the DOM when it changed.
         */
        @Nullable
        public String iconKey() {
            return this.iconKey;
        }

        public void iconKey(@Nullable String iconKey) {
            this.iconKey = iconKey;
        }

        /**
         * Shows this row at one rectangle. The identity, the entry, the rectangle and the display flag are written
         * here, and only when they changed, because every DOM write re-runs the page's style pass.
         */
        public void show(@Nullable String key, @Nullable Object entry, int left, int top, int width) {
            this.key = key;
            this.entry = entry;
            this.visible = true;
            this.left = left;
            this.top = top;
            this.width = width;
            AuiElements.style(this.root, "left", left + "px");
            AuiElements.style(this.root, "top", top + "px");
            AuiElements.style(this.root, "width", width + "px");
            AuiElements.style(this.root, "display", "block");
        }

        /**
         * Hides this row. Written for a cell that was never shown as well: the row elements are in the page from the
         * start, so anything they paint on their own would otherwise stay in the list as residue.
         */
        public void hide() {
            this.visible = false;
            this.key = null;
            this.entry = null;
            AuiElements.style(this.root, "display", "none");
        }

        /**
         * One class token of this row - the highlight, an icon kind; only written when it changed.
         */
        public void setClass(String token, boolean present) {
            AuiElements.setClass(this.root, token, present);
        }

        /**
         * Runs the action when this row is clicked.
         */
        public void click(Runnable action) {
            this.root.addEventListener("click", _ -> action.run());
        }

        void setSelected(boolean selected) {
            this.setClass("selected", selected);
        }
    }

    /**
     * What one kind of row shows: {@code bind} collects the elements it needs, {@code prepare} runs once per
     * rebuild (column widths, diagnostics) and {@code show} writes the visible window.
     */
    public interface Binding<E> {
        /**
         * Collects the cells of one list. A page that does not carry a row is reported through the screen's own
         * helpers, so the fault names the piece that is missing and the bind fails with it.
         */
        List<Cell> bind(AuiWrappedScreen screen, String prefix, int count) throws NoSuchElementException;

        default void prepare(Font font, List<E> entries, int width) {
        }

        /**
         * One row's height; the page owns its own geometry.
         */
        int rowHeight();

        /**
         * The technique page lays its rows out in two columns; everything else is one.
         */
        default int columns() {
            return 1;
        }

        default int columnGap() {
            return 0;
        }

        void clear(Cell cell);

        void show(Font font, Cell cell, E entry, int index, int left, int top, int width);
    }
}
