package com.iafenvoy.mxt.runtime.aura;

import com.iafenvoy.mxt.data.aura.Aura;
import com.iafenvoy.mxt.data.resource.Resource;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import net.minecraft.core.Holder;
import net.minecraft.core.Holder.Reference;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.stream.Stream;

/**
 * Looks up aura definitions. The registry is small and read from both sides, so the lookup scans the
 * synchronised registry instead of keeping a second cache to invalidate; every entry point tolerates a missing
 * access and reports "no aura" rather than throwing.
 * <p>
 * Everything carrying an aura carries a {@code Holder<Aura>} and needs no lookup; what is left here is the
 * value-to-aura direction and the registry-as-a-stream reads.
 */
public final class AuraLookup {
    private AuraLookup() {
    }

    public static Stream<Reference<Aura>> all(@NotNull Provider access) {
        return MxtDatapackRegistries.holders(access, MxtResourceKeys.AURA);
    }

    public static Stream<Reference<Aura>> all(LivingEntity entity) {
        return all(entity.level().registryAccess());
    }

    // Keeps the holder: a realm chain is keyed by the aura holder, so comparisons need the reference.
    public static Optional<Reference<Aura>> holder(@NotNull Provider access, Holder<Resource> resource) {
        return all(access).filter(aura -> aura.value().resource().equals(resource)).findFirst();
    }

    public static Optional<Reference<Aura>> holder(@Nullable LivingEntity entity, Holder<Resource> resource) {
        return entity == null ? Optional.empty() : holder(entity.level().registryAccess(), resource);
    }

    public static Optional<Aura> find(@NotNull Provider access, Holder<Resource> resource) {
        return holder(access, resource).map(Reference::value);
    }
}
