package com.iafenvoy.mxt.data.curse;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.data.curse.CurseType.Empty;
import com.iafenvoy.mxt.data.curse.CurseType.Permanent;
import com.iafenvoy.mxt.data.curse.CurseType.Timed;
import com.iafenvoy.mxt.data.curse.CurseType.Triggered;
import com.iafenvoy.mxt.data.trigger.Trigger;
import com.iafenvoy.mxt.registry.MxtRegistries;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.OptionalLong;
import java.util.function.Function;

/**
 * Code-owned lifecycle policy selected by a curse definition's {@code type}.
 */
public sealed interface CurseType permits Timed, Permanent, Triggered, Empty {
    Codec<CurseType> CODEC = MxtRegistries.CURSE_TYPE.byNameCodec().dispatch("type", CurseType::codec, Function.identity());
    MapCodec<CurseType> MAP_CODEC = MapCodec.assumeMapUnsafe(CODEC);

    Identifier id();

    MapCodec<? extends CurseType> codec();

    /**
     * When an instance applied now with that resolved duration expires, where a negative value means it never
     * does.
     * <p>
     * An empty result means the resolution cannot be applied at all. Callers reject the application instead of
     * throwing, so one malformed definition - a formula that produced a nonsense duration, a definition whose
     * duration was edited while instances existed - can never break a tick or an event handler.
     */
    OptionalLong expiry(long duration, long gameTime);

    /**
     * Whether the type runs no behaviour of its own, which is how a definition can exist as a pure marker.
     */
    default boolean inert() {
        return false;
    }

    enum Timed implements CurseType {
        INSTANCE;
        public static final MapCodec<Timed> CODEC = MapCodec.unit(INSTANCE);

        @Override
        public Identifier id() {
            return Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "timed");
        }

        @Override
        public MapCodec<Timed> codec() {
            return CODEC;
        }

        @Override
        public OptionalLong expiry(long duration, long gameTime) {
            return duration <= 0L ? OptionalLong.empty() : OptionalLong.of(Math.addExact(gameTime, duration));
        }
    }

    enum Permanent implements CurseType {
        INSTANCE;
        public static final MapCodec<Permanent> CODEC = MapCodec.unit(INSTANCE);

        @Override
        public Identifier id() {
            return Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "permanent");
        }

        @Override
        public MapCodec<Permanent> codec() {
            return CODEC;
        }

        @Override
        public OptionalLong expiry(long duration, long gameTime) {
            return OptionalLong.of(-1L);
        }
    }

    /**
     * A curse whose periodic behaviour is driven by the trigger system instead of a tick interval: while the
     * curse is held, every signal one of its {@code triggers} matches runs {@code on_tick} once. It lasts like
     * {@code mxt:timed} when it declares a duration and like {@code mxt:permanent} when it does not.
     */
    record Triggered(List<Trigger> triggers) implements CurseType {
        public static final MapCodec<Triggered> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Trigger.CODEC.listOf().optionalFieldOf("triggers", List.of()).forGetter(Triggered::triggers)
        ).apply(i, Triggered::new));

        public Triggered {
            triggers = List.copyOf(triggers);
        }

        @Override
        public Identifier id() {
            return Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "triggered");
        }

        @Override
        public MapCodec<Triggered> codec() {
            return CODEC;
        }

        @Override
        public OptionalLong expiry(long duration, long gameTime) {
            return duration <= 0L ? OptionalLong.of(-1L) : OptionalLong.of(Math.addExact(gameTime, duration));
        }
    }

    /**
     * A marker type: it never expires and runs no behaviour, so a definition can exist while doing nothing.
     */
    enum Empty implements CurseType {
        INSTANCE;
        public static final MapCodec<Empty> CODEC = MapCodec.unit(INSTANCE);

        @Override
        public Identifier id() {
            return Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "empty");
        }

        @Override
        public MapCodec<Empty> codec() {
            return CODEC;
        }

        @Override
        public OptionalLong expiry(long duration, long gameTime) {
            return OptionalLong.of(-1L);
        }

        @Override
        public boolean inert() {
            return true;
        }
    }
}
