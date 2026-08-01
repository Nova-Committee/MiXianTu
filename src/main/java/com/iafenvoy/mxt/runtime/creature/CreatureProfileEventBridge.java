package com.iafenvoy.mxt.runtime.creature;

import com.iafenvoy.mxt.registry.MxtAttachments;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootParams.Builder;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

/**
 * Applies creature profiles once on server-side spawn and materializes an optional inner core at death.
 */
public final class CreatureProfileEventBridge {
    private CreatureProfileEventBridge() {
    }

    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof Mob creature)) return;
        CreatureProfileService.applySelected(creature);
    }

    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide() || !(event.getEntity() instanceof Mob creature)) return;
        creature.getData(MxtAttachments.CREATURE_SPIRIT).innerCore().flatMap(BuiltInRegistries.ITEM::getOptional).ifPresent(item ->
                creature.spawnAtLocation((ServerLevel) creature.level(), new ItemStack(item)));
        creature.getData(MxtAttachments.CREATURE_SPIRIT).lootTable().ifPresent(tableId -> dropProfileLoot(creature, event, tableId));
    }

    private static void dropProfileLoot(Mob creature, LivingDeathEvent event, Identifier tableId) {
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
        level.getServer().reloadableRegistries().getLootTable(ResourceKey.create(Registries.LOOT_TABLE, tableId)).getRandomItems(params)
                .forEach(stack -> creature.spawnAtLocation(level, stack));
    }
}
