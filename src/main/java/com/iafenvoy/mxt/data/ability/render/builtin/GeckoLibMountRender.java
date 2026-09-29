package com.iafenvoy.mxt.data.ability.render.builtin;

import com.iafenvoy.mxt.data.ability.render.MountPose;
import com.iafenvoy.mxt.data.ability.render.MountRender;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

import java.util.Map;
import java.util.Optional;

/**
 * A mount drawn from a GeckoLib model, which needs GeckoLib installed on the client. The three asset ids are baked
 * by GeckoLib's own resource reloader, so nothing has to be registered here; without the mod the client falls back
 * to the item model instead (never at load time, so a pack works on both kinds of machine).
 *
 * <p>Paths are written without the folder and suffix GeckoLib's cache keys leave out:
 * {@code assets/<namespace>/geckolib/models/<path>.geo.json} is {@code "<namespace>:<path>"}.
 */
public record GeckoLibMountRender(Identifier model, Identifier texture, Optional<Identifier> animations,
                                  Map<MountPose, String> states, int transitionTicks, double scale) implements MountRender {
    public static final int DEFAULT_TRANSITION_TICKS = 5;
    public static final double DEFAULT_SCALE = 1.0D;
    private static final Codec<Double> POSITIVE = Codec.doubleRange(Double.MIN_VALUE, Double.MAX_VALUE);
    private static final Codec<Map<MountPose, String>> STATES = Codec.unboundedMap(MountPose.CODEC, Codec.STRING);
    private static final MapCodec<GeckoLibMountRender> RAW_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Identifier.CODEC.fieldOf("model").forGetter(GeckoLibMountRender::model),
            Identifier.CODEC.fieldOf("texture").forGetter(GeckoLibMountRender::texture),
            Identifier.CODEC.optionalFieldOf("animations").forGetter(GeckoLibMountRender::animations),
            STATES.optionalFieldOf("states", Map.of()).forGetter(GeckoLibMountRender::states),
            Codec.intRange(0, 200).optionalFieldOf("transition_ticks", DEFAULT_TRANSITION_TICKS).forGetter(GeckoLibMountRender::transitionTicks),
            POSITIVE.optionalFieldOf("scale", DEFAULT_SCALE).forGetter(GeckoLibMountRender::scale)
    ).apply(i, GeckoLibMountRender::new));
    public static final MapCodec<GeckoLibMountRender> CODEC = RAW_CODEC.validate(GeckoLibMountRender::validate);

    public GeckoLibMountRender {
        states = Map.copyOf(states);
    }

    private static DataResult<GeckoLibMountRender> validate(GeckoLibMountRender render) {
        boolean named = render.states().values().stream().noneMatch(String::isBlank);
        return named ? DataResult.success(render)
                : DataResult.error(() -> "Mount animation names must not be blank");
    }

    // An unwritten entry asks for the animation named after the pose, which is what makes an empty table usable.
    public String animationFor(MountPose pose) {
        return this.states.getOrDefault(pose, pose.getSerializedName());
    }

    @Override
    public MapCodec<GeckoLibMountRender> codec() {
        return CODEC;
    }
}
