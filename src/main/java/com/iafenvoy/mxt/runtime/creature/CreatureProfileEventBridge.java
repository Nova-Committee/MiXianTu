package com.iafenvoy.mxt.runtime.creature;

import com.iafenvoy.mxt.registry.MxtAttachments;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootParams.Builder;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

/**
 * Applies creature profiles once when a mob joins the server level and materializes an optional inner core at death.
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
        creature.getData(MxtAttachments.CREATURE_SPIRIT).innerCore().ifPresent(item ->
                creature.spawnAtLocation((ServerLevel) creature.level(), new ItemStack(item.value())));
        creature.getData(MxtAttachments.CREATURE_SPIRIT).lootTable().ifPresent(table -> dropProfileLoot(creature, event, table));
    }

    private static void dropProfileLoot(Mob creature, LivingDeathEvent event, ResourceKey<LootTable> table) {
        ServerLevel level = (ServerLevel) creature.level();
        Builder builder = new Builder(level)
                .withParameter(LootContextParams.THIS_ENTITY, creature)
                .withParameter(LootContextParams.ORIGIN, creature.position())
                .withParameter(LootContextParams.DAMAGE_SOURCE, event.getSource())
                .withOptionalParameter(LootContextParams.ATTACKING_ENTITY, event.getSource().getEntity())
                .withOptionalParameter(LootContextParams.DIRECT_ATTACKING_ENTITY, event.getSource().getDirectEntity());
        if (creature.getKillCredit() instanceof Player player) {
            builder.withOptionalParameter(LootContextParams.LAST_DAMAGE_PLAYER, player);
        }
        LootParams params = builder.create(LootContextParamSets.ENTITY);
        level.getServer().reloadableRegistries().getLootTable(table).getRandomItems(params)
                .forEach(stack -> creature.spawnAtLocation(level, stack));
    }
}
