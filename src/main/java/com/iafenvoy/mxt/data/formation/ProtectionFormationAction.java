package com.iafenvoy.mxt.data.formation;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * The protection module: what people may not do to the array, or from inside it.
 *
 * <p>Both scopes live in one record because they are one rule. A ward defends a place, and a place is
 * defended both by refusing what is done <em>to</em> it and by refusing what is done <em>from</em> it, so
 * the question is asked about either end of the action: the flags apply when the player is inside, or when
 * the block or entity being acted on is inside. Kept as two modules they were two halves that had to be
 * declared together to say one thing, and the overlap on right-click — the one action the ground and the
 * person both have an opinion about — read as a contradiction when it was only a difference of position.</p>
 *
 * <p>Every flag defaults to true, so declaring the module is the whole statement and a pack that wants to
 * leave one of them alone writes it false. The two environmental flags are the expensive ones — each is
 * asked on a path that runs far more often than a player ever clicks — but a ward that let explosions and
 * mob griefing through by default would be protecting a building from everything except the two things
 * that level it.</p>
 *
 * <p><b>{@code delegate_to_claims} hands the whole job to the server's claim plugin.</b> With it set, none
 * of the flags above are enforced at all: the module becomes a declaration that protection comes from
 * claims, and FTB Chunks — which protects its own claims by itself and unconditionally — is what a player
 * actually runs into. This needs no API call and no dependency, because standing down is the whole of it;
 * the flag is a packaging switch, not an integration. Two consequences follow, and both belong to whoever
 * sets it: the formation then protects only what is <em>claimed</em>, so its own radius stops mattering,
 * and on a server without a claim plugin it protects nothing at all. Activation logs a warning in that
 * second case rather than letting a ward stand there doing nothing in silence.</p>
 *
 * <p><b>Who is exempt.</b> The owner always is: a ward that locked its own caster out of his house would be
 * a bug, not a rule, so this is not configurable and does not depend on {@code respect_friends}. Friends
 * are exempt only while {@code spare_friends} is set <em>and</em> the server option
 * {@code config.mxt.server.formation.respect_friends} is on — that option is the server's single "do
 * friends mean anything here" switch, and a ward is not special enough to ignore it. An entity nobody can
 * identify is treated as a stranger and therefore <em>is</em> stopped, which is the opposite of how a
 * hostile array reads the same silence: the safe direction for a ward is to keep protecting, and the safe
 * direction for a weapon is to hold fire.</p>
 *
 * <p>Explosions and mob griefing have no actor, so nobody can be recognised as the owner in either case;
 * while those flags are on, an array stops every explosion and every mob that would otherwise grief inside
 * it, its owner's own explosions included. That is the point of a ward, and a pack that wants its owner to
 * keep blasting inside his own array writes {@code explosions: false}.</p>
 *
 * <p>Two gaps are worth knowing rather than discovering. {@code attack_entity} answers for a melee swing,
 * which is the event the game routes through the attacker; a projectile fired from inside is not an attack
 * the shooter performs at the moment it lands, so it is not stopped. And {@code item_use} covers using an
 * item with nothing in front of the player — a bucket, a potion, a bow being drawn — while using one
 * <em>on</em> a block is a block interaction and is answered by {@code block_interact}.</p>
 */
public record ProtectionFormationAction(boolean blockBreak, boolean blockPlace, boolean blockInteract,
                                        boolean explosions, boolean mobGriefing, boolean entityInteract,
                                        boolean attackEntity, boolean itemUse, boolean spareFriends,
                                        boolean delegateToClaims) implements FormationActionType {
    public static final MapCodec<ProtectionFormationAction> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.BOOL.optionalFieldOf("block_break", true).forGetter(ProtectionFormationAction::blockBreak),
            Codec.BOOL.optionalFieldOf("block_place", true).forGetter(ProtectionFormationAction::blockPlace),
            Codec.BOOL.optionalFieldOf("block_interact", true).forGetter(ProtectionFormationAction::blockInteract),
            Codec.BOOL.optionalFieldOf("explosions", true).forGetter(ProtectionFormationAction::explosions),
            Codec.BOOL.optionalFieldOf("mob_griefing", true).forGetter(ProtectionFormationAction::mobGriefing),
            Codec.BOOL.optionalFieldOf("entity_interact", true).forGetter(ProtectionFormationAction::entityInteract),
            Codec.BOOL.optionalFieldOf("attack_entity", true).forGetter(ProtectionFormationAction::attackEntity),
            Codec.BOOL.optionalFieldOf("item_use", true).forGetter(ProtectionFormationAction::itemUse),
            Codec.BOOL.optionalFieldOf("spare_friends", true).forGetter(ProtectionFormationAction::spareFriends),
            Codec.BOOL.optionalFieldOf("delegate_to_claims", false).forGetter(ProtectionFormationAction::delegateToClaims)
    ).apply(i, ProtectionFormationAction::new));

    @Override
    public MapCodec<ProtectionFormationAction> codec() {
        return CODEC;
    }
}
