package com.iafenvoy.mxt.screen.wheel;

import com.iafenvoy.mxt.api.WheelMenuEntry;
import com.iafenvoy.mxt.data.IconReference;
import com.iafenvoy.mxt.registry.MxtKeyMappings;
import com.iafenvoy.mxt.render.IconRenderer;
import com.iafenvoy.mxt.screen.aui.AuiElements;
import com.iafenvoy.mxt.screen.aui.AuiPages;
import com.iafenvoy.mxt.screen.aui.AuiScreen;
import com.sighs.apricityui.element.Item;
import com.sighs.apricityui.init.Element;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.Nullable;

import java.util.*;

/**
 * The wheel: twelve sectors, the pointer's direction picks one, and the chosen entry's name is written in the
 * middle. The pixels come from an ApricityUI page (page contract: {@code docs/guide/java/wheel.md}): the page owns the ring, the
 * gradients and every animation, while this screen keeps the input, the pages and the entries and pushes state
 * into the DOM. The geometry is constant, so nothing is ever read back from the page - the twelve sector
 * outlines and their content anchors are written once per page load. The screen is created fresh every time the
 * wheel opens, it never closes itself, and it draws no background: the default one would blur the HUD behind it.
 *
 * <p>The page must not set {@code aui-mouse-events=intercept}: that makes ApricityUI cancel the native click,
 * and the left button would stop spending the pointed cell.</p>
 */
public final class WheelMenuScreen extends AuiScreen {
    /**
     * The page's ring box in document pixels; the radii behind it are {@link WheelGeometry}'s.
     */
    private static final int RING_BOX = 208;
    /**
     * Degrees cut from both ends of a sector, so the plate under it shows through as the separator.
     */
    private static final double SECTOR_GAP_DEGREES = 0.6D;
    /**
     * Straight segments per arc: five keeps a sector's outline looking round.
     */
    private static final int ARC_SEGMENTS = 5;
    /**
     * The icon/name box at the middle of a sector, in document pixels.
     */
    private static final int CONTENT_WIDTH = 40, CONTENT_HEIGHT = 20;
    /**
     * The cooldown sheet covers this square over the icon; a sector that draws a name instead gets the whole
     * content box, so the sheet never sits as a patch in the middle of the text.
     */
    private static final int SHEET_ICON_SIZE = IconRenderer.ICON_SIZE;
    /**
     * The gap left between a name and the arc it sits on, as before this screen drew through a page.
     */
    private static final int LABEL_GAP = 6;

    @Nullable
    private Element ring, sectors, centre;
    @Nullable
    private Label title, note, page;
    private final List<Sector> cells = new ArrayList<>(WheelGeometry.SECTORS);

    private int shownScalePercent = Integer.MIN_VALUE;
    private int shownPageNumber = Integer.MIN_VALUE;
    private boolean turnFlip;

    public WheelMenuScreen() {
        super(AuiPages.wheelPage());
    }

    /**
     * The page is submitted by {@code super} (ApricityScreen), so the pointed entry's tooltip still lands on top.
     */
    @Override
    public void extractRenderState(@NotNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        if (this.auiPageWritable()) this.writeFrame();
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        this.extractTooltip(graphics, mouseX, mouseY);
    }

    private void writeFrame() {
        Player player = Minecraft.getInstance().player;
        List<@Nullable WheelMenuEntry> sectors =
                WheelMenuContent.sectors(WheelSelectionState.pages(), WheelSelectionState.page());
        int pointed = this.pointedSector();
        this.showScale();
        this.showSectors(sectors, pointed, player);
        this.showCentre(sectors.get(pointed), player);
        this.showPage();
    }

    int pointedSector() {
        return WheelGeometry.sectorAt(WheelGeometry.pointerAngle());
    }

    // The whole ring shrinks to fit a small window, exactly as the canvas geometry did; the page keeps its
    // full-size layout and only this scale changes.
    private void showScale() {
        if (this.ring == null) return;
        double fit = Mth.clamp(WheelGeometry.ring(this.width, this.height, 1.0D).scale(), 0.1D, 1.0D);
        int percent = (int) Math.round(fit * 100.0D);
        if (percent == this.shownScalePercent) return;
        this.shownScalePercent = percent;
        AuiElements.style(this.ring, "transform", "scale(" + percent / 100.0D + ")");
    }

