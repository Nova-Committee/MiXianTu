package com.iafenvoy.mxt.screen.wheel;

import com.iafenvoy.mxt.api.WheelMenuEntry;
import com.iafenvoy.mxt.data.IconReference;
import com.iafenvoy.mxt.registry.MxtKeyMappings;
import com.iafenvoy.mxt.render.IconRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix3x2f;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/**
 * The wheel: twelve sectors, the pointer's direction picks one, and the chosen entry's name is written in the
 * middle. The screen is created fresh every time the wheel opens, it never closes itself, and it draws no
 * background: the default one would blur the HUD behind it.
 */
public final class WheelMenuScreen extends Screen {
    private static final int MENU_COLOR = 0xB010131D;
    private static final int EMPTY_COLOR = 0x6010131D;
    private static final int COOLDOWN_COLOR = 0x90_3A2A20;
    // The pointed sector: the HUD editor's gold, so "this is the one" means the same thing twice.
    private static final int SELECT_COLOR = 0xE0FFD24A;
    private static final int TITLE_BACKDROP = 0xB010131D;
    private static final int TITLE_COLOR = 0xFFFFFFFF;
    private static final int NOTE_COLOR = 0xFFAAAAAA;
    private static final int TITLE_PADDING = 5;
    private static final int LABEL_GAP = 6;
    private static final long OPEN_NANOS = 160_000_000L;

    // A monotonic clock: a tick counter would visibly step at 20 Hz.
    private final long openedAt = System.nanoTime();

    public WheelMenuScreen() {
        super(Component.translatable("screen.mxt.wheel"));
    }

    @Override
    public void extractRenderState(@NotNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        Player player = Minecraft.getInstance().player;
        // This tick's page, read once so the ring, the icons and the middle text cannot disagree.
        List<@Nullable WheelMenuEntry> sectors =
                WheelMenuContent.sectors(WheelSelectionState.pages(), WheelSelectionState.page());
        int pointed = this.pointedSector();
        WheelGeometry.Ring ring = WheelGeometry.ring(this.width, this.height, this.growth());
        // The pose is copied - the extractor's stack keeps changing, so a live reference would drift.
        graphics.submitGuiElementRenderState(new WheelRingRenderState(RenderPipelines.GUI, TextureSetup.noTexture(),
                new Matrix3x2f(graphics.pose()), ring, this.sectorColors(sectors, pointed, player),
                pointedEntry(sectors, pointed) == null ? -1 : pointed, graphics.peekScissorStack()));
        this.extractSectorContents(graphics, ring, sectors);
        this.extractTitle(graphics, ring, sectors.get(pointed), player);
        this.extractPage(graphics, ring);
        this.extractTooltip(graphics, mouseX, mouseY, sectors.get(pointed), player);
    }

    int pointedSector() {
        return WheelGeometry.sectorAt(WheelGeometry.pointerAngle());
    }

    private static @Nullable WheelMenuEntry pointedEntry(List<@Nullable WheelMenuEntry> sectors, int pointed) {
        return pointed >= 0 && pointed < sectors.size() ? sectors.get(pointed) : null;
    }

    // One colour per sector: what it holds and whether it is usable. Decided only here.
    private int[] sectorColors(List<@Nullable WheelMenuEntry> sectors, int pointed, @Nullable Player player) {
        int[] colors = new int[WheelGeometry.SECTORS];
        for (int sector = 0; sector < colors.length; sector++) {
            WheelMenuEntry entry = sectors.get(sector);
            if (entry == null) colors[sector] = EMPTY_COLOR;
            else if (sector == pointed) colors[sector] = SELECT_COLOR;
            else colors[sector] = entry.usable(player) ? MENU_COLOR : COOLDOWN_COLOR;
        }
        return colors;
    }

    // Opening progress, eased out: most of the travel happens early.
    private double growth() {
        double progress = Mth.clamp((double) (System.nanoTime() - this.openedAt) / OPEN_NANOS, 0.0D, 1.0D);
        return 1.0D - (1.0D - progress) * (1.0D - progress);
    }

