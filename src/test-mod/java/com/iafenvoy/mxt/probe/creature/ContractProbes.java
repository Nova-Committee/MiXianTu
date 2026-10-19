package com.iafenvoy.mxt.probe.creature;

import com.iafenvoy.mxt.api.Contractable;
import com.iafenvoy.mxt.attachment.AbilityAttachment;
import com.iafenvoy.mxt.attachment.CreatureSpiritAttachment;
import com.iafenvoy.mxt.attachment.ResourceHolderAttachment;
import com.iafenvoy.mxt.data.action.NoOpAction;
import com.iafenvoy.mxt.data.action.builtin.entity.SetNoGravityAction;
import com.iafenvoy.mxt.data.creature.*;
import com.iafenvoy.mxt.data.item.ContractBellComponent;
import com.iafenvoy.mxt.data.item.SpiritBeastComponent;
import com.iafenvoy.mxt.data.resource.Resource;
import com.iafenvoy.mxt.probe.Probe;
import com.iafenvoy.mxt.probe.ProbeContext;
import com.iafenvoy.mxt.probe.ProbeDefinitions;
import com.iafenvoy.mxt.registry.*;
import com.iafenvoy.mxt.runtime.ability.AbilityGrantService;
import com.iafenvoy.mxt.runtime.creature.*;
import com.iafenvoy.mxt.runtime.failure.ContractFailure;
import com.iafenvoy.mxt.runtime.wheel.WheelEntryKinds;
import com.iafenvoy.mxt.runtime.wheel.WheelService;
import com.iafenvoy.mxt.runtime.wheel.WheelSourceTypes;
import com.iafenvoy.mxt.testmod.MxtTestEntities;
import com.iafenvoy.mxt.testmod.ProbeBeast;
import com.iafenvoy.mxt.util.HolderHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

import java.util.ArrayList;
import java.util.List;
import com.iafenvoy.mxt.runtime.failure.Failures;

/**
 * The contract lifecycle, driven through the interfaces a creature implements itself: eligibility, the price before
 * the record, the owner side, the recall latch, and the bell, the wheel and the carrying bag that reach the beast.
 */
public final class ContractProbes {
    private ContractProbes() {
    }

    public static List<Probe> probes() {
        return List.of(
                Probe.kitted("creature/contract/eligibility", ContractProbes::eligibility),
                Probe.kitted("creature/contract/bind", ContractProbes::bind),
                Probe.kitted("creature/contract/limit", ContractProbes::limit),
                Probe.kitted("creature/contract/recall", ContractProbes::recall),
                Probe.kitted("creature/contract/bell", ContractProbes::bell),
                Probe.kitted("creature/contract/input", ContractProbes::input),
                Probe.kitted("creature/contract/orders", ContractProbes::orders),
                Probe.kitted("creature/contract/bag", ContractProbes::bag),
                Probe.kitted("creature/contract/ending", ContractProbes::ending));
    }

    // Eligibility is code: a vanilla creature implements nothing, so no record ever reaches it, while the
    // contract type's own entity type tag narrows the list among the creatures that do implement it.
    private static void eligibility(ProbeContext context) {
        ServerLevel level = context.level();
        ServerPlayer player = context.player();
        MinecraftServer server = context.server();
        ProbeBeast.reset();
        // The index outlives the probe entities a previous run discarded, so the leg starts from a clean list.
        BoundBeastService.clear(server, player.getUUID());
        List<Mob> spawned = new ArrayList<>();
        try {
            Holder<ContractType> tagged = context.require(MxtResourceKeys.CONTRACT_TYPE, ProbeDefinitions.CONTRACT);
            Holder<ContractType> open = context.require(MxtResourceKeys.CONTRACT_TYPE, ProbeDefinitions.OPEN_CONTRACT);

            Wolf wolf = EntityType.WOLF.create(level, EntitySpawnReason.COMMAND);
            if (wolf == null) {
                context.fail("contract probe: could not create the vanilla creature");
                return;
            }
            spawned.add(wolf);
            boolean vanillaRefused = ContractService.bind(tagged, player, wolf, false).failure()
                    == ContractFailure.NOT_CONTRACTABLE;
            boolean tagNarrows = !Contractable.acceptsContractByTag(context(wolf, player, tagged))
                    && Contractable.acceptsContractByTag(context(wolf, player, open));
            boolean eligibility = vanillaRefused && tagNarrows;
            context.note("contract probe: vanilla_refused=" + vanillaRefused
                    + " tag_narrows=" + tagNarrows + (eligibility ? " OK" : " MISMATCH"));
            context.check("contract eligibility: vanilla_refused", vanillaRefused);
            context.check("contract eligibility: tag_narrows", tagNarrows);
            context.check("contract eligibility", eligibility);
        } finally {
            BoundBeastService.clear(server, player.getUUID());
            AbilityGrantService.recalculate(player);
            for (Mob mob : spawned) mob.discard();
        }
    }

