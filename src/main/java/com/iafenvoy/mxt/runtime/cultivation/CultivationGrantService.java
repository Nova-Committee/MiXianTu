package com.iafenvoy.mxt.runtime.cultivation;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.attachment.AbilityHolderData;
import com.iafenvoy.mxt.attachment.SpiritData;
import com.iafenvoy.mxt.data.cultivation.CultivationTechnique;
import com.iafenvoy.mxt.data.cultivation.Physique;
import com.iafenvoy.mxt.data.cultivation.SpiritRoot;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.registry.MxtRegistryKeys;
import com.iafenvoy.mxt.util.HolderHelper;
import com.iafenvoy.mxt.util.codec.RegistryCodecs;
import com.iafenvoy.mxt.data.ability.Ability;
import com.mojang.datafixers.util.Either;
import net.minecraft.core.Holder;
import net.minecraft.tags.TagKey;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Map;

/**
 * Rebuilds only the abilities owned by cultivation identity sources.
 */
public final class CultivationGrantService {
    private static final String SOURCE_PREFIX = "grant/";

    private CultivationGrantService() {
    }

    public static Result recalculate(SpiritData spirit, AbilityHolderData abilities) {
        int revoked = 0;
        for (Map.Entry<Holder<Ability>, Identifier> entry : abilities.sources().entries())
            if (isCultivationSource(entry.getValue()) && abilities.revoke(entry.getKey(), entry.getValue())) revoked++;
        int granted = 0;
        for (Holder<SpiritRoot> root : spirit.spiritRoots()) {
            granted += grantAll(abilities, root.value().grantedAbilities(), source("spirit_root", HolderHelper.id(root)));
        }
        for (Holder<Physique> physique : spirit.physiques()) {
            granted += grantAll(abilities, physique.value().grantedAbilities(), source("physique", HolderHelper.id(physique)));
        }
        for (Holder<CultivationTechnique> technique : spirit.learnedTechniques()) {
            granted += grantAll(abilities, technique.value().grantedAbilities(), source("technique", HolderHelper.id(technique)));
        }
        return new Result(granted, revoked);
    }

    private static int grantAll(AbilityHolderData holder, List<Either<Holder<Ability>, TagKey<Ability>>> values, Identifier source) {
        int granted = 0;
        for (Holder<Ability> ability : RegistryCodecs.resolve(values, MxtDatapackRegistries.registry(MxtRegistryKeys.ABILITY))
                .distinct().toList())
            if (holder.grant(ability, source)) granted++;
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
