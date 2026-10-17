// Shares camera, timing, quality settings, and render-target dimensions across flame passes.
// Kept separate from item records so the final composite does not require a FlameItems binding.
#ifndef MXT_FLAME_FRAME
#define MXT_FLAME_FRAME
layout(std140) uniform FlameFrame {
    mat4 ViewRotation;
    vec4 CameraRight;
    vec4 CameraUp;
    vec4 FrameInfo;
    vec4 RenderInfo;
    vec4 ParticleCounts;
    vec4 ViewportInfo;
};
#endif
