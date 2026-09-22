package com.iafenvoy.mxt.compat.kubejs.binding;

import com.iafenvoy.mxt.compat.kubejs.MxtKubeJsApi;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import dev.latvian.mods.kubejs.typings.Info;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;

import java.util.List;

/**
 * Runtime curse operations exposed as {@code MxtCurses}. A curse is kept alive by its sources, the way an ability
 * grant is: applying adds one, releasing drops one, and the curse only leaves once no source holds it.
 */
public final class MxtKubeJsCurseBindings {
    @Info("Applies a curse through the standard transactional service, under the given source.")
    public Object apply(Entity entity, String curse, int stacks, String source) {
        checkStacks(stacks);
        return MxtKubeJsApi.applyCurse(entity, id(curse), stacks, sourceId(source), FormulaContext.of(entity));
    }

    @Info("Applies a curse with a duration the definition may shorten but never be outlasted by.")
    public Object applyFor(Entity entity, String curse, int stacks, String source, long durationTicks) {
        checkStacks(stacks);
        return MxtKubeJsApi.applyCurseFor(entity, id(curse), stacks, sourceId(source), durationTicks, FormulaContext.of(entity));
    }

    @Info("Removes a curse outright, whichever sources held it, using the explicit-removal path.")
    public boolean remove(Entity entity, String curse) {
        return MxtKubeJsApi.removeCurse(entity, id(curse));
    }

    @Info("Lets go of one source's claim; the curse only leaves when no other source holds it.")
    public boolean release(Entity entity, String curse, String source) {
        return MxtKubeJsApi.releaseCurse(entity, id(curse), sourceId(source));
    }

    @Info("Whether the entity currently holds that curse.")
    public boolean has(Entity entity, String curse) {
        return MxtKubeJsApi.hasCurse(entity, id(curse));
    }

    @Info("How many stacks of that curse the entity holds, or 0.")
    public int stacks(Entity entity, String curse) {
        return MxtKubeJsApi.curseStacks(entity, id(curse));
    }

    @Info("Ticks left on that curse, -1 when it never expires, and 0 when the entity does not hold it.")
    public long remainingTicks(Entity entity, String curse) {
        return MxtKubeJsApi.curseRemainingTicks(entity, id(curse));
    }

    @Info("Which sources are keeping that curse alive right now.")
    public List<String> sources(Entity entity, String curse) {
        return MxtKubeJsApi.curseSources(entity, id(curse)).stream().map(Identifier::toString).sorted().toList();
    }

    private static void checkStacks(int stacks) {
        if (stacks < 1) throw new IllegalArgumentException("Curse stacks must be positive");
    }

    private static Identifier id(String raw) {
        Identifier id = Identifier.tryParse(raw);
        if (id == null) throw new IllegalArgumentException("Invalid MXT identifier: " + raw);
        return id;
    }

    // Sources are identifiers now that abilities and curses share the ledger, so a script names itself like any
    // built-in source.
    private static Identifier sourceId(String raw) {
        Identifier id = raw == null ? null : Identifier.tryParse(raw);
        if (id == null) throw new IllegalArgumentException("Invalid curse source identifier: " + raw
                + " (use a namespaced id such as example:quest_reward)");
        return id;
    }
}
