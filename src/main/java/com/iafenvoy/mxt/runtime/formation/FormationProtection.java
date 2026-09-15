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

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Answers whether a ward inside an active formation forbids an action.
 *
 * <p>The decision lives here rather than in the event subscribers so that every path — breaking, placing,
 * using, interacting, attacking, explosions, mob griefing — asks one question with one answer, and so that
 * the answer is assertable without standing at a block and swinging at it.</p>
 *
 * <p><b>A ward is asked about both ends of the action.</b> The flags apply when the actor is inside the
 * radius or when the thing being acted on is, whichever comes first: a stranger reaching in from outside
 * is refused because the block is his ward's, and a stranger standing inside is refused because he is.
 * That is the whole of the position rule, and it is deliberately not two rules — a ward that protected the
 * ground but let an intruder act freely from within would be watching the wrong end.</p>
 */
public final class FormationProtection {
    /**
     * Definitions already reported as delegating with nothing to delegate to, so the warning is one line
     * per definition rather than one per activation.
     */
    private static final Set<Identifier> WARNED_DELEGATIONS = ConcurrentHashMap.newKeySet();

    /**
     * Whether the inert {@code claims_only} option has already been reported, so that it is one line per
     * server rather than one per attempt.
     */
    private static final AtomicBoolean WARNED_INERT_LINKAGE = new AtomicBoolean();

    private FormationProtection() {
    }

    /**
     * What is being attempted. Each of these is answered by one flag of the protection module.
     */
    public enum Action {
        BREAK, PLACE, INTERACT, EXPLOSION, MOB_GRIEFING,
        ENTITY_INTERACT, ATTACK_ENTITY, ITEM_USE
    }

