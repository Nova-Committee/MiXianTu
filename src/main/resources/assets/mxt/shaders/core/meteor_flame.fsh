#version 330
#moj_import <minecraft:projection.glsl>
#moj_import <mxt:flame_data.glsl>
#moj_import <mxt:flame_probability.glsl>
#moj_import <mxt:flame_depth.glsl>
#moj_import <mxt:flame_meteor.glsl>
in vec3 viewRay;
in vec2 screenUV;
flat in int itemIndex;
flat in float screenCoverage;
out vec4 fragColor;
void main() {
    FlameItem item = Items[itemIndex];
    vec3 worldRay = transpose(mat3(ViewRotation)) * normalize(viewRay);
    vec3 direction = normalize(worldVectorToLocal(worldRay, item));
    vec3 origin = worldVectorToLocal(-item.centerAge.xyz, item);
    vec3 safeDirection = mix(vec3(-1), vec3(1), step(vec3(0), direction)) * max(abs(direction), vec3(1e-6));
    vec3 a = (meteorBoundsMin(item) - origin) / safeDirection;
    vec3 b = (meteorBoundsMax(item) - origin) / safeDirection;
    vec3 lower = min(a, b);
    vec3 upper = max(a, b);
    float entry = max(max(lower.x, lower.y), max(lower.z, 0.0));
    float exit = min(min(upper.x, upper.y), upper.z);
    float scale = length(item.basisX.xyz);
    float viewRate = max(-normalize(viewRay).z * scale, 1e-6);
    float depth = texture(SceneDepth, screenUV).r;
    float scene = depth < 0.999999 ? viewDepth(depth) / viewRate : 1e20;
    exit = min(exit, scene);
    if (exit <= entry || item.tint.a <= 0.0 || FrameInfo.z <= 0.0) discard;

    int steps = item.style.w < 0.5 ? 32 : (item.style.w < 1.5 ? 24 : 14);
    steps = int(ceil(mix(float(steps), min(float(steps), 16.0), smoothstep(0.05, 0.50, screenCoverage))));
    float stride = (exit - entry) / float(steps);
    vec3 color = vec3(0);
    float opacity = 0.0;
    for (int sampleIndex = 0; sampleIndex < 32; sampleIndex++) {
        if (sampleIndex >= steps || opacity > 0.98) break;
        float distance = entry + (float(sampleIndex) + 0.5) * stride;
        vec3 position = origin + direction * distance;
        float heat;
        float density = meteorDensity(position, item, heat);
        if (density <= 0.0) continue;
        float soft = RenderInfo.x > 0.0 ? smoothstep(0.0, RenderInfo.x, (scene - distance) * scale) : 1.0;
        float alpha = 1.0 - exp(-density * stride / item.shape.x * 2.4 * item.tint.a * FrameInfo.z * soft);
        vec3 tint = flameTint(item.tint.rgb, heat, 0.15 + clamp(-position.y / meteorTailLength(item), 0.0, 1.0) * 0.65);
        color += (1.0 - opacity) * alpha * tint;
        opacity += (1.0 - opacity) * alpha;
    }
    if (opacity < 0.002) discard;
    fragColor = vec4(color / max(opacity, 0.001), opacity);
}
