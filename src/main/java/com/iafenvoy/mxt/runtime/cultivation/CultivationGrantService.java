package com.iafenvoy.mxt.runtime.cultivation;

import com.iafenvoy.mxt.attachment.AbilityHolderData;
import com.iafenvoy.mxt.attachment.SpiritData;
import com.iafenvoy.mxt.data.cultivation.CultivationTechniqueDefinition;
import com.iafenvoy.mxt.data.cultivation.PhysiqueDefinition;
import com.iafenvoy.mxt.data.cultivation.SpiritRootDefinition;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;

/**
 * Rebuilds only the abilities owned by cultivation identity sources.
 */
public final class CultivationGrantService {
    private static final String SOURCE_PREFIX = "grant/";

    private CultivationGrantService() {
    }

    public static Result recalculate(SpiritData spirit, AbilityHolderData abilities) {
        return recalculate(spirit, abilities, id -> MxtDatapackRegistries.get(MxtDatapackRegistries.SPIRIT_ROOT, id), id -> MxtDatapackRegistries.get(MxtDatapackRegistries.PHYSIQUE, id),
                id -> MxtDatapackRegistries.get(MxtDatapackRegistries.CULTIVATION_TECHNIQUE, id));
    }

    public static Result recalculate(SpiritData spirit, AbilityHolderData abilities, Lookup<SpiritRootDefinition> roots,
                                     Lookup<PhysiqueDefinition> physiques, Lookup<CultivationTechniqueDefinition> techniques) {
        int revoked = 0;
        for (Entry<Identifier, List<Identifier>> entry : abilities.sources().entrySet()) {
            for (Identifier source : entry.getValue()) {
                if (isCultivationSource(source) && abilities.revoke(entry.getKey(), source)) revoked++;
            }
        }
        int granted = 0;
        for (Identifier rootId : spirit.spiritRoots()) {
            Optional<SpiritRootDefinition> definition = roots.get(rootId);
            if (definition.isPresent())
                granted += grantAll(abilities, definition.get().grantedAbilities(), source("spirit_root", rootId));
        }
        for (Identifier physiqueId : spirit.physiques()) {
            Optional<PhysiqueDefinition> definition = physiques.get(physiqueId);
            if (definition.isPresent())
                granted += grantAll(abilities, definition.get().grantedAbilities(), source("physique", physiqueId));
        }
        for (Identifier techniqueId : spirit.learnedTechniques()) {
            Optional<CultivationTechniqueDefinition> definition = techniques.get(techniqueId);
            if (definition.isPresent())
                granted += grantAll(abilities, definition.get().grantedAbilities(), source("technique", techniqueId));
        }
        return new Result(granted, revoked);
    }

    private static int grantAll(AbilityHolderData holder, List<Identifier> values, Identifier source) {
        int granted = 0;
        for (Identifier ability : values) if (holder.grant(ability, source)) granted++;
        return granted;
    }

    private static Identifier source(String category, Identifier content) {
        return Identifier.fromNamespaceAndPath("mxt", SOURCE_PREFIX + category + "/" + content.getNamespace() + "/" + content.getPath());
    }

    private static boolean isCultivationSource(Identifier source) {
        return source.getNamespace().equals("mxt") && source.getPath().startsWith(SOURCE_PREFIX);
    }

    @FunctionalInterface
    public interface Lookup<T> {
        Optional<T> get(Identifier id);
    }

    public record Result(int granted, int revoked) {
    }
}
