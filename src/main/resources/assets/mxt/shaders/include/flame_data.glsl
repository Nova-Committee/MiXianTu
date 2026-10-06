#ifndef MXT_FLAME_DATA
#define MXT_FLAME_DATA
struct FlameItem {
    vec4 centerAge;
    vec4 basisX;
    vec4 basisY;
    vec4 basisZ;
    vec4 shape;
    vec4 style;
    vec4 wind;
    vec4 tile;
    vec4 tint;
};
layout(std140) uniform FlameItems {
    FlameItem Items[64];
};
layout(std140) uniform FlameFrame {
    mat4 ViewRotation;
    vec4 CameraRight;
    vec4 CameraUp;
    vec4 FrameInfo;
    vec4 RenderInfo;
    vec4 ParticleCounts;
};
uniform sampler2D FluidField;

float flameHash(vec3 p) {
    p = fract(p * 0.1031);
    p += dot(p, p.yzx + 33.33);
    return fract((p.x + p.y) * p.z);
}
float flameNoise(vec3 p) {
    vec3 i = floor(p);
    vec3 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(mix(flameHash(i), flameHash(i + vec3(1,0,0)), f.x),
                   mix(flameHash(i + vec3(0,1,0)), flameHash(i + vec3(1,1,0)), f.x), f.y),
               mix(mix(flameHash(i + vec3(0,0,1)), flameHash(i + vec3(1,0,1)), f.x),
                   mix(flameHash(i + vec3(0,1,1)), flameHash(i + vec3(1,1,1)), f.x), f.y), f.z);
}
vec2 fieldUV(vec2 uv, float slot) {
    vec2 tile = vec2(mod(slot, 32.0), floor(slot / 32.0));
    // Keep filtering inside the tile; U wraps around the sword, V ends at pommel/tip.
    vec2 local = vec2(fract(uv.x), clamp(uv.y, 0.0, 1.0));
    return (tile * 64.0 + clamp(local * 64.0, vec2(0.5), vec2(63.5))) / 2048.0;
}
vec4 fluidAt(vec2 uv, FlameItem item) {
    return texture(FluidField, fieldUV(uv, item.tile.x));
}
vec3 localToWorld(vec3 position, FlameItem item) {
    return item.centerAge.xyz + item.basisX.xyz * position.x
         + item.basisY.xyz * position.y + item.basisZ.xyz * position.z;
}
vec3 localVectorToWorld(vec3 vector, FlameItem item) {
    return item.basisX.xyz * vector.x + item.basisY.xyz * vector.y + item.basisZ.xyz * vector.z;
}
vec3 worldVectorToLocal(vec3 vector, FlameItem item) {
    float scaleSquared = max(dot(item.basisX.xyz, item.basisX.xyz), 0.0001);
    return vec3(dot(vector, item.basisX.xyz), dot(vector, item.basisY.xyz), dot(vector, item.basisZ.xyz)) / scaleSquared;
}
#endif