    // Price before the record: a beast that cannot pay is not bound, nothing is charged and no row is written;
    // paying leaves exactly the price behind and a record that names the owner.
    private static void bind(ProbeContext context) {
        ServerLevel level = context.level();
        ServerPlayer player = context.player();
        MinecraftServer server = context.server();
        ProbeBeast.reset();
        BoundBeastService.clear(server, player.getUUID());
        List<Mob> spawned = new ArrayList<>();
        ResourceHolderAttachment resources = player.getData(MxtAttachments.RESOURCE_HOLDER);
        AbilityAttachment abilities = player.getData(MxtAttachments.ABILITY_HOLDER);
        Holder<Resource> qi = context.require(MxtResourceKeys.RESOURCE, ProbeDefinitions.QI);
        double previousQi = resources.get(qi);
        try {
            Holder<ContractType> tagged = context.require(MxtResourceKeys.CONTRACT_TYPE, ProbeDefinitions.CONTRACT);

            resources.set(qi, 3.0D, 0.0D, 10_000.0D, -1L, "probe");
            ProbeBeast beast = spawnProbeBeast(level, player.blockPosition().offset(6, 0, 0));
            spawned.add(beast);
            boolean priceRefused = ContractService.bind(tagged, player, beast, false).failure()
                    == Failures.INSUFFICIENT_COST
                    && !beast.getData(MxtAttachments.CONTRACT).bound()
                    && close(resources.get(qi), 3.0D)
                    && BoundBeastService.of(server, player.getUUID()).isEmpty();
            resources.set(qi, 5.0D, 0.0D, 10_000.0D, -1L, "probe");
            ContractService.Result secondBind = ContractService.bind(tagged, player, beast, false);
            // The pieces are printed, not just the aggregate: the owner lookup and the failure code are what tell
            // a refused payment apart from a bind that never reached the record.
            boolean ownerRecorded = Contracts.ownerOf(beast).filter(player.getUUID()::equals).isPresent();
            boolean vanillaOwner = beast.getOwner() == player;
            int rows = BoundBeastService.of(server, player.getUUID()).size();
            boolean bound = secondBind.changed() && close(resources.get(qi), 0.0D)
                    && ownerRecorded && vanillaOwner && rows == 1;
            ItemStack probeCore = beast.getData(MxtAttachments.CREATURE_SPIRIT).innerCore();
            // A profile's spawn action is one action, and the profile is written once, so it runs once: the probe
            // beast is left weightless by its own definition rather than by probe code.
            CreatureProfile beastProfile = MxtDatapackRegistries.get(context.registries(), MxtResourceKeys.CREATURE_PROFILE,
                    ProbeDefinitions.PROBE_BEAST_PROFILE).orElse(null);
            boolean spawnAction = beastProfile != null && beastProfile.spawnAction() instanceof SetNoGravityAction
                    && beast.isNoGravity();
            // Declaring no action writes the no-op in, which is the same as running nothing at all.
            CreatureProfile wolfProfile = MxtDatapackRegistries.get(context.registries(), MxtResourceKeys.CREATURE_PROFILE,
                    context.id("spirit_wolf")).orElse(null);
            boolean idleAction = wolfProfile != null && wolfProfile.spawnAction() == NoOpAction.INSTANCE;
            boolean profiled = beast.getData(MxtAttachments.CREATURE_SPIRIT).profile().isPresent()
                    && close(beast.getData(MxtAttachments.CREATURE_SPIRIT).intelligence(), 20.0D)
                    && probeCore.is(Items.AMETHYST_SHARD) && probeCore.getCount() == 2 && spawnAction && idleAction;
            // Writing a profile onto an attachment that already exists has to mark it for sync too, not only its
            // construction: the flag is consumed here first, the same way the server tick consumes it.
            CreatureSpiritAttachment spirit = beast.getData(MxtAttachments.CREATURE_SPIRIT);
            Holder<CreatureProfile> beastHolder = MxtDatapackRegistries.holder(context.registries(), MxtResourceKeys.CREATURE_PROFILE,
                    ProbeDefinitions.PROBE_BEAST_PROFILE).orElse(null);
            if (beastHolder != null) {
                spirit.checkDirty();
                spirit.apply(beastHolder, 20.0D, probeCore.copy());
            }
            boolean synced = beastHolder != null && spirit.checkDirty();
            boolean twice = ContractService.bind(tagged, player, beast, false).failure()
                    == ContractFailure.ALREADY_BOUND;
            // The owner side of the same bind: what the type declares is held by the owner, and the bind action
            // runs on the owner rather than on the beast that signed.
            boolean ownerBound = abilities.has(context.require(MxtResourceKeys.ABILITY, ProbeDefinitions.OWNER_BOND))
                    && player.hasEffect(MobEffects.ABSORPTION);
            boolean record = priceRefused && bound && profiled && synced && twice && ownerBound;
            context.note("contract probe: price_refused=" + priceRefused
                    + " bind_failure=" + secondBind.failure() + " qi=" + resources.get(qi)
                    + " owner_recorded=" + ownerRecorded + " vanilla_owner=" + vanillaOwner + " rows=" + rows
                    + " bound=" + bound + " profiled=" + profiled + " synced=" + synced + " twice=" + twice
                    + " owner_bound=" + ownerBound
                    + (record ? " OK" : " MISMATCH"));
            context.check("contract bind: price_refused", priceRefused);
            context.check("contract bind: bound", bound);
            context.check("contract bind: profiled", profiled);
            context.check("contract bind: synced", synced);
            context.check("contract bind: twice", twice);
            context.check("contract bind: owner_bound", ownerBound);
            context.check("contract bind", record);
        } finally {
            resources.set(qi, previousQi, 0.0D, 10_000.0D, -1L, "probe");
            BoundBeastService.clear(server, player.getUUID());
            AbilityGrantService.recalculate(player);
            for (Mob mob : spawned) mob.discard();
        }
    }

