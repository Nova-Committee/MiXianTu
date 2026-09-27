package com.iafenvoy.mxt.runtime.alchemy;

import com.iafenvoy.mxt.data.alchemy.SpiritHerb;
import com.iafenvoy.mxt.data.alchemy.SpiritHerb.Growth;
import com.iafenvoy.mxt.data.cost.CostTransaction;
import com.iafenvoy.mxt.data.cost.context.CostContext;
import com.iafenvoy.mxt.data.cost.context.CostOrigin;
import com.iafenvoy.mxt.item.block.entity.SpiritHerbPlotBlockEntity;
import com.iafenvoy.mxt.item.block.entity.SpiritHerbPlotBlockEntity.Pause;
import com.iafenvoy.mxt.registry.MxtDataComponents;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.runtime.world.AuraService;
import com.iafenvoy.mxt.util.HolderHelper;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Sowing, the 20-tick growth settlement and harvest. The plot entity owns the plant; this service is the only
 * place that decides whether a tick spends aura or a click gives items.
 */
public final class SpiritHerbGrowthService {
    public static final int PERIOD = 20;

    private SpiritHerbGrowthService() {
    }

    public static InteractionResult plant(ServerPlayer player, ServerLevel level, BlockPos pos, ItemStack stack,
                                          SpiritHerbPlotBlockEntity plot) {
        if (!allowed(player, level, pos)) return seedOf(level, stack).isPresent() ? InteractionResult.FAIL : InteractionResult.PASS;
        if (plot.occupied()) return seedOf(level, stack).isPresent() ? InteractionResult.FAIL : InteractionResult.PASS;
        Optional<Holder<SpiritHerb>> herb = seedOf(level, stack);
        if (herb.isEmpty()) return InteractionResult.PASS;
        ItemStack saved = stack.copyWithCount(1);
        if (!player.hasInfiniteMaterials()) stack.shrink(1);
        plot.plant(herb.get(), saved);
        return InteractionResult.SUCCESS;
    }

    public static InteractionResult emptyHand(ServerPlayer player, ServerLevel level, BlockPos pos,
                                              SpiritHerbPlotBlockEntity plot, boolean sneak) {
        if (!plot.occupied()) return InteractionResult.PASS;
        if (!allowed(player, level, pos)) return InteractionResult.FAIL;
        if (sneak || !harvestable(level, plot)) {
            if (sneak || unresolved(level, plot)) return pull(player, level, pos, plot);
            tell(player, level, pos, plot);
            return InteractionResult.SUCCESS;
        }
        return harvest(player, level, pos, plot);
    }

    public static void tick(ServerLevel level, BlockPos pos, SpiritHerbPlotBlockEntity plot) {
        if (!plot.occupied()) return;
        int next = plot.remainder() + 1;
        if (next < PERIOD) {
            plot.setGrowth(plot.progress(), next, plot.pause());
            plot.setChanged();
            return;
        }
        settle(level, pos, plot);
    }

    // One settlement, one roll. The amount and the cost plan are not evaluated again for the commit.
    private static void settle(ServerLevel level, BlockPos pos, SpiritHerbPlotBlockEntity plot) {
        Pause blocked = structural(level, pos, plot);
        if (blocked != Pause.NONE) {
            plot.setGrowth(plot.progress(), 0, blocked);
            plot.setChanged();
            return;
        }
        Growth growth = liveGrowth(level, plot).orElseThrow();
        FormulaContext formula = FormulaContext.of(level);
        double amount = growthAmount(level, pos, growth, formula);
        if (amount <= 0.0D) {
            plot.setGrowth(plot.progress(), 0, Pause.GROWTH);
            plot.setChanged();
            return;
        }
        if (!growth.costs().isEmpty()) {
            CostContext context = CostContext.pool(null, level, pos, formula, CostOrigin.HERB_GROWTH);
            CostTransaction.Planning plan = CostTransaction.plan(growth.costs(), context);
            if (!plan.ok() || !CostTransaction.commit(plan, context).paid()) {
                plot.setGrowth(plot.progress(), 0, Pause.AURA);
                plot.setChanged();
                return;
            }
        }
        float capped = (float) Math.min(growth.maxAge(), plot.progress() + amount);
        plot.setGrowth(capped, 0, Pause.NONE);
        plot.sync();
    }

    public static void dropOnRemove(ServerLevel level, BlockPos pos, SpiritHerbPlotBlockEntity plot) {
        if (!plot.occupied()) return;
        List<ItemStack> drops = breakDrops(level, plot);
        plot.clear();
        plot.setChanged();
        for (ItemStack drop : drops) Block.popResource(level, pos, drop);
    }

    public static boolean previewPlant(net.minecraft.world.entity.player.Player player,
                                       net.minecraft.world.level.Level level, BlockPos pos, ItemStack stack,
                                       SpiritHerbPlotBlockEntity plot) {
        return allowed(player, level, pos) && !plot.occupied() && seedOf(level, stack).isPresent();
    }

