package com.iafenvoy.mxt.probe.artifact;

import com.iafenvoy.mxt.attachment.ResourceHolderAttachment;
import com.iafenvoy.mxt.data.artifact.Artifact;
import com.iafenvoy.mxt.data.aura.Aura;
import com.iafenvoy.mxt.probe.Probe;
import com.iafenvoy.mxt.probe.ProbeContext;
import com.iafenvoy.mxt.probe.ProbeDefinitions;
import com.iafenvoy.mxt.probe.ProbeKit;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.runtime.artifact.ArtifactHold;
import com.iafenvoy.mxt.runtime.artifact.ArtifactHoldService;
import com.iafenvoy.mxt.runtime.artifact.ArtifactHoldService.ClaimResult;
import com.iafenvoy.mxt.runtime.artifact.ArtifactService;
import com.iafenvoy.mxt.runtime.artifact.ArtifactService.RefineResult;
import com.iafenvoy.mxt.runtime.artifact.ArtifactUpkeepService;
import com.iafenvoy.mxt.runtime.cultivation.CultivationIdentityService;
import com.iafenvoy.mxt.runtime.hold.HoldLookup;
import com.iafenvoy.mxt.runtime.resource.ResourceService;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Holder.Reference;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The long press and what it settles: what each definition declares, who it takes over for, the price of a claim, the
 * pour of aura into a carried artifact, the two gesture-shaped actions and the periodic upkeep a carried entry asks.
 */
public final class ArtifactHoldProbes {
    private ArtifactHoldProbes() {
    }

    public static List<Probe> probes() {
        return List.of(
                Probe.of("artifact/hold", ArtifactHoldProbes::hold),
                Probe.of("artifact/hold/takeover", ArtifactHoldProbes::takeover),
                Probe.of("artifact/hold/claim", ArtifactHoldProbes::claim),
                Probe.of("artifact/hold/pour", ArtifactHoldProbes::pour),
                Probe.kitted("artifact/hold/pour/feedback", ArtifactHoldProbes::pourFeedback),
                Probe.of("artifact/hold/gesture_actions", ArtifactHoldProbes::gestureActions),
                Probe.of("artifact/upkeep", ArtifactHoldProbes::upkeep));
    }

    // The long press: a definition that declares `hold_ticks: 0` is not a hold, so nothing captures the item.
    // Driven through the lookup rather than a real use cycle, because the arming half belongs to the hold module.
    private static void hold(ProbeContext context) {
        ItemStack flightStack = new ItemStack(Items.IRON_SWORD);
        ItemStack dormantStack = new ItemStack(Items.GUNPOWDER);
        boolean declaredHold = HoldLookup.hold(dormantStack) == null
                && HoldLookup.hold(flightStack) instanceof ArtifactHold;
        context.check("artifact hold hold_ticks=0 is not captured and a hold is", declaredHold);
    }

    // The entity-aware half of the hold: an unclaimed artifact is on offer to anybody, a claimed one only to its
    // owner, and one that can take no more aura advertises nothing at all.
    private static void takeover(ProbeContext context) {
        Provider access = context.registries();
        ServerPlayer player = context.player();
        FormulaContext formula = FormulaContext.of(player);
        Holder<Aura> qi = context.require(MxtResourceKeys.AURA, ProbeDefinitions.QI);
        Holder<Aura> waterPower = context.require(MxtResourceKeys.AURA, ProbeDefinitions.WATER_POWER);
        Holder<Aura> soulPower = context.require(MxtResourceKeys.AURA, ProbeDefinitions.SOUL_POWER);
        ItemStack flightStack = new ItemStack(Items.IRON_SWORD);
        ItemStack ownedHoldSword = flightStack.copy();
        ArtifactService.refine(ownedHoldSword, player);
        // Filling every declared aura is what makes the pour pointless, so the gesture must stop being offered.
        ItemStack fullJade = new ItemStack(Items.AMETHYST_SHARD);
        ArtifactService.refine(fullJade, player);
        for (int round = 0; round < 3; round++) {
            ArtifactService.addEnergy(access, fullJade, qi, 1000.0D, 0.0D, formula);
            ArtifactService.addEnergy(access, fullJade, waterPower, 1000.0D, 0.0D, formula);
            ArtifactService.addEnergy(access, fullJade, soulPower, 1000.0D, 0.0D, formula);
        }
        LivingEntity holdBystander = spawnProbe(context, context.playerSpot().above(2), null);
        boolean takeover = HoldLookup.hold(flightStack) instanceof ArtifactHold unownedHold
                // Unclaimed: the claim is on offer to anybody, so both the player and a probe entity take it over.
                && unownedHold.claims(player, access, flightStack)
                && holdBystander != null && unownedHold.claims(holdBystander, access, flightStack)
                // Claimed: the owner is taken over and the stranger is not - the whole point of the entity-aware
                // answer, since the stack says nothing about who is holding it.
                && unownedHold.claims(player, access, ownedHoldSword)
                && !unownedHold.claims(holdBystander, access, ownedHoldSword)
                && HoldLookup.hold(fullJade) instanceof ArtifactHold fullHold
                && !fullHold.claims(player, access, fullJade);
        if (holdBystander != null) holdBystander.discard();
        context.check("artifact hold takeover unclaimed=both claimed=owner-only full=refused", takeover);
    }

