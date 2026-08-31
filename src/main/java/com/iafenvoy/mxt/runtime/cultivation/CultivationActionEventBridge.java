package com.iafenvoy.mxt.runtime.cultivation;

import com.iafenvoy.mxt.attachment.CultivationAttachment;
import com.iafenvoy.mxt.data.cultivation.CultivateAction;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.runtime.cultivation.CultivationActionService.Result;
import com.iafenvoy.mxt.runtime.world.AuraResult;
import com.iafenvoy.mxt.runtime.world.AuraService;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.formula.FormulaContexts;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent.Post;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Drives the one persisted cultivation action using the entity's current chunk aura.
 */
@EventBusSubscriber
public final class CultivationActionEventBridge {
    private static final Map<UUID, FailureNotice> LAST_FAILURES = new HashMap<>();

    private CultivationActionEventBridge() {
    }

    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof LivingEntity entity) || entity.level().isClientSide() || entity instanceof ServerPlayer)
            return;
        tick(entity);
    }

    @SubscribeEvent
    public static void onLevelTick(Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        AuraDistributionService.prepare(level);
        for (ServerPlayer player : level.players()) tick(player);
    }

    @SubscribeEvent
    public static void onLivingDamage(LivingDamageEvent.Post event) {
        if (event.getInflictedDamage() <= 0.0F || !(event.getEntity() instanceof ServerPlayer player)) return;
        CultivationModeService.stopIfCultivating(player);
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            CultivationModeService.stopIfCultivating(player);
            LAST_FAILURES.remove(player.getUUID());
        }
    }

    private static void tick(LivingEntity entity) {
        CultivationAttachment spirit = entity.getData(MxtAttachments.CULTIVATION);
        if (!spirit.cultivating()) return;
        Holder<CultivateAction> action = spirit.cultivateAction().orElse(null);
        if (action == null) {
            if (entity instanceof ServerPlayer player) LAST_FAILURES.remove(player.getUUID());
            return;
        }
        CultivateAction definition = action.value();
        FormulaContext context = FormulaContexts.forEntity(entity);
        boolean mayContinue = definition.condition().test(entity, context);
        AuraResult aura = AuraService.getPositionAura(entity.level(), entity.blockPosition());
        Result result = CultivationActionService.tick(entity, spirit, entity.getData(MxtAttachments.RESOURCE_HOLDER), aura, action, definition,
                entity.level().getGameTime(), context, () -> mayContinue);
        if (entity instanceof ServerPlayer player) {
            if (result.failure() == null) {
                LAST_FAILURES.remove(player.getUUID());
            } else {
                FailureNotice notice = new FailureNotice(result.failure(), result.failedResource());
                FailureNotice previous = LAST_FAILURES.put(player.getUUID(), notice);
                long gameTime = entity.level().getGameTime();
                if (!notice.equals(previous) || gameTime % 20L == 0L)
                    CultivationModeService.notifyFailure(player, result);
                if (!spirit.cultivating()) LAST_FAILURES.remove(player.getUUID());
            }
        }
        // Cultivation can change progress, active-state timing, and resource conversions in one tick.
    }

    private record FailureNotice(CultivationActionService.Failure failure, net.minecraft.resources.Identifier resource) {
    }
}
