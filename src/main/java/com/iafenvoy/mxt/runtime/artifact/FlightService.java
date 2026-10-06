package com.iafenvoy.mxt.runtime.artifact;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.api.MountVehicle;
import com.iafenvoy.mxt.attachment.FlightAttachment;
import com.iafenvoy.mxt.data.ability.Ability;
import com.iafenvoy.mxt.data.ability.type.FlightControlAbilityType;
import com.iafenvoy.mxt.data.ability.type.MountAbilityType;
import com.iafenvoy.mxt.data.artifact.Artifact;
import com.iafenvoy.mxt.data.aura.Aura;
import com.iafenvoy.mxt.data.cost.Cost;
import com.iafenvoy.mxt.data.cost.CostPayment;
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
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Unit;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.NeoForgeMod;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Authoritative flight controller: the skill finds the vehicle, the vehicle is taken from the hand and becomes an
 * entity of the type its definition names, and the server owns every end of the flight.
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

    // The types a definition has already named that cannot be flown, so the log says it once rather than per press.
    private static final Set<EntityType<?>> UNFLYABLE = new HashSet<>();

    // The body a definition names, or the framework's own when it names none. Server side only: whether a type can be
    // flown is only visible by creating one, and a definition naming a body that cannot is a load-clean pack mistake.
    public static Optional<MountVehicle> createVehicle(Level level, MountAbilityType mount) {
        EntityType<?> type = mount.entityType().orElse(MxtEntityTypes.FLYING_SWORD.get());
        Entity entity = type.create(level, EntitySpawnReason.COMMAND);
        if (entity instanceof MountVehicle vehicle) return Optional.of(vehicle);
        if (entity != null) entity.discard();
        if (UNFLYABLE.add(type))
            MiXianTu.LOGGER.warn("Mount entity type {} does not implement MountVehicle and cannot be flown",
                    BuiltInRegistries.ENTITY_TYPE.getKey(type));
        return Optional.empty();
    }

    // Hands the artifact back, at most once: the carried stack is cleared before it is handed over, so a discard and a
    // kill in the same tick cannot duplicate it. Where it goes is the vehicle's own owner, or the ground it flew over.
    public static void giveBack(MountVehicle vehicle) {
        if (!(vehicle instanceof Entity body) || body.level().isClientSide()) return;
        ItemStack stack = vehicle.visual();
        if (stack.isEmpty()) return;
        vehicle.setVisual(ItemStack.EMPTY);
        if (vehicle.getOwner() instanceof ServerPlayer owner && owner.isAlive() && owner.level() == body.level()) {
            if (!owner.getInventory().add(stack)) owner.drop(stack, false);
            return;
        }
        if (body.level() instanceof ServerLevel level)
            level.addFreshEntity(new ItemEntity(level, body.getX(), body.getY(), body.getZ(), stack));
    }

    // Everything a take-off needs before it does anything, as a read: the press asks this before the shared gate
    // pays, so a press that cannot take off costs nothing. mount asks it again, and a state that changed in between
    // is still caught.
    public static @Nullable Failure canMount(LivingEntity holder, Holder<Ability> skill, FormulaContext context) {
        if (!(skill.value().type() instanceof FlightControlAbilityType control))
            return Failure.NOT_FLYABLE;
        Mount found = find(holder, control.hand());
        if (found == null) return Failure.NO_VEHICLE;
        Provider access = holder.level().registryAccess();
        Reference<Artifact> definition = ArtifactService.definition(access, found.stack()).orElse(null);
        if (definition == null || !ArtifactService.mayUse(found.stack(), definition, holder.getUUID()))
            return Failure.NOT_OWNED;
        if (holder.getData(MxtAttachments.FLIGHT).active()) return Failure.ALREADY_ACTIVE;
        return found.type().canMove(speed(found.type(), control, context)) ? null : Failure.INVALID_FORMULA;
    }

    public static Result mount(LivingEntity holder, Holder<Ability> skill, FormulaContext context) {
        Failure refusal = canMount(holder, skill, context);
        if (refusal != null) return Result.rejected(refusal);
        FlightControlAbilityType control = (FlightControlAbilityType) skill.value().type();
        Mount found = find(holder, control.hand());
        FlightAttachment data = holder.getData(MxtAttachments.FLIGHT);
        double speed = speed(found.type(), control, context);
        MountVehicle vehicle = createVehicle(holder.level(), found.type()).orElse(null);
        if (vehicle == null) return Result.rejected(Failure.INVALID_VEHICLE);
        // The contract promises a body, not one that is an entity: only an entity can be created by an EntityType and
        // only an entity can be ridden, so this cast cannot fail where it is made.
        Entity body = (Entity) vehicle;
        body.setPos(holder.getX(), holder.getY(), holder.getZ());
        vehicle.setFlightSpeed(speed);
        vehicle.setOwner(holder.getUUID());
        // The artifact leaves the hand and rides inside the mount: that is the custody this design has, and it is also
        // why the press cannot live on the artifact's own entry - its grants end the moment the stack leaves.
        holder.setItemInHand(found.hand(), ItemStack.EMPTY);
        vehicle.setVisual(found.stack());
        // A summon the world refuses never reaches a removal, so the artifact is handed back here rather than left
        // inside an entity nothing will ever remove.
        if (!holder.level().addFreshEntity(body)) {
            giveBack(vehicle);
            return Result.rejected(Failure.CANNOT_MOUNT);
        }
        if (!holder.startRiding(body, true, true)) {
            // Discarding is a removal, which is what hands the artifact back, so a failed start costs nothing.
            body.discard();
            return Result.rejected(Failure.CANNOT_MOUNT);
        }
        // The flight allowance is an attribute ({@code NeoForgeMod.CREATIVE_FLIGHT}); the mayfly flag is the
        // deprecated view of it, and reading the attribute is what can actually be written back. Only a player has
        // any of that to remember, so the snapshot stays at its defaults for anything else.
        data.start(skill, HolderHelper.id(found.ability()), holder.level().getGameTime(),
                holder.getAttributeValue(NeoForgeMod.CREATIVE_FLIGHT),
                holder instanceof Player player && player.getAbilities().flying,
                holder instanceof Player flying ? flying.getAbilities().getFlyingSpeed() : 0.0F, body.getUUID());
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
        Entity body = holder.getVehicle();
        if (!(body instanceof MountVehicle mountVehicle) || data.mount().filter(body.getUUID()::equals).isEmpty()) {
            return dismount(holder, Failure.MOUNT_LOST);
        }
        double speed = speed(mount, control, context);
        if (!mount.canMove(speed)) return dismount(holder, Failure.INVALID_FORMULA);
        mountVehicle.setFlightSpeed(speed);
        if (!vehicle.value().condition().test(holder, context)) return dismount(holder, Failure.CONDITION_FAILED);
        // The mount moves for real every tick, so hitting a wall or the ground ends the flight instead of grinding
        // along it - see the vehicle's own tick for why the prediction that used to guard this made it dead code.
        if (body.horizontalCollision || body.verticalCollision) return dismount(holder, Failure.COLLISION);
        List<Cost> costs = vehicle.value().costs();
        if (!costs.isEmpty() && !payFuel(holder, mountVehicle, body.level(), costs, context))
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
    private static boolean payFuel(LivingEntity holder, MountVehicle vehicle, Level level, List<Cost> costs,
                                   FormulaContext context) {
        CostContext costContext = CostContext.of(holder, context, CostOrigin.ARTIFACT_FLIGHT);
        CostPayment planning = CostPayment.of(costContext);
        if (planning.loadAll(costs).isPresent()) return false;
        List<Fuel> burned = burn(vehicle, level, planning, context);
        CostPayment.Result payment = planning.commit();
        if (payment.paid()) return true;
        refund(vehicle, level, burned, context);
        return false;
    }

    // One aura's share of a tick, kept so a refused payment can put it back where it came from.
    private record Fuel(Holder<Aura> aura, double amount) {
    }

    private static List<Fuel> burn(MountVehicle vehicle, Level level, CostPayment planning,
                                   FormulaContext context) {
        ItemStack stack = vehicle.visual();
        if (stack.isEmpty()) return List.of();
        Provider access = level.registryAccess();
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
    private static void refund(MountVehicle vehicle, Level level, List<Fuel> burned, FormulaContext context) {
        if (burned.isEmpty()) return;
        ItemStack stack = vehicle.visual();
        Provider access = level.registryAccess();
        for (Fuel fuel : burned) {
            double capacity = ArtifactService.capacity(access, stack, fuel.aura(), 0.0D, context);
            ArtifactService.energyStorage(stack, fuel.aura(), capacity).receive(fuel.amount());
        }
    }

    // The spawns the fill command leaves in the empty seats: whoever ends the flight takes them with it, so a vehicle
    // an addon wrote never has to know these markers exist.
    public static final String SEAT_DUMMY_TAG = "mxt:seat_dummy";

    public static void clearSeatMarkers(Entity body) {
        for (Entity passenger : new ArrayList<>(body.getPassengers()))
            if (passenger.entityTags().contains(SEAT_DUMMY_TAG)) passenger.discard();
    }

    // Seat offsets are only ever judged by eye, so this puts a body in every seat still open and reports how many.
    // The markers do not think, do not despawn, and wear an unbreakable helmet so daylight cannot burn them away.
    public static int fillSeats(Entity body) {
        if (!(body instanceof MountVehicle vehicle)) return 0;
        int filled = 0;
        for (int seat = vehicle.freeSeats(); seat > 0; seat--) {
            Zombie dummy = EntityType.ZOMBIE.create(body.level(), EntitySpawnReason.COMMAND);
            if (dummy == null) break;
            dummy.setNoAi(true);
            dummy.setPersistenceRequired();
            dummy.addTag(SEAT_DUMMY_TAG);
            // A head slot that is occupied and cannot take damage is what stops the daylight burn entirely.
            ItemStack helmet = new ItemStack(Items.IRON_HELMET);
            helmet.set(DataComponents.UNBREAKABLE, Unit.INSTANCE);
            dummy.setItemSlot(EquipmentSlot.HEAD, helmet);
            dummy.setPos(body.getX(), body.getY(), body.getZ());
            if (!body.level().addFreshEntity(dummy) || !dummy.startRiding(body, true, true)) {
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
        Entity body = holder.getVehicle();
        if (body instanceof MountVehicle vehicle) {
            // Before the seat is given up, so the hook still sees a rider on a mount. A flight whose driver is
            // already gone has nobody to run it on, which is why the logout path has no hook.
            vehicle.mountDefinition().ifPresent(mount -> mount.actions().onDismount().execute(holder, FormulaContext.of(holder)));
            holder.stopRiding();
            // Discarding is a removal, and a removal is what hands the artifact back and clears the seat markers:
            // see EntityMountRemovalMixin, which is the one place that rule lives.
            body.discard();
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

    public enum Failure {NOT_FLYABLE, NO_VEHICLE, NOT_OWNED, ALREADY_ACTIVE, INVALID_FORMULA, CONDITION_FAILED, INSUFFICIENT_RESOURCE, COLLISION, STOPPED, CANNOT_MOUNT, MOUNT_LOST, INVALID_VEHICLE}

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
