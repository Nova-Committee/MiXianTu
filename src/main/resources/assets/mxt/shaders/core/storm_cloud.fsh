#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:projection.glsl>
#moj_import <mxt:scene_depth.glsl>
#moj_import <mxt:storm_cloud.glsl>

in vec3 relativePosition;
flat in int cloudIndex;
flat in uvec4 boltNodes[4];
flat in vec4 boltLower;
flat in vec4 boltUpper;

out vec4 fragColor;

float noise(vec2 p) {
    vec2 cell = floor(p), f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(stormHash(cell), stormHash(cell + vec2(1, 0)), f.x),
               mix(stormHash(cell + vec2(0, 1)), stormHash(cell + vec2(1, 1)), f.x), f.y);
}

vec2 rotate(vec2 p, float angle) {
    float s = sin(angle), c = cos(angle);
    return vec2(c * p.x - s * p.y, s * p.x + c * p.y);
}

float density(vec3 p, float time, float seed, out float light) {
    float r = length(p.xz);
    float angle = r > 0.0001 ? atan(p.z, p.x) : 0.0;
    vec2 flow = rotate(p.xz, -0.19 * time - 2.0 * r);
    float broad = noise(4.7 * flow + vec2(seed * 13.0, p.y * 0.85));
    float fine = noise(13.0 * flow + vec2(0.16 * time, p.y * 1.9 + seed));
    float arms = 0.5 + 0.5 * cos(3.0 * angle - 6.4 * r - 0.57 * time + 1.8 * broad + 3.0 * seed);
    float outer = 0.82 + 0.14 * (broad - 0.5) + 0.075 * (arms - 0.5)
            + 0.045 * (fine - 0.5) + 0.045 * sin(5.0 * angle + 4.0 * seed + 0.29 * time);
    float edge = 1.0 - smoothstep(outer - 0.20, outer, r);
    float eye = smoothstep(0.055, 0.28, r);
    float eyeWall = smoothstep(0.09, 0.22, r) * (1.0 - smoothstep(0.23, 0.40, r));
    float height = mix(0.30, 0.72, eye) * (0.65 + 0.35 * broad + 0.16 * arms) * (0.58 + 0.42 * edge);
    float middle = (broad - 0.48) * 0.30 + (fine - 0.5) * 0.13;
    float layer = 1.0 - smoothstep(height * 0.28, height, abs(p.y - middle));
    float pockets = smoothstep(0.23, 0.77, 0.49 * broad + 0.33 * fine + 0.18 * arms);
    light = 0.35 + 0.46 * broad + 0.26 * fine + 0.27 * eyeWall + 0.16 * p.y;
    return edge * layer * mix(0.18, 1.0, pockets) * mix(0.48, 1.0, eye);
}

vec3 boltPoint(int index, vec3 extent) {
    return stormUnpackPoint(boltNodes[index / 4][index % 4]) * extent;
}

bool intersectsBoltBounds(vec3 origin, vec3 direction, float travel, vec3 extent, float padding) {
    vec3 divisor = mix(-max(abs(direction), vec3(1e-6)), max(abs(direction), vec3(1e-6)),
            greaterThanEqual(direction, vec3(0.0)));
    vec3 a = (boltLower.xyz * extent - padding - origin) / divisor;
    vec3 b = (boltUpper.xyz * extent + padding - origin) / divisor;
    vec3 entry = min(a, b);
    vec3 exit = max(a, b);
    return max(0.0, max(entry.x, max(entry.y, entry.z))) <= min(travel, min(exit.x, min(exit.y, exit.z)));
}

void traceBoltSegment(vec3 a, vec3 axis, vec3 direction, float travel, vec2 radii, float aa, inout vec3 hit) {
    float projectedA = dot(a, direction);
    float projectedAxis = dot(axis, direction);
    vec3 crossA = a - projectedA * direction;
    vec3 crossAxis = axis - projectedAxis * direction;
    float u = clamp(-dot(crossA, crossAxis) / max(dot(crossAxis, crossAxis), 1e-8), 0.0, 1.0);
    float t = projectedA + projectedAxis * u;
    if (t < 0.0 || t > travel) {
        t = clamp(t, 0.0, travel);
        u = clamp(dot(t * direction - a, axis) / max(dot(axis, axis), 1e-8), 0.0, 1.0);
    }
    float distance = length(a + axis * u - direction * t);
    float radius = mix(radii.x, radii.y, u);
    float core = 1.0 - smoothstep(radius * 0.35, radius + aa, distance);
    float glow = 1.0 - smoothstep(radius, radius * 3.8 + aa, distance);
    float strength = 0.8 * core + 0.2 * glow;
    if (strength > hit.y || (strength == hit.y && t < hit.x)) hit = vec3(t, strength, core);
}

void traceBoltPath(int root, int first, int segments, float progress, float radius, vec3 extent,
                   vec3 origin, vec3 direction, float travel, float aa, inout vec3 hit) {
    vec3 a = boltPoint(root, extent) - origin;
    for (int i = 0; i < segments && float(i) < progress; ++i) {
        vec3 b = boltPoint(first + i, extent) - origin;
        float lengthFactor = min(1.0, progress - float(i));
        vec2 radii = radius * mix(vec2(1.0), vec2(0.28), (float(i) + vec2(0.0, lengthFactor)) / float(segments));
        traceBoltSegment(a, (b - a) * lengthFactor, direction, travel, radii, aa, hit);
        a = b;
    }
}

