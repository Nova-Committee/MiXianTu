package com.iafenvoy.mxt.runtime.formation;

import com.iafenvoy.mxt.MiXianTu;
import net.minecraft.resources.Identifier;

/**
 * The ability source identity that a formation's grants are expected to use.
 *
 * <p>{@code mxt:grant_ability} already takes an explicit, persistent {@code source}, and a formation
 * cannot invent one for the content pack. This class publishes the convention instead: a formation
 * with id {@code <namespace>:<path>} owns the source {@code mxt:formation/<namespace>/<path>}.</p>
 *
 * <p>Using it buys automatic cleanup. The ticker reconciles that source to nothing for every entity
 * that leaves the formation, for every entity still tracked when the formation is torn down, and for
 * every player the sweep finds outside it — so a buff cannot outlive the formation even though
 * {@code ABILITY_HOLDER} is persistent and {@code deactivate_action} cannot see entities.</p>
 *
 * <p>Nothing breaks if a content pack ignores the convention: reconciling an unused source matches
 * nothing. A grant that must survive the formation simply belongs to a different source.</p>
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
