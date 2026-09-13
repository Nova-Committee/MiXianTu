package com.iafenvoy.mxt.runtime.cultivation;

import com.iafenvoy.mxt.data.cultivation.CultivationProfile;
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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * Looks up the cultivation profile of a stored value.
 *
 * <p>The registry is small and is read from either side of the connection, so the lookup scans the
 * synchronised registry instead of keeping a second cache that would have to be invalidated next to
 * it. Every entry point tolerates a missing access and reports "no profile" instead of throwing, so
 * a value without cultivation behaves like a plain counter everywhere.</p>
 */
public final class CultivationProfiles {
    private CultivationProfiles() {
    }

    /**
     * The profile describing a value, keeping its holder: a realm chain is keyed by the profile
     * holder, so chain comparisons need the reference rather than the decoded value.
     */
    public static Optional<Reference<CultivationProfile>> holder(@Nullable Provider access, Identifier resource) {
        if (access == null) return Optional.empty();
        return MxtDatapackRegistries.holders(access, MxtResourceKeys.CULTIVATION)
                .filter(profile -> HolderHelper.id(profile.value().resource()).equals(resource)).findFirst();
    }

    public static Optional<Reference<CultivationProfile>> holder(@Nullable Provider access, Holder<Resource> resource) {
        return holder(access, HolderHelper.id(resource));
    }

    public static Optional<Reference<CultivationProfile>> holder(LivingEntity entity, Holder<Resource> resource) {
        return holder(entity.level().registryAccess(), resource);
    }

    public static Optional<CultivationProfile> find(@Nullable Provider access, Identifier resource) {
        return holder(access, resource).map(Reference::value);
    }

    public static Optional<CultivationProfile> find(@Nullable Provider access, Holder<Resource> resource) {
        return find(access, HolderHelper.id(resource));
    }

    public static Optional<CultivationProfile> find(LivingEntity entity, Holder<Resource> resource) {
        return find(entity.level().registryAccess(), resource);
    }

    public static Optional<CultivationProfile> find(@Nullable Level level, Holder<Resource> resource) {
        return level == null ? Optional.empty() : find(level.registryAccess(), resource);
    }

    /**
     * Server-only read for callers that hold no entity, such as commands and administrative paths.
     * It reads the server registry directly, so it stays valid while the server cache is still being
     * built during startup.
     */
    public static Optional<Reference<CultivationProfile>> holderServer(Identifier resource) {
        return MxtDatapackRegistries.holders(MxtResourceKeys.CULTIVATION)
                .filter(profile -> HolderHelper.id(profile.value().resource()).equals(resource)).findFirst();
    }

    public static Optional<Reference<CultivationProfile>> holderServer(Holder<Resource> resource) {
        return holderServer(HolderHelper.id(resource));
    }

    public static Optional<CultivationProfile> findServer(Holder<Resource> resource) {
        return holderServer(resource).map(Reference::value);
    }

    public static Optional<CultivationProfile> findServer(Identifier resource) {
        return holderServer(resource).map(Reference::value);
    }

    /**
     * Every profile keyed by the value it describes, for reads that ask about each value of an
     * entity in one pass. A duplicate resource keeps the first entry; the server cache rejects
     * duplicates before any runtime read.
     */
    public static Map<Identifier, CultivationProfile> byResource(@Nullable Provider access) {
        return collect(access, profile -> HolderHelper.id(profile.value().resource()), Reference::value);
    }

    /**
     * Every profile keyed by the value it describes, keeping the holders a realm chain is keyed by.
     */
    public static Map<Identifier, Reference<CultivationProfile>> holdersByResource(@Nullable Provider access) {
        return collect(access, profile -> HolderHelper.id(profile.value().resource()), Function.identity());
    }

    private static <T> Map<Identifier, T> collect(@Nullable Provider access, Function<Reference<CultivationProfile>, Identifier> key,
                                                  Function<Reference<CultivationProfile>, T> value) {
        Map<Identifier, T> profiles = new LinkedHashMap<>();
        if (access == null) return profiles;
        MxtDatapackRegistries.holders(access, MxtResourceKeys.CULTIVATION)
                .forEach(profile -> profiles.putIfAbsent(key.apply(profile), value.apply(profile)));
        return profiles;
    }

    /**
     * The registry access of a running server, or {@code null} when no server is up.
     */
    public static @Nullable Provider serverAccess() {
        return ServerCache.get().map(cache -> (Provider) cache.server().registryAccess()).orElse(null);
    }

    /**
     * The registry access a formula can read the synced registries through: the acting entity first,
     * then its target, and finally the server when neither is present.
     */
    public static @Nullable Provider access(FormulaContext context) {
        Entity caster = context.caster();
        if (caster != null) return caster.level().registryAccess();
        Entity target = context.target();
        if (target != null) return target.level().registryAccess();
        return serverAccess();
    }
}
