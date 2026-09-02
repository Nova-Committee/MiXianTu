package com.iafenvoy.mxt.runtime;

import com.iafenvoy.mxt.data.cultivation.RealmStage;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.util.HolderHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.TagsUpdatedEvent.ServerDataLoad;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

import java.util.*;

/**
 * Global server-lifetime cache for derived datapack data. It is absent on the
 * client and outside an active server lifecycle.
 */
@EventBusSubscriber
public final class ServerCache {
    private static ServerCache INSTANCE;

    private final MinecraftServer server;
    private Map<Identifier, Identifier> resourceByRealm = new LinkedHashMap<>();
    private Map<Identifier, Integer> rankByRealm = new LinkedHashMap<>();

    private ServerCache(MinecraftServer server) {
        this.server = server;
    }

    /**
     * Returns the active server cache, or empty when no server is running.
     */
    public static Optional<ServerCache> get() {
        return Optional.ofNullable(INSTANCE);
    }

    public MinecraftServer server() {
        return this.server;
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        INSTANCE = new ServerCache(event.getServer());
        INSTANCE.rebuild();
    }

    @SubscribeEvent
    public static void onDatapackLoaded(ServerDataLoad event) {
        // Rebuild only after a server datapack load or /reload, not for every player sync.
        get().ifPresent(ServerCache::rebuild);
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        if (INSTANCE != null && INSTANCE.server == event.getServer()) INSTANCE = null;
    }

    /**
     * Rebuilds validated linear cultivation chains after datapack data is available.
     * Invalid chains are rejected so no partial cache can become authoritative.
     */
    private void rebuild() {
        Map<Identifier, Identifier> resolved = new LinkedHashMap<>();
        Map<Identifier, Integer> ranks = new LinkedHashMap<>();
        MxtDatapackRegistries.holders(this.server.registryAccess(), MxtResourceKeys.RESOURCE).forEach(resourceHolder -> {
            Identifier resource = resourceHolder.key().identifier();
            resourceHolder.value().firstRealm().ifPresent(first -> this.indexChain(resource, HolderHelper.id(first), resolved, ranks));
        });
        this.resourceByRealm = resolved;
        this.rankByRealm = ranks;
    }

    /**
     * Gets the resource owning a validated realm.
     */
    public Optional<Identifier> resourceForRealm(Identifier realm) {
        return Optional.ofNullable(this.resourceByRealm.get(realm));
    }

    public boolean containsRealm(Identifier realm) {
        return this.resourceByRealm.containsKey(realm);
    }

    /**
     * Returns the zero-based rank of a realm in its validated resource chain.
     */
    public Optional<Integer> rankForRealm(Identifier realm) {
        return Optional.ofNullable(this.rankByRealm.get(realm));
    }

    /**
     * Returns whether two realms share a resource chain and current is no lower than required.
     */
    public boolean isRealmAtLeast(Identifier current, Identifier required) {
        Identifier currentResource = this.resourceByRealm.get(current);
        return currentResource != null && currentResource.equals(this.resourceByRealm.get(required))
                && this.rankByRealm.getOrDefault(current, -1) >= this.rankByRealm.getOrDefault(required, Integer.MAX_VALUE);
    }

    private void indexChain(Identifier resource, Identifier first, Map<Identifier, Identifier> resolved, Map<Identifier, Integer> ranks) {
        Set<Identifier> visited = new HashSet<>();
        Map<Identifier, Identifier> chain = new LinkedHashMap<>();
        Map<Identifier, Integer> chainRanks = new LinkedHashMap<>();
        Identifier current = first;
        int rank = 0;
        while (current != null) {
            if (!visited.add(current)) {
                throw new IllegalStateException("Cyclic cultivation realm chain for resource " + resource + " at realm " + current);
            }
            RealmStage stage = MxtDatapackRegistries.get(MxtResourceKeys.REALM_STAGE, current).orElse(null);
            if (stage == null || !HolderHelper.id(stage.resource()).equals(resource)) {
                throw new IllegalStateException("Invalid cultivation realm chain for resource " + resource + " at realm " + current);
            }
            Identifier previous = resolved.get(current);
            if (previous != null && !previous.equals(resource)) {
                throw new IllegalStateException("Realm " + current + " belongs to both resources " + previous + " and " + resource);
            }
            chain.put(current, resource);
            chainRanks.put(current, rank++);
            current = stage.nextRealm().map(HolderHelper::id).orElse(null);
        }
        resolved.putAll(chain);
        ranks.putAll(chainRanks);
    }
}
