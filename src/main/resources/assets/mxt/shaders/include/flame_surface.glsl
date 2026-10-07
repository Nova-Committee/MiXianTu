// Defines the sword's local surface geometry from its blade and hilt dimensions.
// Used to place the surface flame and sample the fluid field along the sword.
#ifndef MXT_FLAME_SURFACE
#define MXT_FLAME_SURFACE
float swordBottom(FlameItem item) {
    return -(item.shape.x + item.shape.w) * 0.5;
}
float swordSpan(FlameItem item) {
    return item.shape.x + item.shape.w + 0.035;
}
float surfaceY(float ring, FlameItem item) {
    float bottom = swordBottom(item);
    float base = bottom + item.shape.w;
    float guard = max(0.06, item.shape.z * 1.7);
    if (ring < 0.5) return bottom - 0.035;
    if (ring < 1.5) return bottom + 0.065;
    if (ring < 2.5) return bottom + 0.0651;
    if (ring < 3.5) return base - guard * 0.5001;
    if (ring < 4.5) return base - guard * 0.4999;
    if (ring < 5.5) return base + guard * 0.4999;
    if (ring < 6.5) return base + guard * 0.5001;
    if (ring > 22.5) return base + item.shape.x;
    return mix(base + guard * 0.5001, base + item.shape.x * 0.8, (ring - 6.0) / 16.0);
}
vec3 swordSurface(vec2 uv, FlameItem item, out vec3 normal) {
    float bottom = swordBottom(item);
    float base = bottom + item.shape.w;
    float y = bottom - 0.035 + clamp(uv.y, 0.0, 1.0) * swordSpan(item);
    float guard = max(0.06, item.shape.z * 1.7);
    float halfWidth;
    float halfDepth;
    bool diamond = false;
    float handleWidth = max(item.shape.y * 0.28, item.shape.z * 2.0);
    float handleDepth = max(item.shape.z * 1.15, 0.06);
    if (y <= bottom + 0.065) {
        halfWidth = handleWidth * 0.65;
        halfDepth = handleDepth * 0.7;
    } else if (y < base - guard * 0.5) {
        halfWidth = handleWidth * 0.5;
        halfDepth = handleDepth * 0.5;
    } else if (y <= base + guard * 0.5) {
        halfWidth = item.style.x * 0.5;
        halfDepth = item.shape.z * 0.85;
    } else {
        diamond = true;
        float bladeV = clamp((y - base) / item.shape.x, 0.0, 1.0);
        float taper = clamp((1.0 - bladeV) / 0.2, 0.0, 1.0);
        halfWidth = item.shape.y * 0.5 * mix(0.62, 1.0, min(bladeV / 0.8, 1.0)) * taper;
        halfDepth = item.shape.z * 0.5 * taper;
    }
    vec2 corners[4];
    if (diamond) {
        corners[0] = vec2(-halfWidth, 0);
        corners[1] = vec2(0, halfDepth);
        corners[2] = vec2(halfWidth, 0);
        corners[3] = vec2(0, -halfDepth);
    } else {
        corners[0] = vec2(-halfWidth, halfDepth);
        corners[1] = vec2(halfWidth, halfDepth);
        corners[2] = vec2(halfWidth, -halfDepth);
        corners[3] = vec2(-halfWidth, -halfDepth);
    }
    float around = fract(uv.x) * 4.0;
    int side = int(floor(around));
    vec2 a = corners[side];
    vec2 b = corners[(side + 1) % 4];
    vec2 edge = b - a;
    // At the exact tip the cross-section collapses; use a finite fallback normal.
    vec3 faceNormal = vec3(-edge.y, 0, edge.x);
    normal = dot(faceNormal, faceNormal) > 1e-10 ? normalize(faceNormal) : vec3(1,0,0);
    vec2 crossSection = mix(a, b, fract(around));
    return vec3(crossSection.x, y, crossSection.y);
}
#endif