    // The owner's list is what a limit counts, and releasing frees the slot it held. A contract of another type
    // is signed first, because that is what makes the cap a per-type count rather than a count of everything.
    private static void limit(ProbeContext context) {
        ServerLevel level = context.level();
        ServerPlayer player = context.player();
        MinecraftServer server = context.server();
        ProbeBeast.reset();
        BoundBeastService.clear(server, player.getUUID());
        List<Mob> spawned = new ArrayList<>();
        ResourceHolderAttachment resources = player.getData(MxtAttachments.RESOURCE_HOLDER);
        AbilityAttachment abilities = player.getData(MxtAttachments.ABILITY_HOLDER);
        Holder<Resource> qi = context.require(MxtResourceKeys.RESOURCE, ProbeDefinitions.QI);
        double previousQi = resources.get(qi);
        try {
            Holder<ContractType> tagged = context.require(MxtResourceKeys.CONTRACT_TYPE, ProbeDefinitions.CONTRACT);
            Holder<ContractType> open = context.require(MxtResourceKeys.CONTRACT_TYPE, ProbeDefinitions.OPEN_CONTRACT);
            resources.set(qi, 5.0D, 0.0D, 10_000.0D, -1L, "probe");
            ProbeBeast taggedBeast = spawnProbeBeast(level, player.blockPosition().offset(6, 0, 0));
            spawned.add(taggedBeast);
            ContractService.bind(tagged, player, taggedBeast, false);

            ProbeBeast first = spawnProbeBeast(level, player.blockPosition().offset(3, 0, 0));
            ProbeBeast second = spawnProbeBeast(level, player.blockPosition().offset(0, 0, 3));
            spawned.add(first);
            spawned.add(second);
            boolean firstBound = ContractService.bind(open, player, first, false).changed();
            boolean limitHit = ContractService.bind(open, player, second, false).failure()
                    == ContractFailure.LIMIT_REACHED;
            // Two contracts, two grants: the second type adds its own ability without disturbing the first.
            boolean bothHeld = abilities.has(context.require(MxtResourceKeys.ABILITY, ProbeDefinitions.OPEN_BOND))
                    && abilities.has(context.require(MxtResourceKeys.ABILITY, ProbeDefinitions.OWNER_BOND));
            // Ending a contract is not the same act as forgetting who owns the creature, and the owner lives on
            // the creature, so a released probe still answers the player it was signed with.
            boolean releasedFirst = ContractService.release(first, player.getUUID(), false).changed()
                    && !first.getData(MxtAttachments.CONTRACT).bound()
                    && Contracts.ownerOf(first).filter(player.getUUID()::equals).isPresent();
            // The release action runs on the owner, and the grant of the type they no longer hold goes with it
            // while the one another contract still declares stays.
            boolean ownerReleased = !abilities.has(context.require(MxtResourceKeys.ABILITY, ProbeDefinitions.OPEN_BOND))
                    && abilities.has(context.require(MxtResourceKeys.ABILITY, ProbeDefinitions.OWNER_BOND))
                    && player.hasEffect(MobEffects.WEAKNESS);
            boolean freed = releasedFirst && ContractService.bind(open, player, second, false).changed();
            boolean limit = firstBound && limitHit && freed && bothHeld && ownerReleased;
            context.note("contract probe: first_bound=" + firstBound
                    + " limit_hit=" + limitHit + " both_held=" + bothHeld + " owner_released=" + ownerReleased
                    + " freed=" + freed + (limit ? " OK" : " MISMATCH"));
            context.check("contract limit: first_bound", firstBound);
            context.check("contract limit: limit_hit", limitHit);
            context.check("contract limit: both_held", bothHeld);
            context.check("contract limit: owner_released", ownerReleased);
            context.check("contract limit: freed", freed);
            context.check("contract limit", limit);
        } finally {
            resources.set(qi, previousQi, 0.0D, 10_000.0D, -1L, "probe");
            BoundBeastService.clear(server, player.getUUID());
            AbilityGrantService.recalculate(player);
            for (Mob mob : spawned) mob.discard();
        }
    }

