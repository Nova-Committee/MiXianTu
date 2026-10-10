package com.iafenvoy.mxt.item;

import com.iafenvoy.mxt.registry.MxtDataComponents;
import com.iafenvoy.mxt.registry.MxtItems;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.tooltip.TooltipAppender;
import net.neoforged.neoforge.event.RegisterTooltipAppendersEvent;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.function.Consumer;

/**
 * Opens into whatever the loot table the stack names rolls: one box is one roll, rolled where the player stands with
 * the player as its context, so conditions reading luck or the opener work as they do for any other gift. The table is
 * named by a {@link ResourceKey} rather than held, because {@code /reload} swaps loot table instances and a held one
 * would keep rolling the table from before the reload.
 *
 * <p>A stack with no table bound, or one naming a table the loaded packs do not provide, refuses instead of eating the
 * box: neither is the player's mistake, and an empty roll would take the box for nothing.
 */
@EventBusSubscriber(Dist.CLIENT)
public final class RewardBoxItem extends Item implements TooltipAppender {
    public RewardBoxItem(Properties properties) {
        super(properties);
    }

    @Override
    public @NotNull InteractionResult use(@NotNull Level level, @NotNull Player player, @NotNull InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(player instanceof ServerPlayer serverPlayer)) return InteractionResult.SUCCESS;
        ResourceKey<LootTable> key = stack.get(MxtDataComponents.REWARD_BOX);
        if (key == null) {
            ItemFeedback.send(player, Component.translatable("item.mxt.reward_box.unbound"));
            return InteractionResult.FAIL;
        }
        ServerLevel serverLevel = serverPlayer.level();
        LootTable table = serverLevel.getServer().reloadableRegistries().getLootTable(key);
        // A table nobody provides resolves to the empty one, which would roll nothing and take the box with it.
        if (table == LootTable.EMPTY) {
            ItemFeedback.send(player, Component.translatable("item.mxt.reward_box.missing", key.identifier().toString()));
            return InteractionResult.FAIL;
        }
        LootParams params = new LootParams.Builder(serverLevel)
                .withParameter(LootContextParams.ORIGIN, player.position())
                .withParameter(LootContextParams.THIS_ENTITY, player)
                .withLuck(player.getLuck())
                .create(LootContextParamSets.GIFT);
        for (ItemStack drop : table.getRandomItems(params)) player.getInventory().placeItemBackInInventory(drop);
        // Creative keeps the box, the way every other framework consumable behaves there.
        if (!player.hasInfiniteMaterials()) stack.shrink(1);
        return InteractionResult.SUCCESS_SERVER;
    }

    @SubscribeEvent
    public static void registerTooltips(RegisterTooltipAppendersEvent event) {
        // The line belongs to the component, so only a bound box has one to print.
        event.registerComponentAppenderBeforeAll(MxtDataComponents.REWARD_BOX, MxtItems.REWARD_BOX.get());
    }

    @Override
    public void append(ItemStack stack, @NonNull TooltipContext context, @NonNull TooltipDisplay display, @Nullable Player player, @NonNull TooltipFlag tooltipFlag, @NonNull Consumer<Component> builder) {
        ResourceKey<LootTable> key = stack.get(MxtDataComponents.REWARD_BOX);
        if (key != null)
            builder.accept(Component.translatable("tooltip.mxt.reward_box.loot_table", key.identifier().toString()));
    }
}