    // Claiming pays the price and then runs the action, and the two are separate fields: the blood fixture states
    // both, the jade replaces the price with a no-op to bind for free.
    private static void claim(ProbeContext context) {
        Provider access = context.registries();
        ServerPlayer player = context.player();
        Holder<Aura> qi = context.require(MxtResourceKeys.AURA, ProbeDefinitions.QI);
        // The priced claim is health, so the body has to be able to bleed; it also must not have been hurt in the
        // last ten ticks, because vanilla absorbs a second hit inside that window. That is why this leg is not
        // kitted: handing out the scenario's artifacts claims them, and a claim charges health.
        player.getAbilities().invulnerable = false;
        player.onUpdateAbilities();
        player.setHealth(player.getMaxHealth());
        float healthBeforeClaim = player.getHealth();
        ItemStack claimBlood = new ItemStack(Items.BLAZE_ROD);
        ClaimResult claimedHold = ArtifactHoldService.claim(player, claimBlood, access);
        double claimedStored = ArtifactService.stored(claimBlood, qi);
        float claimedHealth = player.getHealth();
        // What this leg can pin exactly is that the claim_action ran (five points arrived) and that it charged the
        // body at all. The exact number cannot be: the price is dealt as damage, and both of the fixture's two
        // prices land in one tick, so vanilla's invulnerability window absorbs the second - and the pack's own
        // element mapping turns magic into fire, so an accumulated attachment can add a reaction on top.
        boolean pricePaid = claimedHold == ClaimResult.CLAIMED && ArtifactService.isOwner(claimBlood, player.getUUID())
                && claimedStored == 5
                && claimedHealth < healthBeforeClaim;
        // A price replaced by mxt:no_op binds for free, at health a priced claim would have spent.
        player.setHealth(4.0F);
        ItemStack freeJade = new ItemStack(Items.AMETHYST_SHARD);
        ClaimResult freeHold = ArtifactHoldService.claim(player, freeJade, access);
        boolean freeClaim = freeHold == ClaimResult.CLAIMED && ArtifactService.isOwner(freeJade, player.getUUID())
                && close(player.getHealth(), 4.0D);
        // No health pre-check any more: a price the holder cannot survive is charged anyway and the binding still
        // stands. Driven on a disposable probe, since the alternative is killing the player mid-run.
        LivingEntity poorClaimant = spawnProbe(context, context.playerSpot().above(3), null);
        boolean unguarded = false;
        if (poorClaimant != null) {
            poorClaimant.setHealth(2.0F);
            ItemStack doomedSword = new ItemStack(Items.IRON_SWORD);
            ClaimResult doomed = ArtifactHoldService.claim(poorClaimant, doomedSword, access);
            unguarded = doomed == ClaimResult.CLAIMED && ArtifactService.isOwner(doomedSword, poorClaimant.getUUID())
                    && (poorClaimant.isDeadOrDying() || close(poorClaimant.getHealth(), 0.0D));
            poorClaimant.discard();
        }
        // The definition's own condition is asked before either action runs: this fixture states a price of one,
        // and a refused binding does not pay it.
        player.setHealth(4.0F);
        ItemStack sealedStack = new ItemStack(Items.GLOWSTONE_DUST);
        ClaimResult sealedHold = ArtifactHoldService.claim(player, sealedStack, access);
        boolean sealedRefused = sealedHold == ClaimResult.CONDITION_FAILED && !ArtifactService.hasOwner(sealedStack)
                && close(player.getHealth(), 4.0F);
        // Charging belongs to the actions, so every writer of a binding pays it - the loot function and a script
        // reach `refine` directly. Health is topped up first, because this path is guarded by nothing, and the
        // invulnerability window the first claim left behind is cleared: two priced claims in one tick is not
        // something a player can do, and leaving it would let vanilla absorb this price as if it were free.
        player.setHealth(player.getMaxHealth());
        player.invulnerableTime = 0;
        float healthBeforeScript = player.getHealth();
        ItemStack scriptedBlood = new ItemStack(Items.BLAZE_ROD);
        RefineResult scriptedRefine = ArtifactService.refine(scriptedBlood, player);
        double scriptStored = ArtifactService.stored(scriptedBlood, qi);
        float scriptHealth = player.getHealth();
        boolean scriptPaid = scriptedRefine == RefineResult.REFINED
                && scriptStored == 5
                && scriptHealth < healthBeforeScript;
        player.setHealth(healthBeforeClaim);
        // Split so the log names which price went unpaid rather than only that one did.
        context.check("artifact hold claim: the priced claim landed (" + claimedHold + ", stored " + claimedStored
                + ", health " + healthBeforeClaim + " -> " + claimedHealth + ")", pricePaid);
        context.check("artifact hold claim: a no-op price binds for free", freeClaim);
        context.check("artifact hold claim: a price that would kill is charged anyway", unguarded);
        context.check("artifact hold claim: the definition's condition is asked first", sealedRefused);
        context.check("artifact hold claim: refine pays the same price (" + scriptedRefine + ", stored " + scriptStored
                + ", health " + healthBeforeScript + " -> " + scriptHealth + ")", scriptPaid);
        context.check("artifact hold claim", pricePaid && freeClaim && unguarded && sealedRefused && scriptPaid);
    }

