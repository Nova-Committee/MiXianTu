package com.iafenvoy.mxt.runtime.cultivation;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.attachment.CultivationAttachment;
import com.iafenvoy.mxt.attachment.ResourceHolderAttachment;
import com.iafenvoy.mxt.data.aura.Aura;
import com.iafenvoy.mxt.data.cost.CostPayment;
import com.iafenvoy.mxt.data.cost.context.CostContext;
import com.iafenvoy.mxt.data.cost.context.CostOrigins;
import com.iafenvoy.mxt.data.cultivation.CultivateConditions;
import com.iafenvoy.mxt.data.cultivation.RealmStage;
import com.iafenvoy.mxt.event.CultivationBreakEvent.Post;
import com.iafenvoy.mxt.event.CultivationBreakEvent.Pre;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.registry.MxtCriteriaTriggers;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.registry.cache.RegistryCaches;
import com.iafenvoy.mxt.runtime.ability.AbilityEventBridge;
import com.iafenvoy.mxt.runtime.resource.ResourceService;
import com.iafenvoy.mxt.runtime.resulting.Failure;
import com.iafenvoy.mxt.runtime.resulting.Failures;
import com.iafenvoy.mxt.runtime.resulting.ResultWithData;
import com.iafenvoy.mxt.runtime.resulting.failures.CultivationFailure;
import com.iafenvoy.mxt.runtime.tribulation.TribulationService;
import com.iafenvoy.mxt.util.HolderHelper;
import com.iafenvoy.mxt.util.MiscUtil;
import com.iafenvoy.mxt.util.chain.Chain;
import com.iafenvoy.mxt.util.codec.RegistryCodecs;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.iafenvoy.mxt.util.profiling.ProfilerScope;
import com.iafenvoy.mxt.util.profiling.ServerProfiling;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.common.NeoForge;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BooleanSupplier;

/**
 * Server-side breakthrough transaction; callers evaluate content conditions before payment is committed. A
 * chain is identified by its aura, which is why the aura-keyed methods are the core: the id-keyed overloads
 * exist for callers that hold only a name, and they resolve that name against the aura registry.
 */
public final class CultivationService {
    private static final double PROGRESS_EPSILON = 1.0E-7D;
    // Guards the segment total against a breakthrough requirement whose own formula reads minor_stage.
    private static final ThreadLocal<Boolean> RESOLVING_MINOR_STAGE = ThreadLocal.withInitial(() -> Boolean.FALSE);

    private CultivationService() {
    }

    public static BreakthroughResult attempt(LivingEntity entity, CultivationAttachment spirit, ResourceHolderAttachment resources, Holder<Aura> aura, FormulaContext context, BooleanSupplier conditionsMet) {
        try (ProfilerScope _ = ServerProfiling.scope(ServerProfiling.Source.CULTIVATION)) {
            if (entity.level().isClientSide()) return BreakthroughResult.rejected(Failures.SERVER_ONLY, null);
            Holder<RealmStage> target = next(aura, spirit);
            if (target == null) return BreakthroughResult.rejected(CultivationFailure.NO_NEXT_REALM, null);
            return attempt(entity, spirit, resources, aura, target, context, conditionsMet);
        }
    }