    // The latch and its stamp: the bell sets it, the type's cooldown gates the next one from the stamp rather
    // than from a countdown, and force is the operator's bypass of the wait alone.
    private static void recall(ProbeContext context) {
        ServerLevel level = context.level();
        ServerPlayer player = context.player();
        MinecraftServer server = context.server();
        ProbeBeast.reset();
        BoundBeastService.clear(server, player.getUUID());
        List<Mob> spawned = new ArrayList<>();
        ResourceHolderAttachment resources = player.getData(MxtAttachments.RESOURCE_HOLDER);
        Holder<Resource> qi = context.require(MxtResourceKeys.RESOURCE, ProbeDefinitions.QI);
        double previousQi = resources.get(qi);
        try {
            Holder<ContractType> tagged = context.require(MxtResourceKeys.CONTRACT_TYPE, ProbeDefinitions.CONTRACT);
            resources.set(qi, 5.0D, 0.0D, 10_000.0D, -1L, "probe");
            ProbeBeast beast = spawnProbeBeast(level, player.blockPosition().offset(6, 0, 0));
            spawned.add(beast);
            ContractService.bind(tagged, player, beast, false);

            long now = level.getGameTime();
            boolean latch = ContractService.requestRecall(beast, player.getUUID(), false).changed()
                    && beast.getData(MxtAttachments.CONTRACT).recalled()
                    && beast.getData(MxtAttachments.CONTRACT).recallAt() == now;
            // The latch is still set, so a second order has to name that instead of reporting no reason at all.
            boolean pending = ContractService.requestRecall(beast, player.getUUID(), false).failure()
                    == ContractFailure.RECALL_PENDING;
            ContractService.completeRecall(beast);
            boolean cooling = ContractService.requestRecall(beast, player.getUUID(), false).failure()
                    == ContractFailure.RECALL_COOLDOWN;
            boolean forced = ContractService.requestRecall(beast, player.getUUID(), true).changed();
            ContractService.completeRecall(beast);
            // The interface default is the movement the framework used to hardcode, asked through the same
            // context the bridge builds.
            beast.setPos(player.getX() + 20.0D, player.getY(), player.getZ());
            Contracts.operations(beast).orElseThrow().recall(context(beast, player, tagged));
            boolean landed = beast.distanceToSqr(player) < 1.0D;
            boolean recall = latch && pending && cooling && forced && landed;
            context.note("contract probe: latch=" + latch + " pending=" + pending
                    + " cooling=" + cooling + " forced=" + forced + " landed=" + landed
                    + (recall ? " OK" : " MISMATCH"));
            context.check("contract recall: latch", latch);
            context.check("contract recall: pending", pending);
            context.check("contract recall: cooling", cooling);
            context.check("contract recall: forced", forced);
            context.check("contract recall: landed", landed);
            context.check("contract recall", recall);
        } finally {
            resources.set(qi, previousQi, 0.0D, 10_000.0D, -1L, "probe");
            BoundBeastService.clear(server, player.getUUID());
            AbilityGrantService.recalculate(player);
            for (Mob mob : spawned) mob.discard();
        }
    }

