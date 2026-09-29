package com.iafenvoy.mxt.api;

/**
 * Per-vehicle scratch a mount renderer keeps between extracting a frame and submitting it. The framework creates
 * one per vehicle per frame, so nothing here survives a frame and history belongs in the renderer's own caches.
 *
 * <p>Client-side only: the server never resolves a renderer.
 */
public interface MountRenderState {
    // For a renderer that needs no scratch of its own.
    MountRenderState EMPTY = new MountRenderState() {
    };
}
