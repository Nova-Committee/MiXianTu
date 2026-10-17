// Defines the continuous meteor volume and its conservative local bounds.
// Shared by screen coverage and ray integration so wind cannot push flames outside the quad.
#ifndef MXT_FLAME_METEOR
#define MXT_FLAME_METEOR
float meteorTailLength(FlameItem item) {
    return max(item.shape.x, item.shape.y * (1.0 + max(-item.wind.y, 0.0) * 0.075));
}
vec3 meteorBoundsMin(FlameItem item) {
    float radius = item.shape.x;
    return vec3(-radius * 3.0, -meteorTailLength(item) - radius * 1.6, -radius * 3.0);
}
vec3 meteorBoundsMax(FlameItem item) {
    float radius = item.shape.x;
    return vec3(radius * 3.0, radius * 1.6, radius * 3.0);
}
vec2 meteorBend(FlameItem item, float progress) {
    float time = item.centerAge.w;
    vec2 curl = vec2(sin(time * 4.1 + progress * 8.0 + item.style.z * 21.0),
                     cos(time * 3.7 - progress * 9.0 + item.style.z * 17.0));
    return item.shape.x * (item.wind.xz * 0.08 * progress * progress + curl * 0.25 * progress);
}
float meteorDensity(vec3 position, FlameItem item, out float heat) {
    float radius = item.shape.x;
    float time = item.centerAge.w;
    float progress = clamp(-position.y / meteorTailLength(item), 0.0, 1.0);
    vec3 noisePosition = position / radius * 2.8
            + vec3(-item.wind.x * 0.08, 3.5, -item.wind.z * 0.08) * time;
    float noise = flameNoise(noisePosition + item.style.z * 37.0);
    if (item.style.w < 1.5) noise = noise * 0.7 + flameNoise(noisePosition * 2.13 - time) * 0.3;
    float head = 1.0 - smoothstep(0.40, 1.18, length(position) / radius + (noise - 0.5) * 0.38);
    float width = max(radius * 0.02, radius * pow(1.0 - progress, 0.65));
    width *= 0.78 + 0.22 * sin(progress * 17.0 - time * 6.0 + noise * 5.0);
    float distance = length(position.xz - meteorBend(item, progress)) / width;
    float tail = (1.0 - smoothstep(0.25, 1.25, distance + (noise - 0.5) * 1.4));
    tail *= (1.0 - smoothstep(0.65, 1.0, progress)) * step(position.y, 0.0);
    tail *= mix(1.0, smoothstep(0.22, 0.68, noise), progress);
    float density = max(head, tail * (1.0 - progress * 0.65));
    vec2 uv = vec2(atan(position.z, position.x) / 6.2831853,
                   clamp(position.y / (radius * 2.0) + 0.5, 0.0, 1.0));
    vec4 field = item.style.w > 1.5 ? vec4(0.75, 1, 0.65, 1) : fluidAt(uv, item);
    density *= mix(0.65, 1.2, field.b) * mix(0.65, 1.15, noise);
    heat = clamp(head * 0.8 + (1.0 - progress) * 0.35 + field.r * 0.15, 0.0, 1.0);
    return max(density, 0.0);
}
#endif
