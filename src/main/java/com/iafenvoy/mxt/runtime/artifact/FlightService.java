package com.iafenvoy.mxt.runtime.artifact;

import com.iafenvoy.mxt.attachment.FlightAttachment;
import com.iafenvoy.mxt.data.ability.Ability;
import com.iafenvoy.mxt.data.ability.type.FlightControlAbilityType;
import com.iafenvoy.mxt.data.ability.type.MountAbilityType;
import com.iafenvoy.mxt.data.artifact.Artifact;
import com.iafenvoy.mxt.data.aura.Aura;
import com.iafenvoy.mxt.data.cost.Cost;
import com.iafenvoy.mxt.data.cost.CostTransaction;
import com.iafenvoy.mxt.data.cost.context.CostContext;
import com.iafenvoy.mxt.data.cost.context.CostOrigin;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.registry.MxtEntityTypes;
import com.iafenvoy.mxt.util.HolderHelper;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import net.minecraft.core.Holder;
import net.minecraft.core.Holder.Reference;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Unit;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.NeoForgeMod;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Authoritative flight controller: the skill finds the vehicle, the vehicle is taken from the hand and becomes an
 * entity, and the server owns every end of the flight.
 *
 * <p>Two prices, two declarations: the skill's own {@code costs} are what starting costs and are paid by the shared
 * gate before this runs, while the mount's {@code costs} are the fuel of every tick and are paid here - out of the
 * artifact the mount carries first, and out of the pilot only for what the artifact cannot cover.
 */
public final class FlightService {
    private FlightService() {
    }

    // What one skill picked up: the mount entry, the stack declaring it, and the hand it has to leave.
    private record Mount(Holder<Ability> ability, MountAbilityType type, ItemStack stack, InteractionHand hand) {
    }

    public static Result mount(LivingEntity holder, Holder<Ability> skill, FormulaContext context) {
        if (!(skill.value().type() instanceof FlightControlAbilityType control))
            return Result.rejected(Failure.NOT_FLYABLE);
        Mount found = find(holder, control.hand());
        if (found == null) return Result.rejected(Failure.NO_VEHICLE);
        Provider access = holder.level().registryAccess();
        Reference<Artifact> definition = ArtifactService.definition(access, found.stack()).orElse(null);
        if (definition == null || !ArtifactService.mayUse(found.stack(), definition, holder.getUUID()))
            return Result.rejected(Failure.NOT_OWNED);
        FlightAttachment data = holder.getData(MxtAttachments.FLIGHT);
        if (data.active()) return Result.rejected(Failure.ALREADY_ACTIVE);
        double speed = speed(found.type(), control, context);
        if (!found.type().canMove(speed)) return Result.rejected(Failure.INVALID_FORMULA);
        FlyingSwordEntity sword = new FlyingSwordEntity(MxtEntityTypes.FLYING_SWORD.get(), holder.level());
        sword.setPos(holder.getX(), holder.getY(), holder.getZ());
        sword.setFlightSpeed(speed);
        sword.setOwner(holder.getUUID());
        // The artifact leaves the hand and rides inside the mount: that is the custody this design has, and it is also
        // why the press cannot live on the artifact's own entry - its grants end the moment the stack leaves.
        holder.setItemInHand(found.hand(), ItemStack.EMPTY);
        sword.setVisual(found.stack());
        // A summon the world refuses never ticks, so it would never give the artifact back either: the item is
        // handed back here rather than left inside an entity nothing will ever remove.
        if (!holder.level().addFreshEntity(sword)) {
            sword.giveBack();
            return Result.rejected(Failure.CANNOT_MOUNT);
        }
        if (!holder.startRiding(sword, true, true)) {
            // Discarding runs the mount's own return path, so a failed start costs the holder nothing.
            sword.discard();
            return Result.rejected(Failure.CANNOT_MOUNT);
        }
        // The flight allowance is an attribute ({@code NeoForgeMod.CREATIVE_FLIGHT}); the mayfly flag is the
        // deprecated view of it, and reading the attribute is what can actually be written back. Only a player has
        // any of that to remember, so the snapshot stays at its defaults for anything else.
        data.start(skill, HolderHelper.id(found.ability()), holder.level().getGameTime(),
                holder.getAttributeValue(NeoForgeMod.CREATIVE_FLIGHT),
                holder instanceof Player player && player.getAbilities().flying,
                holder instanceof Player flying ? flying.getAbilities().getFlyingSpeed() : 0.0F, sword.getUUID());
        // The mount's own "the flight has begun" hook. It runs on the driver, because that is the entity a pack can
        // actually give an effect or a resource to; the mount itself is data with a body.
        found.type().actions().onMount().execute(holder, context);
        return Result.mounted();
    }

