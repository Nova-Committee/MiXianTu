package com.iafenvoy.mxt.runtime.creature;

import com.iafenvoy.mxt.registry.MxtAttachments;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

/**
 * Applies creature profiles once when a mob joins the server level and drops an optional inner core at death.
 */
@EventBusSubscriber
public final class CreatureProfileEventBridge {
    private CreatureProfileEventBridge() {
    }

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof Mob creature)) return;
        CreatureProfileService.applySelected(creature);
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide() || !(event.getEntity() instanceof Mob creature)) return;
        ItemStack core = creature.getData(MxtAttachments.CREATURE_SPIRIT).innerCore();
        // The dropped stack is the one the item entity shrinks on pickup, so the stored copy must not be handed over.
        if (!core.isEmpty()) creature.spawnAtLocation((ServerLevel) creature.level(), core.copy());
    }
}
