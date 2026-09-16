package com.iafenvoy.mxt.runtime.formation;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.compat.ftb.FtbChunksCompat;
import com.iafenvoy.mxt.config.MxtServerConfig;
import com.iafenvoy.mxt.config.MxtServerConfig.ClaimLinkage;
import com.iafenvoy.mxt.data.Formation;
import com.iafenvoy.mxt.data.formation.FormationActionType;
import com.iafenvoy.mxt.data.formation.ProtectionFormationAction;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.runtime.friend.FriendService;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.TriState;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Answers whether a ward inside an active formation forbids an action. The decision lives here rather than
 * in the event subscribers so every path - breaking, placing, using, interacting, attacking, explosions,
 * mob griefing - asks one question with one answer that is assertable without a live block. A ward is
 * asked about both ends of the action: the flags apply when the actor or the target is inside the radius.
 */
public final class FormationProtection {
    /**
     * Definitions already reported as delegating with nothing to delegate to: one line per definition
     * rather than one per activation.
     */
    private static final Set<Identifier> WARNED_DELEGATIONS = ConcurrentHashMap.newKeySet();

    /**
     * Whether the inert {@code claims_only} option has been reported: one line per server, not per attempt.
     */
    private static final AtomicBoolean WARNED_INERT_LINKAGE = new AtomicBoolean();

    private FormationProtection() {
    }

    /**
     * What is being attempted, each answered by one flag of the protection module.
     */
    public enum Action {
        BREAK, PLACE, INTERACT, EXPLOSION, MOB_GRIEFING,
        ENTITY_INTERACT, ATTACK_ENTITY, ITEM_USE
    }

    /**
     * The actor's id is taken so the server audit can drive the rule without logged-in players.
     *
     * @param target  the block or entity acted on; a null target can still be refused by the actor's own
     *                position
     * @param actorId the player attempting it, null for an explosion or a mob
     */
    public static boolean prevented(ServerLevel level, Action action, @Nullable BlockPos target, @Nullable UUID actorId) {
        Entity actor = actorId == null ? null : level.getEntities().get(actorId);
        BlockPos actorPosition = actor == null ? null : actor.blockPosition();
        for (Entry<BlockPos, FormationInstance> entry : level.getData(MxtAttachments.FORMATION_WORLD).formations().entrySet()) {
            FormationInstance instance = entry.getValue();
            double radius = instance.radius();
            if (!inside(entry.getKey(), radius, target) && !inside(entry.getKey(), radius, actorPosition)) continue;
            Optional<Formation> definition = MxtDatapackRegistries.get(MxtResourceKeys.FORMATION, instance.formation());
            if (definition.isEmpty()) continue;
            for (FormationActionType module : definition.get().actions()) {
                if (!(module instanceof ProtectionFormationAction ward)) continue;
                // Handed to the claim plugin, either because the module says so or because the server's
                // linkage option puts a claim in charge where the ward stands.
                if (handsOver(ward, level, entry.getKey())) continue;
                if (!covers(ward, action)) continue;
                if (exempt(level, instance, ward.spareFriends(), actor)) continue;
                return true;
            }
        }
        return false;
    }

    /**
     * Whether the module says anything about this kind of action.
     */
    public static boolean covers(ProtectionFormationAction ward, Action action) {
        return switch (action) {
            case BREAK -> ward.blockBreak();
            case PLACE -> ward.blockPlace();
            case INTERACT -> ward.blockInteract();
            case EXPLOSION -> ward.explosions();
            case MOB_GRIEFING -> ward.mobGriefing();
            case ENTITY_INTERACT -> ward.entityInteract();
            case ATTACK_ENTITY -> ward.attackEntity();
            case ITEM_USE -> ward.itemUse();
        };
    }

    /**
     * Whether this ward's enforcement belongs to the claim plugin rather than to the formation: either the
     * module's {@code delegate_to_claims}, or the server option {@code claim_linkage} putting a claim in
     * charge of every ward whose controller's chunk is claimed. The claim is asked about the controller's
     * chunk, so a ward straddling a boundary is not governed by two rules at once.
     */
    private static boolean handsOver(ProtectionFormationAction ward, ServerLevel level, BlockPos controller) {
        boolean declared = ward.delegateToClaims();
        boolean claimed = MxtServerConfig.INSTANCE.compat.claimLinkage.getValue() == ClaimLinkage.CLAIMS_PRECEDENCE
                && FtbChunksCompat.chunkClaimed(level, controller);
        return (declared || claimed) && delegationHandsOver();
    }

