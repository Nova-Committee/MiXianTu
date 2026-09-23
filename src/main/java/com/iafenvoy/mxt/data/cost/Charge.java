package com.iafenvoy.mxt.data.cost;

import com.iafenvoy.mxt.data.aura.Aura;
import com.iafenvoy.mxt.data.cost.builtin.JsCost;
import com.iafenvoy.mxt.util.matcher.ItemMatcher;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;

import java.util.Map;

/**
 * What one cost entry takes, on the channel its context offers. Amounts are already evaluated: the transaction
 * that receives a charge neither re-evaluates a formula nor second-guesses whether the entry was well formed, so
 * checking and spending can never disagree about what is owed.
 */
public sealed interface Charge {
    /** A resource amount, taken from a payer's resource attachment. */
    record Resources(Map<Identifier, Double> amounts) implements Charge {
    }

    /** Whole-aura amounts, taken from the shared pool at the context position or from the context's bank. */
    record Auras(Map<Holder<Aura>, Double> amounts) implements Charge {
    }

    /** Matching items, taken from a player's inventory. */
    record Items(ItemMatcher matcher, int count) implements Charge {
    }

    /** A server script that answers both halves itself. */
    record Script(JsCost cost) implements Charge {
    }
}
