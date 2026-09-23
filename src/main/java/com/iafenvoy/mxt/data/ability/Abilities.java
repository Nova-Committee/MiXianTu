package com.iafenvoy.mxt.data.ability;

import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

/**
 * The one place a stored ability id becomes its holder: the caller passes the registry access it has, so both sides
 * of the connection read the same registry. The holder is an ability's whole address, since the id is the registry
 * id of {@code mxt:ability} and nothing else names one. A missing holder reads as "no such ability", which is how a
 * deleted definition and a disabled one look the same to every caller.
 *
 * <p>That last half is the part a stored {@code Holder} does not carry: whoever keeps a holder instead of an id
 * loses the {@code mxt:disabled} filter and has to ask {@code MxtDatapackRegistries.isDisabled} before acting.
 */
public final class Abilities {
    private Abilities() {
    }

    public static Optional<Holder<Ability>> resolve(Provider access, @Nullable Identifier id) {
        if (id == null) return Optional.empty();
        return MxtDatapackRegistries.holder(access, MxtResourceKeys.ABILITY, id).map(holder -> holder);
    }
}
