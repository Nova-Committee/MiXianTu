package com.iafenvoy.mxt.api;

import com.iafenvoy.mxt.data.creature.ContractBehavior;
import com.iafenvoy.mxt.data.creature.ContractBehaviors;
import com.iafenvoy.mxt.data.creature.ContractContext;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

import java.util.List;

/**
 * What a bound creature does on its own. The framework no longer drives following, recall or combat itself:
 * it asks. Every default reproduces the behaviour the framework used to hardcode, so a creature that
 * implements this interface and overrides nothing behaves the way a contract always did.
 *
 * <p>Not implementing it is a choice with a meaning: the creature can be bound, but the framework drives no
 * general behaviour for it, and the contract type's {@code follow_action} and {@code combat_action} are not
 * run either - they exist to colour exactly these two moments.</p>
 */
public interface ContractOperations {
    /**
     * The orders this creature takes, in the order the owner's wheel shows them. The default is the four the
     * framework defines; a creature with an order of its own answers with it here as well, and the framework
     * never sends one that is missing from this list. It has to be answerable on both sides, since the bell
     * reads it to fill the wheel.
     */
    default List<ContractBehavior> behaviors() {
        return ContractBehaviors.BUILT_IN;
    }

    /**
     * The owner picked an order - the bell's wheel or {@code /contract behavior}. Answering false refuses it and
     * leaves the order in force alone. A momentary order that is not the built-in recall stops here as well: it is
     * an event the creature acts on once, never a state the record keeps.
     */
    default boolean onBehaviorSelected(ContractContext context, ContractBehavior behavior) {
        return true;
    }

    /**
     * Once per server tick while bound, with the owner online and in the same level: do whatever the order in
     * force means for this creature. The default answers the orders the framework defines and ignores any other,
     * so a creature that adds one of its own overrides this.
     */
    default void tick(ContractContext context, ContractBehavior behavior) {
        if (behavior.equals(ContractBehaviors.FOLLOW)) this.follow(context);
        else if (behavior.equals(ContractBehaviors.WANDER)) this.wander(context);
        else if (behavior.equals(ContractBehaviors.STAY)) this.stay(context);
    }

    // The owner rang the bell, and this call consumes the recall latch. The default blinks the creature to the
    // owner; one that walks there instead leaves the entity where it is and moves it on its own.
    default void recall(ContractContext context) {
        ServerPlayer owner = context.player();
        if (owner == null || owner.level() != context.self().level()) return;
        context.self().teleportTo(owner.getX(), owner.getY(), owner.getZ());
    }

    // Follow: teleport past 32 blocks, pathfind past 4. The walk-up the framework used to hardcode, and the order
    // a contract starts under.
    default void follow(ContractContext context) {
        Mob self = context.self();
        ServerPlayer owner = context.player();
        if (owner == null || owner.level() != self.level()) return;
        double distance = self.distanceToSqr(owner);
        if (distance > 32.0D * 32.0D) self.teleportTo(owner.getX(), owner.getY(), owner.getZ());
        else if (distance > 4.0D * 4.0D) self.getNavigation().moveTo(owner, 1.0D);
    }

    // Wander: keep near the owner without stepping in their footprints. A stroll adds no goal of its own, so a
    // creature whose own goals already walk it around keeps doing that until it overrides this.
    default void wander(ContractContext context) {
        Mob self = context.self();
        ServerPlayer owner = context.player();
        if (owner == null || owner.level() != self.level()) return;
        // Out of sight is brought over, the same rescue following uses: an owner who keeps walking must not leave
        // a strolling beast behind for good.
        if (self.distanceToSqr(owner) > 32.0D * 32.0D) {
            self.teleportTo(owner.getX(), owner.getY(), owner.getZ());
            return;
        }
        // A spot is picked only when there is nothing left to walk to, and only once every couple of seconds, so
        // a stroll is not a pathfinding call per tick.
        if (!self.getNavigation().isDone() || self.getRandom().nextInt(40) != 0) return;
        double angle = self.getRandom().nextDouble() * Math.PI * 2.0D;
        double radius = 3.0D + self.getRandom().nextDouble() * 5.0D;
        self.getNavigation().moveTo(owner.getX() + Math.cos(angle) * radius, owner.getY(),
                owner.getZ() + Math.sin(angle) * radius, 1.0D);
    }

    // Stay: stop rather than freeze, so the creature still looks around. A creature whose own goals walk it
    // around overrides this if it wants to hold truly still.
    default void stay(ContractContext context) {
        Mob self = context.self();
        self.getNavigation().stop();
        self.setTarget(null);
    }

    // After damage this creature dealt is resolved; the hit itself is never mutated here.
    default void onDealtDamage(ContractContext context, LivingEntity target, double damage) {
    }
}