    // Only the hands: an artifact that is not held is not being used, and a flight never has to ask where the thing
    // that started it went, because the mount entity is holding it.
    private static Mount find(LivingEntity holder, FlightControlAbilityType.Hand hand) {
        List<InteractionHand> hands = switch (hand) {
            case MAIN -> List.of(InteractionHand.MAIN_HAND);
            case OFF -> List.of(InteractionHand.OFF_HAND);
            case EITHER -> List.of(InteractionHand.MAIN_HAND, InteractionHand.OFF_HAND);
        };
        Provider access = holder.level().registryAccess();
        for (InteractionHand slot : hands) {
            ItemStack stack = holder.getItemInHand(slot);
            if (stack.isEmpty()) continue;
            Holder<Ability> ability = ArtifactService.mountAbility(access, stack).orElse(null);
            if (ability == null) continue;
            return new Mount(ability, (MountAbilityType) ability.value().type(), stack, slot);
        }
        return null;
    }

    private static double speed(MountAbilityType mount, FlightControlAbilityType control, FormulaContext context) {
        return mount.speed().evaluate(context) * control.speedMultiplier().evaluate(context);
    }

    public static Result tick(LivingEntity holder, Holder<Ability> skill, Holder<Ability> vehicle, FormulaContext context) {
        FlightAttachment data = holder.getData(MxtAttachments.FLIGHT);
        if (!data.active()) return Result.inactive();
        if (!(skill.value().type() instanceof FlightControlAbilityType control))
            return dismount(holder, Failure.NOT_FLYABLE);
        if (!(vehicle.value().type() instanceof MountAbilityType mount))
            return dismount(holder, Failure.NOT_FLYABLE);
        if (!(holder.getVehicle() instanceof FlyingSwordEntity sword) || data.mount().filter(sword.getUUID()::equals).isEmpty()) {
            return dismount(holder, Failure.MOUNT_LOST);
        }
        double speed = speed(mount, control, context);
        if (!mount.canMove(speed)) return dismount(holder, Failure.INVALID_FORMULA);
        sword.setFlightSpeed(speed);
        if (!vehicle.value().condition().test(holder, context)) return dismount(holder, Failure.CONDITION_FAILED);
        // The mount moves for real every tick, so hitting a wall or the ground ends the flight instead of grinding
        // along it - see FlyingSwordEntity#tick for why the prediction that used to guard this made it dead code.
        if (sword.horizontalCollision || sword.verticalCollision) return dismount(holder, Failure.COLLISION);
        List<Cost> costs = vehicle.value().costs();
        if (!costs.isEmpty() && !payFuel(holder, sword, costs, context))
            return dismount(holder, Failure.INSUFFICIENT_RESOURCE);
        // After the fuel, so a tick this hook runs for is a tick that was paid for.
        mount.actions().tick().execute(holder, context);
        return Result.flying();
    }

    // The fuel is bought out of the mount's own artifact before the rider's pool: the stack that became this
    // vehicle carries the aura it burns, so what it holds is spent first and only the rest falls back on the
    // pilot. The whole price is planned first, the artifact's share is taken out of that plan, and the shared
    // transaction pays what is left - it rolls its own writes back, so the artifact's share is handed back when
    // the remainder cannot be paid.
    private static boolean payFuel(LivingEntity holder, FlyingSwordEntity sword, List<Cost> costs, FormulaContext context) {
        CostContext costContext = CostContext.of(holder, context, CostOrigin.ARTIFACT_FLIGHT);
        CostTransaction.Planning planning = CostTransaction.planDeferred(costs, costContext);
        List<Fuel> burned = burn(sword, planning, context);
        CostTransaction.PayResult payment = CostTransaction.commit(planning, costContext);
        if (payment.paid()) return true;
        refund(sword, burned, context);
        return false;
    }

    // One aura's share of a tick, kept so a refused payment can put it back where it came from.
    private record Fuel(Holder<Aura> aura, double amount) {
    }

