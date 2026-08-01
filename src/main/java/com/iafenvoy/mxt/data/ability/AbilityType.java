package com.iafenvoy.mxt.data.ability;

import com.iafenvoy.mxt.data.ability.AbilityType.Active;
import com.iafenvoy.mxt.data.ability.AbilityType.Aura;
import com.iafenvoy.mxt.data.ability.AbilityType.Channelled;
import com.iafenvoy.mxt.data.ability.AbilityType.Composite;
import com.iafenvoy.mxt.data.ability.AbilityType.Empty;
import com.iafenvoy.mxt.data.ability.AbilityType.Modifier;
import com.iafenvoy.mxt.data.ability.AbilityType.Triggered;
import com.iafenvoy.mxt.data.ability.AbilityType.Word;
import com.iafenvoy.mxt.data.resource.ResourceCost;
import com.iafenvoy.mxt.registry.MxtTypeRegistries;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.iafenvoy.mxt.util.formula.NumberProvider.Constant;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Locale;
import java.util.function.Function;

/**
 * Code-owned ability behaviour selected by a datapack {@code type}. Concrete spells remain
 * named datapack entries; this type only selects their lifecycle algorithm.
 */
public sealed interface AbilityType permits Active, Triggered, Modifier,
        Aura, Channelled, Composite, Word, Empty {
    Codec<AbilityType> CODEC = MxtTypeRegistries.ABILITY_TYPE.byNameCodec().dispatch("type", AbilityType::codec, Function.identity());
    /**
     * Allows a typed dispatch codec to participate in a flat enclosing record codec.
     */
    MapCodec<AbilityType> MAP_CODEC = MapCodec.assumeMapUnsafe(CODEC);

    Identifier id();

    MapCodec<? extends AbilityType> codec();

    static AbilityType forIdentifier(Identifier id) {
        return switch (id.getNamespace().equals("mxt") ? id.getPath() : "") {
            case "active" -> new Active("primary");
            case "modifier" -> new Modifier(List.of());
            case "aura" -> new Aura(new Constant(20.0D), new Constant(4.0D));
            case "channelled" -> new Channelled(new Constant(1.0D), List.of());
            case "composite" -> new Composite(List.of(), true);
            case "word" -> new Word(WordEffect.SELF_HEAL, true, new Constant(1.0D));
            default -> Empty.INSTANCE;
        };
    }

    record Active(String slot) implements AbilityType {
        public static final MapCodec<Active> CODEC = Codec.STRING.optionalFieldOf("slot", "primary").xmap(Active::new, Active::slot);

        public Active {
            if (slot.isBlank()) throw new IllegalArgumentException("Ability slot cannot be blank");
        }

        @Override
        public Identifier id() {
            return Identifier.fromNamespaceAndPath("mxt", "active");
        }

        @Override
        public MapCodec<Active> codec() {
            return CODEC;
        }
    }

    record Triggered(AbilityTrigger trigger, NumberProvider chance) implements AbilityType {
        public static final MapCodec<Triggered> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                AbilityTrigger.CODEC.fieldOf("trigger").forGetter(Triggered::trigger),
                NumberProvider.CODEC.optionalFieldOf("chance", new Constant(1.0D)).forGetter(Triggered::chance)
        ).apply(instance, Triggered::new));

        @Override
        public Identifier id() {
            return Identifier.fromNamespaceAndPath("mxt", "triggered");
        }

        @Override
        public MapCodec<Triggered> codec() {
            return CODEC;
        }
    }

    record Modifier(List<Identifier> queryTags) implements AbilityType {
        public static final MapCodec<Modifier> CODEC = Identifier.CODEC.listOf().optionalFieldOf("query_tags", List.of()).xmap(Modifier::new, Modifier::queryTags);

        public Modifier {
            queryTags = List.copyOf(queryTags);
        }

        @Override
        public Identifier id() {
            return Identifier.fromNamespaceAndPath("mxt", "modifier");
        }

        @Override
        public MapCodec<Modifier> codec() {
            return CODEC;
        }
    }

    record Aura(NumberProvider interval, NumberProvider radius) implements AbilityType {
        public static final MapCodec<Aura> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                NumberProvider.CODEC.optionalFieldOf("interval", new Constant(20.0D)).forGetter(Aura::interval),
                NumberProvider.CODEC.optionalFieldOf("radius", new Constant(4.0D)).forGetter(Aura::radius)
        ).apply(instance, Aura::new));

        @Override
        public Identifier id() {
            return Identifier.fromNamespaceAndPath("mxt", "aura");
        }

        @Override
        public MapCodec<Aura> codec() {
            return CODEC;
        }
    }

    record Channelled(NumberProvider tickInterval,
                      List<ResourceCost> upkeepCosts) implements AbilityType {
        public static final MapCodec<Channelled> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                NumberProvider.CODEC.optionalFieldOf("tick_interval", new Constant(1.0D)).forGetter(Channelled::tickInterval),
                ResourceCost.CODEC.listOf().optionalFieldOf("upkeep_costs", List.of()).forGetter(Channelled::upkeepCosts)
        ).apply(instance, Channelled::new));

        public Channelled {
            upkeepCosts = List.copyOf(upkeepCosts);
        }

        @Override
        public Identifier id() {
            return Identifier.fromNamespaceAndPath("mxt", "channelled");
        }

        @Override
        public MapCodec<Channelled> codec() {
            return CODEC;
        }
    }

    record Composite(List<Identifier> abilities, boolean allRequired) implements AbilityType {
        public static final MapCodec<Composite> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Identifier.CODEC.listOf().fieldOf("abilities").forGetter(Composite::abilities),
                Codec.BOOL.optionalFieldOf("all_required", true).forGetter(Composite::allRequired)
        ).apply(instance, Composite::new));

        public Composite {
            abilities = List.copyOf(abilities);
        }

        @Override
        public Identifier id() {
            return Identifier.fromNamespaceAndPath("mxt", "composite");
        }

        @Override
        public MapCodec<Composite> codec() {
            return CODEC;
        }
    }

    /**
     * High-tier, code-whitelisted word effects. Datapacks cannot supply an arbitrary command string.
     */
    record Word(WordEffect effect, boolean requiresOperator, NumberProvider amount) implements AbilityType {
        public static final MapCodec<Word> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                WordEffect.CODEC.fieldOf("effect").forGetter(Word::effect),
                Codec.BOOL.optionalFieldOf("requires_operator", true).forGetter(Word::requiresOperator),
                NumberProvider.CODEC.optionalFieldOf("amount", new Constant(0.0D)).forGetter(Word::amount)
        ).apply(instance, Word::new));

        @Override
        public Identifier id() {
            return Identifier.fromNamespaceAndPath("mxt", "word");
        }

        @Override
        public MapCodec<Word> codec() {
            return CODEC;
        }
    }

    enum WordEffect {
        SELF_HEAL,
        PURGE_SELF_CURSES;

        public static final Codec<WordEffect> CODEC = Codec.STRING.comapFlatMap(value -> {
            try {
                return DataResult.success(valueOf(value.toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException exception) {
                return DataResult.error(() -> "Unknown word effect " + value);
            }
        }, value -> value.name().toLowerCase(Locale.ROOT));
    }

    enum Empty implements AbilityType {
        INSTANCE;
        public static final MapCodec<Empty> CODEC = MapCodec.unit(INSTANCE);

        @Override
        public Identifier id() {
            return Identifier.fromNamespaceAndPath("mxt", "empty");
        }

        @Override
        public MapCodec<Empty> codec() {
            return CODEC;
        }
    }
}
