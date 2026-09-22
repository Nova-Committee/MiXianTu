package com.iafenvoy.mxt.runtime.artifact;

import com.iafenvoy.mxt.attachment.ResourceHolderAttachment;
import com.iafenvoy.mxt.data.artifact.Artifact;
import com.iafenvoy.mxt.data.aura.Aura;
import com.iafenvoy.mxt.data.item.HoldBinding;
import com.iafenvoy.mxt.data.resource.Resource;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.runtime.artifact.ArtifactService.RefineResult;
import com.iafenvoy.mxt.runtime.hold.HoldLookup;
import com.iafenvoy.mxt.runtime.resource.ResourceService;
import com.iafenvoy.mxt.runtime.resource.ResourceService.Result;
import com.iafenvoy.mxt.util.DefinitionText;
import com.iafenvoy.mxt.util.HolderHelper;
import com.iafenvoy.mxt.util.TooltipText;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.Holder.Reference;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent.Finish;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent.Start;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent.Stop;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent.Tick;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickItem;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * What holding an artifact down does, one gesture per definition: an unclaimed artifact is claimed, one already
 * yours is poured into, one belonging to somebody else is not this module's click at all.
 *
 * <p>Claiming goes through {@link ArtifactService#refine} so the loot function, scripts and this gesture cannot
 * disagree about what a refined stack looks like - each pays the same price, which is the claim action's own
 * default. Only the server writes: the client half of the gesture belongs to the general hold module.
 */
@EventBusSubscriber
public final class ArtifactHoldService {
    // One whole unit a tick per declared aura, one for one: a definition declaring several auras just takes
    // longer to fill, and one that is full costs nothing.
    public static final int POUR_INTAKE_PER_TICK = 1;
    public static final double POUR_COST_PER_UNIT = 1.0D;

    // One line per holder, keyed by code rather than message because the message is rebuilt from the numbers.
    private static final Map<UUID, String> LAST_LINE = new ConcurrentHashMap<>();

    // A session is deliberately not one use cycle: vanilla starts a new cycle for as long as the button is held,
    // so a total cleared per cycle would restart from zero once per hold_ticks, which reads as the artifact having
    // been emptied. The total is kept while pour ticks keep arriving and starts over only after
    // SESSION_GAP_TICKS.
    private record Session(int total, long lastTick) {
    }

    // Far longer than the gap between two use cycles of one held button, far shorter than anybody picks an item
    // up again in.
    private static final long SESSION_GAP_TICKS = 40L;
    private static final Map<UUID, Session> POURED = new ConcurrentHashMap<>();

    private ArtifactHoldService() {
    }

    // Every artifact definition that declares a gesture, handed to the hold module once at construction.
    public static void initialize() {
        HoldLookup.register(registries -> MxtDatapackRegistries.holders(registries, MxtResourceKeys.ARTIFACT)
                .map(Reference::value)
                .map(ArtifactHold::new)
                .filter(ArtifactHold::requiresHold)
                .map(HoldBinding.class::cast)
                .toList());
    }

    // Nothing is cancelled: a stack this module does not take over is one the item itself answers, and swallowing
    // the click would take that away.
    @SubscribeEvent
    public static void onItemUse(RightClickItem event) {
        LivingEntity holder = event.getEntity();
        if (holder.level().isClientSide() || holder.isShiftKeyDown()) return;
        ItemStack stack = holder.getItemInHand(event.getHand());
        if (!(HoldLookup.hold(stack) instanceof ArtifactHold)) return;
        LAST_LINE.remove(holder.getUUID());
        if (!ArtifactService.hasOwner(stack)) return;
        if (!ArtifactService.isOwner(stack, holder.getUUID()))
            show(holder, Component.translatable("actionbar.mxt.artifact.claimed_by_other"), "other");
        else if (!ArtifactService.hasRoom(holder.level().registryAccess(), stack, FormulaContext.of(holder)))
            show(holder, Component.translatable("actionbar.mxt.artifact.pour_full"), "full");
    }

    // An unclaimed artifact is settled when the hold finishes, so nothing is poured while it is held. The pour
    // total is deliberately not cleared here: a cycle boundary is not a gesture boundary.
    @SubscribeEvent
    public static void onUseStart(Start event) {
        if (event.getEntity().level().isClientSide()) return;
        if (!(HoldLookup.hold(event.getItem()) instanceof ArtifactHold))
            POURED.remove(event.getEntity().getUUID());
    }

    // Server only: the aura is item state, and only the server may write it.
    @SubscribeEvent
    public static void onUseTick(Tick event) {
        LivingEntity holder = event.getEntity();
        if (holder.level().isClientSide()) return;
        ItemStack stack = event.getItem();
        if (!(HoldLookup.hold(stack) instanceof ArtifactHold(Artifact artifact))) return;
        // Claiming and pouring are two different gestures on the same input: the claim is what finishing the
        // hold does, so an unclaimed stack is left exactly as it is until then.
        if (!ArtifactService.hasOwner(stack) || !ArtifactService.isOwner(stack, holder.getUUID())) return;
        int moved = pour(holder, stack, holder.level().registryAccess(), artifact);
        if (moved <= 0) {
            // The two reasons are told apart rather than lumped together: an artifact that is full for everything
            // it declares says nothing, while one whose aura the holder cannot pay names that aura. Asking "is
            // there room anywhere" alone made a full second aura report as a shortfall.
            Holder<Aura> blocked = blockedAura(holder, stack, artifact);
            if (blocked != null)
                show(holder, Component.translatable("actionbar.mxt.charge.insufficient_aura",
                        DefinitionText.name(blocked, "aura")), "blocked:" + HolderHelper.id(blocked));
            return;
        }
        long gameTime = holder.level().getGameTime();
        Session previous = POURED.get(holder.getUUID());
        int total = (previous == null || gameTime - previous.lastTick() > SESSION_GAP_TICKS ? 0 : previous.total()) + moved;
        POURED.put(holder.getUUID(), new Session(total, gameTime));
        runPourAction(holder, stack);
        show(holder, Component.translatable("actionbar.mxt.artifact.pouring", total), "pouring:" + total);
    }

    // A hold released early never reaches here, which is what makes the claim require the full gesture. The
    // definition's use_action runs last either way. The pour total is reported but not cleared: the button may
    // still be held, and the next cycle continues the same session.
    @SubscribeEvent
    public static void onUseFinish(Finish event) {
        LivingEntity holder = event.getEntity();
        if (holder.level().isClientSide()) return;
        // The event's own stack is a copy of the item before it was used, so the live hand stack is the one to
        // work on - the same reason the technique module reads it the same way.
        ItemStack stack = holder.getItemInHand(holder.getUsedItemHand());
        if (stack.isEmpty()) stack = event.getItem();
        if (!(HoldLookup.hold(stack) instanceof ArtifactHold)) return;
        Provider access = holder.level().registryAccess();
        if (!ArtifactService.hasOwner(stack)) {
            ClaimResult result = claim(holder, stack, access);
            report(holder, stack, access, result);
            if (result == ClaimResult.CLAIMED) runUseAction(holder, stack);
            return;
        }
        Session poured = POURED.get(holder.getUUID());
        if (poured != null && poured.total() > 0)
            show(holder, Component.translatable("actionbar.mxt.artifact.poured", poured.total()), "poured");
        runUseAction(holder, stack);
    }

    @SubscribeEvent
    public static void onUseStop(Stop event) {
        if (event.getEntity().level().isClientSide()) return;
        POURED.remove(event.getEntity().getUUID());
    }

    // Naming the aura is what separates "this artifact is full" from "you cannot pay for it". Asked the same way
    // pour asks it, so the two cannot disagree about who is blocking.
    public static Holder<Aura> blockedAura(LivingEntity holder, ItemStack stack, Artifact artifact) {
        Provider access = holder.level().registryAccess();
        FormulaContext formula = FormulaContext.of(holder);
        // Read-only: a holder with no resource attachment has nothing to pay with, which is the same answer as an
        // empty pool, and asking must not create one on a player who never touched a resource.
        ResourceHolderAttachment resources = holder.getExistingData(MxtAttachments.RESOURCE_HOLDER).orElse(null);
        for (Holder<Aura> aura : artifact.spiritCapacity().keySet()) {
            int room = ArtifactService.capacity(access, stack, aura, 0.0D, formula) - ArtifactService.stored(stack, aura);
            if (room <= 0) continue;
            double pool = resources == null ? 0.0D : resources.get(aura.value().resource());
            // The first aura with room decides: if it can be paid the tick above would have moved something, so a
            // shortfall can only be somebody else's - and if it cannot, it is this one.
            return pool / POUR_COST_PER_UNIT >= 1.0D ? null : aura;
        }
        return null;
    }

    // No health check here: the cost belongs to the definition's claim_action, which refine runs for a binding
    // that was really written, so a cancelled claim costs nothing and a price that kills still claims. Health is
    // dealt as damage rather than subtracted, which is what lets protections and absorption behave normally and
    // makes an invulnerable holder's claim free.
    public static ClaimResult claim(LivingEntity holder, ItemStack stack, Provider access) {
        if (holder.level().isClientSide()) return ClaimResult.CLIENT_SIDE;
        if (ArtifactService.hasOwner(stack)) return ClaimResult.OWNED_BY_OTHER;
        if (!ArtifactService.mayClaim(access, stack, holder, FormulaContext.of(holder)))
            return ClaimResult.CONDITION_FAILED;
        RefineResult refined = ArtifactService.refine(stack, holder);
        if (refined == RefineResult.OWNED_BY_OTHER) return ClaimResult.OWNED_BY_OTHER;
        if (refined == RefineResult.CANCELLED) return ClaimResult.CANCELLED;
        return ClaimResult.CLAIMED;
    }

    // Once per tick that really moved aura into the artifact - the settlement of a pour rather than the gesture
    // around it. Public so a probe can drive one settlement without a use cycle.
    public static void runPourAction(LivingEntity holder, ItemStack stack) {
        if (holder.level().isClientSide()) return;
        ArtifactService.definition(holder.level().registryAccess(), stack).ifPresent(holder_ ->
                holder_.value().pourAction().execute(holder, stack, FormulaContext.of(holder)));
    }

    // A claim carried through, or the end of a pour. Nothing runs for a hold released early, because that
    // gesture settled nothing.
    public static void runUseAction(LivingEntity holder, ItemStack stack) {
        if (holder.level().isClientSide()) return;
        ArtifactService.definition(holder.level().registryAccess(), stack).ifPresent(holder_ ->
                holder_.value().useAction().execute(holder, stack, FormulaContext.of(holder)));
    }

    // A pool that cannot afford a unit skips that aura for this tick rather than failing the whole gesture, and a
    // payment is measured rather than assumed (a resource bound may trim what was asked for), so what is stored is
    // what was really paid.
    public static int pour(LivingEntity holder, ItemStack stack, Provider access, Artifact artifact) {
        FormulaContext formula = FormulaContext.of(holder);
        ResourceHolderAttachment resources = holder.getData(MxtAttachments.RESOURCE_HOLDER);
        int moved = 0;
        for (Holder<Aura> aura : artifact.spiritCapacity().keySet()) {
            int room = ArtifactService.capacity(access, stack, aura, 0.0D, formula) - ArtifactService.stored(stack, aura);
            if (room <= 0) continue;
            int units = Math.min(POUR_INTAKE_PER_TICK, room);
            Holder<Resource> resource = aura.value().resource();
            FormulaContext pool = ResourceService.formulaContext(holder, resource, formula);
            double before = resources.get(resource);
            units = Math.min(units, (int) Math.floor(before / POUR_COST_PER_UNIT));
            if (units <= 0) continue;
            Result paid = ResourceService.change(resources, resource, -(units * POUR_COST_PER_UNIT), pool);
            if (!paid.valid()) continue;
            units = Math.min(units, (int) Math.floor(Math.max(0.0D, before - paid.value()) / POUR_COST_PER_UNIT));
            if (units <= 0) continue;
            moved += ArtifactService.addEnergy(access, stack, aura, units, 0.0D, formula);
        }
        return moved;
    }

    // One line per ending rather than one per tick. The name and the price are re-read here rather than carried
    // in the result, because the definition is what knows both.
    private static void report(LivingEntity holder, ItemStack stack, Provider access, ClaimResult result) {
        FormulaContext context = FormulaContext.of(holder);
        switch (result) {
            case CLAIMED -> {
                String name = ArtifactService.definition(access, stack)
                        .map(definition -> DefinitionText.name(definition).getString())
                        .orElse(stack.getHoverName().getString());
                double cost = ArtifactService.claimHealthCost(access, stack, context);
                show(holder, cost > 0.0D
                        ? Component.translatable("actionbar.mxt.artifact.claimed", name, TooltipText.number(cost))
                        : Component.translatable("actionbar.mxt.artifact.claimed_free", name), "claimed");
            }
            case CONDITION_FAILED -> show(holder, Component.translatable("actionbar.mxt.artifact.claim_condition"), "condition");
            case OWNED_BY_OTHER -> show(holder, Component.translatable("actionbar.mxt.artifact.claimed_by_other"), "other");
            // Nothing to say: either the client asked, or somebody else owns this ending.
            case CANCELLED, CLIENT_SIDE -> {
            }
        }
    }

    // Writes one action-bar line per holder, and only when it says something the last one did not.
    private static void show(LivingEntity holder, MutableComponent line, String code) {
        if (!(holder instanceof ServerPlayer player)) return;
        if (code.equals(LAST_LINE.put(player.getUUID(), code))) return;
        player.sendSystemMessage(line.withStyle(ChatFormatting.AQUA), true);
    }

    public enum ClaimResult {
        CLAIMED, OWNED_BY_OTHER, CONDITION_FAILED, CANCELLED, CLIENT_SIDE
    }
}
