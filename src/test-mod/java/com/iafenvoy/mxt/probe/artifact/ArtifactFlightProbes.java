package com.iafenvoy.mxt.probe.artifact;

import com.iafenvoy.mxt.attachment.CultivationAttachment;
import com.iafenvoy.mxt.attachment.ResourceHolderAttachment;
import com.iafenvoy.mxt.attachment.TechniqueAttachment;
import com.iafenvoy.mxt.data.ability.Ability;
import com.iafenvoy.mxt.data.ability.Togglable;
import com.iafenvoy.mxt.data.ability.type.MountAbilityType;
import com.iafenvoy.mxt.data.aura.Aura;
import com.iafenvoy.mxt.data.resource.Resource;
import com.iafenvoy.mxt.probe.Probe;
import com.iafenvoy.mxt.probe.ProbeContext;
import com.iafenvoy.mxt.probe.ProbeDefinitions;
import com.iafenvoy.mxt.probe.ProbeKit;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.runtime.ability.AbilityActivationService;
import com.iafenvoy.mxt.runtime.ability.AbilityGrantService;
import com.iafenvoy.mxt.runtime.artifact.ArtifactService;
import com.iafenvoy.mxt.runtime.artifact.FlightService;
import com.iafenvoy.mxt.entity.FlyingSwordEntity;
import com.iafenvoy.mxt.runtime.cultivation.CultivationIdentityService;
import com.iafenvoy.mxt.runtime.cultivation.CultivationService;
import com.iafenvoy.mxt.runtime.resulting.failures.AbilityFailure;
import com.iafenvoy.mxt.runtime.resulting.failures.FlightFailure;
import com.iafenvoy.mxt.runtime.wheel.WheelEntryKinds;
import com.iafenvoy.mxt.runtime.wheel.WheelService;
import com.iafenvoy.mxt.runtime.wheel.WheelSourceTypes;
import com.iafenvoy.mxt.util.HolderHelper;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/**
 * The flight itself: taking off through the skill with the artifact in hand, custody of the stack, the seats, the fuel
 * of a tick, the directed request the wheel sends, the crash that ends a flight and the artifact coming back.
 */
public final class ArtifactFlightProbes {
    private ArtifactFlightProbes() {
    }

    public static List<Probe> probes() {
        return List.of(
                Probe.kitted("artifact/flight", ArtifactFlightProbes::flight),
                Probe.of("artifact/flight/directed", ArtifactFlightProbes::directed),
                Probe.of("artifact/flight/crash", ArtifactFlightProbes::crash));
    }

