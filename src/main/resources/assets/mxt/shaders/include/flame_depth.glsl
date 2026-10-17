// Reconstructs scene depth and fades flames where they intersect solid geometry.
// Keeps particle and surface layers from ending in hard, distracting edges.
#ifndef MXT_FLAME_DEPTH
#define MXT_FLAME_DEPTH
uniform sampler2D SceneDepth;
float viewDepth(float depth) {
    // Cleared sky depth is unbounded, including with infinite-far projection matrices.
    if (depth >= 0.999999) return 1e20;
    float ndc = RenderInfo.y > 0.5 ? depth : depth * 2.0 - 1.0;
    float denominator = ndc * ProjMat[2][3] - ProjMat[2][2];
    return abs(ProjMat[3][2] - ndc * ProjMat[3][3]) / max(abs(denominator), 1e-8);
}
float softFlame() {
    vec2 screenUV = gl_FragCoord.xy / ViewportInfo.xy;
    float scene = texture(SceneDepth, screenUV).r;
    if (scene >= 0.999999) return 1.0;
    float gap = viewDepth(scene) - viewDepth(gl_FragCoord.z);
    if (gap <= 0.0) return 0.0;
    return RenderInfo.x > 0.0 ? smoothstep(0.0, RenderInfo.x, gap) : 1.0;
}
#endif
