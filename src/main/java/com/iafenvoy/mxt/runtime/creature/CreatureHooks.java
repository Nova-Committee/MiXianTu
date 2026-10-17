package com.iafenvoy.mxt.runtime.creature;

import com.iafenvoy.mxt.attachment.CreatureSpiritAttachment;
import com.iafenvoy.mxt.data.creature.ContractType;
import com.iafenvoy.mxt.event.dispatcher.CollectAbilityGrantsEvent;
import com.iafenvoy.mxt.event.dispatcher.CollectProgressionOwnersEvent;
import com.iafenvoy.mxt.event.dispatcher.EntitySources;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.runtime.Sources;
import com.iafenvoy.mxt.runtime.progression.ProgressionService;
import com.iafenvoy.mxt.util.HolderHelper;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

/**
 * What the creature side of a body contributes: the abilities a creature profile's level grants and the profile it
 * owns a chain for, plus what the contracts it holds grant its owner. A profile's levels are kept in the creature's
 * own spirit attachment, so they cannot collide with another sort of owner's records.
 */
@EventBusSubscriber
public final class CreatureHooks {
    private CreatureHooks() {
    }

    // A profile's entry level is what its creature is born with, so whatever level it stands on is the whole
    // answer - the cumulative rule techniques use, asked of the profile.
    @SubscribeEvent
    public static void onCollectGrants(CollectAbilityGrantsEvent event) {
        LivingEntity entity = event.getEntity();
        CreatureSpiritAttachment spirit = entity.getExistingData(MxtAttachments.CREATURE_SPIRIT).orElse(null);
        if (spirit == null) return;
        RegistryAccess access = entity.level().registryAccess();
        spirit.profile().ifPresent(profile -> spirit.profileLevels().current(profile)
                .ifPresent(current -> event.wantResolved(Sources.granted(Sources.Grant.CREATURE, HolderHelper.id(profile)), ProgressionService.grantedAbilities(profile.value(), current, access))));
    }

    // The owner side of a contract is held by the owner, not by the beast that signed it, so the index of bound
    // beasts is the answer to which types this body holds; the source is the type, so two beasts of one contract
    // are one grant and the same ability granted by two types survives losing either one of them.
    @SubscribeEvent
    public static void onCollectContractGrants(CollectAbilityGrantsEvent event) {
        LivingEntity entity = event.getEntity();
        for (Holder<ContractType> type : ContractGrantService.heldTypes(entity))
            event.want(Sources.granted(Sources.Grant.CONTRACT, HolderHelper.id(type)), type.value().ownerAbilities());
    }

    // A profile only counts as an owner once it names a chain: a profile that stays a pure stat block keeps every
    // creature exactly as it was before progressions existed.
    @SubscribeEvent
    public static void onCollectOwners(CollectProgressionOwnersEvent event) {
        CreatureSpiritAttachment spirit = event.getEntity().getExistingData(MxtAttachments.CREATURE_SPIRIT).orElse(null);
        if (spirit == null) return;
        spirit.profile()
                .filter(profile -> profile.value().entryLevel().isPresent())
                .ifPresent(profile -> event.add(new EntitySources.Owner<>(profile, spirit.profileLevels())));
    }
}