    // Flight is a skill and the artifact is what it spends: with no skill the press is refused, and the mount
    // leaves the hand the moment it becomes an entity - which is why the press cannot live on the artifact.
    private static void flight(ProbeContext context) {
        Provider access = context.registries();
        Holder<Resource> qiResource = context.require(MxtResourceKeys.RESOURCE, ProbeDefinitions.QI);
        Holder<Aura> qi = context.require(MxtResourceKeys.AURA, ProbeDefinitions.QI);
        ItemStack flightStack = new ItemStack(Items.IRON_SWORD);
        Holder<Ability> controlAbility = context.require(MxtResourceKeys.ABILITY, "sword_control");
        Holder<Ability> mountAbility = context.require(MxtResourceKeys.ABILITY, "azure_sword_mount");
        MountAbilityType mountEntry = ArtifactService.mount(access, flightStack).orElse(null);

        LivingEntity pilot = spawnProbe(context, context.playerSpot().above(5), null);
        boolean flight = false;
        if (pilot != null) {
            ResourceHolderAttachment pilotResources = pilot.getData(MxtAttachments.RESOURCE_HOLDER);
            // The pilot has to be able to pay, and a qi ceiling is a formula over the body's own realm: a body that
            // never entered one reads a ceiling of nothing however much is written into the account.
            CultivationAttachment pilotSpirit = pilot.getData(MxtAttachments.CULTIVATION);
            CultivationService.setRealm(pilotSpirit, context.require(MxtResourceKeys.REALM_STAGE, ProbeDefinitions.QI_REFINING));
            pilotSpirit.setCultivationProgress(context.require(MxtResourceKeys.AURA, ProbeDefinitions.QI), 80.0D);
            ProbeKit.ensureResource(pilot, pilotResources, qiResource, 40.0D);
            ItemStack pilotMount = new ItemStack(Items.IRON_SWORD);
            ArtifactService.refine(pilotMount, pilot);
            pilot.setItemInHand(InteractionHand.MAIN_HAND, pilotMount);
            Togglable.PressResult refused = AbilityActivationService.activate(pilot, controlAbility, null);
            boolean ungranted = refused.failure() == AbilityFailure.NOT_GRANTED
                    && !pilot.getData(MxtAttachments.FLIGHT).active() && !pilot.getMainHandItem().isEmpty();
            // A technique is the ordinary way in: learning it grants the skill through the same ledger as any grant.
            TechniqueAttachment spirit = pilot.getData(MxtAttachments.TECHNIQUE);
            spirit.addLearnedTechnique(context.require(MxtResourceKeys.TECHNIQUE, "sword_control_manual"));
            AbilityGrantService.recalculate(pilot);
            boolean granted = pilot.getData(MxtAttachments.ABILITY_HOLDER).has(controlAbility);
            double qiBeforePress = pilotResources.get(qiResource);
            context.check("artifact roster flight: the pilot holds qi before the press (" + qiBeforePress + ")", qiBeforePress >= 5.0D);
            Togglable.PressResult pressed = AbilityActivationService.activate(pilot, controlAbility, null);
            FlyingSwordEntity sword = pilot.getVehicle() instanceof FlyingSwordEntity value ? value : null;
            boolean pressedOk = pressed.failure() == null;
            boolean created = sword != null && mountEntry != null;
            boolean active = pilot.getData(MxtAttachments.FLIGHT).active()
                    && pilot.getData(MxtAttachments.FLIGHT).vehicle().filter(vehicle -> vehicle.is(ProbeDefinitions.id("azure_sword_mount"))).isPresent()
                    && pilot.getData(MxtAttachments.FLIGHT).archetype().filter(archetype -> archetype.is(HolderHelper.id(controlAbility))).isPresent();
            // Custody: the artifact left the hand, and it is what the mount is made of.
            boolean custody = sword != null && pilot.getMainHandItem().isEmpty() && sword.visual().is(Items.IRON_SWORD)
                    && ArtifactService.isOwner(sword.visual(), pilot.getUUID());
            // The definition, not a registered constant, decides the box, the pose and who is driving.
            boolean shape = sword != null && mountEntry != null
                    && close(sword.getDimensions(Pose.STANDING).width(), mountEntry.width())
                    && close(sword.getDimensions(Pose.STANDING).height(), mountEntry.height())
                    && close(sword.maxUpStep(), 0.0D) && !sword.shouldRiderSit()
                    && sword.getControllingPassenger() == pilot;
            // One take-off price, and it is the skill's own, charged by the shared gate.
            boolean paid = close(qiBeforePress - pilotResources.get(qiResource), 5.0D);
            boolean riding = pressedOk && created && active && custody && shape && paid;
            context.check("artifact roster flight: the press was accepted, failure=" + (pressed.failure() == null ? "none" : pressed.failure()), pressedOk);
            context.check("artifact roster flight: a mount entity was created", created);
            context.check("artifact roster flight: the flight state names the mount and the skill", active);
            context.check("artifact roster flight: the artifact is in the mount's custody", custody);
            context.check("artifact roster flight: the definition decides the box and the driver", shape);
            context.check("artifact roster flight: take-off charged the skill's own price", paid);
            LivingEntity passenger = spawnProbe(context, context.playerSpot().above(6), null);
            LivingEntity third = spawnProbe(context, context.playerSpot().above(7), null);
            boolean seats = sword != null && passenger != null && third != null
                    && passenger.startRiding(sword) && sword.getPassengers().size() == 2
                    && !third.startRiding(sword) && sword.getPassengers().size() == 2;
            double qiBeforeTick = pilotResources.get(qiResource);
            boolean flying = sword != null
                    && FlightService.tick(pilot, controlAbility, mountAbility, FormulaContext.of(pilot)).getData() == FlightService.State.FLYING
                    // The fuel of a tick is the mount's own price. This sword was refined empty, so the first
                    // tick has nothing in the artifact to burn and the pilot pays it.
                    && close(qiBeforeTick - pilotResources.get(qiResource), 0.1D);
            // Once the artifact is charged, the same tick comes out of it instead: the mount carries the aura it
            // burns, and only what the artifact cannot cover falls back on the pilot.
            if (sword != null)
                ArtifactService.addEnergy(access, sword.visual(), qi, 5.0D, 0.0D, FormulaContext.of(pilot));
            double artifactBefore = sword == null ? 0.0D : ArtifactService.stored(sword.visual(), qi);
            double poolBefore = pilotResources.get(qiResource);
            boolean artifactFuel = sword != null
                    && FlightService.tick(pilot, controlAbility, mountAbility, FormulaContext.of(pilot)).getData() == FlightService.State.FLYING
                    && close(artifactBefore - ArtifactService.stored(sword.visual(), qi), 0.1D)
                    && close(poolBefore - pilotResources.get(qiResource), 0.0D)
                    && artifactBefore > 0.0D;
            flying = flying && artifactFuel;
            boolean landed = FlightService.dismount(pilot, FlightFailure.STOPPED).getData() == FlightService.State.STOPPED
                    && pilot.getVehicle() == null && !pilot.getData(MxtAttachments.FLIGHT).active()
                    && sword != null && sword.isRemoved() && sword.visual().isEmpty()
                    // Nobody is there to take it back, so it lands where the mount was instead of going with it.
                    && !context.level().getEntitiesOfClass(ItemEntity.class, pilot.getBoundingBox().inflate(8.0D),
                    item -> item.getItem().is(Items.IRON_SWORD) && ArtifactService.isOwner(item.getItem(), pilot.getUUID())).isEmpty();
            flight = ungranted && granted && riding && seats && flying && landed;
            // The aggregate is one scenario, but a bare aggregate cannot say which half broke; each named part is
            // reported on its own line so the log points at it.
            context.check("artifact roster flight: ungranted refused", ungranted);
            context.check("artifact roster flight: granted by technique", granted);
            context.check("artifact roster flight: riding with the artifact in custody", riding);
            context.check("artifact roster flight: two seats and the third refused", seats);
            context.check("artifact roster flight: fuel charged per tick", flying);
            context.check("artifact roster flight: landing hands the artifact back", landed);
            if (passenger != null) passenger.discard();
            if (third != null) third.discard();
        }
        if (pilot != null) pilot.discard();
        context.check("artifact roster flight", flight);
    }

