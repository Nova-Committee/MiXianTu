package com.iafenvoy.mxt.runtime.cultivation;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.attachment.SpiritStatsAttachment;
import com.iafenvoy.mxt.config.MxtServerConfig;
import com.iafenvoy.mxt.config.MxtServerConfig.LifespanOutcome;
import com.iafenvoy.mxt.event.LifeSpanEndEvent.Post;
import com.iafenvoy.mxt.event.LifeSpanEndEvent.Pre;
import com.iafenvoy.mxt.event.LifeSpanRebirthEvent;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.runtime.ability.PassiveAttributeService;
import com.iafenvoy.mxt.runtime.damage.DamageCalculationService;
import com.iafenvoy.mxt.runtime.tribulation.TribulationService;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.NeoForge;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * The lifespan ledger: two numbers per body, spent by settlement and answered by one of three outcomes. Data
 * packs own the numbers ({@code realm_stage.lifespan}, {@code mxt:modify_lifespan}) and the server config owns
 * the rules, so no value in here is a world-view number.
 * <p>
 * The writes never kill on their own: expiry is judged only by {@link #settle(Entity)}, which is the single exit,
 * so a transaction or an ability can hand out or take life without ending a body halfway through.
 */
public final class LifeSpanService {
    // The ledger's "never opened" value, on both numbers.
    public static final long UNACCOUNTED = -1L;
    private static final Identifier LIFESPAN_DAMAGE = Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "lifespan");
    private static final Identifier FALLBACK_DAMAGE = Identifier.withDefaultNamespace("generic_kill");

    private LifeSpanService() {
    }

    // Read-only and without creating the attachment: asking a chicken how long it may live must not give it a
    // ledger, and one is asked for every non-passenger entity on the tick hook.
    public static long remaining(Entity entity) {
        SpiritStatsAttachment stats = existing(entity);
        return stats == null ? UNACCOUNTED : stats.lifespanRemaining();
    }

    public static long total(Entity entity) {
        SpiritStatsAttachment stats = existing(entity);
        return stats == null ? UNACCOUNTED : stats.lifespanTotal();
    }

    /**
     * Opens or rewrites the ledger: both numbers become {@code ticks}. Written even while the lifespan system is
     * switched off, because that switch decides whether time passes, not whether the numbers exist.
     */
    public static Result set(LivingEntity entity, long ticks) {
        if (entity.level().isClientSide()) return Result.refused(Failure.SERVER_ONLY);
        if (ticks < 0L) return Result.refused(Failure.INVALID_VALUE);
        SpiritStatsAttachment stats = entity.getData(MxtAttachments.SPIRIT_STATS);
        long before = stats.lifespanRemaining();
        stats.setLifespan(ticks, ticks);
        warnIfCrossed(entity, stats, before);
        return Result.written();
    }

    /**
     * A non-negative {@code ticks} extends the life (both numbers grow); a negative one takes life away and only
     * lowers what is left. A body nobody ever granted life to is measured from the configured base first.
     */
    public static Result add(LivingEntity entity, long ticks) {
        if (entity.level().isClientSide()) return Result.refused(Failure.SERVER_ONLY);
        // Adding nothing is no reason to open an account. A stage whose formula rounds to zero, or an admin
        // typing {@code add 0}, would otherwise leave the body accounted for at zero - which the very next
        // settlement reads as a spent life and executes.
        if (ticks == 0L) return Result.written();
        SpiritStatsAttachment stats = entity.getData(MxtAttachments.SPIRIT_STATS);
        long before = stats.lifespanRemaining();
        if (stats.lifespanTotal() < 0L) {
            long base = Math.max(0L, MxtServerConfig.INSTANCE.lifespan.baseLifespan.getValue());
            stats.setLifespan(base, base);
        }
        long remaining = stats.lifespanRemaining();
        long total = stats.lifespanTotal();
        long nextRemaining = saturatedAdd(remaining, ticks);
        // Taking life away only lowers what is left: the ceiling is this life's own record of what it was given.
        stats.setLifespan(ticks < 0L ? Math.max(0L, nextRemaining) : nextRemaining,
                ticks < 0L ? total : saturatedAdd(total, ticks));
        warnIfCrossed(entity, stats, before);
        return Result.written();
    }

    // Whether a value was written. Switched off, or already accounted for, means nothing to seed.
    public static boolean seed(LivingEntity entity) {
        if (entity.level().isClientSide()) return false;
        if (!MxtServerConfig.INSTANCE.lifespan.enabled.getValue()) return false;
        long base = MxtServerConfig.INSTANCE.lifespan.baseLifespan.getValue();
        if (base <= 0L) return false;
        SpiritStatsAttachment stats = entity.getData(MxtAttachments.SPIRIT_STATS);
        if (stats.lifespanTotal() >= 0L) return false;
        stats.setLifespan(base, base);
        return true;
    }

    /**
     * One settlement: spends {@code age_per_settle} of the remaining life and, if that reaches zero, runs the
     * expiry. Answers whether a ledger was settled at all, so a probe can tell "not applicable" from "no change".
     */
    public static boolean settle(Entity entity) {
        if (entity.level().isClientSide()) return false;
        if (!MxtServerConfig.INSTANCE.lifespan.enabled.getValue()) return false;
        if (!(entity instanceof LivingEntity living)) return false;
        // A body that is already dead is only playing its death animation: it must not age, and it must not be
        // handed an expiry verdict while it lies there.
        if (!living.isAlive()) return false;
        // Creative and spectator bodies are outside the survival rules - and spectating is what expiry itself
        // leaves a player as, which is also what keeps one death from being replayed every settlement.
        if (entity instanceof ServerPlayer player && (player.isCreative() || player.isSpectator())) return false;
        SpiritStatsAttachment stats = existing(entity);
        if (stats == null) return false;
        if (stats.lifespanTotal() < 0L && !seed(living)) return false;
        long before = stats.lifespanRemaining();
        if (before < 0L) return false;
        int age = MxtServerConfig.INSTANCE.lifespan.agePerSettle.getValue();
        if (age > 0) stats.setLifespan(Math.max(0L, before - age), stats.lifespanTotal());
        warnIfCrossed(entity, stats, before);
        if (stats.lifespanRemaining() > 0L) return true;
        expire(living, stats);
        return true;
    }

    /**
     * Rebirth on demand, for the command, a script, a data pack action or a Java addon with a trigger of its own;
     * expiry reaches the same reset through its own branch. It runs while the system is switched off too, and the
     * reset happens even when {@code kills} cannot kill. Answers {@link Result} so a caller can tell a refusal.
     */
    public static Result reincarnate(LivingEntity entity) {
        if (entity.level().isClientSide()) return Result.refused(Failure.SERVER_ONLY);
        SpiritStatsAttachment stats = entity.getData(MxtAttachments.SPIRIT_STATS);
        // Nothing has happened yet, so a listener saying no leaves the body untouched - unlike expiry, where the
        // life is already spent and a refusal can only mean closing the account.
        if (NeoForge.EVENT_BUS.post(new LifeSpanRebirthEvent.Pre(entity, stats)).isCanceled())
            return Result.refused(Failure.CANCELLED);
        reincarnate(entity, stats);
        // A body loses everything it was and cannot see it coming, so it is told. Expiry has its own line, because
        // only there did a lifespan actually run out.
        if (entity instanceof ServerPlayer player)
            player.sendSystemMessage(Component.translatable("message.mxt.lifespan.remade"));
        NeoForge.EVENT_BUS.post(new LifeSpanRebirthEvent.Post(entity, stats));
        return Result.written();
    }

    // The one place the expiry state machine lives.
    private static void expire(LivingEntity entity, SpiritStatsAttachment stats) {
        if (NeoForge.EVENT_BUS.post(new Pre(entity, stats)).isCanceled()) {
            // A listener that wrote a positive number has bought this body more time; one that wrote nothing has
            // simply refused the end, which closes the account rather than retrying it forever.
            if (stats.lifespanRemaining() > 0L) return;
            closeAccount(stats);
            return;
        }
        LifespanOutcome outcome = MxtServerConfig.INSTANCE.lifespan.onExpire.getValue();
        switch (outcome) {
            case NONE -> closeAccount(stats);
            case DEATH -> death(entity, stats);
            case REINCARNATE -> {
                reincarnate(entity, stats);
                // A rebirth wipes a player's whole cultivation, so it must never happen in silence. The explicit
                // entry points say their own line instead: there, no lifespan ran out.
                if (entity instanceof ServerPlayer player)
                    player.sendSystemMessage(Component.translatable("message.mxt.lifespan.reincarnated"));
            }
        }
        NeoForge.EVENT_BUS.post(new Post(entity, stats, outcome));
    }

    // A player watches on: no death event, no drops, no respawn point. The ledger keeps its zero, so releasing
    // them without granting life ends the next life at the next settlement - the account is spent, not unlimited.
    private static void death(LivingEntity entity, SpiritStatsAttachment stats) {
        if (entity instanceof ServerPlayer player) {
            player.setGameMode(GameType.SPECTATOR);
            player.sendSystemMessage(Component.translatable("message.mxt.lifespan.deceased"));
            return;
        }
        kill(entity);
        if (!entity.isAlive()) return;
        // Invulnerability or another mod refused the death; closing the account is what stops the retry.
        closeAccount(stats);
        MiXianTu.LOGGER.warn("Lifespan expiry could not kill {}; its lifespan account was closed instead",
                entity.getDisplayName().getString());
    }

    // Through the damage pipeline, so totems, damage events and other mods' protection all still answer.
    private static void kill(LivingEntity entity) {
        if (!entity.isAlive()) return;
        DamageCalculationService.deal(null, entity, Float.MAX_VALUE, damageType(entity.level()), null);
    }

    private static void closeAccount(SpiritStatsAttachment stats) {
        stats.setLifespan(UNACCOUNTED, 0L);
    }

    // The death cause is data, and a pack that deletes it must not turn expiry into immortality.
    private static Optional<Holder<DamageType>> damageType(Level level) {
        Registry<DamageType> registry = level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE);
        Holder<DamageType> lifespan = registry.get(ResourceKey.create(Registries.DAMAGE_TYPE, LIFESPAN_DAMAGE)).orElse(null);
        if (lifespan != null) return Optional.of(lifespan);
        MiXianTu.LOGGER.warn("Damage type {} is missing; lifespan expiry falls back to {}",
                LIFESPAN_DAMAGE, FALLBACK_DAMAGE);
        Holder<DamageType> fallback = registry.get(ResourceKey.create(Registries.DAMAGE_TYPE, FALLBACK_DAMAGE)).orElse(null);
        return Optional.ofNullable(fallback);
    }

    // The reset list, one switch per line; the defaults are what a plain rebirth keeps. Grants and passive
    // attributes are reconciled last, because they answer what the body still is rather than what it was.
    private static void reincarnate(LivingEntity entity, SpiritStatsAttachment stats) {
        MxtServerConfig.Reincarnation settings = MxtServerConfig.INSTANCE.reincarnation;
        if (settings.kills.getValue()) kill(entity);
        if (settings.resetCultivation.getValue()) entity.getData(MxtAttachments.CULTIVATION).resetCultivation();
        if (settings.clearMinorStages.getValue()) entity.getData(MxtAttachments.SPIRIT_IDENTITY).clearMinorStageRecords();
        if (settings.cancelTribulation.getValue()) TribulationService.cancel(entity.getData(MxtAttachments.TRIBULATION));
        if (settings.clearResources.getValue()) entity.getData(MxtAttachments.RESOURCE_HOLDER).clear();
        if (!settings.keepSpiritRoots.getValue()) {
            entity.getData(MxtAttachments.SPIRIT_IDENTITY).setSpiritRoots(List.of());
            entity.getData(MxtAttachments.SPIRIT_IDENTITY).setPhysiques(List.of());
        }
        if (!settings.keepTechniques.getValue()) {
            entity.getData(MxtAttachments.SPIRIT_IDENTITY).setLearnedTechniques(List.of());
            entity.getData(MxtAttachments.SPIRIT_IDENTITY).setTechniqueStages(Map.of());
        }
        if (!settings.keepSoul.getValue()) stats.resetSoul();
        CultivationGrantService.recalculate(entity, entity.getData(MxtAttachments.SPIRIT_IDENTITY),
                entity.getData(MxtAttachments.ABILITY_HOLDER));
        PassiveAttributeService.reconcile(entity);
        long base = MxtServerConfig.INSTANCE.lifespan.baseLifespan.getValue();
        if (base > 0L) stats.setLifespan(base, base);
        else closeAccount(stats);
    }

    // A crossing, not a state: buying life back and losing it again warns again, which is the wanted reading, and
    // a single large loss still warns because it crossed on the way down.
    private static void warnIfCrossed(Entity entity, SpiritStatsAttachment stats, long before) {
        if (!(entity instanceof ServerPlayer player)) return;
        MxtServerConfig.Lifespan settings = MxtServerConfig.INSTANCE.lifespan;
        double fraction = settings.warningFraction.getValue();
        long total = stats.lifespanTotal();
        if (fraction <= 0.0D || total <= 0L) return;
        long threshold = (long) Math.ceil(total * fraction);
        long after = stats.lifespanRemaining();
        if (before > threshold && after <= threshold)
            player.sendSystemMessage(Component.translatable("actionbar.mxt.lifespan.warning",
                    display(after, total, settings.ticksPerYear.getValue())), true);
    }

    /**
     * The ledger as players read it, in years unless the display scale is one tick per year. The information
     * panel and the expiry warning share it, so the two can never show different numbers.
     */
    public static Component display(long remaining, long total, int ticksPerYear) {
        if (remaining < 0L) return Component.translatable("info.mxt.lifespan.unlimited");
        return ticksPerYear > 1
                ? Component.translatable("info.mxt.lifespan.value", years(remaining, ticksPerYear), years(total, ticksPerYear))
                : Component.translatable("info.mxt.lifespan.ticks", remaining, total);
    }

    private static String years(long ticks, int ticksPerYear) {
        return String.format(Locale.ROOT, "%.1f", (double) Math.max(0L, ticks) / ticksPerYear);
    }

    private static SpiritStatsAttachment existing(Entity entity) {
        return entity.getExistingData(MxtAttachments.SPIRIT_STATS).orElse(null);
    }

    private static long saturatedAdd(long value, long delta) {
        long sum = value + delta;
        return ((value ^ sum) & (delta ^ sum)) < 0L ? (delta > 0L ? Long.MAX_VALUE : Long.MIN_VALUE) : sum;
    }

    public enum Failure {
        SERVER_ONLY, INVALID_VALUE, CANCELLED
    }

    public record Result(boolean changed, Failure failure) {
        private static Result written() {
            return new Result(true, null);
        }

        private static Result refused(Failure failure) {
            return new Result(false, failure);
        }
    }
}