    private static InteractionResult pull(ServerPlayer player, ServerLevel level, BlockPos pos,
                                          SpiritHerbPlotBlockEntity plot) {
        ItemStack seed = plot.seed().copy();
        plot.clear();
        plot.sync();
        give(player, level, pos, seed);
        return InteractionResult.SUCCESS;
    }

    private static InteractionResult harvest(ServerPlayer player, ServerLevel level, BlockPos pos,
                                             SpiritHerbPlotBlockEntity plot) {
        List<ItemStack> given = breakDrops(level, plot);
        plot.clear();
        plot.sync();
        for (ItemStack stack : given) give(player, level, pos, stack);
        return InteractionResult.SUCCESS;
    }

    private static List<ItemStack> breakDrops(ServerLevel level, SpiritHerbPlotBlockEntity plot) {
        List<ItemStack> drops = new ArrayList<>(2);
        if (harvestable(level, plot)) drops.add(harvestStack(level, plot));
        if (!plot.seed().isEmpty()) drops.add(plot.seed().copy());
        return drops;
    }

    private static ItemStack harvestStack(ServerLevel level, SpiritHerbPlotBlockEntity plot) {
        Growth growth = liveGrowth(level, plot).orElseThrow();
        ItemStack stack = growth.harvest().create();
        stack.set(MxtDataComponents.HERB_AGE.get(), plot.age());
        return stack;
    }

    private static void give(ServerPlayer player, ServerLevel level, BlockPos pos, ItemStack stack) {
        if (stack.isEmpty()) return;
        if (!player.getInventory().add(stack)) Block.popResource(level, pos, stack);
    }

    private static void tell(ServerPlayer player, ServerLevel level, BlockPos pos, SpiritHerbPlotBlockEntity plot) {
        Pause reason = preview(level, pos, plot);
        int mature = liveGrowth(level, plot).map(Growth::matureAge).orElse(0);
        player.sendSystemMessage(Component.translatable("message.mxt.herb.status", plot.age(), mature,
                Component.translatable("message.mxt.herb.pause." + reason.name().toLowerCase(java.util.Locale.ROOT))), true);
    }

    // Display only. A click that asks why the plant paused must not hand its roll to the next settlement.
    private static Pause preview(ServerLevel level, BlockPos pos, SpiritHerbPlotBlockEntity plot) {
        Pause blocked = structural(level, pos, plot);
        if (blocked != Pause.NONE) return blocked;
        Growth growth = liveGrowth(level, plot).orElseThrow();
        FormulaContext formula = FormulaContext.of(level);
        if (growthAmount(level, pos, growth, formula) <= 0.0D) return Pause.GROWTH;
        if (growth.costs().isEmpty()) return Pause.NONE;
        CostContext context = CostContext.pool(null, level, pos, formula, CostOrigin.HERB_GROWTH);
        return CostTransaction.plan(growth.costs(), context).ok() ? Pause.NONE : Pause.AURA;
    }

    private static Pause structural(ServerLevel level, BlockPos pos, SpiritHerbPlotBlockEntity plot) {
        if (plot.herb() == null) return Pause.MISSING;
        Growth growth = liveGrowth(level, plot).orElse(null);
        if (growth == null) return Pause.MISSING;
        if (plot.age() >= growth.maxAge()) return Pause.CAPPED;
        if (!growth.condition().test(level, pos, FormulaContext.of(level))) return Pause.CONDITION;
        return Pause.NONE;
    }

    private static double growthAmount(ServerLevel level, BlockPos pos, Growth growth, FormulaContext formula) {
        double bonus = AuraService.getPositionAura(level, pos).rules().spiritPlantBonus();
        return SpiritHerbService.appliedGrowth(growth.growthRate(), formula, bonus);
    }

    private static boolean harvestable(ServerLevel level, SpiritHerbPlotBlockEntity plot) {
        Growth growth = liveGrowth(level, plot).orElse(null);
        return growth != null && plot.age() >= growth.matureAge();
    }

    private static boolean unresolved(ServerLevel level, SpiritHerbPlotBlockEntity plot) {
        return liveGrowth(level, plot).isEmpty();
    }

    private static Optional<Growth> liveGrowth(ServerLevel level, SpiritHerbPlotBlockEntity plot) {
        Holder<SpiritHerb> herb = plot.herb();
        if (herb == null) return Optional.empty();
        Identifier id = HolderHelper.id(herb);
        if (id.equals(HolderHelper.EMPTY)) return Optional.empty();
        return MxtDatapackRegistries.get(level.registryAccess(), MxtResourceKeys.SPIRIT_HERB, id).flatMap(SpiritHerb::growth);
    }

    private static Optional<Holder<SpiritHerb>> seedOf(net.minecraft.world.level.Level level, ItemStack stack) {
        return SpiritHerbService.findSeed(level.registryAccess(), stack);
    }

    private static boolean allowed(net.minecraft.world.entity.player.Player player, net.minecraft.world.level.Level level,
                                   BlockPos pos) {
        return player.mayBuild() && !player.blockActionRestricted(level, pos, player.gameMode());
    }
}
