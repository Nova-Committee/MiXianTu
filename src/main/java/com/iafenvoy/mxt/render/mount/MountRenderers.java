package com.iafenvoy.mxt.render.mount;

import com.iafenvoy.mxt.api.MountRenderer;
import com.iafenvoy.mxt.api.MountRenderContext;
import com.iafenvoy.mxt.api.MountRenderState;
import com.iafenvoy.mxt.data.ability.render.MountRender;
import com.iafenvoy.mxt.data.ability.render.builtin.GeckoLibMountRender;
import com.iafenvoy.mxt.data.ability.render.builtin.ItemMountRender;
import com.iafenvoy.mxt.registry.MxtRegistries;
import com.mojang.logging.LogUtils;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.serialization.MapCodec;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * The client's renderers, opened by the codec a definition was decoded with, so the two sides can never disagree
 * about an id. A type this client has no renderer for is drawn by the item renderer instead and warns once: that
 * is what lets one pack run on a machine with GeckoLib and on one without.
 *
 * <p>Registered during client setup and only read from the game thread afterwards, so it needs no lock.
 */
public final class MountRenderers {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<MapCodec<? extends MountRender>, MountRenderer<?>> RENDERERS = new HashMap<>();
    private static final Set<MapCodec<? extends MountRender>> WARNED = new HashSet<>();

    private MountRenderers() {
    }

    public static void registerBuiltins() {
        register(ItemMountRender.CODEC, new ItemMountRenderer());
    }

    public static <T extends MountRender> void register(MapCodec<T> codec, MountRenderer<T> renderer) {
        RENDERERS.putIfAbsent(codec, renderer);
    }

    @SuppressWarnings("unchecked")
    public static <T extends MountRender> Resolved<?> resolve(T definition) {
        MountRenderer<?> renderer = RENDERERS.get(definition.codec());
        if (renderer != null) return new Resolved<>(definition, (MountRenderer<T>) renderer);
        warnOnce(definition);
        MountRenderer<?> item = RENDERERS.get(ItemMountRender.CODEC);
        if (item == null) throw new IllegalStateException("MountRenderers.registerBuiltins() has not run");
        return new Resolved<>(ItemMountRender.INSTANCE, (MountRenderer<ItemMountRender>) item);
    }

    public record Resolved<T extends MountRender>(T definition, MountRenderer<T> renderer) {
        public MountRenderState extract(MountRenderContext context) {
            MountRenderState state = this.renderer.createState();
            this.renderer.extract(this.definition, context, state);
            return state;
        }

        public void submit(MountRenderContext context, MountRenderState state, PoseStack poseStack,
                           SubmitNodeCollector collector, CameraRenderState camera) {
            this.renderer.submit(this.definition, context, state, poseStack, collector, camera);
        }
    }

    private static void warnOnce(MountRender definition) {
        if (!WARNED.add(definition.codec())) return;
        if (definition instanceof GeckoLibMountRender)
            LOGGER.warn("A mount asks for a GeckoLib model but GeckoLib is not installed; drawing it as the item it carries instead");
        else
            LOGGER.warn("No mount renderer is registered for {}; drawing the mount as the item it carries instead",
                    MxtRegistries.MOUNT_RENDER_TYPE.getKey(definition.codec()));
    }
}