    // The pour moves one unit of every declared aura out of the holder's own pools, one for one. The jade's
    // claim_action charged 40 of qi on the way in, so the tick starts from there.
    private static void pour(ProbeContext context) {
        Provider access = context.registries();
        ServerPlayer player = context.player();
        Holder<Aura> qi = context.require(MxtResourceKeys.AURA, ProbeDefinitions.QI);
        Holder<Aura> waterPower = context.require(MxtResourceKeys.AURA, ProbeDefinitions.WATER_POWER);
        Holder<Aura> soulPower = context.require(MxtResourceKeys.AURA, ProbeDefinitions.SOUL_POWER);
        ItemStack pourStack = new ItemStack(Items.AMETHYST_SHARD);
        ArtifactService.refine(pourStack, player);
        ResourceHolderAttachment pools = player.getData(MxtAttachments.RESOURCE_HOLDER);
        ProbeKit.ensureResource(player, pools, qi.value().resource(), 10.0D);
        ProbeKit.ensureResource(player, pools, waterPower.value().resource(), 10.0D);
        ProbeKit.ensureResource(player, pools, soulPower.value().resource(), 10.0D);
        double qiPool = pools.get(qi.value().resource());
        double waterPool = pools.get(waterPower.value().resource());
        double soulPool = pools.get(soulPower.value().resource());
        double poured = ArtifactHoldService.pour(player, pourStack, access,
                ArtifactService.definition(access, pourStack).map(Reference::value).orElseThrow());
        boolean pourLeg = poured == 3
                && ArtifactService.stored(pourStack, qi) == 41
                && ArtifactService.stored(pourStack, waterPower) == 1
                && ArtifactService.stored(pourStack, soulPower) == 1
                && close(pools.get(qi.value().resource()), qiPool - 1.0D)
                && close(pools.get(waterPower.value().resource()), waterPool - 1.0D)
                && close(pools.get(soulPower.value().resource()), soulPool - 1.0D);
        context.check("artifact hold pour units=" + poured + " qi=" + ArtifactService.stored(pourStack, qi)
                + " water=" + ArtifactService.stored(pourStack, waterPower), pourLeg);
    }

    // The two gesture-shaped actions: pour_action settles every tick that really moved aura, use_action
    // closes a gesture that ran to its end. Driven through the methods the event handlers call.
    private static void gestureActions(ProbeContext context) {
        ServerPlayer player = context.player();
        Holder<Aura> qi = context.require(MxtResourceKeys.AURA, ProbeDefinitions.QI);
        ItemStack pourStack = new ItemStack(Items.AMETHYST_SHARD);
        ArtifactService.refine(pourStack, player);
        double beforePourAction = ArtifactService.stored(pourStack, qi);
        ArtifactHoldService.runPourAction(player, pourStack);
        double beforeUseAction = ArtifactService.stored(pourStack, qi);
        ArtifactHoldService.runUseAction(player, pourStack);
        boolean gestureActions = beforeUseAction == beforePourAction + 1
                && ArtifactService.stored(pourStack, qi) == beforeUseAction + 2;
        context.check("artifact gesture actions pour=+" + (beforeUseAction - beforePourAction)
                + " use=+" + (ArtifactService.stored(pourStack, qi) - beforeUseAction), gestureActions);
    }

