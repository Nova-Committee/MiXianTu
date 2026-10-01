package com.iafenvoy.mxt.runtime.talisman;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.config.MxtServerConfig;
import com.iafenvoy.mxt.registry.MxtDataComponents;
import com.iafenvoy.mxt.registry.MxtItems;
import com.iafenvoy.mxt.runtime.talisman.TalismanDrawingScorer.Point;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * The brush's own store of pigment: which items feed it, how much it holds, how a dip adds to it and how much one
 * stroke takes out.
 *
 * <p>Which items count is one item tag, not a recipe: every item in {@code #mxt:brush_pigment} is one portion, and
 * what a portion is worth is a server setting. Both the brush's dipping hooks and the workstation's pigment slot
 * read only this class.
 *
 * <p>Pigment is the tool's state, not a price a payer owns, so it never goes through {@code Cost} - the same
 * exception the aura store of a talisman carrier already is. Everything here reads the server config, and every
 * amount a client sends is ignored: only the lengths the server measures are charged.
 */
public final class BrushPigmentService {
    /**
     * Every item a brush can be fed, one portion per item; the station's pigment slot accepts exactly these.
     */
    public static final TagKey<Item> PIGMENT = TagKey.create(Registries.ITEM,
            Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "brush_pigment"));

    private BrushPigmentService() {
    }

    public static boolean isBrush(ItemStack stack) {
        return stack.is(MxtItems.TALISMAN_BRUSH.get());
    }

    public static boolean isPigment(ItemStack stack) {
        return stack.is(PIGMENT);
    }

    public static int pigment(ItemStack stack) {
        return stack.getOrDefault(MxtDataComponents.BRUSH_PIGMENT, 0);
    }

    /**
     * Whether this stack is a brush that still has pigment in it; the formula list and every stroke ask this.
     */
    public static boolean isReady(ItemStack stack) {
        return isBrush(stack) && pigment(stack) > 0;
    }

    public static int capacity() {
        return MxtServerConfig.INSTANCE.talisman.brushCapacity.getValue();
    }

    /**
     * What one item of pigment is worth; the dip is the only reader.
     */
    public static int perPortion() {
        return MxtServerConfig.INSTANCE.talisman.pigmentPerItem.getValue();
    }

    /**
     * The whole portions this brush still has room for, so a dip never takes more than it can hold.
     */
    public static int portionsThatFit(ItemStack brush) {
        int per = perPortion();
        return per <= 0 ? 0 : Math.max(0, (capacity() - pigment(brush)) / per);
    }

    public static boolean isFull(ItemStack brush) {
        return portionsThatFit(brush) <= 0;
    }

    /**
     * Adds as many of {@code portions} as still fit, and answers how much pigment that was.
     */
    public static int dip(ItemStack brush, int portions) {
        int fitting = Math.min(portions, portionsThatFit(brush));
        if (fitting <= 0) return 0;
        int added = fitting * perPortion();
        brush.set(MxtDataComponents.BRUSH_PIGMENT, pigment(brush) + added);
        return added;
    }

    /**
     * Feeds one portion from the slot into the brush, which is one item: the click is the vanilla bundle's, but a
     * brush takes a portion at a time rather than whatever fits. Zero means the brush had no room for it.
     */
    public static int dipFromSlot(ItemStack brush, Slot slot, Player player) {
        if (portionsThatFit(brush) <= 0) return 0;
        ItemStack taken = slot.safeTake(1, 1, player);
        return taken.isEmpty() ? 0 : dip(brush, 1);
    }

    /**
     * The same, for a brush that sits in a slot and is fed from the stack on the cursor.
     */
    public static int dipFromCarried(ItemStack brush, ItemStack carried) {
        if (carried.isEmpty() || portionsThatFit(brush) <= 0) return 0;
        carried.shrink(1);
        return dip(brush, 1);
    }

    public static boolean hasPigment(ItemStack brush, int required) {
        return required <= 0 || pigment(brush) >= required;
    }

    /**
     * What one stroke of this measured length costs, once rate, floor and cap have had their say.
     */
    public static int chargeFor(double length) {
        MxtServerConfig.Talisman settings = MxtServerConfig.INSTANCE.talisman;
        double rate = settings.pigmentRate.getValue();
        double raw = Double.isFinite(length) && length > 0.0D ? length * rate : 0.0D;
        int charge = raw >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) Math.ceil(raw);
        charge = Math.max(charge, settings.pigmentFloor.getValue());
        int cap = settings.pigmentCap.getValue();
        return cap > 0 ? Math.min(charge, cap) : charge;
    }

    public static int chargeForStroke(List<Point> points) {
        return chargeFor(length(points));
    }

    public static double length(List<Point> points) {
        double total = 0.0D;
        for (int index = 1; index < points.size(); index++) {
            Point previous = points.get(index - 1), current = points.get(index);
            total += Math.hypot(current.x() - previous.x(), current.y() - previous.y());
        }
        return total;
    }
}
