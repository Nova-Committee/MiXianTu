package com.iafenvoy.mxt.api;

import com.iafenvoy.mxt.data.ability.type.MountAbilityType;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;
import java.util.UUID;

/**
 * The body a {@code mxt:mount} entry flies in: implemented by the entity type that definition names under
 * {@code entity_type}, and the only way the framework ever reaches a mount. A type that does not implement it
 * cannot take off, however well the definition reads.
 *
 * <p>These seven members plus the owner reference of {@link OwnableEntity} are the whole contract; an entity already
 * answers {@code level()} and gains {@code getOwner()} / {@code getRootOwner()} from vanilla. Movement, geometry,
 * seats and boarding stay the entity's own business, and so does the artifact: what the framework handed over is
 * handed back by the framework.
 */
public interface MountVehicle extends OwnableEntity {
    // The artifact given up for the duration, which is also where the definition is read from. Empty once it has been
    // handed back, which is what makes that a one-time event.
    void setVisual(ItemStack visual);

    ItemStack visual();

    // Who gets the artifact back when the flight ends. Reading it is vanilla's own getOwner(); the write side is what
    // the framework has to be able to do at take-off, and there is no setter there.
    void setOwner(UUID owner);

    // The speed of this flight: already multiplied by the skill's own multiplier and clamped by the controller.
    void setFlightSpeed(double speed);

    // How many the vehicle carries in total, driver included, and how many of those are still free.
    int seats();

    int freeSeats();

    // Empty when the carried stack declares no mount, which is a vehicle summoned without an artifact.
    Optional<MountAbilityType> mountDefinition();
}
