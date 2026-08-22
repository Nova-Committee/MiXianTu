package com.iafenvoy.mxt.data.aura;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.iafenvoy.mxt.util.codec.RegistryCodecs;
import com.iafenvoy.mxt.util.codec.MiscCodecs;
import com.iafenvoy.mxt.data.ParticleEffect;
import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.data.condition.builtin.entity.meta.AlwaysTrueEntityCondition;
import com.iafenvoy.mxt.data.cultivation.Element;
import com.iafenvoy.mxt.data.resource.ResourceBar.Anchor;
import com.mojang.datafixers.util.Either;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
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
import java.util.Optional;


/**
 * Immutable datapack template for one aura environment.
 */
public record AuraZone(Map<Holder<Element>, AuraValue> aura,
                       List<Identifier> auraKinds,
                       List<Either<ResourceKey<LevelStem>, TagKey<LevelStem>>> dimensions,
                       List<Either<Holder<Biome>, TagKey<Biome>>> biomes, Fluctuation fluctuation, Rules rules,
                       EntityCondition cultivateCondition, Distribution distribution,
                       double elementFitBonus, double elementConflictPenalty, Noise noise,
                       Optional<ParticleEffect> particle, ClientRender clientRender,
                       ClientHud clientHud) {
    public static final Codec<Holder<AuraZone>> CODEC = RegistryFixedCodec.create(MxtResourceKeys.AURA_ZONE);
    private static final MapCodec<Core> CORE_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            AuraValue.MAP_CODEC.optionalFieldOf("aura", Map.of()).forGetter(Core::aura),
            Identifier.CODEC.listOf().optionalFieldOf("aura_kinds", List.of()).forGetter(Core::auraKinds),
            RegistryCodecs.keyOrTagList(Registries.LEVEL_STEM).optionalFieldOf("dimensions", List.of()).forGetter(Core::dimensions),
            RegistryCodecs.holderOrTagList(Registries.BIOME).optionalFieldOf("biomes", List.of()).forGetter(Core::biomes),
            Fluctuation.CODEC.optionalFieldOf("fluctuation", Fluctuation.NONE).forGetter(Core::fluctuation),
            Rules.CODEC.optionalFieldOf("rules", Rules.DEFAULT).forGetter(Core::rules),
            EntityCondition.CODEC.optionalFieldOf("cultivate_condition", AlwaysTrueEntityCondition.INSTANCE).forGetter(Core::cultivateCondition),
            Distribution.CODEC.optionalFieldOf("distribution", Distribution.EQUAL).forGetter(Core::distribution)
    ).apply(i, Core::from));
    private static final MapCodec<Visual> VISUAL_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.DOUBLE.optionalFieldOf("element_fit_bonus", 0.0D).forGetter(Visual::elementFitBonus),
            Codec.DOUBLE.optionalFieldOf("element_conflict_penalty", 0.0D).forGetter(Visual::elementConflictPenalty),
            Noise.CODEC.optionalFieldOf("noise", Noise.NONE).forGetter(Visual::noise),
            ParticleEffect.CODEC.optionalFieldOf("particle").forGetter(Visual::particle),
            ClientRender.CODEC.optionalFieldOf("client_render", ClientRender.DEFAULT).forGetter(Visual::clientRender),
            ClientHud.CODEC.optionalFieldOf("client_hud", ClientHud.NONE).forGetter(Visual::clientHud)
    ).apply(i, Visual::from));
    public static final Codec<AuraZone> DIRECT_CODEC = RecordCodecBuilder.<AuraZone>create(i -> i.group(
            CORE_CODEC.forGetter(AuraZone::core),
            VISUAL_CODEC.forGetter(AuraZone::visual)
    ).apply(i, AuraZone::from)).validate(AuraZone::validate);

    private Core core() {
        return new Core(this.aura, this.auraKinds, this.dimensions,
                this.biomes, this.fluctuation, this.rules, this.cultivateCondition, this.distribution);
    }

    private Visual visual() {
        return new Visual(this.elementFitBonus, this.elementConflictPenalty, this.noise, this.particle, this.clientRender, this.clientHud);
    }

    private static AuraZone from(Core core, Visual visual) {
        return new AuraZone(core.aura, core.auraKinds, core.dimensions,
                core.biomes, core.fluctuation, core.rules, core.cultivateCondition, core.distribution, visual.elementFitBonus,
                visual.elementConflictPenalty, visual.noise, visual.particle, visual.clientRender, visual.clientHud);
    }

    private record Core(Map<Holder<Element>, AuraValue> aura,
                        List<Identifier> auraKinds, List<Either<ResourceKey<LevelStem>, TagKey<LevelStem>>> dimensions,
                        List<Either<Holder<Biome>, TagKey<Biome>>> biomes, Fluctuation fluctuation, Rules rules,
                        EntityCondition cultivateCondition, Distribution distribution) {
        private static Core from(Map<Holder<Element>, AuraValue> aura,
                                 List<Identifier> auraKinds, List<Either<ResourceKey<LevelStem>, TagKey<LevelStem>>> dimensions,
                                 List<Either<Holder<Biome>, TagKey<Biome>>> biomes, Fluctuation fluctuation, Rules rules,
                                 EntityCondition cultivateCondition, Distribution distribution) {
            return new Core(aura, auraKinds, dimensions, biomes, fluctuation, rules,
                    cultivateCondition, distribution);
        }
    }

    private record Visual(double elementFitBonus, double elementConflictPenalty, Noise noise, Optional<ParticleEffect> particle,
                          ClientRender clientRender, ClientHud clientHud) {
        private static Visual from(double elementFitBonus, double elementConflictPenalty, Noise noise, Optional<ParticleEffect> particle,
                                   ClientRender clientRender, ClientHud clientHud) {
            return new Visual(elementFitBonus, elementConflictPenalty, noise, particle, clientRender, clientHud);
        }
    }

    private static DataResult<AuraZone> validate(AuraZone value) {
        if (!finite(value.elementFitBonus) || !finite(value.elementConflictPenalty))
            return DataResult.error(() -> "Aura zone numbers must be finite");
        if (!value.clientHud.valid())
            return DataResult.error(() -> "client_hud maximum values must be finite and greater than zero");
        return DataResult.success(value);
    }

    private static boolean finite(double value) {
        return Double.isFinite(value);
    }

    public record Fluctuation(boolean enabled, CycleType cycleType, double amplitude, long offsetTick) {
        public static final Fluctuation NONE = new Fluctuation(false, CycleType.STATIC, 0.0D, 0L);
        public static final Codec<Fluctuation> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.BOOL.optionalFieldOf("enable", false).forGetter(Fluctuation::enabled),
                CycleType.CODEC.optionalFieldOf("cycle_type", CycleType.STATIC).forGetter(Fluctuation::cycleType),
                Codec.DOUBLE.optionalFieldOf("amplitude", 0.0D).forGetter(Fluctuation::amplitude),
                Codec.LONG.optionalFieldOf("offset_tick", 0L).forGetter(Fluctuation::offsetTick)
        ).apply(i, Fluctuation::new));
    }

    public enum CycleType {
        DAY, MOON, STATIC;
        static final Codec<CycleType> CODEC = Codec.STRING.xmap(value -> valueOf(value.toUpperCase(Locale.ROOT)), value -> value.name().toLowerCase(Locale.ROOT));
    }

    /** Selects how one chunk's shared cultivation aura is allocated among due players. */
    public enum Distribution {
        RANDOM, EQUAL, REALM_WEIGHTED;
        public static final Codec<Distribution> CODEC = Codec.STRING.xmap(
                value -> valueOf(value.toUpperCase(Locale.ROOT)),
                value -> value.name().toLowerCase(Locale.ROOT));
    }

    public record Rules(boolean cultivateSuppress, double tribulationModify, double spiritPlantBonus,
                        boolean alchemyEnvBonus, boolean naturalSpawnHerb) {
        public static final Rules DEFAULT = new Rules(false, 0.0D, 0.0D, false, false);
        public static final Codec<Rules> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.BOOL.optionalFieldOf("cultivate_suppress", false).forGetter(Rules::cultivateSuppress),
                Codec.DOUBLE.optionalFieldOf("tribulation_modify", 0.0D).forGetter(Rules::tribulationModify),
                Codec.DOUBLE.optionalFieldOf("spirit_plant_bonus", 0.0D).forGetter(Rules::spiritPlantBonus),
                Codec.BOOL.optionalFieldOf("alchemy_env_bonus", false).forGetter(Rules::alchemyEnvBonus),
                Codec.BOOL.optionalFieldOf("natural_spawn_herb", false).forGetter(Rules::naturalSpawnHerb)
        ).apply(i, Rules::new));
    }

    /**
     * Seeded 2D value noise; seed lives in datapacks so template distributions are reproducible.
     */
    public record Noise(boolean enabled, long seed, double scale, double amplitude) {
        public static final Noise NONE = new Noise(false, 0L, 640.0D, 0.0D);
        public static final Codec<Noise> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.BOOL.optionalFieldOf("enable", false).forGetter(Noise::enabled),
                Codec.LONG.optionalFieldOf("seed", 0L).forGetter(Noise::seed),
                Codec.DOUBLE.optionalFieldOf("scale", 640.0D).forGetter(Noise::scale),
                Codec.DOUBLE.optionalFieldOf("amplitude", 0.0D).forGetter(Noise::amplitude)
        ).apply(i, Noise::new));
    }

    public record ClientRender(int fogColor, int renderDistance, float fogStrength) {
        public static final ClientRender DEFAULT = new ClientRender(0xFFFFFF, 64, 0.35F);
        public static final Codec<ClientRender> CODEC = RecordCodecBuilder.create(i -> i.group(
                MiscCodecs.COLOR_NO_ALPHA.optionalFieldOf("fog_color", 0xFFFFFF).forGetter(ClientRender::fogColor),
                Codec.intRange(8, 256).optionalFieldOf("render_distance", 64).forGetter(ClientRender::renderDistance),
                Codec.floatRange(0.0F, 1.0F).optionalFieldOf("fog_strength", 0.35F).forGetter(ClientRender::fogStrength)
        ).apply(i, ClientRender::new));
    }

    /**
     * Optional Origins-style bars for the current chunk's stored and resolved aura values.
     */
    public record ClientHud(Optional<Bar> storedAura, Optional<Bar> sensedConcentration) {
        public static final ClientHud NONE = new ClientHud(Optional.empty(), Optional.empty());
        public static final Codec<ClientHud> CODEC = RecordCodecBuilder.create(i -> i.group(
                Bar.CODEC.optionalFieldOf("stored_aura").forGetter(ClientHud::storedAura),
                Bar.CODEC.optionalFieldOf("sensed_concentration").forGetter(ClientHud::sensedConcentration)
        ).apply(i, ClientHud::new));

        private boolean valid() {
            return this.storedAura.stream().allMatch(Bar::valid)
                    && this.sensedConcentration.stream().allMatch(Bar::valid);
        }
    }

    /**
     * One bar row on the aura HUD. The texture row also selects its icon, as in Origins.
     */
    public record Bar(double maximum, int barIndex, boolean inverted, Anchor anchor, int order) {
        public static final Codec<Bar> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.DOUBLE.fieldOf("maximum").forGetter(Bar::maximum),
                Codec.intRange(0, 24).optionalFieldOf("bar_index", 0).forGetter(Bar::barIndex),
                Codec.BOOL.optionalFieldOf("inverted", false).forGetter(Bar::inverted),
                Anchor.CODEC.optionalFieldOf("anchor", Anchor.LEFT).forGetter(Bar::anchor),
                Codec.INT.optionalFieldOf("order", 0).forGetter(Bar::order)
        ).apply(i, Bar::new));

        private boolean valid() {
            return Double.isFinite(this.maximum) && this.maximum > 0.0D;
        }
    }
}
