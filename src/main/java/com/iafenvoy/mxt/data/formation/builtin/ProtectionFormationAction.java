package com.iafenvoy.mxt.data.formation.builtin;

import com.iafenvoy.mxt.data.formation.FormationActionType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * The protection module: what may not be done to the array, or from inside it. The flags answer for either end of an
 * action (the player doing it is inside, or the target is), and every flag defaults to true, so declaring the
 * module is the whole statement. {@code delegate_to_claims} hands the job to the server's claim plugin: no flag is
 * enforced and only claimed land is protected. The owner is always exempt, friends only while
 * {@code spare_friends} and the server option are on, and an unidentifiable entity counts as a stranger.
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
