package com.iafenvoy.mxt.render.entity.state;

import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;

import java.util.function.Consumer;

public final class SoulRenderState extends EntityRenderState {
    public @Nullable EntityRenderer<LivingEntity, EntityRenderState> renderer;
    public @Nullable EntityRenderState body;
    public @Nullable Model<?> model;
    public @Nullable Identifier texture;
    public @Nullable Consumer<RuntimeException> renderFailure;
}
