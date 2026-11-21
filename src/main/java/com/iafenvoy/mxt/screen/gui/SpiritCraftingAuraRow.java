package com.iafenvoy.mxt.screen.gui;

import com.iafenvoy.mxt.screen.AuraDisplay;
import com.iafenvoy.mxt.screen.aui.AuiElements;
import com.iafenvoy.mxt.screen.aui.AuiStyles;
import com.iafenvoy.saturn.data.IconReference;
import com.iafenvoy.saturn.util.TooltipText;
import com.sighs.apricityui.element.Item;
import com.sighs.apricityui.init.Element;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

public final class SpiritCraftingAuraRow {
    private static final int BAR_WIDTH = 105;
    private final Element root, name, amount, fill, texture;
    private final Item item;
    @Nullable
    private Optional<IconReference> icon;
    private List<Component> tooltip = List.of();

    public SpiritCraftingAuraRow(Element root) {
        this.root = root;
        this.name = part(root, ".name");
        this.amount = part(root, ".amount");
        this.fill = part(root, ".bar .fill");
        if (!(part(root, ".aura-item") instanceof Item item)) throw new NoSuchElementException("aura_row item");
        this.item = item;
        this.texture = part(root, ".aura-texture");
    }

    private static Element part(Element root, String selector) {
        Element found = root.querySelector(selector);
        if (found == null) throw new NoSuchElementException("aura_row " + selector);
        return found;
    }

    public void show(int top, AuraDisplay display, double amount, double required) {
        String progress = TooltipText.number(Math.min(amount, required)) + " / " + TooltipText.number(required);
        int width = display.icon().isPresent() ? BAR_WIDTH - 20 : BAR_WIDTH;
        int filled = AuiStyles.progressWidth(width, required <= 0 ? 0 : amount / required);
        AuiElements.style(this.root, "top", top + "px");
        AuiElements.style(this.root, "display", "block");
        AuiElements.setText(this.name, display.name().getString());
        AuiElements.style(this.name, "color", AuiStyles.textColor(this.name, display.color()));
        AuiElements.setText(this.amount, progress);
        AuiElements.style(this.fill, "background-color", AuiStyles.hex(display.color()));
        AuiElements.style(this.fill, "width", filled + "px");
        this.tooltip = display.tooltip(Component.literal(progress));
        if (display.icon().equals(this.icon)) return;
        this.icon = display.icon();
        ItemStack stack = this.icon.flatMap(IconReference::stack).orElse(ItemStack.EMPTY);
        if (stack.isEmpty()) this.item.clearDrivenState(Item.Source.INGREDIENT);
        else this.item.setIngredientStack(stack);
        this.icon.flatMap(IconReference::texture).ifPresentOrElse(
                source -> this.texture.setAttribute("src", source.toString()), () -> this.texture.removeAttribute("src"));
        AuiElements.setClass(this.root, "has-icon", this.icon.isPresent());
        AuiElements.setClass(this.root, "icon-item", !stack.isEmpty());
        AuiElements.setClass(this.root, "icon-texture", this.icon.flatMap(IconReference::texture).isPresent());
    }

    public List<Component> tooltip() {
        return this.tooltip;
    }

    public void hide() {
        this.tooltip = List.of();
        AuiElements.style(this.root, "display", "none");
    }
}
