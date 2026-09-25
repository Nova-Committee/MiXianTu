package com.iafenvoy.mxt.runtime.formation;

import com.iafenvoy.mxt.config.MxtServerConfig;
import com.iafenvoy.mxt.data.Formation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.util.TriState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

/**
 * Who a formation treats as friendly: every friendly-fire decision goes through here so the answer has one home.
 * Whether the array identifies friends at all is its own switch, {@link Formation#spareFriends()}, never inferred
 * from the actions a pack wrote, and {@code config.mxt.server.formation.respect_friends} turns the whole
 * judgement off. {@code mxt:formation_owner} answers only whether an entity is an owner and must not be widened.
 *
 * <p>A shared array has several owners, so every question here is asked about the whole set rather than one id.
 */
public final class FormationRelations {
    private FormationRelations() {
    }

    public static boolean isOwner(FormationCarrier carrier, Entity entity) {
        return carrier.owners().contains(entity);
    }

    // Without spare_friends it applies to everyone the array covers, owners and friends included; with it,
    // friends are left alone and an entity nobody can identify stops the whole array rather than being hit
    // blind. An ownerless array has nobody to identify anybody by.
    public static boolean affects(Formation definition, FormationOwners owners, Level level, Entity entity) {
        if (!definition.spareFriends() || !MxtServerConfig.INSTANCE.formations.respectFriends.getValue()) return true;
        if (owners.isEmpty()) return false;
        return switch (owners.identify(level, entity)) {
            // A friend of any owner is spared.
            case TRUE -> false;
            // A stranger is affected.
            case FALSE -> true;
            // Nobody could identify this entity: stand down rather than fire blind.
            case DEFAULT -> false;
        };
    }

    // An owner may always take down their own formation, an operator any, and an ownerless instance belongs to
    // nobody so it is left open to anyone rather than stranded. A friend of an owner counts too, but only while
    // the server option says so: taking an array down is demolition rather than an effect, so it is off by
    // default and an entity nobody can identify is refused like anyone else.
    public static boolean canDismantle(FormationInstance instance, ServerPlayer player) {
        FormationOwners owners = instance.owners();
        if (owners.isEmpty()) return true;
        if (owners.contains(player)) return true;
        if (player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)) return true;
        if (!MxtServerConfig.INSTANCE.formations.teammatesCanDismantle.getValue()) return false;
        return owners.identify(player.level(), player) == TriState.TRUE;
    }
}
