package com.iafenvoy.mxt.data.creature;

import com.iafenvoy.mxt.util.HolderHelper;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;

import java.util.Optional;

/**
 * The entity type tag a contract type owns: {@code #<namespace>:contract/<path>}, named after the contract
 * type itself. It is the datapack-side answer to "which creatures sign this contract", so a pack narrows the
 * list with a vanilla tag instead of a field of its own; a contract type without the tag is unrestricted, and
 * the interface remains the only thing that can make a creature contractable at all.
 */
public final class ContractTags {
    private ContractTags() {
    }

    public static TagKey<EntityType<?>> of(Identifier contractId) {
        return TagKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath(contractId.getNamespace(), "contract/" + contractId.getPath()));
    }

    // Client-safe: the vanilla entity type registry is the same static registry on both sides. A tag that is
    // absent - or written but empty - places no restriction, so an unwritten list can never lock a contract out.
    public static boolean accepts(ContractContext context) {
        Identifier id = HolderHelper.idOrNull(context.type());
        if (id == null) return true;
        Optional<HolderSet.Named<EntityType<?>>> allowed = BuiltInRegistries.ENTITY_TYPE.get(of(id));
        if (allowed.isEmpty() || allowed.get().size() == 0) return true;
        return allowed.get().stream().anyMatch(holder -> holder.value() == context.self().getType());
    }
}
