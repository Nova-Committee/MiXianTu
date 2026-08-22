package com.iafenvoy.mxt.compat.jade;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.data.aura.SpiritStorageComponent;
import com.iafenvoy.mxt.item.block.entity.DisplayStandBlockEntity;
import com.iafenvoy.mxt.registry.MxtDataComponents;
import com.iafenvoy.mxt.runtime.cultivation.ItemAuraService;
import com.iafenvoy.mxt.runtime.spirit.SpiritItemAccess;
import com.iafenvoy.mxt.util.TooltipText;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent.ShowItem;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import org.jspecify.annotations.NonNull;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;
import snownee.jade.api.ui.Element;

/** Shows the displayed item and its charge on wooden display stands. */
public enum DisplayStandComponentProvider implements IComponentProvider<BlockAccessor> {
    INSTANCE;

    private static final Identifier ID = Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "display_stand");

    @Override
    public void appendTooltip(@NonNull ITooltip tooltip, BlockAccessor accessor, @NonNull IPluginConfig config) {
        if (!(accessor.getBlockEntity() instanceof DisplayStandBlockEntity stand)) return;
        ItemStack item = stand.displayedItem();
        if (item.isEmpty()) return;

        tooltip.add(new DisplayedItemElement(item));
        if (!(item.getItem() instanceof SpiritItemAccess)) return;
        int capacity = ItemAuraService.capacity(accessor.getLevel().registryAccess(), item, FormulaContext.EMPTY);
        if (capacity <= 0) return;

        SpiritStorageComponent storedComponent = item.get(MxtDataComponents.SPIRIT_STORAGE);
        int stored = Math.min(capacity, storedComponent == null ? capacity : storedComponent.amount());
        int percentage = (int) Math.round(stored * 100.0D / capacity);
        tooltip.add(new ChargeBarElement(percentage, stored, capacity));
    }

    @Override
    public Element getIcon(BlockAccessor accessor, @NonNull IPluginConfig config, Element currentIcon) {
        return currentIcon;
    }

    @Override
    public @NonNull Identifier getUid() {
        return ID;
    }

    private static ChatFormatting color(int percentage) {
        if (percentage >= 75) return ChatFormatting.GREEN;
        if (percentage >= 50) return ChatFormatting.YELLOW;
        if (percentage >= 25) return ChatFormatting.GOLD;
        return ChatFormatting.RED;
    }

    private static int colorValue(int percentage) {
        return switch (color(percentage)) {
            case GREEN -> 0xFF55FF55;
            case YELLOW -> 0xFFFFFF55;
            case GOLD -> 0xFFFFAA00;
            default -> 0xFFFF5555;
        };
    }

    /** Jade-style single-item row: item icon on the left and its name on the right. */
    private static final class DisplayedItemElement extends Element {
        private static final float ICON_SCALE = 0.75F;
        private final ItemStack item;

        private DisplayedItemElement(ItemStack item) {
            this.item = item;
            this.width = 16 + Minecraft.getInstance().font.width(item.getHoverName());
            this.height = 14;
        }

        @Override
        public void extractRenderState(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            int x = this.getX();
            int y = this.getY();
            if (mouseX >= x && mouseX < x + 14 && mouseY >= y && mouseY < y + 14)
                Element.setHoverEffect(graphics, new ShowItem(ItemStackTemplate.fromNonEmptyStack(this.item)));
            graphics.pose().pushMatrix();
            graphics.pose().translate(x + 1.0F, y + 1.0F);
            graphics.pose().scale(ICON_SCALE, ICON_SCALE);
            graphics.item(this.item, 0, 0);
            graphics.pose().popMatrix();
            graphics.text(Minecraft.getInstance().font, this.item.getHoverName(), x + 16, y + 3, 0xFFAAAAAA);
        }

        @Override
        public Component getNarration() {
            return this.item.getHoverName();
        }
    }

    /** Jade layout element with a real filled rectangle instead of a text-made bar. */
    private static final class ChargeBarElement extends Element {
        private static final int BAR_WIDTH = 100;
        private static final int BAR_HEIGHT = 8;
        private final int percentage;
        private final int stored;
        private final int capacity;

        private ChargeBarElement(int percentage, int stored, int capacity) {
            this.percentage = percentage;
            this.stored = stored;
            this.capacity = capacity;
            this.width = 132;
            this.height = BAR_HEIGHT;
        }

        @Override
        public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            int x = this.getX();
            int y = this.getY();
            int filledWidth = Math.round(BAR_WIDTH * this.percentage / 100.0F);
            graphics.fill(x, y, x + BAR_WIDTH, y + BAR_HEIGHT, 0xFF303030);
            if (filledWidth > 0) graphics.fill(x, y, x + filledWidth, y + BAR_HEIGHT, colorValue(this.percentage));
            graphics.outline(x, y, BAR_WIDTH, BAR_HEIGHT, 0xFF000000);
            graphics.text(Minecraft.getInstance().font, this.percentage + "%", x + BAR_WIDTH + 5, y,
                    colorValue(this.percentage));
        }

        @Override
        public Component getNarration() {
            return Component.translatable("jade.mxt.display_stand.charge", "", TooltipText.number(this.stored),
                    TooltipText.number(this.capacity), this.percentage + "%");
        }
    }
}
