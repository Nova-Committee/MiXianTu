package com.iafenvoy.mxt.screen.gui;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.attachment.ResourceHolderAttachment;
import com.iafenvoy.mxt.attachment.SpiritIdentityAttachment;
import com.iafenvoy.mxt.config.MxtClientConfig;
import com.iafenvoy.mxt.data.resource.Resource;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.render.IconRenderer;
import com.iafenvoy.mxt.runtime.cultivation.TechniqueProgress;
import com.iafenvoy.mxt.runtime.cultivation.TechniqueProgress.Entry;
import com.iafenvoy.mxt.runtime.cultivation.TechniqueProgress.Mode;
import com.iafenvoy.mxt.runtime.cultivation.TechniqueProgress.Progress;
import com.iafenvoy.mxt.util.DefinitionText;
import com.iafenvoy.mxt.util.HolderHelper;
import com.iafenvoy.mxt.util.formula.FormulaContexts;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractScrollArea;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.Holder;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;

/**
 * A scrollable list of the player's learned techniques: one row per technique with its icon, level and
 * progress towards the next level. The panel is a view over synchronized state, so the rows are computed
 * locally and no packet is involved. The background is an art asset; the row separators and progress bars
 * are drawn in code.
 */
public final class TechniquePanelScreen extends Screen {
    private static final Identifier BACKGROUND = Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "textures/gui/classic/technique_panel.png");
    private static final Identifier ICON_FRAME = Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "textures/gui/classic/slot_24.png");
    private static final int PANEL_WIDTH = 240;
    private static final int PANEL_HEIGHT = 300;
    private static final int CONTENT_PADDING = 12;
    private static final int EMPTY_INSET = 6;
    /**
     * Room kept beside the rows for the vanilla scrollbar.
     */
    private static final int SCROLLBAR_ROOM = AbstractScrollArea.SCROLLBAR_WIDTH;
    private static final int ROW_HEIGHT = 34;
    private static final int ICON_SIZE = 24;
    private static final int ICON_GAP = 9;
    private static final int LABEL_OFFSET = 4;
    private static final int BAR_HEIGHT = 7;
    private static final int BAR_OFFSET = 19;
    private static final int SEPARATOR_OFFSET = ROW_HEIGHT - 3;
    private static final int TEXT_COLOR = 0xFF404040;
    private static final int UNKNOWN_COLOR = 0xFF5A5A5A;
    private static final int BAR_BORDER_COLOR = 0xFF000000;
    private static final int BAR_TRACK_COLOR = 0xFF373737;
    private static final int BAR_FALLBACK_COLOR = 0xFF8B8B8B;
    private static final int DIVIDER_DARK_COLOR = 0xFF555555;
    private static final int DIVIDER_LIGHT_COLOR = 0xFFFFFFFF;
    private static final int HOVER_COLOR = 0x503F6A91;

    private TechniqueList list;
    private int panelLeft;
    private int panelTop;
    private int panelWidth;
    private int panelHeight;
    private int refreshTicks;
    private boolean empty;

    public TechniquePanelScreen() {
        super(Component.translatable("screen.mxt.technique_panel"));
    }

    @Override
    protected void init() {
        this.layoutWidgets();
        if (!this.children().contains(this.list)) this.addRenderableOnly(this.list);
    }

    private void layoutWidgets() {
        this.panelWidth = Math.max(1, Math.min(PANEL_WIDTH, this.width - 12));
        this.panelHeight = Math.max(1, Math.min(PANEL_HEIGHT, this.height - 12));
        this.panelLeft = (this.width - this.panelWidth) / 2;
        this.panelTop = (this.height - this.panelHeight) / 2;
        int listX = this.panelLeft + CONTENT_PADDING;
        int listTop = this.panelTop + CONTENT_PADDING;
        int listWidth = Math.max(1, this.panelWidth - CONTENT_PADDING * 2 - SCROLLBAR_ROOM);
        int listHeight = Math.max(1, this.panelTop + this.panelHeight - CONTENT_PADDING - listTop);
        if (this.list == null) {
            this.list = new TechniqueList(this.minecraft, listX, listTop, listWidth, listHeight);
            this.list.replaceEntries(this.buildEntries());
        } else {
            this.list.updateSizeAndPosition(listWidth, listHeight, listX, listTop);
        }
    }

    @Override
    protected void repositionElements() {
        this.layoutWidgets();
    }

    @Override
    public void tick() {
        super.tick();
        int interval = MxtClientConfig.INSTANCE.information.refreshInterval.getValue();
        if (++this.refreshTicks < interval) return;
        this.refreshTicks = 0;
        if (this.list == null) return;
        double scroll = this.list.scrollAmount();
        this.list.replaceEntries(this.buildEntries());
        this.list.setScrollAmount(scroll);
    }

    private List<TechniqueList.RowEntry> buildEntries() {
        if (this.minecraft.player == null) {
            this.empty = true;
            return List.of();
        }
        SpiritIdentityAttachment spirit = this.minecraft.player.getData(MxtAttachments.SPIRIT_IDENTITY);
        ResourceHolderAttachment resources = this.minecraft.player.getData(MxtAttachments.RESOURCE_HOLDER);
        Mode mode = MxtClientConfig.INSTANCE.techniques.progressMode.getValue();
        List<Entry> rows = TechniqueProgress.rows(spirit, resources, FormulaContexts.forEntity(this.minecraft.player));
        this.empty = rows.isEmpty();
        List<TechniqueList.RowEntry> entries = new ArrayList<>(rows.size());
        for (Entry row : rows) entries.add(new TechniqueList.RowEntry(row, TechniqueProgress.progress(row, mode)));
        return entries;
    }

    @Override
    public void extractBackground(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        this.extractTransparentBackground(graphics);
        graphics.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND, this.panelLeft, this.panelTop, 0.0F, 0.0F, this.panelWidth, this.panelHeight, PANEL_WIDTH, PANEL_HEIGHT);
        if (this.empty)
            graphics.text(this.font, Component.translatable("screen.mxt.technique_panel.empty"), this.panelLeft + CONTENT_PADDING + EMPTY_INSET, this.panelTop + CONTENT_PADDING + EMPTY_INSET, TEXT_COLOR, false);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (this.list != null && this.list.isMouseOver(mouseX, mouseY)) {
            this.list.setScrollAmount(this.list.scrollAmount() - scrollY * ROW_HEIGHT);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static final class TechniqueList extends ObjectSelectionList<TechniqueList.RowEntry> {
        private TechniqueList(Minecraft minecraft, int x, int y, int width, int height) {
            super(minecraft, width, height, y, ROW_HEIGHT);
            this.setX(x);
        }

        /**
         * The panel's own art is the background; the vanilla translucent backdrop would dim it.
         */
        @Override
        protected void extractListBackground(@NonNull GuiGraphicsExtractor graphics) {
        }

        /**
         * Vanilla draws a line above the first row and below the last; each row already carries its own
         * separator.
         */
        @Override
        protected void extractListSeparators(@NonNull GuiGraphicsExtractor graphics) {
        }

        /**
         * Vanilla puts the scrollbar past the row and the widget, which lands on the panel's border.
         */
        @Override
        protected int scrollBarX() {
            return this.getRowRight();
        }

        @Override
        public int getRowWidth() {
            return this.getWidth();
        }

        private static final class RowEntry extends Entry<RowEntry> {
            private final TechniqueProgress.Entry row;
            private final Progress progress;

            private RowEntry(TechniqueProgress.Entry row, Progress progress) {
                this.row = row;
                this.progress = progress;
            }

            @Override
            public void extractContent(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float partialTick) {
                Font font = Minecraft.getInstance().font;
                int rowX = this.getX();
                int rowRight = rowX + this.getWidth();
                int rowTop = this.getY();
                if (hovered || this.isFocused())
                    graphics.fill(rowX, rowTop, rowRight, rowTop + ROW_HEIGHT, HOVER_COLOR);
                int iconX = rowX + 1;
                int iconY = rowTop + (ROW_HEIGHT - ICON_SIZE) / 2;
                graphics.blit(RenderPipelines.GUI_TEXTURED, ICON_FRAME, iconX, iconY, 0.0F, 0.0F,
                        ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE);
                this.row.technique().value().icon().ifPresent(icon ->
                        IconRenderer.render(graphics, icon, iconX, iconY, ICON_SIZE));

                int textX = rowX + ICON_SIZE + ICON_GAP;
                Component value = this.valueText(font);
                int valueWidth = font.width(value);
                int valueX = Math.max(textX, rowRight - valueWidth);
                Component level = this.levelText(font, valueX - 4 - textX);
                graphics.text(font, level, textX, rowTop + LABEL_OFFSET,
                        this.row.hasStage() ? TEXT_COLOR : UNKNOWN_COLOR, false);
                graphics.text(font, value, valueX, rowTop + LABEL_OFFSET, TEXT_COLOR, false);

                int barWidth = Math.max(1, rowRight - textX);
                int barTop = rowTop + BAR_OFFSET;
                graphics.fill(textX, barTop, textX + barWidth, barTop + BAR_HEIGHT, BAR_BORDER_COLOR);
                graphics.fill(textX + 1, barTop + 1, textX + barWidth - 1, barTop + BAR_HEIGHT - 1, BAR_TRACK_COLOR);
                int filled = (int) Math.round((barWidth - 2) * this.progress.fraction());
                if (filled > 0)
                    graphics.fill(textX + 1, barTop + 1, textX + 1 + Math.min(filled, barWidth - 2),
                            barTop + BAR_HEIGHT - 1, this.fillColor());

                int separatorY = rowTop + SEPARATOR_OFFSET;
                graphics.fill(rowX, separatorY, rowRight, separatorY + 1, DIVIDER_DARK_COLOR);
                graphics.fill(rowX, separatorY + 1, rowRight, separatorY + 2, DIVIDER_LIGHT_COLOR);

                if (hovered) graphics.setTooltipForNextFrame(font, this.tooltip(), mouseX, mouseY);
            }

            /**
             * The level column, using the level's own display name when the data pack provides one and its
             * rank otherwise, measured because the level and the progress value share one line.
             */
            private Component levelText(Font font, int width) {
                if (!this.row.hasStage()) return Component.translatable("screen.mxt.technique_panel.level_unknown");
                Identifier stage = HolderHelper.id(this.row.stage());
                Component name = DefinitionText.name(stage, "skill_stage");
                Component label = Language.getInstance().has(stage.toLanguageKey("skill_stage"))
                        ? name : Component.literal(Integer.toString(this.row.rank() + 1));
                Component text = Component.translatable("screen.mxt.technique_panel.level", label,
                        this.row.rank() + 1, this.row.total());
                return Component.literal(abbreviate(font, text.getString(), width));
            }

            private Component valueText(Font font) {
                if (!this.row.hasStage() || !this.row.hasMastery())
                    return Component.translatable("screen.mxt.technique_panel.value_unknown");
                if (!this.row.hasNextLevel()) return Component.translatable("screen.mxt.technique_panel.value_max");
                return Component.translatable("screen.mxt.technique_panel.value",
                        format(this.progress.done()), format(this.progress.span()));
            }

            /**
             * Tinted with the mastery resource's own particle colour, like the crafting progress bar.
             */
            private int fillColor() {
                Holder<Resource> mastery = this.row.technique().value().masteryResource().orElse(null);
                return mastery == null ? BAR_FALLBACK_COLOR : 0xFF000000 | mastery.value().particleColor();
            }

            /**
             * The technique's name, plus the level's own ID while it has one: the row itself only has room
             * for the level's short display name or its rank.
             */
            private Component tooltip() {
                MutableComponent line = DefinitionText.name(this.row.technique(), "cultivation_technique").copy();
                if (this.row.hasStage())
                    line.append(" ").append(Component.literal(HolderHelper.id(this.row.stage()).toString())
                            .withStyle(ChatFormatting.DARK_GRAY));
                return line;
            }

            @Override
            public @NonNull Component getNarration() {
                return DefinitionText.name(this.row.technique(), "cultivation_technique");
            }
        }
    }

    private static String format(double value) {
        return Math.abs(value - Math.rint(value)) < 1.0E-6D
                ? Long.toString(Math.round(value)) : String.format("%.1f", value);
    }

    private static String abbreviate(Font font, String text, int width) {
        if (font.width(text) <= width) return text;
        String suffix = "...";
        return font.plainSubstrByWidth(text, Math.max(0, width - font.width(suffix))) + suffix;
    }
}