    // The bell names one beast and carries that creature's own answer about the orders it takes, so the wheel
    // can be drawn without resolving the creature. A beast the holder does not own is never named.
    private static void bell(ProbeContext context) {
        ServerLevel level = context.level();
        ServerPlayer player = context.player();
        MinecraftServer server = context.server();
        ProbeBeast.reset();
        BoundBeastService.clear(server, player.getUUID());
        List<Mob> spawned = new ArrayList<>();
        ResourceHolderAttachment resources = player.getData(MxtAttachments.RESOURCE_HOLDER);
        Holder<Resource> qi = context.require(MxtResourceKeys.RESOURCE, ProbeDefinitions.QI);
        double previousQi = resources.get(qi);
        try {
            Holder<ContractType> tagged = context.require(MxtResourceKeys.CONTRACT_TYPE, ProbeDefinitions.CONTRACT);
            resources.set(qi, 5.0D, 0.0D, 10_000.0D, -1L, "probe");
            ProbeBeast beast = spawnProbeBeast(level, player.blockPosition().offset(6, 0, 0));
            spawned.add(beast);
            ContractService.bind(tagged, player, beast, false);
            Wolf wolf = EntityType.WOLF.create(level, EntitySpawnReason.COMMAND);
            if (wolf == null) {
                context.fail("contract probe: could not create the vanilla creature");
                return;
            }
            spawned.add(wolf);

            player.setItemInHand(InteractionHand.MAIN_HAND, MxtItems.BEAST_TAMING_BELL.toStack());
            ItemStack bell = player.getMainHandItem();
            boolean tuned = bell.getItem().interactLivingEntity(bell, player, beast, InteractionHand.MAIN_HAND) == InteractionResult.SUCCESS
                    && bell.get(MxtDataComponents.CONTRACT_BELL) instanceof ContractBellComponent component
                    && component.beast().equals(beast.getUUID())
                    && component.behaviors().contains(ContractBehaviors.WANDER.id());
            boolean wolfRefused = bell.getItem().interactLivingEntity(bell, player, wolf, InteractionHand.MAIN_HAND) == InteractionResult.FAIL;
            boolean bellLeg = tuned && wolfRefused;
            context.note("contract probe: bell_tuned=" + tuned + " wolf_refused=" + wolfRefused
                    + (bellLeg ? " OK" : " MISMATCH"));
            context.check("contract bell: tuned", tuned);
            context.check("contract bell: wolf_refused", wolfRefused);
            context.check("contract bell", bellLeg);
        } finally {
            resources.set(qi, previousQi, 0.0D, 10_000.0D, -1L, "probe");
            BoundBeastService.clear(server, player.getUUID());
            AbilityGrantService.recalculate(player);
            for (Mob mob : spawned) mob.discard();
        }
    }

    // The wheel is the owner's input: it sends the order, the creature is asked, and the order that stays in
    // force lands on the creature's own record. A creature that refuses is left with what it had.
    private static void input(ProbeContext context) {
        ServerLevel level = context.level();
        ServerPlayer player = context.player();
        MinecraftServer server = context.server();
        ProbeBeast.reset();
        BoundBeastService.clear(server, player.getUUID());
        List<Mob> spawned = new ArrayList<>();
        ResourceHolderAttachment resources = player.getData(MxtAttachments.RESOURCE_HOLDER);
        Holder<Resource> qi = context.require(MxtResourceKeys.RESOURCE, ProbeDefinitions.QI);
        double previousQi = resources.get(qi);
        try {
            Holder<ContractType> tagged = context.require(MxtResourceKeys.CONTRACT_TYPE, ProbeDefinitions.CONTRACT);
            resources.set(qi, 5.0D, 0.0D, 10_000.0D, -1L, "probe");
            ProbeBeast beast = spawnProbeBeast(level, player.blockPosition().offset(6, 0, 0));
            spawned.add(beast);
            ContractService.bind(tagged, player, beast, false);
            // The wheel reads the beast the held bell names, so the bell is tuned the way a player tunes it.
            player.setItemInHand(InteractionHand.MAIN_HAND, MxtItems.BEAST_TAMING_BELL.toStack());
            ItemStack bell = player.getMainHandItem();
            bell.getItem().interactLivingEntity(bell, player, beast, InteractionHand.MAIN_HAND);

            boolean ordered = WheelService.trigger(player, WheelSourceTypes.CONTRACT, WheelEntryKinds.BEHAVIOR,
                    ContractBehaviors.WANDER.id())
                    && beast.getData(MxtAttachments.CONTRACT).behavior().equals(ContractBehaviors.WANDER);
            ProbeBeast.refuseBehavior(true);
            boolean refusedOrder = !WheelService.trigger(player, WheelSourceTypes.CONTRACT, WheelEntryKinds.BEHAVIOR,
                    ContractBehaviors.STAY.id())
                    && beast.getData(MxtAttachments.CONTRACT).behavior().equals(ContractBehaviors.WANDER);
            ProbeBeast.refuseBehavior(false);
            // An order the creature does not take is refused before the creature is even asked, which is what the
            // registered-but-not-offered probe order shows.
            ContractBehaviors.register(new ContractBehavior(ProbeDefinitions.PROBE_ONLY, false));
            boolean unsupported = ContractBehaviorService.request(beast, player.getUUID(),
                    ContractBehaviors.byId(ProbeDefinitions.PROBE_ONLY).orElseThrow(), false).failure()
                    == ContractFailure.UNSUPPORTED_BEHAVIOR;
            boolean input = ordered && refusedOrder && unsupported;
            context.note("contract probe: ordered=" + ordered + " refused=" + refusedOrder
                    + " unsupported=" + unsupported + (input ? " OK" : " MISMATCH"));
            context.check("contract input: ordered", ordered);
            context.check("contract input: refused", refusedOrder);
            context.check("contract input: unsupported", unsupported);
            context.check("contract input", input);
        } finally {
            resources.set(qi, previousQi, 0.0D, 10_000.0D, -1L, "probe");
            BoundBeastService.clear(server, player.getUUID());
            AbilityGrantService.recalculate(player);
            for (Mob mob : spawned) mob.discard();
        }
    }