    private void showSectors(List<@Nullable WheelMenuEntry> sectors, int pointed, @Nullable Player player) {
        for (int slot = 0; slot < WheelGeometry.SECTORS; slot++) {
            WheelMenuEntry entry = sectors.get(slot);
            Optional<IconReference> icon = entry == null ? Optional.empty() : entry.icon();
            boolean cooling = entry != null && !entry.usable(player);
            this.cells.get(slot).show(entry != null, entry != null && slot == pointed, cooling,
                    icon.orElse(null), this.label(entry, icon.isPresent()),
                    cooling ? cooldownFraction(entry, player) : 0.0D);
        }
    }

    // How much of the cooldown is still to wait, the same fraction the HUD cell draws its sheet against. An
    // entry that does not know its cooldown length gets a full sheet, so it still says "cooling" without a
    // progress to show.
    private static double cooldownFraction(@Nullable WheelMenuEntry entry, @Nullable Player player) {
        if (entry == null) return 0.0D;
        long remaining = entry.cooldownTicks(player);
        if (remaining <= 0L) return 0.0D;
        long length = entry.cooldownLength(player);
        return length <= 0L ? 1.0D : Math.min(1.0D, (double) remaining / (double) length);
    }

    // An entry with an icon draws the icon; one without draws its name, cut to the sector's arc the way the
    // canvas ring cut it.
    private String label(@Nullable WheelMenuEntry entry, boolean hasIcon) {
        if (entry == null || hasIcon) return "";
        double arc = 2.0D * Math.PI * WheelGeometry.ring(RING_BOX, RING_BOX, 1.0D).iconRadius() / WheelGeometry.SECTORS;
        int width = Math.max(IconRenderer.ICON_SIZE, (int) Math.round(arc) - LABEL_GAP);
        return IconRenderer.fit(this.font, entry.title().getString(), width);
    }

    private void showCentre(@Nullable WheelMenuEntry entry, @Nullable Player player) {
        if (this.centre == null) return;
        AuiElements.setClass(this.centre, "empty", entry == null);
        if (entry == null) {
            if (this.title != null) this.title.show("");
            if (this.note != null) this.note.show("");
            return;
        }
        if (this.title != null) this.title.show(entry.title().getString());
        if (this.note != null)
            this.note.show(entry.usable(player) ? this.useHint() : this.cooldownNote(entry, player));
    }

    // Which page this ring is. Every page looks the same on the ring, and the key names are the ones the player
    // actually bound, so a rebind cannot turn the line into a lie. A turn nudges the sector layer, alternating
    // two identical animations because one animation name cannot replay itself.
    private void showPage() {
        int number = WheelSelectionState.page();
        if (number != this.shownPageNumber) {
            this.shownPageNumber = number;
            this.turnFlip = !this.turnFlip;
            if (this.sectors != null) {
                AuiElements.setClass(this.sectors, "turn-a", this.turnFlip);
                AuiElements.setClass(this.sectors, "turn-b", !this.turnFlip);
            }
        }
        if (this.page != null) this.page.show(Component.translatable("wheel.mxt.page_hint",
                number + 1, WheelSelectionState.pages().size(), WheelSelectionState.pageSource().displayName(),
                MxtKeyMappings.WHEEL_PREVIOUS.get().getTranslatedKeyMessage(),
                MxtKeyMappings.WHEEL_NEXT.get().getTranslatedKeyMessage()).getString());
    }

    private String useHint() {
        return Component.translatable("wheel.mxt.use_hint",
                MxtKeyMappings.WHEEL_USE.get().getTranslatedKeyMessage()).getString();
    }

    // The server synced the ending tick, so a countdown is possible.
    private String cooldownNote(WheelMenuEntry entry, @Nullable Player player) {
        return Component.translatable("wheel.mxt.cooldown", WheelDuration.seconds(entry.cooldownTicks(player))).getString();
    }