    // Triggered abilities are dispatched only after the realm state has committed.
    private static BreakthroughResult attempt(LivingEntity entity, CultivationAttachment spirit, ResourceHolderAttachment resources, Holder<Aura> aura, Holder<RealmStage> stage, FormulaContext context, BooleanSupplier conditionsMet) {
        try (ProfilerScope _ = ServerProfiling.scope(ServerProfiling.Source.CULTIVATION)) {
            RealmStage target = stage.value();
            if (!Objects.equals(target.aura(), aura))
                return BreakthroughResult.rejected(CultivationFailure.WRONG_AURA, null);
            FormulaContext resourceContext = ResourceService.formulaContext(entity, aura.value().resource(), context);
            Threshold threshold = threshold(stage, resourceContext);
            if (threshold == null) return BreakthroughResult.rejected(Failures.INVALID_FORMULA, null);
            double minimum = threshold.breakthroughExp();
            double maximum = threshold.maxExperience();
            if (!Double.isFinite(minimum) || !Double.isFinite(maximum) || minimum < 0.0D || maximum < 0.0D || minimum > maximum)
                return BreakthroughResult.rejected(Failures.INVALID_FORMULA, null);
            double progress = spirit.cultivationProgress(aura);
            if (progress < minimum) return BreakthroughResult.rejected(CultivationFailure.INSUFFICIENT_PROGRESS, null);
            boolean configuredConditions = target.breakthrough().test(entity, context);
            boolean requiredAbilities = RegistryCodecs.resolve(target.abilityRequirements(), entity.level().registryAccess(), MxtResourceKeys.ABILITY)
                    .allMatch(ability -> entity.getData(MxtAttachments.ABILITY_HOLDER).has(ability));
            BreakthroughResult result = commit(entity, spirit, resources, aura, stage, resourceContext,
                    () -> configuredConditions && requiredAbilities && conditionsMet.getAsBoolean());
            if (result.isSuccess()) {
                if (entity instanceof ServerPlayer player)
                    MxtCriteriaTriggers.BREAKTHROUGH.get().trigger(player, HolderHelper.id(stage));
                target.breakthroughParticle().ifPresent(effect -> {
                    if (entity.level() instanceof ServerLevel level)
                        effect.send(level, entity.position().add(0.0D, entity.getBbHeight() * 0.5D, 0.0D));
                });
                target.successAction().execute(entity, context);
                grantLifespan(entity, stage, context);
                // Sampled here so every tribulation phase is scaled by the current environment, not only the
                // phase that starts at breakthrough.
                target.tribulation().ifPresent(tribulation -> TribulationService.start(entity, entity.getData(MxtAttachments.TRIBULATION), tribulation, entity.level().getGameTime(), context));
                AbilityEventBridge.onBreakthrough(entity, context);
                // The new realm starts on its own first minor stage, which is itself an unlock threshold.
                MinorStageService.refresh(entity, aura, resourceContext);
            } else target.failAction().execute(entity, context);
            return result;
        }
    }

    // A stage's own lifespan is an increment paid once, on the breakthrough that reached it, so a later stage
    // written smaller only adds less; it can never take back what an earlier one granted.
    private static void grantLifespan(LivingEntity entity, Holder<RealmStage> stage, FormulaContext context) {
        NumberProvider provider = stage.value().lifespan().orElse(null);
        if (provider == null) return;
        double value = provider.evaluate(context);
        if (!Double.isFinite(value) || value < 0.0D) {
            MiXianTu.LOGGER.warn("Realm stage {} grants {} lifespan; nothing was added", HolderHelper.id(stage), value);
            return;
        }
        LifeSpanService.add(entity, Math.round(value));
    }

    private static BreakthroughResult commit(LivingEntity entity, CultivationAttachment spirit, ResourceHolderAttachment resources, @NotNull Holder<Aura> aura, @NotNull Holder<RealmStage> stage, FormulaContext context, BooleanSupplier conditionsMet) {
        RealmStage target = stage.value();
        Threshold threshold = threshold(stage, context);
        if (threshold == null) return BreakthroughResult.rejected(Failures.INVALID_FORMULA, null);
        double minimum = threshold.breakthroughExp();
        double progress = spirit.cultivationProgress(aura);
        if (progress < minimum) return BreakthroughResult.rejected(CultivationFailure.INSUFFICIENT_PROGRESS, null);
        if (!conditionsMet.getAsBoolean()) return BreakthroughResult.rejected(Failures.CONDITIONS, null);
        CostContext costContext = CostContext.of(entity, context, CostOrigins.BREAKTHROUGH);
        CostPayment plan = CostPayment.of(costContext);
        Optional<Failure> refusal = plan.price(target.breakthroughCosts());
        if (refusal.isPresent()) return BreakthroughResult.rejected(refusal.get(), null);
        Pre event = new Pre(spirit, resources, stage, context, minimum, plan.resources());
        if (NeoForge.EVENT_BUS.post(event).isCanceled()) return BreakthroughResult.rejected(Failures.CANCELLED, null);
        Optional<Failure> edited = plan.replaceResourceAmounts(event.getCosts());
        if (edited.isPresent()) return BreakthroughResult.rejected(edited.get(), null);
        CostPayment.PaymentResult payment = plan.pay();
        if (!payment.isSuccess()) return BreakthroughResult.rejected(payment.failure(), payment.failedResource());
        spirit.setRealmStage(stage);
        spirit.setCultivationProgress(aura, 0.0D);
        NeoForge.EVENT_BUS.post(new Post(spirit, resources, stage, context, minimum, payment.resources()));
        return BreakthroughResult.committed(payment.resources());
    }

