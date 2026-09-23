package com.iafenvoy.mxt.runtime.world;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.data.secretrealm.SecretRealmGeneration;
import com.iafenvoy.mxt.data.secretrealm.SecretRealmGeneration.Existing;
import com.iafenvoy.mxt.data.secretrealm.SecretRealmGeneration.Flat;
import com.iafenvoy.mxt.data.secretrealm.SecretRealmGeneration.Stem;
import com.iafenvoy.mxt.data.secretrealm.SecretRealmGeneration.Template;
import com.iafenvoy.mxt.data.secretrealm.SecretRealm;
import net.minecraft.core.Holder;
import net.minecraft.core.Holder.Reference;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.minecraft.world.level.levelgen.flat.FlatLayerInfo;
import net.minecraft.world.level.levelgen.flat.FlatLevelGeneratorSettings;
import org.apache.commons.io.FileUtils;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Creates, opens and discards secret realm dimensions.
 * <p>
 * Dimensions are named {@code <definition namespace>:secret_realm/<definition path>/<index>}, index always included,
 * because a dimension's folder follows its identifier - keeping every instance's terrain in its own directory -
 * and an aura zone can name that identifier to cover the dimension.
 */
public final class SecretRealmGenerationService {
    public static final String DIMENSION_PREFIX = "secret_realm";
    private static final String TEMPLATE_ROOT = "mxt_secret_realm";
    private static final String[] DATA_FOLDERS = {"region", "entities", "poi"};
    private static final String TEMPLATE_NAME = "[A-Za-z0-9_-]+";

    private SecretRealmGenerationService() {
    }

    public static ResourceKey<Level> dimensionKey(Identifier definition, int index) {
        String path = DIMENSION_PREFIX + "/" + definition.getPath() + "/" + index;
        return ResourceKey.create(Registries.DIMENSION, Identifier.fromNamespaceAndPath(definition.getNamespace(), path));
    }

    // An existing secret realm has no dimension of its own, so the one it names is the instance's identity as well -
    // which is also what keeps it from being unloaded or deleted when it empties.
    public static ResourceKey<Level> dimensionKey(Identifier definition, SecretRealmGeneration generation, int index) {
        if (generation instanceof Existing(ResourceKey<Level> dimension)) return dimension;
        return dimensionKey(definition, index);
    }

    public static Optional<ServerLevel> open(MinecraftServer server, SecretRealmRecord record) {
        SecretRealm definition = record.instance();
        if (definition.generation() instanceof Existing(ResourceKey<Level> dimension)) {
            ServerLevel level = server.getLevel(dimension);
            if (level == null) return Optional.empty();
            SecretRealmBorderService.apply(level, definition, false);
            return Optional.of(level);
        }
        ServerLevel loaded = server.getLevel(record.dimension());
        if (loaded != null) {
            SecretRealmBorderService.apply(loaded, definition, true);
            return Optional.of(loaded);
        }
        if (definition.generation() instanceof Template template && !record.prepared()
                && !copyTemplate(server, record.dimension(), template.template())) return Optional.empty();
        LevelStem stem = buildStem(server, definition.generation());
        if (stem == null) return Optional.empty();
        SecretRealmSeedBridge.begin(record.dimension(), record.seed());
        try {
            Optional<ServerLevel> level = RuntimeDimensionService.load(server, record.dimension(), stem);
            level.ifPresent(value -> SecretRealmBorderService.apply(value, definition, true));
            return level;
        } finally {
            SecretRealmSeedBridge.end();
        }
    }

    // Every registry read happens here rather than in the codec: datapack registries load in parallel, and the
    // stem and dimension type layers are not available to a codec at all. Null when unresolved.
    @Nullable
    public static LevelStem buildStem(MinecraftServer server, SecretRealmGeneration generation) {
        RegistryAccess registries = server.registryAccess();
        try {
            return switch (generation) {
                case Existing ignored -> null;
                case Stem stem ->
                        registries.lookupOrThrow(Registries.LEVEL_STEM).getOrThrow(stem.stem()).value();
                case Template template ->
                        registries.lookupOrThrow(Registries.LEVEL_STEM).getOrThrow(template.stem()).value();
                case Flat flat -> {
                    FlatPreset preset = parsePreset(registries.lookupOrThrow(Registries.BLOCK), flat.preset());
                    if (preset == null) yield null;
                    yield new LevelStem(dimensionType(registries, flat.dimensionType()),
                            new FlatLevelSource(flatSettings(registries, preset.layers(), preset.biome(), flat.structures())));
                }
                case SecretRealmGeneration.Void empty -> new LevelStem(dimensionType(registries, empty.dimensionType()),
                        new FlatLevelSource(flatSettings(registries, List.of(), empty.biome().or(() -> Optional.of(Biomes.THE_VOID)), empty.structures())));
            };
        } catch (Exception exception) {
            MiXianTu.LOGGER.error("Invalid secret realm generation parameters {}", generation, exception);
            return null;
        }
    }

