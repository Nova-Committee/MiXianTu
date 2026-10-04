package com.iafenvoy.mxt.runtime.creature;

import com.iafenvoy.mxt.attachment.CreatureSpiritAttachment;
import com.iafenvoy.mxt.attachment.ProgressionAttachment;
import com.iafenvoy.mxt.data.creature.ContractType;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.runtime.EntitySources;
import com.iafenvoy.mxt.runtime.ModuleHooks;
import com.iafenvoy.mxt.runtime.Sources;
import com.iafenvoy.mxt.runtime.progression.ProgressionService;
import com.iafenvoy.mxt.util.HolderHelper;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.List;

/**
 * What the creature side of a body contributes: the abilities a creature profile's level grants and the profile it
 * owns a chain for, plus what the contracts it holds grant its owner.
 */
public final class CreatureHooks implements EntitySources.Grants, EntitySources.Owns {
    private CreatureHooks() {
    }

    public static void register() {
        CreatureHooks hooks = new CreatureHooks();
        ModuleHooks.register(EntitySources.Grants.class, hooks);
        ModuleHooks.register(EntitySources.Owns.class, hooks);
        ModuleHooks.register(EntitySources.Grants.class, new ContractGrants());
    }

    // A profile's entry level is what its creature is born with, so whatever level it stands on is the whole
    // answer - the cumulative rule techniques use, asked of the profile.
    @Override
    public void collect(LivingEntity entity, ProgressionAttachment progress, EntitySources.GrantSink sink) {
        CreatureSpiritAttachment spirit = entity.getExistingData(MxtAttachments.CREATURE_SPIRIT).orElse(null);
        if (spirit == null) return;
        spirit.profile().ifPresent(profile -> ProgressionService.currentLevel(progress, HolderHelper.id(profile), profile.value())
                .ifPresent(current -> sink.wantResolved(Sources.granted(Sources.Grant.CREATURE, HolderHelper.id(profile)),
                        ProgressionService.grantedAbilities(profile.value(), current))));
    }

    // A profile only counts as an owner once it names a chain: a profile that stays a pure stat block keeps every
    // creature exactly as it was before progressions existed.
    @Override
    public List<EntitySources.Owner> held(Entity entity) {
        CreatureSpiritAttachment spirit = entity.getExistingData(MxtAttachments.CREATURE_SPIRIT).orElse(null);
        if (spirit == null) return List.of();
        return spirit.profile()
                .filter(profile -> profile.value().entryLevel().isPresent())
                .map(profile -> List.of(new EntitySources.Owner(HolderHelper.id(profile), profile.value())))
                .orElse(List.of());
    }

    // The owner side of a contract is held by the owner, not by the beast that signed it, so the index of bound
    // beasts is the answer to which types this body holds; the source is the type, so two beasts of one contract
    // are one grant and the same ability granted by two types survives losing either one of them.
    private static final class ContractGrants implements EntitySources.Grants {
        @Override
        public void collect(LivingEntity entity, ProgressionAttachment progress, EntitySources.GrantSink sink) {
            for (Holder<ContractType> type : ContractGrantService.heldTypes(entity))
                sink.want(Sources.granted(Sources.Grant.CONTRACT, HolderHelper.id(type)), type.value().ownerAbilities());
        }
    }
}
