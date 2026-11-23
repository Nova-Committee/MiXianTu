#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>
#moj_import <mxt:storm_cloud.glsl>
#define MXT_DEPTH_VERTEX
#moj_import <mxt:scene_depth.glsl>

in vec3 Position;

out vec3 relativePosition;
flat out int cloudIndex;
flat out uvec4 boltNodes[4];
// The bounds carry the flash envelope and growth in their fourth components.
flat out vec4 boltLower;
flat out vec4 boltUpper;

void saveBoltPoint(int index, vec3 point) {
    uint packedPoint = stormPackPoint(point / STORM_BOLT_SCALE);
    boltNodes[index / 4][index % 4] = packedPoint;
    point = stormUnpackPoint(packedPoint);
    boltLower.xyz = min(boltLower.xyz, point);
    boltUpper.xyz = max(boltUpper.xyz, point);
}

vec3 loadBoltPoint(int index) {
    return stormUnpackPoint(boltNodes[index / 4][index % 4]) * STORM_BOLT_SCALE;
}

uint boltRandom(uint value) {
    value = (value ^ (value >> 16u)) * 0x7feb352du;
    value = (value ^ (value >> 15u)) * 0x846ca68bu;
    return value ^ (value >> 16u);
}

float boltUnit(uint key) {
    return float(boltRandom(key) >> 8u) / 16777216.0;
}

vec3 boltStep(float heading, float pitch, float reach, float u, float jitter, uint key) {
    vec2 forward = vec2(cos(heading), sin(heading));
    vec2 sideways = vec2(-forward.y, forward.x);
    float envelope = 4.0 * u * (1.0 - u);
    vec2 horizontal = forward * (reach * cos(pitch) * u)
            + sideways * ((boltUnit(key) - 0.5) * jitter * envelope);
    float descent = u + (boltUnit(key + 1u) - 0.5) * 0.10 * envelope;
    return vec3(horizontal.x, -reach * sin(pitch) * descent, horizontal.y);
}

void generateBolt(StormCloud cloud) {
    boltLower = vec4(1.0, 1.0, 1.0, 0.0);
    boltUpper = vec4(-1.0, -1.0, -1.0, 0.0);
    for (int i = 0; i < 4; ++i) boltNodes[i] = uvec4(0);
    if (cloud.lightningColor.a <= 0.0) return;

    uint seed = uint(cloud.shape.w * 16777216.0);
    int currentTick = int(cloud.shape.y);
    int oldestTick = max(0, currentTick - 3);
    int strikeTick = currentTick;
    uint strike = 0u;
    // Reconstruct the newest strike still within its four-tick visible interval.
    for (; strikeTick >= oldestTick; --strikeTick) {
        strike = boltRandom(seed ^ (uint(strikeTick) * 0x9e3779b9u));
        if (strike % 10u == 0u) break;
    }
    if (strikeTick < oldestTick) return;
    float elapsed = cloud.shape.y - float(strikeTick);
    float heading = 6.2831853 * boltUnit(strike + 11u);
    float pitch = radians(30.0) * boltUnit(strike + 23u);
    float reach = mix(0.53, 0.82, boltUnit(strike + 37u));
    vec3 start = 0.11 * vec3(cos(heading), 0.0, sin(heading));
    for (int i = 0; i <= STORM_TRUNK_SEGMENTS; ++i) {
        float u = float(i) / float(STORM_TRUNK_SEGMENTS);
        saveBoltPoint(i, start + boltStep(heading, pitch, reach, u, 0.21, strike + 101u + 2u * uint(i)));
    }
    for (int fork = 0; fork < 2; ++fork) {
        vec3 root = loadBoltPoint(stormForkRoot(fork));
        float side = fork == 0 ? -1.0 : 1.0;
        uint key = strike + 151u + 19u * uint(fork);
        float forkHeading = heading + side * radians(mix(30.0, 62.0, boltUnit(key)));
        float forkPitch = radians(30.0) * boltUnit(key + 1u);
        float forkReach = mix(0.18, 0.23, boltUnit(key + 2u));
        for (int i = 1; i <= STORM_FORK_SEGMENTS; ++i) {
            float u = float(i) / float(STORM_FORK_SEGMENTS);
            saveBoltPoint(stormForkStart(fork) + i - 1,
                    root + boltStep(forkHeading, forkPitch, forkReach, u, 0.07, key + 3u + 2u * uint(i)));
        }
    }
    boltLower.w = smoothstep(0.0, 0.35, elapsed) * (1.0 - smoothstep(1.5, 4.0, elapsed));
    boltUpper.w = smoothstep(0.0, 1.25, elapsed) * float(STORM_TRUNK_SEGMENTS);
}

void main() {
    cloudIndex = gl_InstanceID;
    StormCloud cloud = Clouds[cloudIndex];
    vec3 extent = vec3(cloud.centerRadius.w, cloud.shape.x, cloud.centerRadius.w);
    mat4 clipFromRelative = ProjMat * ModelViewMat;
#ifdef MXT_SCENE_DEPTH
    mxt_depth_vertex(clipFromRelative);
#endif
    generateBolt(cloud);
    if (Position.y < 0.0 && boltLower.w > 0.0) extent.y += STORM_BOLT_SCALE.y * extent.x;
    relativePosition = cloud.centerRadius.xyz + Position * extent;
    gl_Position = clipFromRelative * vec4(relativePosition, 1.0);
}
