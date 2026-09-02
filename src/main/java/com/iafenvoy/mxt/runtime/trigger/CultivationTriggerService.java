package com.iafenvoy.mxt.runtime.trigger;

import com.iafenvoy.mxt.attachment.CultivationAttachment;
import com.iafenvoy.mxt.attachment.ResourceHolderAttachment;
import com.iafenvoy.mxt.data.cultivation.CultivateConditions;
import com.iafenvoy.mxt.data.resource.Resource;
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
     * Idempotently reconstructs subscriptions for all resources currently
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
        for (Holder<Resource> resource : MxtDatapackRegistries.holders(entity.level().registryAccess(), MxtResourceKeys.RESOURCE).toList()) {
            FormulaContext formula = FormulaContext.of(entity);
            BreakthroughStatus status = CultivationService.breakthroughStatus(entity, resource, formula);
            if (!status.reached() || !status.conditionsMet()) continue;
            CultivationService.pendingConditions(entity, resource).ifPresent(conditions ->
                    register(entity, cultivation, resources, resource, conditions, formula));
        }
    }

    /**
     * Removes all cultivation subscriptions for an entity without inspecting datapack state.
     */
    public static void clear(LivingEntity entity) {
        if (!entity.level().isClientSide()) TriggerDispatcher.clearModule(entity.getUUID(), MODULE);
    }


    private static void register(LivingEntity entity, CultivationAttachment cultivation,
                                 ResourceHolderAttachment resources, Holder<Resource> resource,
                                 CultivateConditions conditions, FormulaContext formula) {
        int index = 0;
        for (Trigger trigger : conditions.triggers()) {
            String resourceId = HolderHelper.id(resource).toString();
            String identity = resourceId + "/" + index++;
            TriggerDispatcher.register(new TriggerSubscription(entity.getUUID(), MODULE, identity, trigger,
                    signal -> signal.context().actor() == entity
                            && entity.getData(MxtAttachments.CULTIVATION).cultivating()
                            && CultivationService.pendingConditions(entity, resource).map(conditions::equals).orElse(false),
                    signal -> {
                        BreakthroughResult result = CultivationService.attempt(
                                entity, cultivation, resources, HolderHelper.id(resource),
                                signal.context().formula(), () -> true);
                        if (result.advanced()) TriggerDispatcher.clearModule(entity.getUUID(), MODULE);
                    }, false));
        }
    }
}
