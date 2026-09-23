package com.iafenvoy.mxt.data.trigger.builtin;

import com.iafenvoy.mxt.data.trigger.TriggerContext;
import net.minecraft.advancements.criterion.ContextAwarePredicate;
import net.minecraft.advancements.criterion.EntityPredicate;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.storage.loot.LootContext;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Rebuilds the arguments a ported vanilla trigger needs: the player vanilla would have called, and the loot
 * context vanilla builds for the entity being described.
 */
public final class VanillaTriggerSupport {
    private VanillaTriggerSupport() {
    }

    // Vanilla's criteria are player-only, so a matcher that needs a context simply does not match without one.
    public static @Nullable ServerPlayer player(TriggerContext context) {
        return context.actor() instanceof ServerPlayer player ? player : null;
    }

    // The context vanilla matches a criterion's own player predicate against: the player describing themselves.
    public static @Nullable LootContext own(TriggerContext context) {
        ServerPlayer player = player(context);
        return player == null ? null : EntityPredicate.createContext(player, player);
    }

    // A missing entity stays missing instead of falling back to the player: vanilla passes null where its own
    // payload has no entity.
    public static @Nullable LootContext forEntity(TriggerContext context, @Nullable Entity entity) {
        ServerPlayer player = player(context);
        return player == null || entity == null ? null : EntityPredicate.createContext(player, entity);
    }

    public static List<LootContext> contexts(TriggerContext context, Iterable<? extends Entity> entities) {
        List<LootContext> contexts = new ArrayList<>();
        for (Entity entity : entities) {
            LootContext wrapped = forEntity(context, entity);
            if (wrapped != null) contexts.add(wrapped);
        }
        return contexts;
    }

    // Reimplements the check SimpleCriterionTrigger performs on every listener before running it.
    public static boolean playerPredicate(Optional<ContextAwarePredicate> predicate, TriggerContext context) {
        if (predicate.isEmpty()) return true;
        LootContext own = own(context);
        return own != null && predicate.get().matches(own);
    }

    // Non-finite payloads read as 0, the integer a vanilla condition compares against.
    public static int intValue(TriggerContext context, String name) {
        double value = context.formula().explicit(name);
        return Double.isFinite(value) ? (int) value : 0;
    }

    // Publishers encode a boolean as a number, so anything above zero is true.
    public static boolean flag(TriggerContext context, String name) {
        return context.formula().explicit(name) > 0.0D;
    }
}
