package com.iafenvoy.mxt.runtime.world;

import com.iafenvoy.mxt.data.secretrealm.SecretRealm;
import com.iafenvoy.mxt.data.secretrealm.SecretRealmGeneration;
import com.iafenvoy.mxt.data.secretrealm.SecretRealmGeneration.Stem;
import com.iafenvoy.mxt.data.secretrealm.SecretRealmGeneration.Template;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.LevelStem;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Stream;

/**
 * The authoritative table of secret realms, keyed by their dimension.
 * <p>
 * The table is persisted on the overworld and rewritten after every change rather than behind a dirty flag:
 * an instance created and immediately entered must not be lost if the server stops later in the same tick.
 */
public final class SecretRealmRegistry {
    private static final Map<ResourceKey<Level>, SecretRealmRecord> INSTANCES = new LinkedHashMap<>();
    @Nullable
    private static MinecraftServer server;

    private SecretRealmRegistry() {
    }

    // An unclaimed secret realm owns no terrain to come back to, so a record of one is a crash leftover and is
    // dropped together with its region files.
    public static void load(MinecraftServer value) {
        server = value;
        INSTANCES.clear();
        SecretRealmWorldAttachment attachment = value.overworld().getData(MxtAttachments.SECRET_REALM_WORLD);
        for (SecretRealmRecord record : attachment.records()) {
            Optional<ResourceKey<SecretRealm>> key = record.definition().unwrapKey();
            if (key.isEmpty() || MxtDatapackRegistries.holder(MxtResourceKeys.SECRET_REALM, key.get().identifier()).isEmpty()) {
                SecretRealmGenerationService.clearData(value, record.dimension());
                continue;
            }
            if (!record.instance().owned()) continue;
            INSTANCES.put(record.dimension(), record.dormant());
        }
        sync();
        SecretRealmGenerationService.clearOrphans(value, INSTANCES.keySet());
    }

    public static void clear() {
        INSTANCES.clear();
        server = null;
    }

    public static List<SecretRealmRecord> all() {
        return List.copyOf(INSTANCES.values());
    }

    public static List<SecretRealmRecord> of(Holder<SecretRealm> definition) {
        return INSTANCES.values().stream().filter(record -> record.definition().equals(definition)).toList();
    }

    public static Optional<SecretRealmRecord> at(ResourceKey<Level> dimension) {
        return Optional.ofNullable(INSTANCES.get(dimension));
    }

    // Found by membership rather than by level, so a player who is between dimensions is still recognised.
    public static Optional<SecretRealmRecord> ofMember(UUID member) {
        return INSTANCES.values().stream().filter(record -> record.holds(member)).findFirst();
    }

    // Claimed and unclaimed realms share one pool: owned records who claimed an instance, it does not reserve it.
    public static Optional<SecretRealmRecord> joinable(Holder<SecretRealm> definition, UUID member) {
        return INSTANCES.values().stream()
                .filter(record -> record.definition().equals(definition))
                .filter(record -> record.holds(member) || !record.full())
                .min(Comparator.comparingInt(SecretRealmRecord::index));
    }

    public static Optional<SecretRealmRecord> replace(SecretRealmRecord record) {
        if (!INSTANCES.containsKey(record.dimension())) return Optional.empty();
        INSTANCES.put(record.dimension(), record);
        sync();
        return Optional.of(record);
    }

    public static void put(SecretRealmRecord record) {
        INSTANCES.put(record.dimension(), record);
        sync();
    }

    public static void remove(ResourceKey<Level> dimension) {
        if (INSTANCES.remove(dimension) != null) sync();
    }

    // The lowest unused index, so instance dimensions keep stable, readable keys.
    public static int nextIndex(Holder<SecretRealm> definition) {
        List<Integer> used = INSTANCES.values().stream()
                .filter(record -> record.definition().equals(definition))
                .map(SecretRealmRecord::index).toList();
        int index = 0;
        while (used.contains(index)) index++;
        return index;
    }

    public static OptionalLong seedFor(ResourceKey<Level> dimension) {
        SecretRealmRecord record = INSTANCES.get(dimension);
        return record == null ? OptionalLong.empty() : OptionalLong.of(record.seed());
    }

    public static boolean isRealm(ResourceKey<Level> dimension) {
        return INSTANCES.containsKey(dimension);
    }

    // A runtime dimension has no level stem entry of its own, so an aura zone that would otherwise only match
    // minecraft:the_end can still cover every secret realm built from that stem, or from the definition itself.
    public static Stream<Identifier> aliases(Identifier dimension) {
        SecretRealmRecord record = INSTANCES.get(ResourceKey.create(Registries.DIMENSION, dimension));
        if (record == null) return Stream.empty();
        List<Identifier> aliases = new ArrayList<>();
        record.definition().unwrapKey().ifPresent(key -> aliases.add(key.identifier()));
        SecretRealmGeneration generation = record.instance().generation();
        if (generation instanceof Stem(
                ResourceKey<LevelStem> stem1
        )) aliases.add(stem1.identifier());
        if (generation instanceof Template template) aliases.add(template.stem().identifier());
        return aliases.stream();
    }

    private static void sync() {
        if (server == null) return;
        server.overworld().getData(MxtAttachments.SECRET_REALM_WORLD).replaceAll(List.copyOf(INSTANCES.values()));
    }
}
