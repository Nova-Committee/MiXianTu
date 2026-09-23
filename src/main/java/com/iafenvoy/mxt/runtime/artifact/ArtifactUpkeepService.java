package com.iafenvoy.mxt.runtime.artifact;

import com.iafenvoy.mxt.compat.CuriosIntegration;
import com.iafenvoy.mxt.data.ability.type.UpkeepAbilityType;
import com.iafenvoy.mxt.data.cost.Cost;
import com.iafenvoy.mxt.data.cost.CostTransaction;
import com.iafenvoy.mxt.data.cost.context.CostContext;
import com.iafenvoy.mxt.data.cost.context.CostOrigin;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * The periodic price of carrying an artifact, charged once a server tick to every carried artifact. The clock is
 * the world's game time rather than a per-stack counter, which keeps a definition's cadence identical for client
 * and server and leaves no state on the stack - so a price falls on every tick whose game time is a multiple of
 * the interval, not "interval ticks after the artifact was picked up".
 *
 * <p>Paying is all or nothing, through the shared resource transaction, so a definition naming several resources
 * never takes half of them. An unpayable price runs the entry's own {@code on_fail} action, which is where a
 * backlash or a wear cost belongs.
 */
@EventBusSubscriber
public final class ArtifactUpkeepService {
    // A clock that cannot produce a shorter interval, so a division is never the reason a price is skipped.
    private static final long MIN_INTERVAL = 1L;

    private ArtifactUpkeepService() {
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            long gameTime = player.level().getGameTime();
            for (ItemStack stack : carried(player)) upkeep(player, stack, gameTime);
        }
    }

    // Both hands plus every equipped Curios stack. Live stacks rather than copies, because a failure action may
    // write to the artifact it belongs to. Public because the damage pipeline reads the same definition to get a
    // victim's item-side factors.
    public static List<ItemStack> carried(LivingEntity holder) {
        List<ItemStack> stacks = new ArrayList<>(4);
        stacks.add(holder.getMainHandItem());
        stacks.add(holder.getOffhandItem());
        stacks.addAll(CuriosIntegration.equippedLive(holder));
        return stacks;
    }

    // Public so a probe can drive one settlement without a server tick. An artifact whose entry asks for nobody in
    // particular, whose costs are empty, that is not yet the holder's when the entry asks for an owner, or whose
    // clock is between intervals, is left exactly as it is.
    public static boolean upkeep(LivingEntity holder, ItemStack stack, long gameTime) {
        ArtifactService.Upkeep upkeep = ArtifactService.upkeep(holder.level().registryAccess(), stack).orElse(null);
        if (upkeep == null) return false;
        List<Cost> costs = upkeep.ability().value().costs();
        if (costs.isEmpty()) return false;
        if (upkeep.type().ownerOnly() && !ArtifactService.isOwner(stack, holder.getUUID())) return false;
        FormulaContext context = FormulaContext.of(holder);
        long interval = interval(upkeep.type(), context);
        if (gameTime % interval != 0L) return false;
        CostTransaction.PayResult payment = CostTransaction.pay(costs,
                CostContext.of(holder, context, CostOrigin.ARTIFACT_UPKEEP));
        if (payment.paid()) return true;
        upkeep.type().onFail().execute(holder, stack, context);
        return false;
    }

    // A declaration that evaluates to nothing usable falls back to the entry's own default rather than to "every
    // tick", so a broken formula cannot turn a gentle price into a drain.
    private static long interval(UpkeepAbilityType upkeep, FormulaContext context) {
        double value = upkeep.interval().evaluate(context);
        if (!Double.isFinite(value) || value < (double) MIN_INTERVAL || value > (double) Long.MAX_VALUE)
            return (long) UpkeepAbilityType.DEFAULT_INTERVAL;
        return Math.max(MIN_INTERVAL, Math.round(value));
    }
}
