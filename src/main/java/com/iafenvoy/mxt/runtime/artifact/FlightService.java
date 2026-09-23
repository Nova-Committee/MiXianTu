package com.iafenvoy.mxt.runtime.artifact;

import com.iafenvoy.mxt.attachment.FlightAttachment;
import com.iafenvoy.mxt.data.ability.Ability;
import com.iafenvoy.mxt.data.ability.type.FlightAbilityType;
import com.iafenvoy.mxt.data.artifact.Artifact;
import com.iafenvoy.mxt.data.cost.Cost;
import com.iafenvoy.mxt.data.cost.CostTransaction;
import com.iafenvoy.mxt.data.cost.context.CostContext;
import com.iafenvoy.mxt.data.cost.context.CostOrigin;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.registry.MxtEntityTypes;
import com.iafenvoy.mxt.util.HolderHelper;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import net.minecraft.core.Holder.Reference;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.NeoForgeMod;

import java.util.List;

/**
 * Authoritative generic flight controller for flying swords and artifacts: the server mounts the sword, charges
 * the upkeep and owns every end of the flight.
 */
public final class FlightService {
    private FlightService() {
    }

    public static Result mount(ServerPlayer player, ItemStack carrier, Holder<Ability> ability, FormulaContext context) {
        // The ability says whether this can fly at all by being a flight entry, so an artifact that never
        // mentioned flight is not one whose speed happens to be zero.
        if (!(ability.value().type() instanceof FlightAbilityType flight)) return Result.rejected(Failure.NOT_FLYABLE);
        if (!ownsEquippedArchetype(player, carrier, ability)) {
            return Result.rejected(Failure.NOT_OWNED);
        }
        FlightAttachment data = player.getData(MxtAttachments.FLIGHT);
        if (data.active()) return Result.rejected(Failure.ALREADY_ACTIVE);
        double speed = flight.speed().evaluate(context);
        if (!Double.isFinite(speed) || speed <= 0.0D) return Result.rejected(Failure.INVALID_FORMULA);
        FlyingSwordEntity sword = new FlyingSwordEntity(MxtEntityTypes.FLYING_SWORD.get(), player.level());
        sword.setPos(player.getX(), player.getY(), player.getZ());
        sword.setFlightSpeed(speed);
        // The vehicle is drawn as the artifact that summoned it, so the mount's look is the item's own model. The
        // copy matters: the vehicle keeps what it is handed, and the main-hand stack is mutated elsewhere.
        sword.setVisual(carrier.copy());
        player.level().addFreshEntity(sword);
        if (!player.startRiding(sword, true, true)) {
            sword.discard();
            return Result.rejected(Failure.CANNOT_MOUNT);
        }
        // The flight allowance is an attribute ({@code NeoForgeMod.CREATIVE_FLIGHT}); the mayfly flag is the
        // deprecated view of it, and reading the attribute is what can actually be written back.
        data.start(ability, player.level().getGameTime(), player.getAttributeValue(NeoForgeMod.CREATIVE_FLIGHT),
                player.getAbilities().flying, player.getAbilities().getFlyingSpeed(), sword.getUUID());
        return Result.mounted();
    }

    // Still the ability the flight was started with: this carrier offers it and that definition still lets this
    // player use it. Resolved from the item rather than read off a component, so re-defining an item mid-flight
    // ends the flight instead of silently continuing with stale numbers.
    public static boolean ownsEquippedArchetype(ServerPlayer player, ItemStack carrier, Holder<Ability> ability) {
        if (carrier.isEmpty()) return false;
        Provider access = player.level().registryAccess();
        Reference<Artifact> definition = ArtifactService.definition(access, carrier).orElse(null);
        if (definition == null || !ArtifactService.mayUse(carrier, definition, player.getUUID())) return false;
        return ArtifactService.abilities(access, carrier).stream().anyMatch(ref -> HolderHelper.id(ref).equals(HolderHelper.id(ability)));
    }

    public static Result tick(ServerPlayer player, Holder<Ability> ability, FormulaContext context) {
        FlightAttachment data = player.getData(MxtAttachments.FLIGHT);
        if (!data.active()) return Result.inactive();
        if (!(ability.value().type() instanceof FlightAbilityType flight))
            return dismount(player, Failure.NOT_FLYABLE);
        if (!(player.getVehicle() instanceof FlyingSwordEntity sword) || data.vehicle().filter(sword.getUUID()::equals).isEmpty()) {
            return dismount(player, Failure.MOUNT_LOST);
        }
        double speed = flight.speed().evaluate(context);
        if (!Double.isFinite(speed) || speed <= 0.0D) return dismount(player, Failure.INVALID_FORMULA);
        sword.setFlightSpeed(speed);
        if (sword.horizontalCollision || sword.verticalCollision) return dismount(player, Failure.COLLISION);
        List<Cost> costs = ability.value().costs();
        CostTransaction.PayResult payment = CostTransaction.pay(costs,
                CostContext.of(player, context, CostOrigin.ARTIFACT_FLIGHT));
        if (!costs.isEmpty() && !payment.paid())
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
