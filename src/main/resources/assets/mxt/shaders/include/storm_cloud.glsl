struct StormCloud {
    vec4 centerRadius;
    vec4 shape;
    vec4 cloudColor;
    vec4 lightningColor;
};

layout(std140) uniform StormCloudInstances {
    vec4 RenderFlags;
    StormCloud Clouds[16];
};

const int STORM_TRUNK_SEGMENTS = 8;
const int STORM_FORK_SEGMENTS = 3;
const vec3 STORM_BOLT_SCALE = vec3(1.0, 0.5, 1.0);
const float STORM_BOLT_ORIGIN_Y = -0.2;

int stormForkRoot(int fork) {
    return 3 + 2 * fork;
}

int stormForkStart(int fork) {
    return STORM_TRUNK_SEGMENTS + 1 + fork * STORM_FORK_SEGMENTS;
}

float stormHash(vec2 p) {
    vec3 q = fract(vec3(p.xyx) * vec3(0.1031, 0.11369, 0.13787));
    q += dot(q, q.yzx + 19.19);
    return fract((q.x + q.y) * q.z);
}

float stormLife(float age, float lifetime) {
    // A negative duration carries the server-managed cloud's opacity.
    if (lifetime < 0.0) return -lifetime;
    return smoothstep(0.0, min(0.65, lifetime * 0.35), age)
            * (1.0 - smoothstep(lifetime - min(1.15, lifetime * 0.4), lifetime, age));
}

// Ten bits per axis keep the shared path within the GLSL varying budget.
uint stormPackPoint(vec3 point) {
    uvec3 bits = uvec3(round((clamp(point, -1.0, 1.0) + 1.0) * 511.5));
    return bits.x | (bits.y << 10u) | (bits.z << 20u);
}

vec3 stormUnpackPoint(uint bits) {
    return vec3(uvec3(bits, bits >> 10u, bits >> 20u) & 1023u) / 511.5 - 1.0;
}

float stormBoltRadius(vec3 extent) {
    return min(0.025 + 0.006 * extent.x, 0.23 * extent.y);
}
