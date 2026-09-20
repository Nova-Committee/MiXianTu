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
 * How a secret realm instance dimension is produced. The whole point of a realm is that its dimension is
 * created on demand, so this is the one required section of a realm definition.
 *
 * <p>Every registry reference is resolved when an instance is created rather than while the datapack
 * registry is decoded: datapack registries load in parallel, so a holder read at decode time may still be
 * unbound, and the level stem and dimension type layers are not even available to a codec.
 */
public sealed interface RealmGeneration {
    Codec<RealmGeneration> CODEC = MxtRegistries.REALM_GENERATION_TYPE.byNameCodec()
            .dispatch("type", RealmGeneration::codec, Function.identity());

    MapCodec<? extends RealmGeneration> codec();

    /**
     * Reuses one registered {@code LevelStem} (generator plus dimension type) under a new dimension key.
     */
    record Stem(ResourceKey<LevelStem> stem) implements RealmGeneration {
        public static final MapCodec<Stem> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                ResourceKey.codec(Registries.LEVEL_STEM).fieldOf("stem").forGetter(Stem::stem)
        ).apply(i, Stem::new));

        @Override
        public MapCodec<? extends RealmGeneration> codec() {
            return CODEC;
        }
    }

    /**
     * A superflat world. The preset string follows the vanilla layer syntax, for example
     * {@code "1*minecraft:bedrock,2*minecraft:dirt,minecraft:grass_block;minecraft:plains"}.
     */
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

    /**
     * An empty world: no layers, one biome, and by default no structures. This is the usual floor for a
     * realm that is furnished entirely from structure templates.
     */
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

    /**
     * Copies a pre-built level from {@code <server directory>/mxt_realm/<template>/} into the instance
     * before it loads, so a hand-built map becomes a realm.
     */
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

    /**
     * Uses an already loaded dimension as the realm and creates nothing. {@code max_instances} is
     * meaningless here, and the dimension is never destroyed by the realm service.
     */
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