    // A dimension a data pack declared through the level stem registry is never touched: the secret_realm/ folder
    // convention must not be able to delete a real dimension that happens to sit under it.
    public static void clearData(MinecraftServer server, ResourceKey<Level> dimension) {
        if (declared(server, dimension)) return;
        discard(server, dimension.identifier());
    }

    // The whole folder goes rather than only the chunk directories, because a dimension also stores its level data
    // and attachments under data/: reusing the key must not resurrect the previous occupant's aura areas.
    private static void discard(MinecraftServer server, Identifier dimension) {
        Path folder = server.getWorldPath(SecretRealmSeedBridge.folder(dimension));
        if (!Files.isDirectory(folder)) return;
        try {
            FileUtils.deleteDirectory(folder.toFile());
        } catch (IOException exception) {
            MiXianTu.LOGGER.warn("Failed to remove secret realm folder {}, clearing its data instead", folder, exception);
            for (String name : DATA_FOLDERS) clearDirectory(folder.resolve(name));
        }
    }

    private static boolean declared(MinecraftServer server, ResourceKey<Level> dimension) {
        return server.registryAccess().lookupOrThrow(Registries.LEVEL_STEM)
                .get(ResourceKey.create(Registries.LEVEL_STEM, dimension.identifier())).isPresent();
    }

