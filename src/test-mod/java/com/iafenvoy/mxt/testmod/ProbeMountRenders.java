package com.iafenvoy.mxt.testmod;

import com.iafenvoy.mxt.data.ability.render.MountRender;
import com.iafenvoy.mxt.registry.MxtRegistries;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * A mount render type belonging to another mod, which is the shape an addon is supposed to use: one codec on the
 * common side, one client renderer on the client side. Only the codec is registered here - a dedicated server can
 * decode a definition that names it, and the probe asserts exactly that.
 */
public final class ProbeMountRenders {
    public static final DeferredRegister<MapCodec<? extends MountRender>> REGISTRY =
            DeferredRegister.create(MxtRegistries.MOUNT_RENDER_TYPE, MxtTestMod.MOD_ID);
    public static final DeferredHolder<MapCodec<? extends MountRender>, MapCodec<ProbeMountRender>> PROBE =
            REGISTRY.register("probe_render", () -> ProbeMountRender.CODEC);

    private ProbeMountRenders() {
    }

    public record ProbeMountRender(String label) implements MountRender {
        public static final MapCodec<ProbeMountRender> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.STRING.fieldOf("label").forGetter(ProbeMountRender::label)
        ).apply(i, ProbeMountRender::new));

        @Override
        public MapCodec<ProbeMountRender> codec() {
            return CODEC;
        }
    }
}
