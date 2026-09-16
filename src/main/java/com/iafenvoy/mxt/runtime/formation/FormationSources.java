package com.iafenvoy.mxt.runtime.formation;

import com.iafenvoy.mxt.MiXianTu;
import net.minecraft.resources.Identifier;

/**
 * The ability source identity a formation's grants are expected to use: {@code mxt:grant_ability} takes an
 * explicit {@code source} and a formation cannot invent one for the content pack. Using {@link #of} buys
 * automatic cleanup, since the ticker reconciles that source to nothing for every entity that leaves.
 */
public final class FormationSources {
    private FormationSources() {
    }

    /**
     * The source a formation's grants are released through.
     */
    public static Identifier of(Identifier formation) {
        return Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID,
                "formation/" + formation.getNamespace() + "/" + formation.getPath());
    }
}
