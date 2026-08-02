package com.iafenvoy.mxt.runtime.artifact;

import com.iafenvoy.mxt.attachment.FlightData;
import com.iafenvoy.mxt.data.artifact.ArtifactStateData;
import com.iafenvoy.mxt.data.artifact.ItemArchetype;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.registry.MxtDataComponents;
import com.iafenvoy.mxt.registry.MxtEntityTypes;
import com.iafenvoy.mxt.runtime.resource.ResourceTransactions;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Authoritative generic flight controller for flying swords and artifacts.
 */
public final class FlightService {
    private FlightService() {
    }

    public static Result mount(ServerPlayer player, Identifier archetype, ItemArchetype definition, FormulaContext context) {
        return mount(player, player.getMainHandItem(), archetype, definition, context);
    }

    public static Result mount(ServerPlayer player, ItemStack artifact, Identifier archetype, ItemArchetype definition, FormulaContext context) {
        if (definition.flightSpeed().evaluate(context) <= 0.0D)
            return Result.rejected(Failure.NOT_FLYABLE);
        if (!ownsEquippedArchetype(player, artifact, archetype)) {
            return Result.rejected(Failure.NOT_OWNED);
        }
        FlightData data = player.getData(MxtAttachments.FLIGHT);
        if (data.active()) return Result.rejected(Failure.ALREADY_ACTIVE);
        double speed = definition.flightSpeed().evaluate(context);
        if (!Double.isFinite(speed) || speed <= 0.0D) return Result.rejected(Failure.INVALID_FORMULA);
        FlyingSwordEntity sword = new FlyingSwordEntity(MxtEntityTypes.FLYING_SWORD.get(), player.level());
        sword.setPos(player.getX(), player.getY(), player.getZ());
        sword.setFlightSpeed(speed);
        player.level().addFreshEntity(sword);
        if (!player.startRiding(sword, true, true)) {
            sword.discard();
            return Result.rejected(Failure.CANNOT_MOUNT);
        }
        data.start(archetype, player.level().getGameTime(), player.getAbilities().mayfly, player.getAbilities().flying, player.getAbilities().getFlyingSpeed(), sword.getUUID());
        return Result.mounted();
    }

    public static boolean ownsEquippedArchetype(ServerPlayer player, ItemStack artifact, Identifier archetype) {
        ArtifactStateData state = artifact.get(MxtDataComponents.ARTIFACT_STATE);
        return !artifact.isEmpty() && state != null && state.archetype().filter(archetype::equals).isPresent()
                && ArtifactService.isOwner(artifact, player.getUUID());
    }

    public static Result tick(ServerPlayer player, ItemArchetype definition, FormulaContext context) {
        FlightData data = player.getData(MxtAttachments.FLIGHT);
        if (!data.active()) return Result.inactive();
        if (!(player.getVehicle() instanceof FlyingSwordEntity sword) || data.vehicle().filter(sword.getUUID()::equals).isEmpty()) {
            return dismount(player, Failure.MOUNT_LOST);
        }
        double speed = definition.flightSpeed().evaluate(context);
        if (!Double.isFinite(speed) || speed <= 0.0D) return dismount(player, Failure.INVALID_FORMULA);
        sword.setFlightSpeed(speed);
        if (sword.horizontalCollision || sword.verticalCollision) return dismount(player, Failure.COLLISION);
        ResourceTransactions.Result payment;
        try {
            payment = ResourceTransactions.tryConsume(player.getData(MxtAttachments.RESOURCE_HOLDER), ResourceTransactions.evaluate(definition.flightCosts(), context));
        } catch (IllegalArgumentException exception) {
            return dismount(player, Failure.INVALID_FORMULA);
        }
        if (!definition.flightCosts().isEmpty() && !payment.committed())
            return dismount(player, Failure.INSUFFICIENT_RESOURCE);
        return Result.flying();
    }

    public static Result dismount(ServerPlayer player, Failure reason) {
        FlightData data = player.getData(MxtAttachments.FLIGHT);
        if (player.getVehicle() instanceof FlyingSwordEntity sword) {
            player.stopRiding();
            sword.discard();
        }
        player.getAbilities().mayfly = data.previousMayfly();
        player.getAbilities().flying = data.previousMayfly() && data.previousFlying();
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