    // Each sector's icon at the ring's middle, or its name when the entry has no icon.
    private void extractSectorContents(GuiGraphicsExtractor graphics, WheelGeometry.Ring ring,
                                       List<@Nullable WheelMenuEntry> sectors) {
        // Measured on the fully open ring: a name that gained a character mid-growth would flicker.
        int labelWidth = labelWidth(WheelGeometry.ring(this.width, this.height, 1.0D));
        for (int slot = 0; slot < WheelGeometry.SECTORS; slot++) {
            WheelMenuEntry entry = sectors.get(slot);
            if (entry == null) continue;
            double angle = WheelGeometry.sectorCentre(slot);
            int centreX = (int) Math.round(ring.x(angle, ring.iconRadius()));
            int centreY = (int) Math.round(ring.y(angle, ring.iconRadius()));
            Optional<IconReference> icon = entry.icon();
            if (icon.isPresent()) {
                IconRenderer.renderAt(graphics, icon.orElseThrow(),
                        centreX - IconRenderer.ICON_SIZE / 2, centreY - IconRenderer.ICON_SIZE / 2);
                continue;
            }
            IconRenderer.renderName(graphics, this.font, entry.title(), centreX, centreY, labelWidth);
        }
    }

    private static int labelWidth(WheelGeometry.Ring ring) {
        double arc = 2.0D * Math.PI * ring.iconRadius() / WheelGeometry.SECTORS;
        return (int) Math.max(IconRenderer.ICON_SIZE, Math.round(arc) - LABEL_GAP);
    }

    // The pointed entry's name in the middle, with a line saying how to use it or how long it is down.
    private void extractTitle(GuiGraphicsExtractor graphics, WheelGeometry.Ring ring,
                              @Nullable WheelMenuEntry entry, @Nullable Player player) {
        if (entry == null) return;
        Component title = entry.title();
        Component note = entry.usable(player) ? this.useHint() : this.cooldownNote(entry, player);
        int width = Math.max(this.font.width(title), this.font.width(note));
        int height = this.font.lineHeight + this.font.lineHeight + 1;
        int x = ring.centreX() - width / 2;
        int y = ring.centreY() - height / 2;
        graphics.fill(x - TITLE_PADDING, y - TITLE_PADDING, x + width + TITLE_PADDING, y + height + TITLE_PADDING, TITLE_BACKDROP);
        graphics.text(this.font, title, ring.centreX() - this.font.width(title) / 2, y, TITLE_COLOR, true);
        graphics.text(this.font, note, ring.centreX() - this.font.width(note) / 2, y + this.font.lineHeight + 1, NOTE_COLOR, true);
    }

    // Which page this ring is, written above it: every page looks the same on the ring. The key names are the
    // ones the player actually bound, so a rebind cannot turn the line into a lie.
    private void extractPage(GuiGraphicsExtractor graphics, WheelGeometry.Ring ring) {
        Component page = Component.translatable("wheel.mxt.page_hint",
                WheelSelectionState.page() + 1, WheelSelectionState.pages().size(),
                WheelSelectionState.pageSource().displayName(),
                MxtKeyMappings.WHEEL_PREVIOUS.get().getTranslatedKeyMessage(),
                MxtKeyMappings.WHEEL_NEXT.get().getTranslatedKeyMessage());
        int width = this.font.width(page);
        int x = ring.centreX() - width / 2;
        int y = Math.max(0, ring.centreY() - (int) Math.round(ring.outerRadius()) - TITLE_PADDING - this.font.lineHeight);
        graphics.fill(x - TITLE_PADDING, y - TITLE_PADDING, x + width + TITLE_PADDING, y + this.font.lineHeight + TITLE_PADDING, TITLE_BACKDROP);
        graphics.text(this.font, page, x, y, TITLE_COLOR, true);
    }

    private Component useHint() {
        return Component.translatable("wheel.mxt.use_hint",
                MxtKeyMappings.WHEEL_USE.get().getTranslatedKeyMessage());
    }

    // The server synced the ending tick, so a countdown is possible.
    private Component cooldownNote(WheelMenuEntry entry, @Nullable Player player) {
        return Component.translatable("wheel.mxt.cooldown", WheelDuration.seconds(entry.cooldownTicks(player)));
    }

    private void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                                @Nullable WheelMenuEntry entry, @Nullable Player player) {
        if (entry == null) return;
        List<Component> lines = entry.tooltip(player);
        if (!lines.isEmpty()) graphics.setComponentTooltipForNextFrame(this.font, lines, mouseX, mouseY);
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

    // A wheel must not pause the world in single player.
    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // No background: the default one would blur the HUD extracted before this screen.
    @Override
    public void extractBackground(@NotNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(null);
    }

    @Override
    public void removed() {
        WheelMenuController.screenRemoved(this);
        super.removed();
    }
}