    // A recall is an order that happens once: it sets the latch and leaves the order in force alone. The
    // framework's own orders are dispatched from there, so a strolling beast left behind is brought back on the
    // tick the bridge drives. The stroll only teleports past thirty two blocks, which the arena fits diagonally.
    private static void orders(ProbeContext context) {
        ServerLevel level = context.level();
        ServerPlayer player = context.player();
        MinecraftServer server = context.server();
        ProbeBeast.reset();
        BoundBeastService.clear(server, player.getUUID());
        List<Mob> spawned = new ArrayList<>();
        ResourceHolderAttachment resources = player.getData(MxtAttachments.RESOURCE_HOLDER);
        Holder<Resource> qi = context.require(MxtResourceKeys.RESOURCE, ProbeDefinitions.QI);
        double previousQi = resources.get(qi);
        try {
            Holder<ContractType> tagged = context.require(MxtResourceKeys.CONTRACT_TYPE, ProbeDefinitions.CONTRACT);
            resources.set(qi, 5.0D, 0.0D, 10_000.0D, -1L, "probe");
            ProbeBeast beast = spawnProbeBeast(level, player.blockPosition().offset(6, 0, 0));
            spawned.add(beast);
            ContractService.bind(tagged, player, beast, false);
            ContractBehaviorService.request(beast, player.getUUID(), ContractBehaviors.WANDER, false);

            boolean momentary = ContractBehaviorService.request(beast, player.getUUID(), ContractBehaviors.RECALL, false).changed()
                    && beast.getData(MxtAttachments.CONTRACT).recalled()
                    && beast.getData(MxtAttachments.CONTRACT).behavior().equals(ContractBehaviors.WANDER);
            ContractService.completeRecall(beast);
            BlockPos far = context.at(29, 1, 29);
            beast.setPos(far.getX() + 0.5D, far.getY(), far.getZ() + 0.5D);
            ContractEventBridge.onEntityTick(new EntityTickEvent.Post(beast));
            boolean dispatched = beast.distanceToSqr(player) < 1.0D && ProbeBeast.calls().contains("tick:wander");
            boolean orders = momentary && dispatched;
            context.note("contract probe: momentary=" + momentary + " dispatched=" + dispatched
                    + (orders ? " OK" : " MISMATCH"));
            context.check("contract orders: momentary", momentary);
            context.check("contract orders: dispatched", dispatched);
            context.check("contract orders", orders);
        } finally {
            resources.set(qi, previousQi, 0.0D, 10_000.0D, -1L, "probe");
            BoundBeastService.clear(server, player.getUUID());
            AbilityGrantService.recalculate(player);
            for (Mob mob : spawned) mob.discard();
        }
    }

