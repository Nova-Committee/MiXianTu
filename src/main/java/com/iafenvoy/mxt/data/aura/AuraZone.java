package com.iafenvoy.mxt.data.aura;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.iafenvoy.mxt.util.codec.RegistryCodecs;
import com.iafenvoy.mxt.util.codec.CollectionCodecs;
import com.iafenvoy.mxt.data.ParticleEffect;
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
import java.util.Optional;

import it.unimi.dsi.fastutil.objects.Object2DoubleMap;
import it.unimi.dsi.fastutil.objects.Object2DoubleMaps;

/**
 * Immutable datapack template for one aura environment.
 */
public record AuraZone(double baseAura, double regenPerTick, Object2DoubleMap<Holder<Element>> elementAura,
                       List<Identifier> auraKinds,
                       List<Either<ResourceKey<LevelStem>, TagKey<LevelStem>>> dimensions,
                       List<Either<Holder<Biome>, TagKey<Biome>>> biomes, Fluctuation fluctuation, Rules rules,
                       double elementFitBonus, double elementConflictPenalty, Noise noise,
                       Optional<ParticleEffect> particle, ClientRender clientRender,
                       ClientHud clientHud) {
    public static final Codec<Holder<AuraZone>> CODEC = RegistryFixedCodec.create(MxtResourceKeys.AURA_ZONE);
    public static final Codec<AuraZone> DIRECT_CODEC = RecordCodecBuilder.<AuraZone>create(i -> i.group(
            Codec.DOUBLE.optionalFieldOf("base_aura", 0.0D).forGetter(AuraZone::baseAura),
            Codec.DOUBLE.optionalFieldOf("regen_per_tick", 0.0D).forGetter(AuraZone::regenPerTick),
            CollectionCodecs.doubleMap(Element.CODEC).optionalFieldOf("element_aura", Object2DoubleMaps.emptyMap()).forGetter(AuraZone::elementAura),
            Identifier.CODEC.listOf().optionalFieldOf("aura_kinds", List.of()).forGetter(AuraZone::auraKinds),
            RegistryCodecs.keyOrTagList(Registries.LEVEL_STEM).optionalFieldOf("dimensions", List.of()).forGetter(AuraZone::dimensions),
            RegistryCodecs.holderOrTagList(Registries.BIOME).optionalFieldOf("biomes", List.of()).forGetter(AuraZone::biomes),
            Fluctuation.CODEC.optionalFieldOf("fluctuation", Fluctuation.NONE).forGetter(AuraZone::fluctuation),
            Rules.CODEC.optionalFieldOf("rules", Rules.DEFAULT).forGetter(AuraZone::rules),
            Codec.DOUBLE.optionalFieldOf("element_fit_bonus", 0.0D).forGetter(AuraZone::elementFitBonus),
            Codec.DOUBLE.optionalFieldOf("element_conflict_penalty", 0.0D).forGetter(AuraZone::elementConflictPenalty),
            Noise.CODEC.optionalFieldOf("noise", Noise.NONE).forGetter(AuraZone::noise),
            ParticleEffect.CODEC.optionalFieldOf("particle").forGetter(AuraZone::particle),
            ClientRender.CODEC.optionalFieldOf("client_render", ClientRender.DEFAULT).forGetter(AuraZone::clientRender),
            ClientHud.CODEC.optionalFieldOf("client_hud", ClientHud.NONE).forGetter(AuraZone::clientHud)
    ).apply(i, AuraZone::new)).validate(AuraZone::validate);

    private static DataResult<AuraZone> validate(AuraZone value) {
        if (!finite(value.baseAura) || value.baseAura < 0.0D || !finite(value.regenPerTick)
                || !finite(value.elementFitBonus) || !finite(value.elementConflictPenalty))
            return DataResult.error(() -> "Aura zone numbers must be finite; base_aura must be non-negative");
        if (value.elementAura.values().doubleStream().anyMatch(number -> !finite(number)))
            return DataResult.error(() -> "element_aura values must be finite");
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

    public record ClientRender(String fogColor, int renderDistance, float fogStrength) {
        public static final ClientRender DEFAULT = new ClientRender("#FFFFFF", 64, 0.35F);
        public static final Codec<ClientRender> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.optionalFieldOf("fog_color", "#FFFFFF").forGetter(ClientRender::fogColor),
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
