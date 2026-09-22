package com.iafenvoy.mxt.runtime.artifact;

import com.iafenvoy.mxt.attachment.FlightAttachment;
import com.iafenvoy.mxt.data.artifact.Artifact;
import com.iafenvoy.mxt.data.artifact.ability.FlightArtifactAbility;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.registry.MxtEntityTypes;
import com.iafenvoy.mxt.runtime.resource.ResourceTransactions;
import com.iafenvoy.mxt.util.HolderHelper;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.NeoForgeMod;

/**
 * Authoritative generic flight controller for flying swords and artifacts: the server mounts the sword, charges
 * the upkeep and owns every end of the flight.
 */
public final class FlightService {
    private FlightService() {
    }

    public static Result mount(ServerPlayer player, Holder<Artifact> archetype, FormulaContext context) {
        return mount(player, player.getMainHandItem(), archetype, context);
    }

    public static Result mount(ServerPlayer player, ItemStack artifact, Holder<Artifact> archetype, FormulaContext context) {
        // The definition says whether it can fly at all by declaring the entry, so an artifact that never
        // mentioned flight is not one whose speed happens to be zero.
        FlightArtifactAbility flight = archetype.value().flight().orElse(null);
        if (flight == null) return Result.rejected(Failure.NOT_FLYABLE);
        if (!ownsEquippedArchetype(player, artifact, archetype)) {
            return Result.rejected(Failure.NOT_OWNED);
        }
        FlightAttachment data = player.getData(MxtAttachments.FLIGHT);
        if (data.active()) return Result.rejected(Failure.ALREADY_ACTIVE);
        double speed = flight.speed().evaluate(context);
        if (!Double.isFinite(speed) || speed <= 0.0D) return Result.rejected(Failure.INVALID_FORMULA);
        FlyingSwordEntity sword = new FlyingSwordEntity(MxtEntityTypes.FLYING_SWORD.get(), player.level());
        sword.setPos(player.getX(), player.getY(), player.getZ());
        sword.setFlightSpeed(speed);
        player.level().addFreshEntity(sword);
        if (!player.startRiding(sword, true, true)) {
            sword.discard();
            return Result.rejected(Failure.CANNOT_MOUNT);
        }
        // The flight allowance is an attribute ({@code NeoForgeMod.CREATIVE_FLIGHT}); the mayfly flag is the
        // deprecated view of it, and reading the attribute is what can actually be written back.
        data.start(archetype, player.level().getGameTime(), player.getAttributeValue(NeoForgeMod.CREATIVE_FLIGHT),
                player.getAbilities().flying, player.getAbilities().getFlyingSpeed(), sword.getUUID());
        return Result.mounted();
    }

    // Still the artifact the flight was started with: the same definition claims it and that definition still lets
    // this player use it. Resolved from the item rather than read off a component, so re-defining an item
    // mid-flight ends the flight instead of silently continuing with stale numbers.
    public static boolean ownsEquippedArchetype(ServerPlayer player, ItemStack artifact, Holder<Artifact> archetype) {
        if (artifact.isEmpty() || !ArtifactService.mayUse(artifact, archetype, player.getUUID())) return false;
        return ArtifactService.definition(player.level().registryAccess(), artifact)
                .map(holder -> HolderHelper.id(holder).equals(HolderHelper.id(archetype))).orElse(false);
    }

    public static Result tick(ServerPlayer player, Artifact definition, FormulaContext context) {
        FlightAttachment data = player.getData(MxtAttachments.FLIGHT);
        if (!data.active()) return Result.inactive();
        FlightArtifactAbility flight = definition.flight().orElse(null);
        if (flight == null) return dismount(player, Failure.NOT_FLYABLE);
        if (!(player.getVehicle() instanceof FlyingSwordEntity sword) || data.vehicle().filter(sword.getUUID()::equals).isEmpty()) {
            return dismount(player, Failure.MOUNT_LOST);
        }
        double speed = flight.speed().evaluate(context);
        if (!Double.isFinite(speed) || speed <= 0.0D) return dismount(player, Failure.INVALID_FORMULA);
        sword.setFlightSpeed(speed);
        if (sword.horizontalCollision || sword.verticalCollision) return dismount(player, Failure.COLLISION);
        ResourceTransactions.Result payment;
        try {
            payment = ResourceTransactions.tryConsume(player, player.getData(MxtAttachments.RESOURCE_HOLDER),
                    ResourceTransactions.evaluate(player, flight.costs(), context));
        } catch (IllegalArgumentException exception) {
            return dismount(player, Failure.INVALID_FORMULA);
        }
        if (!flight.costs().isEmpty() && !payment.committed())
            return dismount(player, Failure.INSUFFICIENT_RESOURCE);
        return Result.flying();
    }

    // Whatever is left of the flight restores the player's own pre-flight state.
    public static Result dismount(ServerPlayer player, Failure reason) {
        FlightAttachment data = player.getData(MxtAttachments.FLIGHT);
        if (player.getVehicle() instanceof FlyingSwordEntity sword) {
            player.stopRiding();
            sword.discard();
        }
        player.getAbilities().flying = data.previousFlight() > 0.0D && data.previousFlying();
        AttributeInstance flight = player.getAttribute(NeoForgeMod.CREATIVE_FLIGHT);
        if (flight != null) flight.setBaseValue(data.previousFlight());
        player.getAbilities().setFlyingSpeed(data.previousFlyingSpeed());
        data.stop();
        player.onUpdateAbilities();
        return Result.stopped(reason);
    }

    public enum Failure {NOT_FLYABLE, NOT_OWNED, ALREADY_ACTIVE, INVALID_FORMULA, INSUFFICIENT_RESOURCE, COLLISION, STOPPED, CANNOT_MOUNT, MOUNT_LOST}

    public record Result(State state, Failure failure) {
        static Result mounted() {
            return new Result(State.MOUNTED, null);
        }

        static Result flying() {
            return new Result(State.FLYING, null);
        }

        static Result inactive() {
            return new Result(State.INACTIVE, null);
        }

        static Result rejected(Failure failure) {
            return new Result(State.REJECTED, failure);
        }

        static Result stopped(Failure failure) {
            return new Result(State.STOPPED, failure);
        }

        public enum State {INACTIVE, MOUNTED, FLYING, STOPPED, REJECTED}
    }
}
