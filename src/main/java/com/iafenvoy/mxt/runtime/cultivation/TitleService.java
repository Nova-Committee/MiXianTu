package com.iafenvoy.mxt.runtime.cultivation;
import com.iafenvoy.mxt.registry.MxtResourceKeys;

import com.iafenvoy.mxt.attachment.SpiritData;
import com.iafenvoy.mxt.data.Title;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import net.minecraft.resources.Identifier;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.LivingEntity;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

/**
 * Server-side title ownership and exclusive-tag policy; attribute application is handled by passive reconciliation.
 */
public final class TitleService {
    private TitleService() {
    }

    public static Result grant(SpiritData spirit, Identifier id, Title definition,
                               Function<Identifier, Title> lookup, BooleanSupplier unlockConditions) {
        Holder<Title> title = MxtDatapackRegistries.holder(MxtResourceKeys.TITLE, id).orElse(null);
        if (title == null) return Result.rejected(Failure.DISABLED);
        if (spirit.titles().contains(title)) return Result.rejected(Failure.ALREADY_GRANTED);
        if (!unlockConditions.getAsBoolean()) return Result.rejected(Failure.CONDITIONS);
        HashSet<Identifier> exclusive = new HashSet<>(definition.exclusiveTags());
        boolean conflict = spirit.titles().stream().map(Holder::value)
                .anyMatch(existing -> existing.exclusiveTags().stream().anyMatch(exclusive::contains));
        if (conflict) return Result.rejected(Failure.EXCLUSIVE_CONFLICT);
        List<Holder<Title>> titles = new LinkedList<>(spirit.titles());
        titles.add(title);
        spirit.setTitles(titles);
        return Result.changedResult();
    }

    /**
     * Evaluates every Java-owned condition declared by the title before changing ownership.
     */
    public static Result grant(LivingEntity entity, SpiritData spirit, Identifier id, Title definition,
                               Function<Identifier, Title> lookup, FormulaContext context) {
        boolean allowed = definition.unlockCondition().test(entity, context);
        return allowed ? grant(spirit, id, definition, lookup, () -> true) : Result.rejected(Failure.CONDITIONS);
    }

    public static boolean revoke(SpiritData spirit, Identifier id) {
        Holder<Title> title = MxtDatapackRegistries.holder(MxtResourceKeys.TITLE, id).orElse(null);
        if (title == null) return false;
        List<Holder<Title>> titles = new LinkedList<>(spirit.titles());
        if (!titles.remove(title)) return false;
        spirit.setTitles(titles);
        return true;
    }

    public enum Failure {DISABLED, ALREADY_GRANTED, CONDITIONS, EXCLUSIVE_CONFLICT}

    public record Result(boolean changed, Failure failure) {
        private static Result changedResult() {
            return new Result(true, null);
        }

        private static Result rejected(Failure failure) {
            return new Result(false, failure);
        }
    }
}
