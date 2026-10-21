package com.iafenvoy.mxt.render.effect;

// Implemented by this mod's extracted particle states, without modifying vanilla collectors.
public interface WorldEffectSource {
    void collect(WorldEffects.Frame frame);
}