    // What a secret realm destroyed by a crash, or by a definition that has since been deleted, leaves behind.
    public static void clearOrphans(MinecraftServer server, Set<ResourceKey<Level>> claimed) {
        Path root = server.getWorldPath(SecretRealmSeedBridge.root());
        if (!Files.isDirectory(root)) return;
        List<Path> candidates = new ArrayList<>();
        try (Stream<Path> namespaces = Files.list(root)) {
            for (Path namespace : namespaces.toList()) {
                Path secretRealmRoot = namespace.resolve(DIMENSION_PREFIX);
                if (!Files.isDirectory(secretRealmRoot)) continue;
                try (Stream<Path> walked = Files.walk(secretRealmRoot)) {
                    walked.filter(Files::isDirectory).filter(SecretRealmGenerationService::holdsData).forEach(candidates::add);
                }
            }
        } catch (IOException exception) {
            MiXianTu.LOGGER.warn("Failed to scan secret realm folders", exception);
            return;
        }
        for (Path candidate : candidates) {
            Identifier id = identifierOf(root.relativize(candidate));
            if (id == null) continue;
            ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, id);
            if (claimed.contains(dimension) || declared(server, dimension)) continue;
            discard(server, id);
            MiXianTu.LOGGER.info("Removed leftover secret realm data {}", id);
        }
    }

    private static boolean copyTemplate(MinecraftServer server, ResourceKey<Level> target, String template) {
        if (!template.matches(TEMPLATE_NAME)) {
            MiXianTu.LOGGER.error("Invalid secret realm template name {}", template);
            return false;
        }
        Path source = server.getServerDirectory().resolve(TEMPLATE_ROOT).resolve(template)
                .resolve(dimensionDataFolder(target.identifier()));
        if (!Files.isDirectory(source)) {
            MiXianTu.LOGGER.error("Missing secret realm template {}", source);
            return false;
        }
        clearData(server, target);
        Path destination = server.getWorldPath(SecretRealmSeedBridge.folder(target.identifier()));
        try {
            for (String name : DATA_FOLDERS) {
                Path from = source.resolve(name);
                if (Files.isDirectory(from)) FileUtils.copyDirectory(from.toFile(), destination.resolve(name).toFile());
            }
            return true;
        } catch (IOException exception) {
            MiXianTu.LOGGER.error("Failed to copy secret realm template {}", source, exception);
            return false;
        }
    }

    // Vanilla's folder names: the overworld is the root, the nether and the end have historical names, and
    // anything else sits under dimensions/.
    private static String dimensionDataFolder(Identifier dimension) {
        if (dimension.equals(Level.OVERWORLD.identifier())) return "";
        if (dimension.equals(Level.NETHER.identifier())) return "DIM-1";
        if (dimension.equals(Level.END.identifier())) return "DIM1";
        return "dimensions/%s/%s".formatted(dimension.getNamespace(), dimension.getPath());
    }

    private static Holder<DimensionType> dimensionType(RegistryAccess registries, Optional<ResourceKey<DimensionType>> key) {
        Registry<DimensionType> registry = registries.lookupOrThrow(Registries.DIMENSION_TYPE);
        return key.map(registry::getOrThrow).orElseGet(() -> registry.getOrThrow(BuiltinDimensionTypes.OVERWORLD));
    }

    private static FlatLevelGeneratorSettings flatSettings(RegistryAccess registries, List<FlatLayerInfo> layers, Optional<ResourceKey<Biome>> biomeKey, boolean structures) {
        HolderGetter<Biome> biomes = registries.lookupOrThrow(Registries.BIOME);
        FlatLevelGeneratorSettings defaults = FlatLevelGeneratorSettings.getDefault(biomes,
                registries.lookupOrThrow(Registries.STRUCTURE_SET), registries.lookupOrThrow(Registries.PLACED_FEATURE));
        Holder<Biome> biome = biomeKey.map(biomes::getOrThrow).orElseGet(() -> biomes.getOrThrow(Biomes.PLAINS));
        return defaults.withBiomeAndLayers(layers,
                structures ? defaults.structureOverrides() : Optional.of(HolderSet.empty()), biome);
    }

    /**
     * Parses the vanilla superflat syntax: layers separated by commas, then an optional biome after a semicolon.
     */
    @Nullable
    private static FlatPreset parsePreset(HolderGetter<Block> blocks, String preset) {
        String[] sections = preset.split(";", -1);
        List<FlatLayerInfo> layers = new ArrayList<>();
        int start = 0;
        for (String layer : sections[0].split(",", -1)) {
            FlatLayerInfo info = parseLayer(blocks, layer, start);
            if (info == null) return null;
            layers.add(info);
            start += info.getHeight();
        }
        Optional<ResourceKey<Biome>> biome = Optional.empty();
        for (int index = 1; index < sections.length; index++) {
            Identifier id = Identifier.tryParse(sections[index]);
            if (id != null) biome = Optional.of(ResourceKey.create(Registries.BIOME, id));
        }
        return new FlatPreset(layers, biome);
    }

    @Nullable
    private static FlatLayerInfo parseLayer(HolderGetter<Block> blocks, String value, int start) {
        String[] parts = value.split("\\*", 2);
        int height = 1;
        String blockName = parts[0];
        if (parts.length == 2) {
            try {
                height = Math.max(0, Integer.parseInt(parts[0]));
            } catch (NumberFormatException ignored) {
                return null;
            }
            blockName = parts[1];
        }
        Identifier id = Identifier.tryParse(blockName.toLowerCase(Locale.ROOT));
        if (id == null) return null;
        int layerHeight = height;
        return blocks.get(ResourceKey.create(Registries.BLOCK, id))
                .map(Reference::value)
                .map(block -> new FlatLayerInfo(layerHeight, block))
                .orElse(null);
    }

    // Instance folders live under secret_realm/, so any folder there holding level data or chunk data is one. It is
    // checked rather than assumed from the depth, because a definition path with slashes nests deeper.
    private static boolean holdsData(Path path) {
        if (Files.isRegularFile(path.resolve("level.dat"))) return true;
        for (String name : DATA_FOLDERS) if (Files.isDirectory(path.resolve(name))) return true;
        return false;
    }

    @Nullable
    private static Identifier identifierOf(Path relative) {
        String value = relative.toString().replace('\\', '/');
        int separator = value.indexOf('/');
        return separator < 0 ? null : Identifier.tryParse(value.substring(0, separator) + ":" + value.substring(separator + 1));
    }

    private static void clearDirectory(Path path) {
        if (!Files.isDirectory(path)) return;
        try {
            FileUtils.cleanDirectory(path.toFile());
        } catch (IOException exception) {
            MiXianTu.LOGGER.warn("Failed to clear secret realm folder {}", path, exception);
        }
    }

    private record FlatPreset(List<FlatLayerInfo> layers, Optional<ResourceKey<Biome>> biome) {
    }
}
