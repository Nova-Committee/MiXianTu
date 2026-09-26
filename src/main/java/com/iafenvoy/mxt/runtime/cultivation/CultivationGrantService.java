package com.iafenvoy.mxt.runtime.cultivation;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.attachment.AbilityAttachment;
import com.iafenvoy.mxt.attachment.SpiritIdentityAttachment;
import com.iafenvoy.mxt.data.ability.Ability;
import com.iafenvoy.mxt.data.cultivation.Physique;
import com.iafenvoy.mxt.data.cultivation.RealmStage;
import com.iafenvoy.mxt.data.cultivation.SpiritRoot;
import com.iafenvoy.mxt.data.cultivation.Technique;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.runtime.ability.AbilityEventBridge;
import com.iafenvoy.mxt.runtime.ability.AbilitySources;
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
 * Rebuilds only the abilities owned by cultivation identity sources.
 */
public final class CultivationGrantService {
    private static final String SOURCE_PREFIX = AbilitySources.GRANT_PREFIX;

    private CultivationGrantService() {
    }

    public static Result recalculate(SpiritIdentityAttachment spirit, AbilityAttachment abilities) {
        int revoked = 0;
        // Revocation mutates the multimap, so iterate a stable snapshot of its entries.
        for (Entry<Identifier, Identifier> entry : List.copyOf(abilities.sources().entries()))
            if (isCultivationSource(entry.getValue()) && abilities.revoke(entry.getKey(), entry.getValue()))
                revoked++;
        int granted = 0;
        for (Holder<SpiritRoot> root : spirit.activeSpiritRoots()) {
            granted += grantAll(abilities, root.value().grantedAbilities(), source("spirit_root", HolderHelper.id(root)));
        }
        for (Holder<Physique> physique : spirit.activePhysiques()) {
            granted += grantAll(abilities, physique.value().grantedAbilities(), source("physique", HolderHelper.id(physique)));
        }
        for (Holder<Technique> technique : spirit.learnedTechniques()) {
            Identifier source = source("technique", HolderHelper.id(technique));
            granted += grantAll(abilities, technique.value().grantedAbilities(), source);
            // Mastery adds to the same source, so a promotion only has to change the level: a technique's
            // grants are revoked and rebuilt together.
            granted += SkillStageService.currentStage(spirit, technique)
                    .map(current -> grantResolved(abilities, SkillStageService.unlockedAbilities(technique.value(), current), source))
                    .orElse(0);
        }
        // A realm stage's unlocks are keyed by how far the body got inside it, so the record is the whole answer:
        // no record means the body never stood in that realm, and a realm it has left keeps granting.
        for (Entry<Holder<RealmStage>, Integer> record : spirit.minorStageRecords().entrySet()) {
            Identifier source = source("realm_stage", HolderHelper.id(record.getKey()));
            granted += grantResolved(abilities, MinorStageService.unlockedAbilities(record.getKey(), record.getValue()), source);
        }
        return new Result(granted, revoked);
    }

    public static Result recalculate(LivingEntity entity, SpiritIdentityAttachment spirit, AbilityAttachment abilities) {
        Result result = recalculate(spirit, abilities);
        AbilityEventBridge.rebuildTriggerSubscriptions(entity);
        return result;
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

    private static boolean isCultivationSource(Identifier source) {
        return source.getNamespace().equals(MiXianTu.MOD_ID) && source.getPath().startsWith(SOURCE_PREFIX);
    }

    public record Result(int granted, int revoked) {
    }
}
