package com.iafenvoy.mxt.data.aura;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.iafenvoy.mxt.util.codec.RegistryCodecs;
import com.mojang.datafixers.util.Either;
import com.iafenvoy.mxt.registry.MxtRegistryKeys;
import net.minecraft.resources.Identifier;
import net.minecraft.core.Holder;
import net.minecraft.resources.RegistryFixedCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.dimension.LevelStem;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Immutable datapack template for one aura environment.
 */
public record AuraZone(double baseAura, double regenPerTick, Map<Identifier, Double> elementAura,
                       List<Identifier> environmentTags,
                       List<Either<ResourceKey<LevelStem>, TagKey<LevelStem>>> dimensions,
                       List<Either<Holder<Biome>, TagKey<Biome>>> biomes, Fluctuation fluctuation, Rules rules,
                       double elementFitBonus, double elementConflictPenalty, Noise noise, ClientRender clientRender) {
    public static final Codec<AuraZone> DIRECT_CODEC = RecordCodecBuilder.<AuraZone>create(instance -> instance.group(
            Codec.DOUBLE.optionalFieldOf("base_aura", 0.0D).forGetter(AuraZone::baseAura),
            Codec.DOUBLE.optionalFieldOf("regen_per_tick", 0.0D).forGetter(AuraZone::regenPerTick),
            Codec.unboundedMap(Identifier.CODEC, Codec.DOUBLE).optionalFieldOf("element_aura", Map.of()).forGetter(AuraZone::elementAura),
            Identifier.CODEC.listOf().optionalFieldOf("environment_tags", List.of()).forGetter(AuraZone::environmentTags),
            RegistryCodecs.keyOrTagList(Registries.LEVEL_STEM).optionalFieldOf("dimensions", List.of()).forGetter(AuraZone::dimensions),
            RegistryCodecs.holderOrTagList(Registries.BIOME).optionalFieldOf("biomes", List.of()).forGetter(AuraZone::biomes),
            Fluctuation.CODEC.optionalFieldOf("fluctuation", Fluctuation.NONE).forGetter(AuraZone::fluctuation),
            Rules.CODEC.optionalFieldOf("rules", Rules.DEFAULT).forGetter(AuraZone::rules),
            Codec.DOUBLE.optionalFieldOf("element_fit_bonus", 0.0D).forGetter(AuraZone::elementFitBonus),
            Codec.DOUBLE.optionalFieldOf("element_conflict_penalty", 0.0D).forGetter(AuraZone::elementConflictPenalty),
            Noise.CODEC.optionalFieldOf("noise", Noise.NONE).forGetter(AuraZone::noise),
            ClientRender.CODEC.optionalFieldOf("client_render", ClientRender.DEFAULT).forGetter(AuraZone::clientRender)
    ).apply(instance, AuraZone::new)).validate(AuraZone::validate);
    public static final Codec<Holder<AuraZone>> CODEC = RegistryFixedCodec.create(MxtRegistryKeys.AURA_ZONE);

    private static DataResult<AuraZone> validate(AuraZone value) {
        if (!finite(value.baseAura) || value.baseAura < 0.0D || !finite(value.regenPerTick)
                || !finite(value.elementFitBonus) || !finite(value.elementConflictPenalty))
            return DataResult.error(() -> "Aura zone numbers must be finite; base_aura must be non-negative");
        if (value.elementAura.values().stream().anyMatch(number -> !finite(number)))
            return DataResult.error(() -> "element_aura values must be finite");
        return DataResult.success(value);
    }

    private static boolean finite(double value) {
        return Double.isFinite(value);
    }

    public record Fluctuation(boolean enabled, CycleType cycleType, double amplitude, long offsetTick) {
        public static final Fluctuation NONE = new Fluctuation(false, CycleType.STATIC, 0.0D, 0L);
        public static final Codec<Fluctuation> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.BOOL.optionalFieldOf("enable", false).forGetter(Fluctuation::enabled),
                CycleType.CODEC.optionalFieldOf("cycle_type", CycleType.STATIC).forGetter(Fluctuation::cycleType),
                Codec.DOUBLE.optionalFieldOf("amplitude", 0.0D).forGetter(Fluctuation::amplitude),
                Codec.LONG.optionalFieldOf("offset_tick", 0L).forGetter(Fluctuation::offsetTick)
        ).apply(instance, Fluctuation::new));
    }

    public enum CycleType {
        DAY, MOON, STATIC;
        static final Codec<CycleType> CODEC = Codec.STRING.xmap(value -> valueOf(value.toUpperCase(Locale.ROOT)), value -> value.name().toLowerCase(Locale.ROOT));
    }

    public record Rules(boolean cultivateSuppress, double tribulationModify, double spiritPlantBonus,
                        boolean alchemyEnvBonus, boolean naturalSpawnHerb) {
        public static final Rules DEFAULT = new Rules(false, 0.0D, 0.0D, false, false);
        public static final Codec<Rules> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.BOOL.optionalFieldOf("cultivate_suppress", false).forGetter(Rules::cultivateSuppress),
                Codec.DOUBLE.optionalFieldOf("tribulation_modify", 0.0D).forGetter(Rules::tribulationModify),
                Codec.DOUBLE.optionalFieldOf("spirit_plant_bonus", 0.0D).forGetter(Rules::spiritPlantBonus),
                Codec.BOOL.optionalFieldOf("alchemy_env_bonus", false).forGetter(Rules::alchemyEnvBonus),
                Codec.BOOL.optionalFieldOf("natural_spawn_herb", false).forGetter(Rules::naturalSpawnHerb)
        ).apply(instance, Rules::new));
    }

    /**
     * Seeded 2D value noise; seed lives in datapacks so template distributions are reproducible.
     */
    public record Noise(boolean enabled, long seed, double scale, double amplitude) {
        public static final Noise NONE = new Noise(false, 0L, 64.0D, 0.0D);
        public static final Codec<Noise> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.BOOL.optionalFieldOf("enable", false).forGetter(Noise::enabled),
                Codec.LONG.optionalFieldOf("seed", 0L).forGetter(Noise::seed),
                Codec.DOUBLE.optionalFieldOf("scale", 64.0D).forGetter(Noise::scale),
                Codec.DOUBLE.optionalFieldOf("amplitude", 0.0D).forGetter(Noise::amplitude)
        ).apply(instance, Noise::new));
    }

    public record ClientRender(Identifier particle, int particleDensity, String fogColor, int renderDistance) {
        public static final ClientRender DEFAULT = new ClientRender(Identifier.withDefaultNamespace("glow"), 0, "#FFFFFF", 64);
        public static final Codec<ClientRender> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Identifier.CODEC.optionalFieldOf("particle", Identifier.withDefaultNamespace("glow")).forGetter(ClientRender::particle),
                Codec.intRange(0, 64).optionalFieldOf("particle_density", 0).forGetter(ClientRender::particleDensity),
                Codec.STRING.optionalFieldOf("fog_color", "#FFFFFF").forGetter(ClientRender::fogColor),
                Codec.intRange(8, 256).optionalFieldOf("render_distance", 64).forGetter(ClientRender::renderDistance)
        ).apply(instance, ClientRender::new));
    }
}
