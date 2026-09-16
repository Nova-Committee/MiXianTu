package com.iafenvoy.mxt.runtime.formation;

import com.iafenvoy.mxt.config.MxtServerConfig;
import com.iafenvoy.mxt.data.Formation;
import com.iafenvoy.mxt.runtime.friend.FriendService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.util.TriState;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Who a formation treats as friendly: every friendly-fire decision goes through here so the answer has one
 * home. Whether the array identifies friends at all is its own switch, {@link Formation#spareFriends()}, never
 * inferred from the actions a pack wrote, and {@code config.mxt.server.formation.respect_friends} turns the
 * whole judgement off. {@code mxt:formation_owner} answers only whether an entity is the owner and must not
 * be widened.
 */
public final class FormationRelations {
    private FormationRelations() {
    }

    public static boolean isOwner(FormationCarrier carrier, Entity entity) {
        return carrier.owner().filter(entity.getUUID()::equals).isPresent();
    }

    /**
     * Whether a formation's per-entity work applies to this entity. Without {@code spare_friends} it applies
     * to everyone the array covers, owner and friends included; with it, friends are left alone and an entity
     * nobody can identify stops the whole array rather than being hit blind.
     *
     * @param ownerId the owner's id, or null when the formation records no owner at all
     * @param owner   that player's entity, or null while they are not loaded
     */
    public static boolean affects(Formation definition, @Nullable UUID ownerId, @Nullable Entity owner, Entity entity) {
        if (!definition.spareFriends() || !MxtServerConfig.INSTANCE.formations.respectFriends.getValue()) return true;
        if (ownerId == null) return false;
        return switch (FriendService.identify(ownerId, owner, entity)) {
            // A friend of the owner is spared.
            case TRUE -> false;
            // A stranger is affected.
            case FALSE -> true;
            // Nobody could identify this entity: stand down rather than fire blind.
            case DEFAULT -> false;
        };
    }

    /**
     * Whether the player may dismantle the formation at the controller: an owner may always take down their
     * own formation, an operator any, and an ownerless instance belongs to nobody so it is left open to
     * anyone rather than stranded. A friend of the owner counts too, but only while the server option says so:
     * taking an array down is demolition rather than an effect, so it is off by default and an entity nobody
     * can identify is refused like anyone else.
     */
    public static boolean canDismantle(FormationInstance instance, ServerPlayer player) {
        if (instance.owner().isEmpty()) return true;
        UUID ownerId = instance.owner().get();
        if (ownerId.equals(player.getUUID())) return true;
        if (player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)) return true;
        if (!MxtServerConfig.INSTANCE.formations.teammatesCanDismantle.getValue()) return false;
        return FriendService.identify(ownerId, player.level().getEntities().get(ownerId), player) == TriState.TRUE;
    }
}
