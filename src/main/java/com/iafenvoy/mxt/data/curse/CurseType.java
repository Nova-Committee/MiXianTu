package com.iafenvoy.mxt.data.curse;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.data.curse.CurseType.Empty;
import com.iafenvoy.mxt.data.curse.CurseType.Permanent;
import com.iafenvoy.mxt.data.curse.CurseType.Timed;
import com.iafenvoy.mxt.data.curse.CurseType.Triggered;
import com.iafenvoy.mxt.registry.MxtRegistries;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.resources.Identifier;

import java.util.function.Function;

/**
 * Code-owned lifecycle policy selected by a curse definition's {@code type}.
 */
public sealed interface CurseType permits Timed, Permanent, Triggered, Empty {
    Codec<CurseType> CODEC = MxtRegistries.CURSE_TYPE.byNameCodec().dispatch("type", CurseType::codec, Function.identity());
    MapCodec<CurseType> MAP_CODEC = MapCodec.assumeMapUnsafe(CODEC);

    Identifier id();

    MapCodec<? extends CurseType> codec();

    long expiry(long duration, long gameTime);

    static CurseType forIdentifier(Identifier id) {
        return switch (id.getNamespace().equals(MiXianTu.MOD_ID) ? id.getPath() : "") {
            case "timed" -> Timed.INSTANCE;
            case "permanent" -> Permanent.INSTANCE;
            case "triggered" -> Triggered.INSTANCE;
            default -> Empty.INSTANCE;
        };
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
        public long expiry(long duration, long gameTime) {
            if (duration <= 0L) throw new IllegalStateException("Timed curse duration must be positive");
            return Math.addExact(gameTime, duration);
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
        public long expiry(long duration, long gameTime) {
            return -1L;
        }
    }

    /**
     * A curse whose application/removal effects are driven by the owning event bridge.
     */
    enum Triggered implements CurseType {
        INSTANCE;
        public static final MapCodec<Triggered> CODEC = MapCodec.unit(INSTANCE);

        @Override
        public Identifier id() {
            return Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "triggered");
        }

        @Override
        public MapCodec<Triggered> codec() {
            return CODEC;
        }

        @Override
        public long expiry(long duration, long gameTime) {
            if (duration <= 0L) return -1L;
            return Math.addExact(gameTime, duration);
        }
    }

    /**
     * A no-expiry curse type for definitions that intentionally have no lifecycle.
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
        public long expiry(long duration, long gameTime) {
            return -1L;
        }
    }
}
