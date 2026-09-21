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
 * What holding an artifact down does, one gesture per artifact definition.
 *
 * <p>An artifact that has no owner is claimed by holding it down: the definition's own condition is asked, and
 * the binding is written through {@link ArtifactService#refine}, which is what runs the definition's
 * {@code claim_action} - the price included, since that action's default is the health it costs - so the loot
 * function, scripts and this gesture cannot disagree about what a refined stack looks like, and each of them
 * pays the same price. Holding an artifact that is already yours pours your own aura into it, one declared aura
 * at a time and one unit a tick, at the price the aura is counted in, and runs {@code pour_action} for every
 * tick that moved something. An artifact that belongs to somebody else is not this module's click at all:
 * {@link ArtifactHold#claims} declines it, the item answers the click itself, and all that is left here is to
 * say why nothing happened. Whatever the gesture did, a hold that ran to its end finishes with the definition's
 * {@code use_action}.</p>
 *
 * <p>Only the server writes. The client half of the gesture is the general hold module's - it arms the use
 * cycle, draws the pose and keeps it alive - and what a hold is worth is never decided there.</p>
 */
@EventBusSubscriber
public final class ArtifactHoldService {
    /**
     * How fast a held artifact is fed: one whole unit a tick, per declared aura, one for one. Nothing about the
     * store's shape is assumed by it - a definition that declares several auras is fed several units a tick and
     * simply takes longer to fill, and one that is full costs nothing.
     */
    public static final int POUR_INTAKE_PER_TICK = 1;
    public static final double POUR_COST_PER_UNIT = 1.0D;

    /**
     * The last action-bar line written to each holder, so a refusal repeated every tick of a hold is said once
     * and a running total that has not moved is not rewritten. Keyed by the code rather than the message,
     * because the message is rebuilt from the numbers each time.
     */
    private static final Map<UUID, String> LAST_LINE = new ConcurrentHashMap<>();

    /**
     * What one held-button session has poured so far, and when it last moved.
     *
     * <p>A session is deliberately not one use cycle. Vanilla starts a new cycle for as long as the button is
     * held, so a total cleared at every cycle boundary restarts from zero once per {@code hold_ticks} - which
     * the reader sees as the artifact having been emptied and refilled from scratch. The total is therefore
     * kept while pour ticks keep arriving, and starts over only after a pause long enough that the button can
     * only have been let go (see {@link #SESSION_GAP_TICKS}).
     */
    private record Session(int total, long lastTick) {
    }

    /**
     * How long a gap between two pour ticks means "a new gesture". Two seconds is far longer than the gap
     * between two use cycles of one held button, and far shorter than anybody picks an item up again in.
     */
    private static final long SESSION_GAP_TICKS = 40L;
    private static final Map<UUID, Session> POURED = new ConcurrentHashMap<>();

    private ArtifactHoldService() {
    }

    /**
     * Hands the hold module every artifact definition that declares a gesture, once, at construction.
     */
    public static void initialize() {
        HoldLookup.register(registries -> MxtDatapackRegistries.holders(registries, MxtResourceKeys.ARTIFACT)
                .map(Reference::value)
                .map(ArtifactHold::new)
                .filter(ArtifactHold::requiresHold)
                .map(HoldBinding.class::cast)
                .toList());
    }

    /**
     * Explains a click the gesture declined. Nothing is cancelled: a stack this module does not take over is one
     * the item itself answers, and swallowing the click here would take that away.
     */
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

    /**
     * Nothing is poured while an unclaimed artifact is held: that gesture is settled when it finishes. The pour
     * total is deliberately not cleared here - a cycle boundary is not a gesture boundary, see {@link Session}.
     */
    @SubscribeEvent
    public static void onUseStart(Start event) {
        if (event.getEntity().level().isClientSide()) return;
        if (!(HoldLookup.hold(event.getItem()) instanceof ArtifactHold))
            POURED.remove(event.getEntity().getUUID());
    }

    /**
     * One tick of the pour. Server only: the aura is item state, and only the server may write it.
     */
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
            // Nothing moved, and the two reasons are told apart rather than lumped together: an artifact that is
            // full for everything it declares has nothing left to do and says nothing, while one whose aura the
            // holder cannot pay names that aura. Asking "is there room anywhere" alone made a full second aura
            // report as a shortfall, which is the answer this replaces.
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

    /**
     * The end of a use cycle: a finished hold claims an unclaimed artifact, and reports what a pour moved. A
     * hold released early never reaches here, which is what makes the claim require the full gesture. Either way
     * the definition's {@code use_action} runs last, because a use that was carried through is what just
     * happened - whether it claimed the artifact or fed it.
     *
     * <p>The pour total is reported but not cleared: the button may still be held, and the next cycle then
     * continues the same session rather than starting the reader's count over.
     */
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

    /**
     * The first aura this definition declares that has room but that the holder cannot put a whole unit into,
     * or {@code null} when there is nothing to say.
     *
     * <p>This is the question the gesture used to answer with "is there room anywhere", which is also true for
     * an artifact whose <em>other</em> aura is the one that cannot be paid - so a defensive talisman holding a
     * full pool of one aura reported the reader's own aura as short. Naming the aura is what separates "this
     * artifact is full" from "you cannot pay for it", and the answer is deliberately asked the same way
     * {@link #pour} asks it, so the two cannot disagree about who is blocking.</p>
     */
    public static Holder<Aura> blockedAura(LivingEntity holder, ItemStack stack, Artifact artifact) {
        Provider access = holder.level().registryAccess();
        FormulaContext formula = FormulaContext.of(holder);
        // Read-only: a holder with no resource attachment at all has nothing to pay with, which is the same
        // answer as an empty pool, and asking must not create one on a player who never touched a resource.
        ResourceHolderAttachment resources = holder.getExistingData(MxtAttachments.RESOURCE_HOLDER).orElse(null);
        for (Holder<Aura> aura : artifact.spiritCapacity().keySet()) {
            int room = ArtifactService.capacity(access, stack, aura, 0.0D, formula) - ArtifactService.stored(stack, aura);
            if (room <= 0) continue;
            double pool = resources == null ? 0.0D : resources.get(aura.value().resource());
            // The first aura with room decides the answer: if it can be paid then the tick above would have
            // moved something, so a shortfall can only be somebody else's - and if it cannot, it is this one.
            return pool / POUR_COST_PER_UNIT >= 1.0D ? null : aura;
        }
        return null;
    }

    /**
     * Claims one artifact for one holder: the definition's own condition has to pass, and then the binding is
     * written. Nothing else is asked.
     *
     * <p>There is no health check here. What claiming costs is part of the definition's {@code claim_action},
     * which {@link ArtifactService#refine} runs for a binding that was really written - so a claim another mod
     * cancels costs nothing, and a holder a price kills has still claimed the artifact. Health is dealt as damage
     * rather than subtracted, which is what lets protections, absorption and death behave the way they do
     * everywhere else, and what makes an invulnerable holder's claim free since nothing can collect it.</p>
     */
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

    /**
     * The definition's {@code pour_action}, run once per tick that really moved aura into the artifact - the
     * settlement of a pour rather than the gesture around it. Public for the same reason {@link #pour} is: a
     * probe drives one settlement without a use cycle.
     */
    public static void runPourAction(LivingEntity holder, ItemStack stack) {
        if (holder.level().isClientSide()) return;
        ArtifactService.definition(holder.level().registryAccess(), stack).ifPresent(holder_ ->
                holder_.value().pourAction().execute(holder, stack, FormulaContext.of(holder)));
    }

    /**
     * The definition's {@code use_action}, run when this gesture finishes - a claim that was carried through, or
     * the end of a pour. Nothing runs for a hold released early, because that gesture settled nothing.
     */
    public static void runUseAction(LivingEntity holder, ItemStack stack) {
        if (holder.level().isClientSide()) return;
        ArtifactService.definition(holder.level().registryAccess(), stack).ifPresent(holder_ ->
                holder_.value().useAction().execute(holder, stack, FormulaContext.of(holder)));
    }

    /**
     * One tick of pouring: every aura the definition declares, one unit each, paid for out of the holder's own
     * pool of that aura's resource. Returns how many whole units were taken in, which is what the gesture
     * reports.
     *
     * <p>A pool that cannot afford a unit simply skips that aura for this tick instead of failing the whole
     * gesture, and a payment is measured rather than assumed - a resource bound may trim what was asked for -
     * so what is stored is what was really paid.</p>
     */
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

    /**
     * Says how the gesture ended, in one line per ending rather than one per tick. The name and the price are
     * re-read here rather than carried in the result, because the definition is what knows both.
     */
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

    /**
     * Writes one action-bar line per holder, and only when it says something the last one did not.
     */
    private static void show(LivingEntity holder, MutableComponent line, String code) {
        if (!(holder instanceof ServerPlayer player)) return;
        if (code.equals(LAST_LINE.put(player.getUUID(), code))) return;
        player.sendSystemMessage(line.withStyle(ChatFormatting.AQUA), true);
    }

    /**
     * How one attempt at claiming ended, which is what a probe asserts without a use cycle.
     */
    public enum ClaimResult {
        CLAIMED, OWNED_BY_OTHER, CONDITION_FAILED, CANCELLED, CLIENT_SIDE
    }
}