    private void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (this.cells.isEmpty()) return;
        WheelMenuEntry entry = WheelMenuContent.sectors(WheelSelectionState.pages(), WheelSelectionState.page())
                .get(this.pointedSector());
        if (entry == null) return;
        List<Component> lines = entry.tooltip(Minecraft.getInstance().player);
        if (!lines.isEmpty()) graphics.setComponentTooltipForNextFrame(this.font, lines, mouseX, mouseY);
    }

    /**
     * Resolves the page contract; a refresh (hot reload) replaces every element and the wheel writes the whole
     * ring into the fresh one.
     */
    @Override
    public void bindPage() {
        this.ring = this.getOrThrow("ring");
        this.sectors = this.getOrThrow("sectors");
        this.centre = this.getOrThrow("centre");
        this.title = new Label(this.getOrThrow("title"));
        this.note = new Label(this.getOrThrow("note"));
        this.page = new Label(this.getOrThrow("page"));

        WheelGeometry.Ring geometry = WheelGeometry.ring(RING_BOX, RING_BOX, 1.0D);
        for (int slot = 0; slot < WheelGeometry.SECTORS; slot++) {
            Element root = this.getOrThrow("sector-" + slot), content = this.getOrThrow("content-" + slot);
            AuiElements.style(root, "clip-path", sectorPolygon(slot, geometry));
            double angle = WheelGeometry.sectorCentre(slot);
            AuiElements.style(content, "left", round(geometry.x(angle, geometry.iconRadius()) - CONTENT_WIDTH / 2.0D) + "px");
            AuiElements.style(content, "top", round(geometry.y(angle, geometry.iconRadius()) - CONTENT_HEIGHT / 2.0D) + "px");
            this.cells.add(new Sector(root, this.getOrThrow("icon-" + slot, Item.class), this.getOrThrow("texture-" + slot), this.getOrThrow("name-" + slot), this.getOrThrow("sheet-" + slot)));
        }
    }

    /**
     * One sector's outline inside the page's ring box: the outer arc, then the inner arc back. The gap is cut
     * from both ends, so the plate underneath shows through as the line between neighbours.
     */
    private static String sectorPolygon(int slot, WheelGeometry.Ring ring) {
        double from = WheelGeometry.sectorStart(slot) + SECTOR_GAP_DEGREES;
        double to = WheelGeometry.sectorStart(slot) + WheelGeometry.SECTOR_DEGREES - SECTOR_GAP_DEGREES;
        StringBuilder polygon = new StringBuilder("polygon(");
        for (int step = 0; step <= ARC_SEGMENTS; step++)
            append(polygon, ring, between(from, to, step), ring.outerRadius());
        for (int step = 0; step <= ARC_SEGMENTS; step++)
            append(polygon, ring, between(to, from, step), ring.innerRadius());
        return polygon.append(')').toString();
    }

    private static double between(double from, double to, int step) {
        return from + (to - from) * step / ARC_SEGMENTS;
    }

    private static void append(StringBuilder polygon, WheelGeometry.Ring ring, double angle, double radius) {
        if (polygon.charAt(polygon.length() - 1) != '(') polygon.append(", ");
        polygon.append(round(ring.x(angle, radius))).append("px ").append(round(ring.y(angle, radius))).append("px");
    }

    private static String round(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    @Override
    public void onBindingsCleared() {
        this.cells.clear();
        this.ring = null;
        this.sectors = null;
        this.centre = null;
        this.page = null;
        this.title = null;
        this.note = null;
        this.shownScalePercent = Integer.MIN_VALUE;
        this.shownPageNumber = Integer.MIN_VALUE;
    }

    // What the pointer is on, or null for an empty cell - a selection is never built from one.
    @Nullable
    WheelSelection selection(WheelSelection.Method method) {
        int sector = this.pointedSector();
        int number = WheelSelectionState.numberAt(sector);
        WheelMenuEntry entry = WheelMenuContent.entry(WheelSelectionState.pages(), number);
        return entry == null ? null
                : new WheelSelection(WheelMenuContent.source(WheelSelectionState.pages(), number), number, entry, method);
    }

    // The left button asks for exactly what the use key asks for, and the wheel stays open either way.
    @Override
    public boolean mouseClicked(@NotNull MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != 0) return false;
        WheelMenuController.usePointed(WheelSelection.Method.CLICK);
        return true;
    }

    // Scrolling up goes back a page, down goes forward, exactly as the two page keys do; the notice is theirs too,
    // so a player who never touches the keys still learns which wheel they landed on.
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (WheelMenuController.scrollTurnsPages() && scrollY != 0.0D) {
            WheelMenuController.stepPage(scrollY > 0.0D ? -1 : 1);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    // A wheel must not pause the world in single player.
    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // The one page host that draws no backdrop at all: the wheel is read while the world is still running and the
    // HUD is still on screen, and the choice is made against what is behind it - the vanilla plate would flatten
    // that, and super's own branch would blur it. Every other host draws the plate through AuiStyles.extract.
    @Override
    public void extractBackground(@NotNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public void removed() {
        WheelMenuController.screenRemoved(this);
        super.removed();
    }

    /**
     * One sector of the page and the state it was last given, so a frame that changes nothing writes nothing.
     */
    private static final class Sector {
        private final Element root;
        private final Item item;
        private final Element texture;
        private final Element name;
        private final Element sheet;
        @Nullable
        private IconReference shownIcon;
        /**
         * What the sheet last covered: 0 while the sector is not cooling, otherwise the covered rows with the
         * box height below them, so a change in either writes the whole box again.
         */
        private int shownSheet;

        private Sector(Element root, Item item, Element texture, Element name, Element sheet) {
            this.root = root;
            this.item = item;
            this.texture = texture;
            this.name = name;
            this.sheet = sheet;
        }

        private void show(boolean holds, boolean pointed, boolean cooling, @Nullable IconReference icon, String label,
                          double cooldown) {
            boolean asItem = icon != null && icon.item().isPresent();
            boolean asTexture = icon != null && icon.texture().isPresent();
            AuiElements.setClass(this.root, "empty", !holds);
            AuiElements.setClass(this.root, "selected", pointed);
            AuiElements.setClass(this.root, "cooling", cooling);
            AuiElements.setClass(this.root, "icon-item", asItem);
            AuiElements.setClass(this.root, "icon-texture", asTexture);
            // The sheet covers the icon box, or the whole content box when this sector draws a name; it is
            // bottom-anchored in it the way the HUD cell draws it, and only whole pixels are written, so a
            // draining cooldown touches the page at most once per pixel instead of every frame.
            boolean hasIcon = asItem || asTexture;
            int boxWidth = hasIcon ? SHEET_ICON_SIZE : CONTENT_WIDTH;
            int boxHeight = hasIcon ? SHEET_ICON_SIZE : CONTENT_HEIGHT;
            int rows = cooldown <= 0.0D ? 0 : Mth.clamp((int) Math.ceil(cooldown * boxHeight), 1, boxHeight);
            int signature = rows == 0 ? 0 : (rows << 1) | (hasIcon ? 1 : 0);
            if (signature != this.shownSheet) {
                this.shownSheet = signature;
                if (rows > 0) {
                    AuiElements.style(this.sheet, "left", ((CONTENT_WIDTH - boxWidth) / 2) + "px");
                    AuiElements.style(this.sheet, "width", boxWidth + "px");
                    AuiElements.style(this.sheet, "top",
                            ((CONTENT_HEIGHT - boxHeight) / 2 + boxHeight - rows) + "px");
                    AuiElements.style(this.sheet, "height", rows + "px");
                }
            }
            // An icon that did not change is not written again: pushing a stack repaints the element.
            if (!Objects.equals(this.shownIcon, icon)) {
                this.shownIcon = icon;
                if (asItem) this.item.setIngredientStack(icon.item().orElseThrow().create());
                else this.item.clearDrivenState(Item.Source.INGREDIENT);
                if (asTexture) this.texture.setAttribute("src", icon.texture().orElseThrow().toString());
                else this.texture.removeAttribute("src");
            }
            AuiElements.setText(this.name, label);
        }
    }
}