    // Carrying is the item's business: the framework gates nothing, the bag keeps its own rule (your own
    // contracted beast), and the creature is only told. The round trip brings its owner back because the probe
    // saves that reference itself, and the released one is still bound, since attachments ride along.
    private static void bag(ProbeContext context) {
        ServerLevel level = context.level();
        ServerPlayer player = context.player();
        MinecraftServer server = context.server();
        ProbeBeast.reset();
        BoundBeastService.clear(server, player.getUUID());
        List<Mob> spawned = new ArrayList<>();
        ResourceHolderAttachment resources = player.getData(MxtAttachments.RESOURCE_HOLDER);
        Holder<Resource> qi = context.require(MxtResourceKeys.RESOURCE, ProbeDefinitions.QI);
        double previousQi = resources.get(qi);
        try {
            Holder<ContractType> tagged = context.require(MxtResourceKeys.CONTRACT_TYPE, ProbeDefinitions.CONTRACT);
            Holder<ContractType> open = context.require(MxtResourceKeys.CONTRACT_TYPE, ProbeDefinitions.OPEN_CONTRACT);
            resources.set(qi, 5.0D, 0.0D, 10_000.0D, -1L, "probe");
            // The beast the owner already has stands on them, and the one whose slot was freed is still here: the
            // two of them are what the lookup below has to step over before it reaches what the bag gave back.
            ProbeBeast beast = spawnProbeBeast(level, player.blockPosition());
            spawned.add(beast);
            ContractService.bind(tagged, player, beast, false);
            ProbeBeast first = spawnProbeBeast(level, player.blockPosition().offset(3, 0, 0));
            spawned.add(first);
            ContractService.bind(open, player, first, false);
            ContractService.release(first, player.getUUID(), false);
            ProbeBeast second = spawnProbeBeast(level, player.blockPosition().offset(9, 0, 0));
            spawned.add(second);
            ContractService.bind(open, player, second, false);

            ItemStack bagStack = MxtItems.SPIRIT_BEAST_BAG.toStack();
            boolean captured = bagStack.getItem().interactLivingEntity(bagStack, player, second, InteractionHand.MAIN_HAND) == InteractionResult.SUCCESS
                    && second.isRemoved();
            SpiritBeastComponent stored = bagStack.getOrDefault(MxtDataComponents.SPIRIT_BEAST, SpiritBeastComponent.EMPTY);
            boolean described = stored.stored()
                    && stored.entityType().filter(second.getType()::equals).isPresent()
                    && stored.name().isPresent()
                    && stored.contractType().map(HolderHelper::id).filter(ProbeDefinitions.OPEN_CONTRACT::equals).isPresent()
                    && stored.owner().filter(player.getUUID()::equals).isPresent()
                    && !stored.ownerName().isBlank();
            player.setItemInHand(InteractionHand.MAIN_HAND, bagStack);
            BlockPos releaseAt = player.blockPosition();
            UseOnContext release = new UseOnContext(player, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atBottomCenterOf(releaseAt), Direction.UP, releaseAt, false));
            boolean releasedFromBag = bagStack.getItem().useOn(release) == InteractionResult.SUCCESS_SERVER
                    && !bagStack.getOrDefault(MxtDataComponents.SPIRIT_BEAST, SpiritBeastComponent.EMPTY).stored();
            ProbeBeast restored = level.getEntitiesOfClass(ProbeBeast.class, player.getBoundingBox().inflate(2.0D)).stream()
                    .filter(candidate -> candidate != beast && candidate != first)
                    .findFirst().orElse(null);
            if (restored != null) spawned.add(restored);
            boolean bag = captured && described && releasedFromBag && restored != null
                    && restored.getUUID().equals(second.getUUID())
                    && Contracts.ownerOf(restored).filter(player.getUUID()::equals).isPresent()
                    && restored.getData(MxtAttachments.CONTRACT).bound();
            context.note("contract probe: captured=" + captured + " described=" + described
                    + " freed=" + releasedFromBag + " restored=" + (restored != null) + (bag ? " OK" : " MISMATCH"));
            context.check("contract bag: captured", captured);
            context.check("contract bag: described", described);
            context.check("contract bag: freed", releasedFromBag);
            context.check("contract bag", bag);
        } finally {
            resources.set(qi, previousQi, 0.0D, 10_000.0D, -1L, "probe");
            BoundBeastService.clear(server, player.getUUID());
            AbilityGrantService.recalculate(player);
            for (Mob mob : spawned) mob.discard();
        }
    }

    // Death ends the record, runs the creature's own death hook and drops its row. The death action is a field
    // of its own, so a release and a death never share one. What is called, and in which order, is the assertion.
    private static void ending(ProbeContext context) {
        ServerLevel level = context.level();
        ServerPlayer player = context.player();
        MinecraftServer server = context.server();
        ProbeBeast.reset();
        BoundBeastService.clear(server, player.getUUID());
        List<Mob> spawned = new ArrayList<>();
        ResourceHolderAttachment resources = player.getData(MxtAttachments.RESOURCE_HOLDER);
        AbilityAttachment abilities = player.getData(MxtAttachments.ABILITY_HOLDER);
        Holder<Resource> qi = context.require(MxtResourceKeys.RESOURCE, ProbeDefinitions.QI);
        double previousQi = resources.get(qi);
        try {
            Holder<ContractType> tagged = context.require(MxtResourceKeys.CONTRACT_TYPE, ProbeDefinitions.CONTRACT);
            Holder<ContractType> open = context.require(MxtResourceKeys.CONTRACT_TYPE, ProbeDefinitions.OPEN_CONTRACT);
            // The call log a creature keeps is cumulative, so this body replays the moments the other legs
            // assert before it ends the bond: a refused price leaves no call behind.
            resources.set(qi, 3.0D, 0.0D, 10_000.0D, -1L, "probe");
            ProbeBeast beast = spawnProbeBeast(level, player.blockPosition().offset(3, 0, 0));
            spawned.add(beast);
            ContractService.bind(tagged, player, beast, false);
            resources.set(qi, 5.0D, 0.0D, 10_000.0D, -1L, "probe");
            ContractService.bind(tagged, player, beast, false);
            ProbeBeast first = spawnProbeBeast(level, player.blockPosition().offset(6, 0, 0));
            ProbeBeast second = spawnProbeBeast(level, player.blockPosition().offset(9, 0, 0));
            spawned.add(first);
            spawned.add(second);
            ContractService.bind(open, player, first, false);
            ContractService.release(first, player.getUUID(), false);
            ContractService.bind(open, player, second, false);
            ContractService.requestRecall(beast, player.getUUID(), false);
            ContractService.completeRecall(beast);
            beast.setPos(player.getX() + 20.0D, player.getY(), player.getZ());
            Contracts.operations(beast).orElseThrow().recall(context(beast, player, tagged));
            player.setItemInHand(InteractionHand.MAIN_HAND, MxtItems.BEAST_TAMING_BELL.toStack());
            ItemStack bell = player.getMainHandItem();
            bell.getItem().interactLivingEntity(bell, player, beast, InteractionHand.MAIN_HAND);
            WheelService.trigger(player, WheelSourceTypes.CONTRACT, WheelEntryKinds.BEHAVIOR, ContractBehaviors.WANDER.id());
            ProbeBeast.refuseBehavior(true);
            WheelService.trigger(player, WheelSourceTypes.CONTRACT, WheelEntryKinds.BEHAVIOR, ContractBehaviors.STAY.id());
            ProbeBeast.refuseBehavior(false);
            BlockPos far = context.at(29, 1, 29);
            beast.setPos(far.getX() + 0.5D, far.getY(), far.getZ() + 0.5D);
            ContractEventBridge.onEntityTick(new EntityTickEvent.Post(beast));
            ItemStack bagStack = MxtItems.SPIRIT_BEAST_BAG.toStack();
            bagStack.getItem().interactLivingEntity(bagStack, player, second, InteractionHand.MAIN_HAND);
            player.setItemInHand(InteractionHand.MAIN_HAND, bagStack);
            BlockPos releaseAt = player.blockPosition();
            UseOnContext release = new UseOnContext(player, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atBottomCenterOf(releaseAt), Direction.UP, releaseAt, false));
            bagStack.getItem().useOn(release);
            // The bag gave the creature back as a fresh entity, which this body has to clean up like any other.
            level.getEntitiesOfClass(ProbeBeast.class, player.getBoundingBox().inflate(2.0D)).stream()
                    .filter(candidate -> candidate != beast && candidate != first)
                    .findFirst().ifPresent(spawned::add);

            int rowsBefore = BoundBeastService.of(server, player.getUUID()).size();
            beast.hurtServer(level, level.damageSources().genericKill(), Float.MAX_VALUE);
            boolean death = !beast.getData(MxtAttachments.CONTRACT).bound()
                    && beast.isOnFire()
                    && BoundBeastService.of(server, player.getUUID()).size() == rowsBefore - 1;
            boolean order = ProbeBeast.calls().equals(List.of("bound", "bound", "released", "bound", "recall",
                    "order:wander", "order:stay", "tick:wander", "captured", "freed", "death"));
            // A death takes the dead beast's contract off the owner the same way a release does, and only that
            // one: the open contract the probe is still holding keeps its own ability.
            boolean ownerEnded = !abilities.has(context.require(MxtResourceKeys.ABILITY, ProbeDefinitions.OWNER_BOND))
                    && abilities.has(context.require(MxtResourceKeys.ABILITY, ProbeDefinitions.OPEN_BOND))
                    && player.hasEffect(MobEffects.GLOWING);
            boolean ending = death && order && ownerEnded;
            context.note("contract probe: death=" + death + " owner_ended=" + ownerEnded
                    + " calls=" + ProbeBeast.calls()
                    + (ending ? " OK" : " MISMATCH"));
            context.check("contract ending: death", death);
            context.check("contract ending: calls", order);
            context.check("contract ending: owner_ended", ownerEnded);
            context.check("contract ending", ending);
        } finally {
            resources.set(qi, previousQi, 0.0D, 10_000.0D, -1L, "probe");
            BoundBeastService.clear(server, player.getUUID());
            AbilityGrantService.recalculate(player);
            for (Mob mob : spawned) mob.discard();
        }
    }

    // The context the beast's own default movement is asked through, which is the one the bridge builds.
    private static ContractContext context(Mob creature, ServerPlayer owner, Holder<ContractType> type) {
        return ContractContext.of(creature, owner.getUUID(), owner, type);
    }

    private static ProbeBeast spawnProbeBeast(ServerLevel level, BlockPos pos) {
        ProbeBeast beast = MxtTestEntities.PROBE_BEAST.get().create(level, EntitySpawnReason.COMMAND);
        if (beast == null) throw new IllegalStateException("The probe beast type failed to create an entity");
        beast.setNoAi(true);
        beast.setPos(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
        level.addFreshEntity(beast);
        return beast;
    }

    private static boolean close(double actual, double expected) {
        return Math.abs(actual - expected) < 1.0E-3D;
    }
}