    public static boolean hasProtection(Formation definition) {
        return definition.actions().stream().anyMatch(ProtectionFormationAction.class::isInstance);
    }

    /**
     * With no claim plugin installed there is nothing to require, so the option is inert rather than fatal -
     * refusing every ward would make the formation unplaceable for a reason the operator cannot satisfy -
     * but the first attempt says so once.
     */
    public static boolean claimsOnlyRefuses(ServerLevel level, BlockPos controller) {
        if (MxtServerConfig.INSTANCE.compat.claimLinkage.getValue() != ClaimLinkage.CLAIMS_ONLY) return false;
        if (!FtbChunksCompat.loaded()) {
            warnClaimsOnlyWithoutClaims();
            return false;
        }
        return !FtbChunksCompat.chunkClaimed(level, controller);
    }

    /**
     * Whether a ward here would stand on land somebody else has claimed and kept this person out of: being
     * allowed to place a block somewhere is not by itself leave to legislate there, so the landowner's own
     * answer decides. The claim plugin's edit permission is asked for rather than reproduced, since a second
     * copy of its rule here would drift. Everything else is refused, including an unresolvable actor.
     */
    public static boolean foreignClaimRefuses(ServerLevel level, BlockPos controller, @Nullable UUID actorId) {
        if (!MxtServerConfig.INSTANCE.compat.wardsNeedClaimPermission.getValue() || !FtbChunksCompat.loaded()) return false;
        // Nobody to judge and no permission of anybody's to check: an activation with no player behind it.
        if (actorId == null) return false;
        if (FtbChunksCompat.mayEdit(level, controller, actorId)) return false;
        Optional<UUID> owner = FtbChunksCompat.claimOwner(level, controller);
        // Unclaimed, or claimed by a team with no player whose friend list could speak for it.
        if (owner.isEmpty()) return false;
        Entity actor = level.getEntities().get(actorId);
        if (actor == null) return true;
        return FriendService.identify(owner.get(), level.getEntities().get(owner.get()), actor) != TriState.TRUE;
    }

    private static void warnClaimsOnlyWithoutClaims() {
        if (!WARNED_INERT_LINKAGE.compareAndSet(false, true)) return;
        MiXianTu.LOGGER.warn("config.mxt.server.compat.claim_linkage is claims_only, but FTB Chunks is not installed: "
                + "protection formations can be raised anywhere, because there are no claims to require");
    }

    /**
     * Delegating only means anything while the claim plugin's rules exist, and by default
     * {@code config.mxt.server.compat.delegate_requires_claims} insists they do. Asked on the decision
     * path, so it stays cheap.
     */
    public static boolean delegationHandsOver() {
        if (!MxtServerConfig.INSTANCE.compat.delegateRequiresClaims.getValue()) return true;
        return FtbChunksCompat.claimsProtect();
    }

    /**
     * The fallback is not the declared answer and nothing in play distinguishes it from a ward leaning on a
     * claim plugin, so it is said out loud once per definition on activation.
     */
    public static void warnIfDelegationFallsBack(Identifier id, Formation definition) {
        if (delegationHandsOver()) return;
        boolean delegates = definition.actions().stream()
                .anyMatch(module -> module instanceof ProtectionFormationAction ward && ward.delegateToClaims());
        if (!delegates || !WARNED_DELEGATIONS.add(id)) return;
        MiXianTu.LOGGER.warn("Formation {} delegates its protection to claims, but no claim protection is active "
                + "(FTB Chunks absent, or its claim protection switched off): its own flags stay in force. Turn off "
                + "the server option config.mxt.server.compat.delegate_requires_claims to hand it over regardless.", id);
    }

    private static boolean inside(BlockPos controller, double radius, @Nullable BlockPos position) {
        return position != null && controller.distSqr(position) <= radius * radius;
    }

    /**
     * The owner always passes, and friends only while the ward declares {@code spare_friends} and the
     * server option {@code respect_friends} is on. Everyone else is stopped, including an entity no friend
     * source can identify, and an ownerless ward exempts nobody.
     */
    private static boolean exempt(ServerLevel level, FormationInstance instance, boolean spareFriends,
                                  @Nullable Entity actor) {
        if (actor == null) return false;
        UUID ownerId = instance.owner().orElse(null);
        if (ownerId == null) return false;
        if (ownerId.equals(actor.getUUID())) return true;
        if (!spareFriends || !MxtServerConfig.INSTANCE.formations.respectFriends.getValue()) return false;
        return FriendService.identify(ownerId, level.getEntities().get(ownerId), actor) == TriState.TRUE;
    }
}
