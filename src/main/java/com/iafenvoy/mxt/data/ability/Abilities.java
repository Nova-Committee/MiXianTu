package com.iafenvoy.mxt.data.ability;

import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

/**
 * Turns a stored ability id back into its registry holder, and a declared ability list ("an id or a {@code #tag}")
 * into the holders it names. Both halves of the reference end here, against the same registry both sides of the
 * connection have; the holder is an ability's whole address, since the id is the registry id of {@code mxt:ability}
 * and nothing else names one. A missing holder reads as "no such ability", which is how a deleted definition and a
 * disabled one look the same to every caller.
 */
public final class Abilities {
    private Abilities() {
    }

    public static Optional<Holder<Ability>> resolve(Provider access, @Nullable Identifier id) {
        if (id == null) return Optional.empty();
        return MxtDatapackRegistries.holder(access, MxtResourceKeys.ABILITY, id).map(holder -> (Holder<Ability>) holder);
    }
}
