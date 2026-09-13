package com.iafenvoy.mxt.runtime.trigger;

import com.iafenvoy.mxt.attachment.CultivationAttachment;
import com.iafenvoy.mxt.attachment.ResourceHolderAttachment;
import com.iafenvoy.mxt.data.cultivation.CultivateConditions;
import com.iafenvoy.mxt.data.cultivation.CultivationProfile;
import com.iafenvoy.mxt.data.trigger.Trigger;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.runtime.cultivation.CultivationService;
import com.iafenvoy.mxt.runtime.cultivation.CultivationService.BreakthroughResult;
import com.iafenvoy.mxt.runtime.cultivation.CultivationService.BreakthroughStatus;
import com.iafenvoy.mxt.util.HolderHelper;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import net.minecraft.core.Holder;
import net.minecraft.core.Holder.Reference;
import net.minecraft.world.entity.LivingEntity;

/**
 * Rebuilds cultivation trigger subscriptions from persisted cultivation state.
 * Only the waiting transition is persisted; subscriptions are runtime cache.
 */
public final class CultivationTriggerService {
    private static final String MODULE = "cultivation";

    static {
        TriggerRehydrators.register(new TriggerRehydrator() {
            @Override
            public String module() {
                return MODULE;
            }

            @Override
            public void rehydrate(LivingEntity entity) {
                refresh(entity);
            }
        });
    }

    private CultivationTriggerService() {
    }

    /**
     * Forces class initialization so the rehydrator is registered before server startup.
     */
    public static void initialize() {
    }

    /**
     * Idempotently reconstructs subscriptions for all chains currently
     * waiting at their breakthrough threshold.
     */
    public static void refresh(LivingEntity entity) {
        if (entity.level().isClientSide()) return;
        CultivationAttachment cultivation = entity.getData(MxtAttachments.CULTIVATION);
        // Runtime subscriptions are only meaningful while cultivation is active.
        // Always clear the module first so stopping cultivation (or changing the
        // selected action) cannot leave a stale breakthrough listener behind.
        TriggerDispatcher.clearModule(entity.getUUID(), MODULE);
        if (!cultivation.cultivating()) return;
        ResourceHolderAttachment resources = entity.getData(MxtAttachments.RESOURCE_HOLDER);
        // Only chains that exist are candidates; a value without a profile has no breakthrough.
        for (Reference<CultivationProfile> chain : MxtDatapackRegistries.holders(entity.level().registryAccess(), MxtResourceKeys.CULTIVATION).toList()) {
            FormulaContext formula = FormulaContext.of(entity);
            BreakthroughStatus status = CultivationService.breakthroughStatusForChain(entity, chain, formula);
            if (!status.reached() || !status.conditionsMet()) continue;
            CultivationService.pendingConditionsForChain(entity, chain).ifPresent(conditions ->
                    register(entity, cultivation, resources, chain, conditions, formula));
        }
    }

    /**
     * Removes all cultivation subscriptions for an entity without inspecting datapack state.
     */
    public static void clear(LivingEntity entity) {
        if (!entity.level().isClientSide()) TriggerDispatcher.clearModule(entity.getUUID(), MODULE);
    }


    private static void register(LivingEntity entity, CultivationAttachment cultivation,
                                 ResourceHolderAttachment resources, Holder<CultivationProfile> chain,
                                 CultivateConditions conditions, FormulaContext formula) {
        int index = 0;
        for (Trigger trigger : conditions.triggers()) {
            String identity = HolderHelper.id(chain) + "/" + index++;
            TriggerDispatcher.register(new TriggerSubscription(entity.getUUID(), MODULE, identity, trigger,
                    signal -> signal.context().actor() == entity
                            && entity.getData(MxtAttachments.CULTIVATION).cultivating()
                            && CultivationService.pendingConditionsForChain(entity, chain).map(conditions::equals).orElse(false),
                    signal -> {
                        BreakthroughResult result = CultivationService.attempt(
                                entity, cultivation, resources, chain,
                                signal.context().formula(), () -> true);
                        if (result.advanced()) TriggerDispatcher.clearModule(entity.getUUID(), MODULE);
                    }, false));
        }
    }
}
