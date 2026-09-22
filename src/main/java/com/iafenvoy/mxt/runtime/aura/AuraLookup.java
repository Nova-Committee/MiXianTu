package com.iafenvoy.mxt.runtime.aura;

import com.iafenvoy.mxt.data.aura.Aura;
import com.iafenvoy.mxt.data.resource.Resource;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.runtime.ServerCache;
import com.iafenvoy.mxt.util.HolderHelper;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import net.minecraft.core.Holder;
import net.minecraft.core.Holder.Reference;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
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

    public static Stream<Reference<Aura>> all(@Nullable Provider access) {
        return access == null ? Stream.empty() : MxtDatapackRegistries.holders(access, MxtResourceKeys.AURA);
    }

    public static Stream<Reference<Aura>> all(@Nullable Level level) {
        return level == null ? Stream.empty() : all(level.registryAccess());
    }

    public static Stream<Reference<Aura>> all(LivingEntity entity) {
        return all(entity.level());
    }

    // The value-to-aura direction, not an identity: an aura names the one value it is measured in
    // (Aura#resource), and a value naming no aura is a plain counter rather than a second kind of aura.
    public static Optional<Reference<Aura>> holder(@Nullable Provider access, Identifier resource) {
        if (access == null) return Optional.empty();
        return all(access).filter(aura -> HolderHelper.id(aura.value().resource()).equals(resource)).findFirst();
    }

    // Keeps the holder: a realm chain is keyed by the aura holder, so comparisons need the reference.
    public static Optional<Reference<Aura>> holder(@Nullable Provider access, Holder<Resource> resource) {
        return holder(access, HolderHelper.id(resource));
    }

    public static Optional<Reference<Aura>> holder(@Nullable LivingEntity entity, Holder<Resource> resource) {
        return entity == null ? Optional.empty() : holder(entity.level().registryAccess(), resource);
    }

    public static Optional<Aura> find(@Nullable Provider access, Identifier resource) {
        return holder(access, resource).map(Reference::value);
    }

    public static Optional<Aura> find(@Nullable Provider access, Holder<Resource> resource) {
        return find(access, HolderHelper.id(resource));
    }

    public static Optional<Aura> find(LivingEntity entity, Holder<Resource> resource) {
        return find(entity.level().registryAccess(), resource);
    }

    public static Optional<Aura> find(@Nullable Level level, Holder<Resource> resource) {
        return level == null ? Optional.empty() : find(level.registryAccess(), resource);
    }

    // Server-only, for callers holding no entity: it reads the server registry directly, so it stays valid
    // while the server cache is still being built.
    public static Optional<Reference<Aura>> holderServer(Identifier resource) {
        return MxtDatapackRegistries.holders(MxtResourceKeys.AURA)
                .filter(aura -> HolderHelper.id(aura.value().resource()).equals(resource)).findFirst();
    }

    public static Optional<Reference<Aura>> holderServer(Holder<Resource> resource) {
        return holderServer(HolderHelper.id(resource));
    }

    public static Optional<Aura> findServer(Holder<Resource> resource) {
        return holderServer(resource).map(Reference::value);
    }

    public static Optional<Aura> findServer(Identifier resource) {
        return holderServer(resource).map(Reference::value);
    }

    // Null when no server is up.
    public static @Nullable Provider serverAccess() {
        return ServerCache.get().map(cache -> (Provider) cache.server().registryAccess()).orElse(null);
    }

    public static @Nullable Provider access(FormulaContext context) {
        Entity caster = context.caster();
        if (caster != null) return caster.level().registryAccess();
        Entity target = context.target();
        if (target != null) return target.level().registryAccess();
        return serverAccess();
    }
}