    // A pour with nothing to move has two reasons, and they are told apart: an artifact full for everything
    // it declares says nothing, while one whose aura the holder cannot pay names it.
    private static void pourFeedback(ProbeContext context) {
        Provider access = context.registries();
        ServerPlayer player = context.player();
        FormulaContext formula = FormulaContext.of(player);
        Holder<Aura> qi = context.require(MxtResourceKeys.AURA, ProbeDefinitions.QI);
        ItemStack fullWard = new ItemStack(Items.PRISMARINE_SHARD);
        ArtifactService.addEnergy(access, fullWard, qi, 1000.0D, 0.0D, formula);
        Artifact fullWardDefinition = ArtifactService.definition(access, fullWard).map(Reference::value).orElseThrow();
        boolean fullSilent = ArtifactHoldService.blockedAura(player, fullWard, fullWardDefinition) == null;
        ResourceHolderAttachment blockPools = player.getData(MxtAttachments.RESOURCE_HOLDER);
        ResourceService.change(blockPools, qi.value().resource(), -blockPools.get(qi.value().resource()),
                ResourceService.formulaContext(player, qi.value().resource(), formula));
        ItemStack emptyWard = new ItemStack(Items.PRISMARINE_SHARD);
        Artifact emptyWardDefinition = ArtifactService.definition(access, emptyWard).map(Reference::value).orElseThrow();
        boolean namedShortfall = ArtifactHoldService.blockedAura(player, emptyWard, emptyWardDefinition) == qi;
        context.check("artifact pour feedback full=" + fullSilent + " named=" + namedShortfall,
                fullSilent && namedShortfall);
    }

    // The periodic price is an ability entry rather than a claim field: it falls due on its own interval while the
    // artifact is carried, asks nobody but the owner when the entry says owner_only, and runs on_fail when unpaid.
    private static void upkeep(ProbeContext context) {
        ServerPlayer player = context.player();
        FormulaContext formula = FormulaContext.of(player);
        Holder<Aura> waterPower = context.require(MxtResourceKeys.AURA, ProbeDefinitions.WATER_POWER);
        ItemStack upkeepStack = new ItemStack(Items.IRON_SHOVEL);
        ArtifactService.refine(upkeepStack, player);
        ResourceHolderAttachment upkeepPools = player.getData(MxtAttachments.RESOURCE_HOLDER);
        ProbeKit.ensureResource(player, upkeepPools, waterPower.value().resource(), 10.0D);
        double upkeepPool = upkeepPools.get(waterPower.value().resource());
        boolean upkeepPaid = ArtifactUpkeepService.upkeep(player, upkeepStack, 10L)
                && close(upkeepPool - upkeepPools.get(waterPower.value().resource()), 2.0D)
                // A tick between two intervals is nobody's, so nothing falls due on it.
                && !ArtifactUpkeepService.upkeep(player, upkeepStack, 11L)
                && close(upkeepPool - upkeepPools.get(waterPower.value().resource()), 2.0D);
        FormulaContext upkeepContext = ResourceService.formulaContext(player, waterPower.value().resource(), formula);
        ResourceService.change(upkeepPools, waterPower.value().resource(),
                -upkeepPools.get(waterPower.value().resource()), upkeepContext);
        boolean upkeepFailed = !ArtifactUpkeepService.upkeep(player, upkeepStack, 15L) && upkeepStack.getDamageValue() == 1;
        LivingEntity upkeepBystander = spawnProbe(context, context.playerSpot().above(4), null);
        boolean upkeepOwnerOnly = upkeepBystander != null
                && !ArtifactUpkeepService.upkeep(upkeepBystander, upkeepStack, 20L) && upkeepStack.getDamageValue() == 1;
        if (upkeepBystander != null) upkeepBystander.discard();
        context.check("artifact upkeep paid=" + upkeepPaid + " fail=" + upkeepFailed
                + " owner_only=" + upkeepOwnerOnly, upkeepPaid && upkeepFailed && upkeepOwnerOnly);
    }

    // One disposable probe entity carrying exactly one spirit root, so the element it strikes or is struck with is
    // never a question. These legs pass a null root, which leaves it carrying none.
    private static LivingEntity spawnProbe(ProbeContext context, BlockPos pos, @Nullable Identifier root) {
        Pig pig = EntityType.PIG.create(context.level(), EntitySpawnReason.COMMAND);
        if (pig == null) return null;
        pig.setNoAi(true);
        pig.setPos(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
        context.level().addFreshEntity(pig);
        if (root != null)
            CultivationIdentityService.grantSpiritRoot(pig, context.require(MxtResourceKeys.SPIRIT_ROOT, root));
        return pig;
    }

    private static boolean close(double actual, double expected) {
        return Math.abs(actual - expected) < 1.0E-3D;
    }
}