    // The stage this chain climbs towards: the next rung when the body already stands in the chain, and the aura's
    // own first realm for a mortal. Every threshold - requirement, cap, conditions, auto flag and minor stages -
    // is read from this one stage, so a breakthrough is fully described by the stage it reaches.
    @Nullable
    private static Holder<RealmStage> next(@Nullable Holder<Aura> aura, CultivationAttachment spirit) {
        if (aura == null) return null;
        Holder<RealmStage> current = spirit.realmStage(aura);
        if (current != null) {
            // The validated chain answers one rung at a time in either direction. Without one - no server, or
            // before a world is loaded - the realm's own next_realm answers instead; a chain the walk refused does
            // not, because advancing along a line nobody validated is what the refusal is about.
            return RegistryCaches.REALM_CHAIN.next(current).orElse(null);
        }
        return aura.value().firstRealm().filter(value -> Objects.equals(value.value().aura(), aura)).orElse(null);
    }

    public static double addProgress(LivingEntity entity, Holder<Aura> aura, double amount, FormulaContext context) {
        return addProgressForChain(entity, aura, amount, context);
    }

    public static double addProgressForChain(LivingEntity entity, Holder<Aura> aura, double amount, FormulaContext context) {
        amount = MiscUtil.positive(amount);
        if (amount <= 0.0D) return 0.0D;
        CultivationAttachment spirit = entity.getData(MxtAttachments.CULTIVATION);
        Holder<RealmStage> target = next(aura, spirit);
        if (target == null) return 0.0D;
        FormulaContext resourceContext = ResourceService.formulaContext(entity, aura.value().resource(), context);
        double accepted = addProgress(spirit, aura, amount, target, resourceContext);
        // Crossing into a new minor stage is what unlocks that stage's abilities, so the record is refreshed with
        // the progress that moved it; nothing is rebuilt unless the record actually grew.
        if (accepted > 0.0D) MinorStageService.refresh(entity, aura, resourceContext);
        return accepted;
    }

    public static double addProgressForChain(CultivationAttachment spirit, Holder<Aura> aura, double amount, FormulaContext context) {
        amount = MiscUtil.positive(amount);
        if (amount <= 0.0D) return 0.0D;
        Holder<RealmStage> target = next(aura, spirit);
        return target == null ? 0.0D : addProgress(spirit, aura, amount, target, context);
    }

    private static double addProgress(CultivationAttachment spirit, Holder<Aura> aura, double amount,
                                      Holder<RealmStage> target, FormulaContext context) {
        Threshold threshold = threshold(target, context);
        if (threshold == null) return 0.0D;
        double maximum = threshold.maxExperience();
        double before = spirit.cultivationProgress(aura);
        if (before >= maximum - PROGRESS_EPSILON) {
            if (Double.compare(before, maximum) != 0) spirit.setCultivationProgress(aura, maximum);
            return 0.0D;
        }
        double accepted = Math.max(0.0D, Math.min(amount, maximum - before));
        if (maximum - (before + accepted) <= PROGRESS_EPSILON) accepted = maximum - before;
        if (accepted > 0.0D) spirit.setCultivationProgress(aura, before + accepted);
        return accepted;
    }

    public static double remainingProgressForChain(CultivationAttachment spirit, Holder<Aura> aura, FormulaContext context) {
        Holder<RealmStage> target = next(aura, spirit);
        if (target == null) return 0.0D;
        Threshold threshold = threshold(target, context);
        if (threshold == null) return 0.0D;
        double maximum = threshold.maxExperience();
        double current = spirit.cultivationProgress(aura);
        double remaining = maximum - current;
        if (remaining >= 0.0D && remaining <= PROGRESS_EPSILON) {
            if (Double.compare(current, maximum) != 0) spirit.setCultivationProgress(aura, maximum);
            return 0.0D;
        }
        return remaining <= PROGRESS_EPSILON ? 0.0D : remaining;
    }

    // 0-based index of the minor stage this chain's progress sits in, or NaN when there is nothing to cut: a
    // mortal stands on no stage, a stage that declares no minor stages has none, and a chain with nothing left to
    // climb has no segment to name. The count and the names belong to the stage the body stands in - its
    // minor_stage_abilities are indexed into them - while the cuts are made of the requirement that reaches the
    // next stage, because that is the number this progress is climbing towards.
    // This is the one place the segment arithmetic lives; the panel and the {@code minor_stage} formula variable
    // both read it.
    public static double minorStage(Holder<Aura> aura, CultivationAttachment spirit, FormulaContext context) {
        Holder<RealmStage> current = spirit.realmStage(aura);
        if (current == null) return Double.NaN;
        Holder<RealmStage> target = next(aura, spirit);
        if (target == null) return Double.NaN;
        return minorStage(current.value().minorStages(), target.value().breakthroughExp(),
                spirit.cultivationProgress(aura), context);
    }