    // The same flight with a player, asked the directed way: the merged payload names the state it wants, and a
    // request for the state it is already in changes nothing.
    private static void directed(ProbeContext context) {
        ServerPlayer player = context.player();
        Holder<Resource> qiResource = context.require(MxtResourceKeys.RESOURCE, ProbeDefinitions.QI);
        Holder<Ability> controlAbility = context.require(MxtResourceKeys.ABILITY, "sword_control");
        // The hand is put back afterwards, since the rest of this leg reads stacks of its own rather than what the
        // player holds.
        ItemStack heldBefore = player.getMainHandItem().copy();
        ProbeKit.ensureResource(player, player.getData(MxtAttachments.RESOURCE_HOLDER), qiResource, 40.0D);
        // Qi cannot be spent by a body that never entered a realm: the aura's own use gate is the realm condition,
        // and it is asked of whoever pays before the first entry is charged.
        CultivationAttachment playerCultivation = player.getData(MxtAttachments.CULTIVATION);
        CultivationService.setRealm(playerCultivation, context.require(MxtResourceKeys.REALM_STAGE, ProbeDefinitions.QI_REFINING));
        playerCultivation.setCultivationProgress(context.require(MxtResourceKeys.AURA, ProbeDefinitions.QI), 80.0D);
        TechniqueAttachment playerSpirit = player.getData(MxtAttachments.TECHNIQUE);
        playerSpirit.addLearnedTechnique(context.require(MxtResourceKeys.TECHNIQUE, "sword_control_manual"));
        AbilityGrantService.recalculate(player);
        int ownedBefore = ownedSwords(player);
        ItemStack playerMount = new ItemStack(Items.IRON_SWORD);
        ArtifactService.refine(playerMount, player);
        player.setItemInHand(InteractionHand.MAIN_HAND, playerMount);
        Identifier controlId = HolderHelper.id(controlAbility);
        boolean firstTrigger = WheelService.trigger(player, WheelSourceTypes.MAIN_HAND, WheelEntryKinds.ABILITY, controlId, Optional.of(true));
        boolean playerRiding = firstTrigger && player.getVehicle() instanceof FlyingSwordEntity;
        context.check("artifact roster flight directed: the payload mounts (trigger=" + firstTrigger + ", vehicle="
                + player.getVehicle() + ")", playerRiding);
        boolean secondTrigger = WheelService.trigger(player, WheelSourceTypes.MAIN_HAND, WheelEntryKinds.ABILITY, controlId, Optional.of(true));
        boolean playerIdle = secondTrigger && player.getVehicle() instanceof FlyingSwordEntity;
        context.check("artifact roster flight directed: asking for the state it is already in changes nothing (trigger="
                + secondTrigger + ", vehicle=" + player.getVehicle() + ")", playerIdle);
        boolean thirdTrigger = WheelService.trigger(player, WheelSourceTypes.MAIN_HAND, WheelEntryKinds.ABILITY, controlId, Optional.of(false));
        boolean playerLanded = thirdTrigger && player.getVehicle() == null && ownedSwords(player) == ownedBefore + 1;
        player.setItemInHand(InteractionHand.MAIN_HAND, heldBefore);
        context.check("artifact roster flight directed: giving it back returns one sword to the owner (trigger="
                + thirdTrigger + ", vehicle=" + player.getVehicle() + ", swords " + ownedBefore + " -> "
                + ownedSwords(player) + ")", playerLanded);
        context.check("artifact roster flight directed", playerRiding && playerIdle && playerLanded);
    }

