package com.iafenvoy.mxt.runtime.item;

import com.iafenvoy.mxt.attachment.PillToxicityAttachment;
import com.iafenvoy.mxt.attachment.PillUsageAttachment;
import com.iafenvoy.mxt.config.MxtServerConfig;
import com.iafenvoy.mxt.data.item.Pill;
import com.iafenvoy.mxt.data.item.PillBinding;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.runtime.item.ItemBindingService.PillResolution;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.serialization.Codec;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import org.jspecify.annotations.NonNull;

import java.util.Locale;
import java.util.Optional;

/**
 * Server-authoritative pill gate, one-dose settlement and toxicity ledger. Reads use
 * {@code getExistingData}; writes are the only path that creates an attachment. Use limits follow the bound
 * holder, never the overlaid effect copy.
 */
@EventBusSubscriber
public final class PillService {
    public static final int DECAY_PERIOD = 20;

    private PillService() {
    }

    public enum Failure {
        UNBOUND,
        CONDITIONS,
        MAX_USES,
        COOLDOWN
    }

    public static Optional<Failure> check(LivingEntity user, ItemStack stack) {
        return check(user.level().registryAccess(), user, stack);
    }

    // Conditions, the use cap and the cooldown. An explicit missing binding refuses and does not fall through.
    // Quality is ItemQualityService's gate, not a second copy of that lookup.
    public static Optional<Failure> check(Provider access, LivingEntity user, ItemStack stack) {
        PillResolution resolution = ItemBindingService.resolvePill(access, stack);
        if (resolution.unbound()) return Optional.of(Failure.UNBOUND);
        Pill definition = resolution.effects().orElse(null);
        if (definition == null) return Optional.empty();
        FormulaContext context = FormulaContext.of(user);
        if (definition.conditions().stream().anyMatch(condition -> !condition.value().test(user, context)))
            return Optional.of(Failure.CONDITIONS);
        return resolution.identity().flatMap(holder -> usageFailure(user, holder));
    }

    public static Optional<Failure> usageFailure(LivingEntity user, Holder<PillBinding> pill) {
        if (!pill.isBound()) return Optional.of(Failure.UNBOUND);
        if (pill.value().maxUses().filter(max -> uses(user, pill) >= max).isPresent())
            return Optional.of(Failure.MAX_USES);
        if (onCooldown(user, pill)) return Optional.of(Failure.COOLDOWN);
        return Optional.empty();
    }

    public static int uses(Entity entity, Holder<PillBinding> pill) {
        return entity.getExistingData(MxtAttachments.PILL_USAGE).map(usage -> usage.uses(pill)).orElse(0);
    }

    public static boolean onCooldown(Entity entity, Holder<PillBinding> pill) {
        return entity.getExistingData(MxtAttachments.PILL_USAGE)
                .map(usage -> usage.onCooldown(pill, overworldGameTime(entity))).orElse(false);
    }

    public static long cooldownUntil(Entity entity, Holder<PillBinding> pill) {
        return entity.getExistingData(MxtAttachments.PILL_USAGE).map(usage -> usage.cooldownUntil(pill)).orElse(0L);
    }

    // One successful consume. The caller has already passed the Start/Tick gate; this does not re-check it.
    // Cooldown and the cap are read from the holder, so an effect overlay cannot shorten or erase them.
    public static void registerUse(LivingEntity entity, Holder<PillBinding> pill) {
        if (entity.level().isClientSide() || !pill.isBound()) return;
        PillUsageAttachment usage = entity.getData(MxtAttachments.PILL_USAGE);
        int cooldown = cooldownTicks(pill.value(), entity);
        long until = cooldown == 0 ? 0L : overworldGameTime(entity) + cooldown;
        usage.record(pill, usage.uses(pill) + 1, until);
    }

    public static Result apply(LivingEntity entity, Pill definition) {
        FormulaContext context = FormulaContext.of(entity);
        definition.onConsume().execute(entity, context);
        PillToxicityAttachment toxicity = entity.getData(MxtAttachments.PILL_TOXICITY);
        double value = toxicity.add(definition.toxicityGain().evaluate(context));
        double threshold = definition.toxicityThreshold().evaluate(context);
        if (Double.isFinite(threshold) && value >= threshold) {
            definition.onOverdose().execute(entity, context);
            toxicity.set(finite(definition.toxicityAfterOverdose().evaluate(context)));
            return Result.overdosed(toxicity.toxicity());
        }
        return Result.consumed(value);
    }

    public static double toxicity(Entity entity) {
        return entity.getExistingData(MxtAttachments.PILL_TOXICITY).map(PillToxicityAttachment::toxicity).orElse(0.0D);
    }

    public static double modify(LivingEntity entity, ModifyMode mode, double amount) {
        if (entity.level().isClientSide() || !Double.isFinite(amount)) return toxicity(entity);
        PillToxicityAttachment toxicity = entity.getData(MxtAttachments.PILL_TOXICITY);
        if (mode == ModifyMode.SET) toxicity.set(amount);
        else toxicity.add(amount);
        return toxicity.toxicity();
    }

    public static long overworldGameTime(Entity entity) {
        MinecraftServer server = entity.level().getServer();
        return server == null ? entity.level().getGameTime() : server.overworld().getGameTime();
    }

    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof LivingEntity entity) || entity.level().isClientSide()) return;
        double rate = MxtServerConfig.INSTANCE.alchemy.toxicityDecayPerSecond.getValue();
        if (!Double.isFinite(rate) || rate <= 0.0D) return;
        PillToxicityAttachment toxicity = entity.getExistingData(MxtAttachments.PILL_TOXICITY).orElse(null);
        if (toxicity == null || toxicity.toxicity() <= 0.0D) return;
        int remainder = toxicity.decayRemainder() + 1;
        if (remainder >= DECAY_PERIOD) {
            toxicity.add(-rate);
            remainder -= DECAY_PERIOD;
        }
        toxicity.setDecayRemainder(remainder);
    }

    static int cooldownTicks(PillBinding definition, LivingEntity entity) {
        double value = definition.cooldown().evaluate(FormulaContext.of(entity));
        if (!Double.isFinite(value) || value <= 0.0D) return 0;
        long ticks = Math.round(value);
        return ticks >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) ticks;
    }

    private static double finite(double value) {
        return Double.isFinite(value) ? Math.max(0.0D, value) : 0.0D;
    }

    public enum ModifyMode implements StringRepresentable {
        ADD, SET;

        public static final Codec<ModifyMode> CODEC =
                StringRepresentable.fromEnum(ModifyMode::values);

        @Override
        public @NonNull String getSerializedName() {
            return this.name().toLowerCase(Locale.ROOT);
        }
    }

    public record Result(boolean consumed, boolean overdosed, double toxicity) {
        public static Result consumed(double toxicity) {
            return new Result(true, false, toxicity);
        }

        public static Result overdosed(double toxicity) {
            return new Result(true, true, toxicity);
        }
    }
}
