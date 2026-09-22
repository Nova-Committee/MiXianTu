package com.iafenvoy.mxt.data.realm;

import com.iafenvoy.mxt.registry.MxtRegistries;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;

import java.util.Optional;
import java.util.function.Function;

/**
 * How a secret realm instance dimension is produced; the one required section of a realm definition. Every
 * registry reference is resolved when an instance is created rather than at decode: datapack registries load in
 * parallel, so a holder read at decode time may still be unbound.
 */
public sealed interface RealmGeneration {
    Codec<RealmGeneration> CODEC = MxtRegistries.REALM_GENERATION_TYPE.byNameCodec()
            .dispatch("type", RealmGeneration::codec, Function.identity());

    MapCodec<? extends RealmGeneration> codec();

    record Stem(ResourceKey<LevelStem> stem) implements RealmGeneration {
        public static final MapCodec<Stem> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                ResourceKey.codec(Registries.LEVEL_STEM).fieldOf("stem").forGetter(Stem::stem)
        ).apply(i, Stem::new));

        @Override
        public MapCodec<? extends RealmGeneration> codec() {
            return CODEC;
        }
    }

    // The preset string follows the vanilla layer syntax, for example
    // "1*minecraft:bedrock,2*minecraft:dirt,minecraft:grass_block;minecraft:plains".
    record Flat(String preset, Optional<ResourceKey<DimensionType>> dimensionType,
                boolean structures) implements RealmGeneration {
        public static final MapCodec<Flat> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.STRING.fieldOf("preset").forGetter(Flat::preset),
                ResourceKey.codec(Registries.DIMENSION_TYPE).optionalFieldOf("dimension_type").forGetter(Flat::dimensionType),
                Codec.BOOL.optionalFieldOf("structures", true).forGetter(Flat::structures)
        ).apply(i, Flat::new));

        @Override
        public MapCodec<? extends RealmGeneration> codec() {
            return CODEC;
        }
    }

    // No layers, one biome, and by default no structures - the usual floor for a realm furnished entirely from
    // structure templates.
    record Void(Optional<ResourceKey<Biome>> biome, Optional<ResourceKey<DimensionType>> dimensionType,
                boolean structures) implements RealmGeneration {
        public static final MapCodec<Void> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                ResourceKey.codec(Registries.BIOME).optionalFieldOf("biome").forGetter(Void::biome),
                ResourceKey.codec(Registries.DIMENSION_TYPE).optionalFieldOf("dimension_type").forGetter(Void::dimensionType),
                Codec.BOOL.optionalFieldOf("structures", false).forGetter(Void::structures)
        ).apply(i, Void::new));

        @Override
        public MapCodec<? extends RealmGeneration> codec() {
            return CODEC;
        }
    }

    // Copies a pre-built level from <server directory>/mxt_realm/<template>/ into the instance before it loads.
    record Template(String template, ResourceKey<LevelStem> stem) implements RealmGeneration {
        public static final MapCodec<Template> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.STRING.fieldOf("template").forGetter(Template::template),
                ResourceKey.codec(Registries.LEVEL_STEM).fieldOf("stem").forGetter(Template::stem)
        ).apply(i, Template::new));

        @Override
        public MapCodec<? extends RealmGeneration> codec() {
            return CODEC;
        }
    }

    // Uses an already loaded dimension and creates nothing: max_instances is meaningless here, and the service
    // never destroys that dimension.
    record Existing(ResourceKey<Level> dimension) implements RealmGeneration {
        public static final MapCodec<Existing> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                ResourceKey.codec(Registries.DIMENSION).fieldOf("dimension").forGetter(Existing::dimension)
        ).apply(i, Existing::new));

        @Override
        public MapCodec<? extends RealmGeneration> codec() {
            return CODEC;
        }
    }
}