    /**
     * Whether any ward forbids the action.
     *
     * <p>Takes the actor's id rather than the player object because the id is the whole of what the
     * exemption needs — it compares with the owner and asks the friend system about the pair — and a rule
     * that only needs a UUID is a rule the server audit can drive without a level full of logged-in
     * players.</p>
     *
     * @param target  the block or entity acted on, or null for an action with nothing in front of the
     *                player; a null target can still be refused by the actor's own position
     * @param actorId the player attempting it, or null when there is nobody to hold responsible —
     *                an explosion or a mob has no owner to exempt
     * @return true to prevent the action
     */
    public static boolean prevented(ServerLevel level, Action action, @Nullable BlockPos target, @Nullable UUID actorId) {
        Entity actor = actorId == null ? null : level.getEntities().get(actorId);
        BlockPos actorPosition = actor == null ? null : actor.blockPosition();
        for (Map.Entry<BlockPos, FormationInstance> entry : level.getData(MxtAttachments.FORMATION_WORLD).formations().entrySet()) {
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
     * Whether the module says anything about this kind of action at all.
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
     * Whether this ward's enforcement belongs to the claim plugin rather than to the formation.
     *
     * <p>Two ways to say so, and they are independent. The module's {@code delegate_to_claims} is the
     * content pack's own choice about that array. The server option {@code claim_linkage} is the server's
     * answer for <em>every</em> ward: under {@code claims_precedence}, a ward standing inside claimed land
     * adopts the claim's rules, because on that server the claim is what decides who may do what and a
     * formation is not entitled to a second opinion. Either way the handover only happens while
     * {@link #delegationHandsOver()} agrees there is something to hand it to.</p>
     *
     * <p>The claim is asked about the <b>controller's</b> chunk: the ward as a whole stands in a claim or it
     * does not, and asking per action would let a single ward straddling a boundary be governed by two
     * rules at once.</p>
     */
    private static boolean handsOver(ProtectionFormationAction ward, ServerLevel level, BlockPos controller) {
        boolean declared = ward.delegateToClaims();
        boolean claimed = MxtServerConfig.formationClaimLinkage() == ClaimLinkage.CLAIMS_PRECEDENCE
                && FtbChunksCompat.chunkClaimed(level, controller);
        return (declared || claimed) && delegationHandsOver();
    }

    /**
     * Whether a definition carries a ward at all.
     */
    public static boolean hasProtection(Formation definition) {
        return definition.actions().stream().anyMatch(ProtectionFormationAction.class::isInstance);
    }

    /**
     * Whether the {@code claims_only} option forbids a ward here.
     *
     * <p>For a server that would rather players claim what they defend than fence off the wilderness with
     * magic. With no claim plugin installed there is nothing to require, so the option is inert rather than
     * fatal — refusing every ward on a server that has no claims would make the formation unplaceable for a
     * reason the operator cannot satisfy. Inert is not silent, though: the first attempt says so once.</p>
     */
    public static boolean claimsOnlyRefuses(ServerLevel level, BlockPos controller) {
        if (MxtServerConfig.formationClaimLinkage() != ClaimLinkage.CLAIMS_ONLY) return false;
        if (!FtbChunksCompat.loaded()) {
            warnClaimsOnlyWithoutClaims();
            return false;
        }
        return !FtbChunksCompat.chunkClaimed(level, controller);
    }

    /**
     * Whether a ward here would stand on land somebody else has claimed and kept this person out of.
     *
     * <p>A ward is a claim of jurisdiction rather than a mere build, so being allowed to place a block
     * somewhere is not by itself leave to legislate there — and a structure that is already standing (a
     * template matching the terrain, or one somebody else built) would otherwise let anyone raise a ward
     * anywhere. This is the rule that closes that: the landowner's own answer decides, and there are three
     * ways to be let in.</p>
     *
     * <p>The claim plugin's edit permission is asked for rather than reproduced: whether a public claim, a
     * rank inside the team or an alliance applies is its rule, and a second copy here would be a copy that
     * drifts. An unclaimed position is nobody's land and passes. Being a friend of the team's owner passes
     * too, asked through the same friend system a ward's exemptions use — which is also where FTB
     * membership and alliances already arrive from. Everything else is refused, including an actor the level
     * cannot resolve: nobody can ask the landowner on his behalf.</p>
     */
    public static boolean foreignClaimRefuses(ServerLevel level, BlockPos controller, @Nullable UUID actorId) {
        if (!MxtServerConfig.formationWardsNeedClaimPermission() || !FtbChunksCompat.loaded()) return false;
        // Nobody to judge and no permission of anybody's to check: an activation with no player behind it.
        if (actorId == null) return false;
        if (FtbChunksCompat.mayEdit(level, controller, actorId)) return false;
        Optional<UUID> owner = FtbChunksCompat.claimOwner(level, controller);
        // Unclaimed, or claimed by a team with no player whose friend list could speak for it.
        if (owner.isEmpty()) return false;
        Entity actor = level.getEntities().get(actorId);
        // Nobody can ask the landowner on his behalf, so an actor the level cannot resolve is refused.
        if (actor == null) return true;
        return FriendService.identify(owner.get(), level.getEntities().get(owner.get()), actor) != TriState.TRUE;
    }

    private static void warnClaimsOnlyWithoutClaims() {
        if (!WARNED_INERT_LINKAGE.compareAndSet(false, true)) return;
        MiXianTu.LOGGER.warn("config.mxt.server.formation.claim_linkage is claims_only, but FTB Chunks is not installed: "
                + "protection formations can be raised anywhere, because there are no claims to require");
    }

    /**
     * Whether a module declaring {@code delegate_to_claims} may hand its enforcement over right now.
     *
     * <p>Delegating means "the claim plugin's rules are the ones that apply", so it is only meaningful
     * while such rules exist. Whether they must exist is the server's call, and the default is yes: with
     * {@code config.mxt.server.formation.delegate_requires_claims} on, a formation only hands its
     * protection over while a claim plugin is installed <em>and</em> its protection is switched on, and
     * otherwise falls back to its own flags. A ward that silently protects nothing is the one failure worth
     * a server option, and the option is what makes the alternative — the literal reading, where the flags
     * are handed over whether or not anything is there to catch them — available on purpose.</p>
     *
     * <p>Asked on the decision path, so it has to stay cheap: a mod-list lookup and one config read, both
     * live rather than cached, for the reason {@link FtbChunksCompat} gives.</p>
     */
    public static boolean delegationHandsOver() {
        if (!MxtServerConfig.formationDelegateRequiresClaims()) return true;
        return FtbChunksCompat.claimsProtect();
    }

    /**
     * Reports a formation whose delegation is falling back to its own flags.
     *
     * <p>The fallback is the safe answer, but it is not the declared one, and nothing in play distinguishes
     * a ward defending itself from a ward leaning on a claim plugin — so it is said out loud, once per
     * definition, on activation. With the option turned off there is nothing to report: the pack asked for
     * the literal reading and gets it.</p>
     */
    public static void warnIfDelegationFallsBack(Identifier id, Formation definition) {
        if (delegationHandsOver()) return;
        boolean delegates = definition.actions().stream()
                .anyMatch(module -> module instanceof ProtectionFormationAction ward && ward.delegateToClaims());
        if (!delegates || !WARNED_DELEGATIONS.add(id)) return;
        MiXianTu.LOGGER.warn("Formation {} delegates its protection to claims, but no claim protection is active "
                + "(FTB Chunks absent, or its claim protection switched off): its own flags stay in force. Turn off "
                + "the server option config.mxt.server.formation.delegate_requires_claims to hand it over regardless.", id);
    }

    private static boolean inside(BlockPos controller, double radius, @Nullable BlockPos position) {
        return position != null && controller.distSqr(position) <= radius * radius;
    }

    /**
     * Whether the actor is allowed through this ward.
     *
     * <p>The owner always is, and that is not configurable: a ward that stopped the person who raised it
     * would be a trap rather than a rule. Friends are a different matter — they pass only while the ward
     * declares {@code spare_friends} <em>and</em> the server option {@code respect_friends} is on, because
     * that option is the server's single answer to whether friends mean anything at all, and a ward is not
     * entitled to a second one.</p>
     *
     * <p>Everyone else is stopped, including entities no friend source can identify. That is the opposite
     * of how a hostile array reads the same silence — it holds fire — and the asymmetry is the point: the
     * safe reading of "unknown" is the one that leaves the world as it was, not the one that lets a
     * stranger through.</p>
     *
     * <p>An ownerless ward exempts nobody. There is no one whose permission it could be acting on, and
     * treating "no owner" as "everyone's ward" would let an abandoned array lock a region forever.</p>
     */
    private static boolean exempt(ServerLevel level, FormationInstance instance, boolean spareFriends,
                                  @Nullable Entity actor) {
        if (actor == null) return false;
        UUID ownerId = instance.owner().orElse(null);
        if (ownerId == null) return false;
        if (ownerId.equals(actor.getUUID())) return true;
        if (!spareFriends || !MxtServerConfig.formationRespectsFriends()) return false;
        return FriendService.identify(ownerId, level.getEntities().get(ownerId), actor) == TriState.TRUE;
    }
}
