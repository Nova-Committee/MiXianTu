package com.iafenvoy.mxt.data.trigger;

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
 * The two things a ported vanilla trigger needs that a signal does not carry: the player vanilla would have
 * called with, and the loot context vanilla builds for the entity being described.
 *
 * <p>Vanilla's criterion instances describe entities with loot predicates, so rebuilding their arguments means
 * rebuilding those contexts. {@code EntityPredicate.createContext} only asks for a {@link ServerPlayer}
 * because it borrows the player's level and position - every ported signal is published for a player exactly
 * when vanilla would have, so that is never a restriction.</p>
 */
public final class VanillaTriggerSupport {
    private VanillaTriggerSupport() {
    }

    /**
     * The acting player, or {@code null} when this signal reached something else. Vanilla's criteria are
     * player-only, so a ported matcher that needs a context simply does not match without one.
     */
    public static @Nullable ServerPlayer player(TriggerContext context) {
        return context.actor() instanceof ServerPlayer player ? player : null;
    }

    /**
     * The context vanilla matches a criterion's own {@code player} predicate against: the player describing
     * themselves.
     */
    public static @Nullable LootContext own(TriggerContext context) {
        ServerPlayer player = player(context);
        return player == null ? null : EntityPredicate.createContext(player, player);
    }

    /**
     * The context vanilla would have built for one entity. A missing entity stays missing instead of falling
     * back to the player, because vanilla passes {@code null} where its own payload has no entity.
     */
    public static @Nullable LootContext forEntity(TriggerContext context, @Nullable Entity entity) {
        ServerPlayer player = player(context);
        return player == null || entity == null ? null : EntityPredicate.createContext(player, entity);
    }

    /**
     * Maps entities to the contexts vanilla passes as a list.
     */
    public static List<LootContext> contexts(TriggerContext context, Iterable<? extends Entity> entities) {
        List<LootContext> contexts = new ArrayList<>();
        for (Entity entity : entities) {
            LootContext wrapped = forEntity(context, entity);
            if (wrapped != null) contexts.add(wrapped);
        }
        return contexts;
    }

    /**
     * Reimplements the check {@code SimpleCriterionTrigger} performs on every listener before running it, for
     * the instances that carry nothing but a {@code player} predicate.
     */
    public static boolean playerPredicate(Optional<ContextAwarePredicate> predicate, TriggerContext context) {
        if (predicate.isEmpty()) return true;
        LootContext own = own(context);
        return own != null && predicate.get().matches(own);
    }

    /**
     * A numeric payload, as the integer a vanilla condition compares against.
     */
    public static int intValue(TriggerContext context, String name) {
        double value = context.formula().explicit(name);
        return Double.isFinite(value) ? (int) value : 0;
    }

    /**
     * A numeric payload used as a flag, matching how publishers encode a boolean.
     */
    public static boolean flag(TriggerContext context, String name) {
        return context.formula().explicit(name) > 0.0D;
    }
}
