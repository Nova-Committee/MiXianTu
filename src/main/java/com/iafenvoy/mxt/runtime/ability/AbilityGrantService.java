package com.iafenvoy.mxt.runtime.ability;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.attachment.AbilityAttachment;
import com.iafenvoy.mxt.attachment.CreatureSpiritAttachment;
import com.iafenvoy.mxt.attachment.ProgressionAttachment;
import com.iafenvoy.mxt.attachment.SpiritIdentityAttachment;
import com.iafenvoy.mxt.data.ability.Ability;
import com.iafenvoy.mxt.data.creature.ContractType;
import com.iafenvoy.mxt.data.cultivation.Physique;
import com.iafenvoy.mxt.data.cultivation.RealmStage;
import com.iafenvoy.mxt.data.cultivation.SpiritRoot;
import com.iafenvoy.mxt.data.cultivation.Technique;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.runtime.creature.ContractGrantService;
import com.iafenvoy.mxt.runtime.cultivation.MinorStageService;
import com.iafenvoy.mxt.runtime.progression.ProgressionService;
import com.iafenvoy.mxt.util.HolderHelper;
import com.iafenvoy.mxt.util.codec.RegistryCodecs;
import com.mojang.datafixers.util.Either;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.LivingEntity;

import java.util.List;
import java.util.Map.Entry;

/**
 * Rebuilds every ability a definition grants, and the only place that does: a level change, a lost technique, a
 * dropped spirit root and a released contract all end here, so nothing that granted an ability can be left
 * granting it. Sources are revoked by the shared {@code grant/} prefix, so a new granting system only has to add
 * its own category.
 */
public final class AbilityGrantService {
    private static final String SOURCE_PREFIX = AbilitySources.GRANT_PREFIX;

    private AbilityGrantService() {
    }

    public static Result recalculate(LivingEntity entity) {
        AbilityAttachment abilities = entity.getData(MxtAttachments.ABILITY_HOLDER);
        int revoked = 0;
        // Revocation mutates the multimap, so iterate a stable snapshot of its entries.
        for (Entry<Identifier, Identifier> entry : List.copyOf(abilities.sources().entries()))
            if (isGrantSource(entry.getValue()) && abilities.revoke(entry.getKey(), entry.getValue()))
                revoked++;
        ProgressionAttachment progress = entity.getExistingData(MxtAttachments.PROGRESSION).orElse(null);
        int granted = cultivation(entity, abilities, progress) + creature(entity, abilities, progress)
                + contract(entity, abilities);
        AbilityEventBridge.rebuildTriggerSubscriptions(entity);
        return new Result(granted, revoked);
    }

    private static int cultivation(LivingEntity entity, AbilityAttachment abilities, ProgressionAttachment progress) {
        SpiritIdentityAttachment spirit = entity.getExistingData(MxtAttachments.SPIRIT_IDENTITY).orElse(null);
        if (spirit == null) return 0;
        int granted = 0;
        for (Holder<SpiritRoot> root : spirit.activeSpiritRoots())
            granted += grantAll(abilities, root.value().grantedAbilities(), source("spirit_root", HolderHelper.id(root)));
        for (Holder<Physique> physique : spirit.activePhysiques())
            granted += grantAll(abilities, physique.value().grantedAbilities(), source("physique", HolderHelper.id(physique)));
        for (Holder<Technique> technique : spirit.learnedTechniques()) {
            Identifier id = HolderHelper.id(technique);
            Identifier source = source("technique", id);
            granted += grantAll(abilities, technique.value().grantedAbilities(), source);
            // Mastery adds to the same source, so a promotion only has to change the level: a technique's
            // grants are revoked and rebuilt together.
            granted += ProgressionService.currentLevel(progress, id, technique.value())
                    .map(current -> grantResolved(abilities, ProgressionService.grantedAbilities(technique.value(), current), source))
                    .orElse(0);
        }
        // A realm stage's unlocks are keyed by how far the body got inside it, so the record is the whole answer:
        // no record means the body never stood in that realm, and a realm it has left keeps granting.
        for (Entry<Holder<RealmStage>, Integer> record : spirit.minorStageRecords().entrySet()) {
            Identifier source = source("realm_stage", HolderHelper.id(record.getKey()));
            granted += grantResolved(abilities, MinorStageService.unlockedAbilities(record.getKey(), record.getValue()), source);
        }
        return granted;
    }

    // A profile's entry level is what its creature is born with, so whatever level it stands on is the whole
    // answer - the cumulative rule techniques use, asked of the creature's own profile.
    private static int creature(LivingEntity entity, AbilityAttachment abilities, ProgressionAttachment progress) {
        CreatureSpiritAttachment spirit = entity.getExistingData(MxtAttachments.CREATURE_SPIRIT).orElse(null);
        if (spirit == null) return 0;
        return spirit.profile()
                .map(profile -> ProgressionService.currentLevel(progress, HolderHelper.id(profile), profile.value())
                        .map(current -> grantResolved(abilities, ProgressionService.grantedAbilities(profile.value(), current),
                                source("creature", HolderHelper.id(profile))))
                        .orElse(0))
                .orElse(0);
    }

    // A contract's owner side is held by the owner, not by the beast that signed it, so the index of bound beasts
    // is the answer to which types this body holds. The source is the type, so two beasts of one contract are one
    // grant, and the same ability granted by two types survives losing either one of them.
    private static int contract(LivingEntity entity, AbilityAttachment abilities) {
        int granted = 0;
        for (Holder<ContractType> type : ContractGrantService.heldTypes(entity))
            granted += grantAll(abilities, type.value().ownerAbilities(), source("contract", HolderHelper.id(type)));
        return granted;
    }

    private static int grantAll(AbilityAttachment holder, List<Either<Holder<Ability>, TagKey<Ability>>> values, Identifier source) {
        return grantResolved(holder, RegistryCodecs.resolve(values, MxtDatapackRegistries.registry(MxtResourceKeys.ABILITY))
                .distinct().toList(), source);
    }

    private static int grantResolved(AbilityAttachment holder, List<Holder<Ability>> abilities, Identifier source) {
        int granted = 0;
        for (Holder<Ability> ability : abilities)
            if (holder.grant(HolderHelper.id(ability), source)) granted++;
        return granted;
    }

    private static Identifier source(String category, Identifier content) {
        return Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, SOURCE_PREFIX + category + "/" + content.getNamespace() + "/" + content.getPath());
    }

    private static boolean isGrantSource(Identifier source) {
        return source.getNamespace().equals(MiXianTu.MOD_ID) && source.getPath().startsWith(SOURCE_PREFIX);
    }

    public record Result(int granted, int revoked) {
    }
}
