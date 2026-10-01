package com.iafenvoy.mxt.runtime.talisman;

import com.iafenvoy.mxt.data.cost.Cost;
import com.iafenvoy.mxt.data.cost.context.CostContext;
import com.iafenvoy.mxt.recipe.TalismanDrawingRecipe;
import com.iafenvoy.mxt.runtime.talisman.TalismanDrawingScorer.Point;
import net.minecraft.resources.Identifier;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * One player's drawing at one workstation: which formula, what has been paid for it, what has been drawn and what
 * has been spent on the brush.
 *
 * <p>The state lives on the menu, not on the block entity, so two players at the same station never share a
 * drawing. The paper and the extras are taken out of the station slot when the session opens and are settled when
 * it leaves; what was actually taken is recorded then and is the whole refund, which is why nothing else may write
 * those resources while a session is open.
 */
public final class TalismanDrawingSession {
    private final Identifier recipeId;
    private final TalismanDrawingRecipe recipe;
    private final List<Cost> costs;
    private final CostContext costContext;
    private final Container paperSlot;
    private final List<ItemStack> chargedItems;
    private final List<Runnable> refunds;
    private final List<List<Point>> strokes = new ArrayList<>();
    private final long openedAt;
    private long lastStrokeAt = Long.MIN_VALUE;
    private int rejected;
    private int pigmentSpent;
    private boolean failed;
    private boolean settled;

    TalismanDrawingSession(Identifier recipeId, TalismanDrawingRecipe recipe, List<Cost> costs,
                           CostContext costContext, Container paperSlot, List<ItemStack> chargedItems,
                           List<Runnable> refunds, long openedAt) {
        this.recipeId = recipeId;
        this.recipe = recipe;
        this.costs = List.copyOf(costs);
        this.costContext = costContext;
        this.paperSlot = paperSlot;
        this.chargedItems = List.copyOf(chargedItems);
        this.refunds = List.copyOf(refunds);
        this.openedAt = openedAt;
    }

    public Identifier recipeId() {
        return this.recipeId;
    }

    public TalismanDrawingRecipe recipe() {
        return this.recipe;
    }

    public List<Cost> costs() {
        return this.costs;
    }

    public CostContext costContext() {
        return this.costContext;
    }

    public Container paperSlot() {
        return this.paperSlot;
    }

    /**
     * What opening this session took out of the item store, slot by slot; this is the whole refund.
     */
    public List<ItemStack> chargedItems() {
        return this.chargedItems;
    }

    public List<Runnable> refunds() {
        return this.refunds;
    }

    public List<List<Point>> strokes() {
        return this.strokes;
    }

    public int strokeCount() {
        return this.strokes.size();
    }

    public int pointCount() {
        int total = 0;
        for (List<Point> stroke : this.strokes) total += stroke.size();
        return total;
    }

    public boolean drewAnything() {
        return !this.strokes.isEmpty();
    }

    /**
     * The tick the last stroke arrived, or the tick the drawing opened when nothing has been drawn yet.
     */
    public long lastStrokeAt() {
        return this.lastStrokeAt == Long.MIN_VALUE ? this.openedAt : this.lastStrokeAt;
    }

    public void record(List<Point> stroke, long gameTime) {
        this.strokes.add(List.copyOf(stroke));
        this.lastStrokeAt = gameTime;
    }

    public int rejected() {
        return this.rejected;
    }

    public void reject() {
        this.rejected++;
    }

    public int pigmentSpent() {
        return this.pigmentSpent;
    }

    public void spendPigment(int amount) {
        this.pigmentSpent += Math.max(0, amount);
    }

    public boolean failed() {
        return this.failed;
    }

    public void fail() {
        this.failed = true;
    }

    public boolean settled() {
        return this.settled;
    }

    /**
     * Answers true the first time only, so a session can never be settled twice.
     */
    public boolean markSettled() {
        if (this.settled) return false;
        this.settled = true;
        return true;
    }
}