    private static List<Fuel> burn(FlyingSwordEntity sword, CostTransaction.Planning planning, FormulaContext context) {
        ItemStack stack = sword.visual();
        if (stack.isEmpty()) return List.of();
        Provider access = sword.level().registryAccess();
        List<Fuel> burned = new ArrayList<>();
        for (Map.Entry<Identifier, Double> entry : new ArrayList<>(planning.resources().entrySet())) {
            double left = entry.getValue();
            // A price written as a resource still names the aura an artifact can pay it in, so the store is
            // asked which of its auras are counted in that resource.
            for (Holder<Aura> aura : ArtifactService.aurasFor(stack, entry.getKey())) {
                if (left <= 0.0D) break;
                double capacity = ArtifactService.capacity(access, stack, aura, 0.0D, context);
                if (capacity <= 0.0D) continue;
                double taken = ArtifactService.energyStorage(stack, aura, capacity).extract(left);
                if (taken <= 0.0D) continue;
                left -= taken;
                burned.add(new Fuel(aura, taken));
            }
            if (left <= 0.0D) planning.resources().remove(entry.getKey());
            else entry.setValue(left);
        }
        return burned;
    }

    // Straight back into the store, so a refusal costs the artifact exactly what it had before the tick.
    private static void refund(FlyingSwordEntity sword, List<Fuel> burned, FormulaContext context) {
        if (burned.isEmpty()) return;
        ItemStack stack = sword.visual();
        Provider access = sword.level().registryAccess();
        for (Fuel fuel : burned) {
            double capacity = ArtifactService.capacity(access, stack, fuel.aura(), 0.0D, context);
            ArtifactService.energyStorage(stack, fuel.aura(), capacity).receive(fuel.amount());
        }
    }

    // The spawns the fill command leaves in the empty seats: the mount takes them with it when the flight ends.
    public static final String SEAT_DUMMY_TAG = "mxt:seat_dummy";

    // Seat offsets are only ever judged by eye, so this puts a body in every seat still open and reports how many.
    // The markers do not think, do not despawn, and wear an unbreakable helmet so daylight cannot burn them away.
    public static int fillSeats(FlyingSwordEntity sword) {
        int filled = 0;
        for (int seat = sword.freeSeats(); seat > 0; seat--) {
            Zombie dummy = EntityType.ZOMBIE.create(sword.level(), EntitySpawnReason.COMMAND);
            if (dummy == null) break;
            dummy.setNoAi(true);
            dummy.setPersistenceRequired();
            dummy.addTag(SEAT_DUMMY_TAG);
            // A head slot that is occupied and cannot take damage is what stops the daylight burn entirely.
            ItemStack helmet = new ItemStack(Items.IRON_HELMET);
            helmet.set(DataComponents.UNBREAKABLE, Unit.INSTANCE);
            dummy.setItemSlot(EquipmentSlot.HEAD, helmet);
            dummy.setPos(sword.getX(), sword.getY(), sword.getZ());
            if (!sword.level().addFreshEntity(dummy) || !dummy.startRiding(sword, true, true)) {
                dummy.discard();
                break;
            }
            filled++;
        }
        return filled;
    }

    // Whatever is left of the flight restores the holder's own pre-flight state, which only a player has. The mount
    // hands the artifact back from its own removal path, so this only has to end the ride.
    public static Result dismount(LivingEntity holder, Failure reason) {
        FlightAttachment data = holder.getData(MxtAttachments.FLIGHT);
        if (holder.getVehicle() instanceof FlyingSwordEntity sword) {
            // Before the seat is given up, so the hook still sees a rider on a mount. A flight whose driver is
            // already gone has nobody to run it on, which is why the logout path has no hook.
            sword.mountDefinition().ifPresent(mount -> mount.actions().onDismount().execute(holder, FormulaContext.of(holder)));
            holder.stopRiding();
            sword.discard();
        }
        if (holder instanceof Player player) {
            player.getAbilities().flying = data.previousFlight() > 0.0D && data.previousFlying();
            AttributeInstance flight = player.getAttribute(NeoForgeMod.CREATIVE_FLIGHT);
            if (flight != null) flight.setBaseValue(data.previousFlight());
            player.getAbilities().setFlyingSpeed(data.previousFlyingSpeed());
            player.onUpdateAbilities();
        }
        data.stop();
        return Result.stopped(reason);
    }

    public enum Failure {NOT_FLYABLE, NO_VEHICLE, NOT_OWNED, ALREADY_ACTIVE, INVALID_FORMULA, CONDITION_FAILED, INSUFFICIENT_RESOURCE, COLLISION, STOPPED, CANNOT_MOUNT, MOUNT_LOST}

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
