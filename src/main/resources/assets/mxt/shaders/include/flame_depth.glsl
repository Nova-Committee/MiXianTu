// Reconstructs scene depth and fades flames where they intersect solid geometry.
// Keeps particle and surface layers from ending in hard, distracting edges.
#ifndef MXT_FLAME_DEPTH
#define MXT_FLAME_DEPTH
uniform sampler2D SceneDepth;
float viewDepth(float depth) {
    float ndc = RenderInfo.y > 0.5 ? depth : depth * 2.0 - 1.0;
    return abs((ProjMat[3][2] - ndc * ProjMat[3][3]) / (ndc * ProjMat[2][3] - ProjMat[2][2]));
}
float softFlame() {
    vec2 screenUV = gl_FragCoord.xy / vec2(textureSize(SceneDepth, 0));
    float scene = texture(SceneDepth, screenUV).r;
    if (scene >= 0.999999 || RenderInfo.x <= 0.0) return 1.0;
    return smoothstep(0.0, RenderInfo.x, viewDepth(scene) - viewDepth(gl_FragCoord.z));
}
#endif
