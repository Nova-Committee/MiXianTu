package com.iafenvoy.mxt.runtime.world;

import com.iafenvoy.mxt.data.realm.RealmGeneration;
import com.iafenvoy.mxt.data.realm.RealmGeneration.Stem;
import com.iafenvoy.mxt.data.realm.RealmGeneration.Template;
import com.iafenvoy.mxt.data.realm.RealmInstance;
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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * The authoritative table of realm instances. Instances are keyed by their dimension, which is what an
 * instance actually is: a dimension created on demand from a definition.
 *
 * <p>The table is persisted on the overworld, so a claimed realm is still there after a restart. That is
 * also why the persisted copy is rewritten after every change instead of relying on a dirty flag: an instance
 * that is created and immediately entered must not be lost if the server stops later in the same tick.
 */
public final class RealmInstanceRegistry {
    private static final Map<ResourceKey<Level>, RealmRecord> INSTANCES = new LinkedHashMap<>();
    @Nullable
    private static MinecraftServer server;

    private RealmInstanceRegistry() {
    }

    /**
     * Loads the persisted instances. An unclaimed realm owns no terrain to come back to, so a record of one is
     * a leftover from a crash and is dropped together with its region files.
     */
    public static void load(MinecraftServer value) {
        server = value;
        INSTANCES.clear();
        RealmWorldAttachment attachment = value.overworld().getData(MxtAttachments.REALM_WORLD);
        for (RealmRecord record : attachment.records()) {
            Optional<ResourceKey<RealmInstance>> key = record.definition().unwrapKey();
            if (key.isEmpty() || MxtDatapackRegistries.holder(MxtResourceKeys.REALM_INSTANCE, key.get().identifier()).isEmpty()) {
                RealmGenerationService.clearData(value, record.dimension());
                continue;
            }
            if (!record.instance().owned()) continue;
            INSTANCES.put(record.dimension(), record.dormant());
        }
        sync();
        RealmGenerationService.clearOrphans(value, INSTANCES.keySet());
    }

    public static void clear() {
        INSTANCES.clear();
        server = null;
    }

    public static List<RealmRecord> all() {
        return List.copyOf(INSTANCES.values());
    }

    public static List<RealmRecord> of(Holder<RealmInstance> definition) {
        return INSTANCES.values().stream().filter(record -> record.definition().equals(definition)).toList();
    }

    public static Optional<RealmRecord> at(ResourceKey<Level> dimension) {
        return Optional.ofNullable(INSTANCES.get(dimension));
    }

    /**
     * The instance a player is currently a member of, found by membership rather than by level so a player who
     * is between dimensions is still recognised.
     */
    public static Optional<RealmRecord> ofMember(UUID member) {
        return INSTANCES.values().stream().filter(record -> record.holds(member)).findFirst();
    }

    /**
     * The instance to join, or empty when a new one has to be created. Claimed and unclaimed realms share one
     * pool: {@code owned} records who claimed an instance, it does not reserve it.
     */
    public static Optional<RealmRecord> joinable(Holder<RealmInstance> definition, UUID member) {
        return INSTANCES.values().stream()
                .filter(record -> record.definition().equals(definition))
                .filter(record -> record.holds(member) || !record.full())
                .min(Comparator.comparingInt(RealmRecord::index));
    }

    public static Optional<RealmRecord> replace(RealmRecord record) {
        if (!INSTANCES.containsKey(record.dimension())) return Optional.empty();
        INSTANCES.put(record.dimension(), record);
        sync();
        return Optional.of(record);
    }

    public static void put(RealmRecord record) {
        INSTANCES.put(record.dimension(), record);
        sync();
    }

    public static void remove(ResourceKey<Level> dimension) {
        if (INSTANCES.remove(dimension) != null) sync();
    }

    /**
     * The lowest unused index for a definition, so instance dimensions keep stable, readable keys.
     */
    public static int nextIndex(Holder<RealmInstance> definition) {
        List<Integer> used = INSTANCES.values().stream()
                .filter(record -> record.definition().equals(definition))
                .map(RealmRecord::index).toList();
        int index = 0;
        while (used.contains(index)) index++;
        return index;
    }

    public static OptionalLong seedFor(ResourceKey<Level> dimension) {
        RealmRecord record = INSTANCES.get(dimension);
        return record == null ? OptionalLong.empty() : OptionalLong.of(record.seed());
    }

    public static boolean isRealm(ResourceKey<Level> dimension) {
        return INSTANCES.containsKey(dimension);
    }

    /**
     * Names a realm dimension also answers to. A runtime dimension has no level stem entry of its own, so an
     * aura zone that would otherwise only match {@code minecraft:the_end} can still cover every realm built
     * from that stem, and one that names the definition covers all of its instances.
     */
    public static Stream<Identifier> aliases(Identifier dimension) {
        RealmRecord record = INSTANCES.get(ResourceKey.create(Registries.DIMENSION, dimension));
        if (record == null) return Stream.empty();
        List<Identifier> aliases = new ArrayList<>();
        record.definition().unwrapKey().ifPresent(key -> aliases.add(key.identifier()));
        RealmGeneration generation = record.instance().generation();
        if (generation instanceof Stem(
                ResourceKey<LevelStem> stem1
        )) aliases.add(stem1.identifier());
        if (generation instanceof Template template) aliases.add(template.stem().identifier());
        return aliases.stream();
    }

    private static void sync() {
        if (server == null) return;
        server.overworld().getData(MxtAttachments.REALM_WORLD).replaceAll(List.copyOf(INSTANCES.values()));
    }
}