vec3 traceBolt(vec3 origin, vec3 direction, float travel, vec3 extent, float aa) {
    vec3 hit = vec3(1e20, 0.0, 0.0);
    float radius = stormBoltRadius(extent);
    if (boltLower.w <= 0.0 || !intersectsBoltBounds(origin, direction, travel, extent, 3.8 * radius + aa)) return hit;
    traceBoltPath(0, 1, STORM_TRUNK_SEGMENTS, boltUpper.w, radius, extent, origin, direction, travel, aa, hit);
    for (int fork = 0; fork < 2; ++fork) {
        int root = stormForkRoot(fork);
        float progress = clamp((boltUpper.w - float(root)) * 1.4, 0.0, float(STORM_FORK_SEGMENTS));
        traceBoltPath(root, stormForkStart(fork), STORM_FORK_SEGMENTS, progress, 0.61 * radius,
                extent, origin, direction, travel, aa, hit);
    }
    return hit;
}

void main() {
    StormCloud instance = Clouds[cloudIndex];
    vec3 extent = vec3(instance.centerRadius.w, instance.shape.x, instance.centerRadius.w);
    float time = instance.shape.y * 0.05;
    float seed = instance.shape.w;
    float life = stormLife(time, instance.shape.z);
    float aa = clamp(0.33 * length(fwidth(relativePosition)), 0.006, 0.045 + 0.015 * extent.x);
    if (life <= 0.001) discard;

    vec3 rayOrigin = vec3(0);
    vec3 direction = normalize(relativePosition);
    float sceneDistance = 1e20;
#ifdef MXT_SCENE_DEPTH
    MxtSceneRay ray = mxt_scene_ray(RenderFlags.x > 0.5);
    if (!ray.valid) discard;
    rayOrigin = ray.origin;
    direction = ray.direction;
    sceneDistance = ray.distance;
#endif
    vec3 cloudOrigin = rayOrigin - instance.centerRadius.xyz;
    vec3 boltOrigin = cloudOrigin - vec3(0.0, STORM_BOLT_ORIGIN_Y * extent.y, 0.0);
    vec3 hit = traceBolt(boltOrigin, direction, sceneDistance, STORM_BOLT_SCALE * extent.x, aa);
    float flash = boltLower.w * life * instance.lightningColor.a;
    float boltAlpha = clamp(hit.y * flash * 1.32, 0.0, 1.0);
    float boltDepth = boltAlpha >= 1.0 / 255.0 ? hit.x : 1e20;
    vec3 boltColor = mix(instance.lightningColor.rgb, vec3(1.0), hit.z * 0.72);
    vec4 bolt = vec4(boltColor * boltAlpha, boltAlpha);

    vec3 start = cloudOrigin / extent;
    vec3 stepDirection = direction / extent;
    float directionSquared = dot(stepDirection, stepDirection);
    float closestDistance = -dot(start, stepDirection) / directionSquared;
    vec3 closest = start + closestDistance * stepDirection;
    float chordSquared = 1.0 - dot(closest, closest);
    float halfChord = sqrt(max(0.0, chordSquared) / directionSquared);
    float nearDistance = max(0.0, closestDistance - halfChord);
    float farDistance = min(sceneDistance, closestDistance + halfChord);
    float first = boltDepth;
    vec4 cloud = vec4(0);
    if (instance.cloudColor.a > 0.0 && farDistance > nearDistance) {
        int samples = length(instance.centerRadius.xyz) < extent.x * 2.6 + 24.0 ? 12 : 7;
        float stride = (farDistance - nearDistance) / float(samples);
        float jitter = mix(0.17, 0.83, fract(52.9829189 * fract(dot(floor(gl_FragCoord.xy), vec2(0.06711056, 0.00583715)))));
        float opticalStep = 10.5 * stride * sqrt(directionSquared) * life * instance.cloudColor.a;
        for (int i = 0; i < samples; ++i) {
            float cellStart = nearDistance + float(i) * stride;
            float t = cellStart + jitter * stride;
            float light;
            float opticalDepth = opticalStep * density(closest + (t - closestDistance) * stepDirection, time, seed, light);
            float alpha = 1.0 - exp(-opticalDepth);
            vec3 tint = instance.cloudColor.rgb * light;
            if (alpha > 0.008) first = min(first, t);
            if (boltDepth <= cellStart + stride) {
                // Insert the emissive channel between the front and back of this cloud sample.
                float portion = clamp((boltDepth - cellStart) / stride, 0.0, 1.0);
                float front = 1.0 - exp(-opticalDepth * portion);
                float transmittance = 1.0 - front;
                vec4 layer = vec4(tint * front, front) + transmittance * bolt
                        + (1.0 - bolt.a) * vec4(tint * (alpha - front), alpha - front);
                cloud += (1.0 - cloud.a) * layer;
                boltDepth = 1e20;
            } else {
                cloud += (1.0 - cloud.a) * vec4(tint * alpha, alpha);
            }
            if (cloud.a > 0.97 && boltDepth == 1e20) break;
        }
    }
    if (boltDepth < 1e20) cloud += (1.0 - cloud.a) * bolt;
    if (first == 1e20) discard;
    vec3 relative = rayOrigin + first * direction;
    vec4 clip = ProjMat * (ModelViewMat * vec4(relative, 1.0));
    float depth = clip.z / clip.w;
    gl_FragDepth = clamp(RenderFlags.x > 0.5 ? depth : depth * 0.5 + 0.5, 0.0, 1.0);
    // Cloud fog keeps local terrain haze from erasing high-altitude effects.
    float fogEnd = max(0.001, min(FogCloudsEnd, FogRenderDistanceEnd));
    float fog = smoothstep(fogEnd * 0.55, fogEnd, fog_spherical_distance(relative));
    fragColor = cloud * (1.0 - fog);
    if (fragColor.a < 1.0 / 255.0) discard;
}
