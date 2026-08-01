package com.iafenvoy.mxt.runtime.cultivation;

import com.iafenvoy.mxt.attachment.SpiritData;
import com.iafenvoy.mxt.data.title.TitleDefinition;
import com.iafenvoy.mxt.registry.MxtTypeRegistries;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

/**
 * Server-side title ownership and exclusive-tag policy; attribute application is handled by passive reconciliation.
 */
public final class TitleService {
    private TitleService() {
    }

    public static Result grant(SpiritData spirit, Identifier id, TitleDefinition definition,
                               Function<Identifier, TitleDefinition> lookup, BooleanSupplier unlockConditions) {
        if (spirit.titles().contains(id)) return Result.rejected(Failure.ALREADY_GRANTED);
        if (!unlockConditions.getAsBoolean()) return Result.rejected(Failure.CONDITIONS);
        HashSet<Identifier> exclusive = new HashSet<>(definition.exclusiveTags());
        boolean conflict = spirit.titles().stream().map(lookup).filter(Objects::nonNull)
                .anyMatch(existing -> existing.exclusiveTags().stream().anyMatch(exclusive::contains));
        if (conflict) return Result.rejected(Failure.EXCLUSIVE_CONFLICT);
        ArrayList<Identifier> titles = new ArrayList<>(spirit.titles());
        titles.add(id);
        spirit.setTitles(titles);
        return Result.changedResult();
    }

    /**
     * Evaluates every Java-owned condition declared by the title before changing ownership.
     */
    public static Result grant(LivingEntity entity, SpiritData spirit, Identifier id, TitleDefinition definition,
                               Function<Identifier, TitleDefinition> lookup, FormulaContext context) {
        boolean allowed = definition.unlockConditions().stream().allMatch(condition -> MxtTypeRegistries.CULTIVATION_CONDITION.get(condition)
                .map(reference -> reference.value().test(entity, context)).orElse(false));
        return allowed ? grant(spirit, id, definition, lookup, () -> true) : Result.rejected(Failure.CONDITIONS);
    }

    public static boolean revoke(SpiritData spirit, Identifier id) {
        ArrayList<Identifier> titles = new ArrayList<>(spirit.titles());
        if (!titles.remove(id)) return false;
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