    // Even cuts of the stage's own breakthrough requirement, for a caller holding one stage and no chain.
    public static double minorStage(RealmStage stage, double progress, FormulaContext context) {
        return minorStage(stage.minorStages(), stage.breakthroughExp(), progress, context);
    }

    // Progress banked past the requirement (up to max_experience) stays on the last segment, so the index never
    // runs off the declared names.
    private static double minorStage(int count, NumberProvider requirement, double progress, FormulaContext context) {
        if (count == 0 || !Double.isFinite(progress) || progress < 0.0D) return Double.NaN;
        if (RESOLVING_MINOR_STAGE.get()) return Double.NaN;
        RESOLVING_MINOR_STAGE.set(Boolean.TRUE);
        double total;
        try {
            total = requirement.evaluate(context);
        } finally {
            RESOLVING_MINOR_STAGE.set(Boolean.FALSE);
        }
        if (MiscUtil.positive(total) <= 0.0D) return Double.NaN;
        return Math.min(count - 1, Math.floor(progress * count / total));
    }

    // Read-only, shared by the automatic breakthrough tick and the information screen.
    public static BreakthroughStatus breakthroughStatusForChain(LivingEntity entity, Holder<Aura> aura, FormulaContext context) {
        CultivationAttachment spirit = entity.getData(MxtAttachments.CULTIVATION);
        Holder<RealmStage> target = next(aura, spirit);
        if (target == null) return BreakthroughStatus.UNAVAILABLE;
        Threshold threshold = threshold(target, ResourceService.formulaContext(entity, aura.value().resource(), context));
        if (threshold == null) return BreakthroughStatus.UNAVAILABLE;
        double progress = spirit.cultivationProgress(aura);
        boolean reached = progress + PROGRESS_EPSILON >= threshold.breakthroughExp();
        boolean conditions = reached && target.value().breakthrough().test(entity, context);
        return new BreakthroughStatus(reached, conditions, target.value().autoBreakthrough(), threshold.breakthroughExp(), threshold.maxExperience());
    }

    // Runtime trigger subscriptions are rebuilt separately and never stored in the attachment.
    public static Optional<CultivateConditions> pendingConditionsForChain(LivingEntity entity, Holder<Aura> aura) {
        Holder<RealmStage> target = next(aura, entity.getData(MxtAttachments.CULTIVATION));
        return target == null ? Optional.empty() : Optional.of(target.value().breakthrough());
    }

    // Administrative, validated through the server cache rather than by traversing the registry here.
    public static boolean setRealm(CultivationAttachment spirit, Holder<RealmStage> target) {
        Holder<Aura> aura = RegistryCaches.REALM_CHAIN.chainOf(target).map(Chain::first).flatMap(RegistryCaches.REALM_CHAIN::getAuraByRealm).orElse(null);
        if (aura == null) return false;
        spirit.setRealmStage(target);
        spirit.setCultivationProgress(aura, 0.0D);
        return true;
    }

    // The whole cost of reaching a stage is written on that stage: how much progress it takes and how much may be
    // banked while waiting. A mortal climbing to a chain's first realm reads the same two numbers there, so no
    // aura carries a breakthrough threshold of its own.
    private static Threshold threshold(Holder<RealmStage> target, FormulaContext context) {
        RealmStage stage = target.value();
        double breakthroughExp = stage.breakthroughExp().evaluate(context);
        double maxExperience = stage.maxExperience().evaluate(context);
        return Double.isFinite(breakthroughExp) && Double.isFinite(maxExperience)
                && breakthroughExp >= 0.0D && maxExperience >= breakthroughExp
                ? new Threshold(breakthroughExp, maxExperience) : null;
    }

    private record Threshold(double breakthroughExp, double maxExperience) {
    }

    public record BreakthroughStatus(boolean reached, boolean conditionsMet, boolean automatic,
                                     double minimumExperience, double maximumExperience) {
        private static final BreakthroughStatus UNAVAILABLE = new BreakthroughStatus(false, false, false, 0.0D, 0.0D);
    }

    public static final class BreakthroughResult extends ResultWithData<Map<Identifier, Double>> {
        private BreakthroughResult(Map<Identifier, Double> costs, Failure failure, Identifier failedResource) {
            super(costs, failure, failedResource);
        }

        private static BreakthroughResult committed(Map<Identifier, Double> costs) {
            return new BreakthroughResult(costs, null, null);
        }

        public static BreakthroughResult rejected(Failure failure, Identifier resource) {
            return new BreakthroughResult(Map.of(), failure, resource);
        }
    }
}