    // Hitting something ends the flight. The probe cannot press a movement key, so the mount is moved into a
    // wall by hand: what the leg is really about is that a real move leaves the flag the landing reads.
    private static void crash(ProbeContext context) {
        Holder<Resource> qiResource = context.require(MxtResourceKeys.RESOURCE, ProbeDefinitions.QI);
        Holder<Ability> controlAbility = context.require(MxtResourceKeys.ABILITY, "sword_control");
        Holder<Ability> mountAbility = context.require(MxtResourceKeys.ABILITY, "azure_sword_mount");
        LivingEntity crasher = spawnProbe(context, context.playerSpot().above(9), null);
        boolean collision = false;
        if (crasher != null) {
            // The pilot has to be able to pay, and a qi ceiling is a formula over the body's own realm: a body that
            // never entered one reads a ceiling of nothing however much is written into the account.
            CultivationAttachment crasherIdentity = crasher.getData(MxtAttachments.CULTIVATION);
            CultivationService.setRealm(crasherIdentity, context.require(MxtResourceKeys.REALM_STAGE, ProbeDefinitions.QI_REFINING));
            crasherIdentity.setCultivationProgress(context.require(MxtResourceKeys.AURA, ProbeDefinitions.QI), 80.0D);
            ProbeKit.ensureResource(crasher, crasher.getData(MxtAttachments.RESOURCE_HOLDER), qiResource, 20.0D);
            ItemStack crashMount = new ItemStack(Items.IRON_SWORD);
            ArtifactService.refine(crashMount, crasher);
            crasher.setItemInHand(InteractionHand.MAIN_HAND, crashMount);
            TechniqueAttachment crashSpirit = crasher.getData(MxtAttachments.TECHNIQUE);
            crashSpirit.addLearnedTechnique(context.require(MxtResourceKeys.TECHNIQUE, "sword_control_manual"));
            AbilityGrantService.recalculate(crasher);
            FlyingSwordEntity crashSword = AbilityActivationService.activate(crasher, controlAbility, null).failure() == null
                    && crasher.getVehicle() instanceof FlyingSwordEntity value ? value : null;
            if (crashSword != null) {
                crashSword.setYRot(0.0F);
                BlockPos wall = crashSword.blockPosition().relative(Direction.SOUTH);
                context.level().setBlockAndUpdate(wall, Blocks.STONE.defaultBlockState());
                crashSword.move(MoverType.SELF, new Vec3(0.0D, 0.0D, 1.0D));
                FlightService.FlightResult crashed = FlightService.tick(crasher, controlAbility, mountAbility, FormulaContext.of(crasher));
                boolean hit = crashSword.horizontalCollision;
                boolean stopped = crashed.getData() == FlightService.State.STOPPED;
                boolean blocked = crashed.failure() == FlightFailure.COLLISION;
                boolean off = crasher.getVehicle() == null;
                collision = hit && stopped && blocked && off;
                context.check("artifact roster flight crash: the move left the collision flag", hit);
                context.check("artifact roster flight crash: the tick stopped the flight", stopped);
                context.check("artifact roster flight crash: the failure names the collision", blocked);
                context.check("artifact roster flight crash: the rider is off the mount", off);
                context.level().setBlockAndUpdate(wall, Blocks.AIR.defaultBlockState());
            }
        }
        if (crasher != null) crasher.discard();
        context.check("artifact roster flight crash=lands", collision);
    }

    // The owner-bound iron swords one holder carries: what a flight gives back must add exactly one of them.
    private static int ownedSwords(ServerPlayer player) {
        int found = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(Items.IRON_SWORD) && ArtifactService.isOwner(stack, player.getUUID())) found++;
        }
        return found;
    }

    // One disposable probe entity carrying exactly one spirit root, so the element it strikes or is struck with is
    // never a question. The artifact legs pass a null root, which leaves it carrying none.
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
