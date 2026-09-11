package com.iafenvoy.mxt.runtime.forging;

import com.iafenvoy.mxt.data.forging.ForgingBlueprint;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

/**
 * What a cancelled forging session does with the materials it locked away.
 *
 * <p>Cancellation is the one settlement a player chooses without having satisfied the blueprint, so what
 * it costs is a balance decision rather than a rule - and balance decisions are what content is expected
 * to want a say in. Today there is exactly one answer: everything the session took goes back.</p>
 *
 * <p>The variants worth planning for - a partial loss, a cancellation fee, an unbreakable blueprint that
 * refuses to cancel - all differ only in what becomes of {@link ForgingTableState#consumed()}, which is
 * the exact stacks the session removed and the one thing a policy cannot reconstruct for itself. So this
 * is one method, and it is handed everything a variant needs: who cancelled, the surface the materials
 * came from, the session state holding them, and the blueprint they belong to.</p>
 *
 * <p>Anything that changes <em>whether</em> a cancellation may happen at all is a different question, and
 * already has an answer: {@code ForgingEvent.Cancel} can refuse it outright, and the session is left
 * running.</p>
 */
@FunctionalInterface
public interface ForgingCancellation {
    /**
     * @param player    who asked to cancel, and who any spilled overflow is given to
     * @param surface   the table; its container is where the materials came from
     * @param state     the session being cancelled; {@link ForgingTableState#consumed()} is what it took
     * @param blueprint the session's blueprint resolved live, or null if the datapack no longer has it -
     *                  for policies that want to be per-blueprint rather than global
     */
    void settle(ServerPlayer player, ForgingSurface surface, ForgingTableState state, @Nullable ForgingBlueprint blueprint);
}
