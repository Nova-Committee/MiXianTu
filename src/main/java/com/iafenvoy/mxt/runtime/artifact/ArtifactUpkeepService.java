package com.iafenvoy.mxt.runtime.artifact;

import com.iafenvoy.mxt.attachment.ResourceHolderAttachment;
import com.iafenvoy.mxt.compat.CuriosIntegration;
import com.iafenvoy.mxt.data.artifact.ability.UpkeepArtifactAbility;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.runtime.resource.ResourceTransactions;
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
 * The periodic price of carrying an artifact: what an {@code mxt:upkeep} entry charges whoever holds it.
 *
 * <p>Once a server tick, every carried artifact - both hands and every equipped Curios slot - is asked for its
 * upkeep entry, and one whose game time falls on its interval pays. The clock is the world's rather than a
 * per-stack counter, which is what keeps a definition's cadence identical for client and server and leaves no
 * state behind on the stack; the consequence is that a price falls on every tick whose game time is a multiple
 * of the interval, not "interval ticks after the artifact was picked up".</p>
 *
 * <p>Paying is all or nothing, through the shared resource transaction, so a definition that names several
 * resources never takes half of them. A price that cannot be paid is not a refusal to do anything else: the
 * entry's own {@code on_fail} action runs, which is where a backlash or a wear cost belongs. Nothing here is
 * decided by the holder's health, and nothing is written to the artifact unless that action writes it.</p>
 */
@EventBusSubscriber
public final class ArtifactUpkeepService {
    /** A clock that cannot produce a shorter interval, so a division is never the reason a price is skipped. */
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

    /**
     * The artifact stacks an entity actually carries: the two hands plus every equipped Curios stack. Live
     * stacks rather than copies, because a failure action is allowed to write to the artifact it belongs to.
     */
    private static List<ItemStack> carried(LivingEntity holder) {
        List<ItemStack> stacks = new ArrayList<>(4);
        stacks.add(holder.getMainHandItem());
        stacks.add(holder.getOffhandItem());
        stacks.addAll(CuriosIntegration.equippedLive(holder));
        return stacks;
    }

    /**
     * Charges one artifact for one tick, reporting whether a price was really paid.
     *
     * <p>Public so a probe can drive one settlement without a server tick, the same reason
     * {@link ArtifactHoldService#claim} and {@link ArtifactHoldService#pour} are. An artifact whose entry asks
     * for nobody in particular, whose definition declares no costs, that is not yet the holder's when the entry
     * asks for an owner, or whose clock is between intervals, is left exactly as it is.</p>
     */
    public static boolean upkeep(LivingEntity holder, ItemStack stack, long gameTime) {
        UpkeepArtifactAbility upkeep = ArtifactService.upkeep(holder.level().registryAccess(), stack).orElse(null);
        if (upkeep == null || upkeep.costs().isEmpty()) return false;
        if (upkeep.ownerOnly() && !ArtifactService.isOwner(stack, holder.getUUID())) return false;
        FormulaContext context = FormulaContext.of(holder);
        long interval = interval(upkeep, context);
        if (gameTime % interval != 0L) return false;
        ResourceHolderAttachment resources = holder.getData(MxtAttachments.RESOURCE_HOLDER);
        ResourceTransactions.Result payment;
        try {
            payment = ResourceTransactions.tryConsume(holder, resources,
                    ResourceTransactions.evaluate(holder, upkeep.costs(), context));
        } catch (IllegalArgumentException | IllegalStateException exception) {
            // A price that cannot be a price is a pack mistake; it is reported where the definition is read, and
            // a tick that cannot be charged must not silently read as one that was.
            return false;
        }
        if (payment.committed()) return true;
        upkeep.onFail().execute(holder, stack, context);
        return false;
    }

    /**
     * How long this definition's interval is, in whole ticks. A declaration that evaluates to nothing usable
     * falls back to the entry's own default rather than to "every tick", so a broken formula cannot turn a
     * gentle price into a drain.
     */
    private static long interval(UpkeepArtifactAbility upkeep, FormulaContext context) {
        double value = upkeep.interval().evaluate(context);
        if (!Double.isFinite(value) || value < (double) MIN_INTERVAL || value > (double) Long.MAX_VALUE)
            return (long) UpkeepArtifactAbility.DEFAULT_INTERVAL;
        return Math.max(MIN_INTERVAL, Math.round(value));
    }
}
