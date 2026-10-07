// Shared sampling and shading helpers for the flame layers.
// Computes low-discrepancy samples, flame density, and heat/age-based tinting.
#ifndef MXT_FLAME_PROBABILITY
#define MXT_FLAME_PROBABILITY
float halton(int index, int base) {
    float result = 0.0;
    float weight = 1.0;
    for (int digit = 0; digit < 10; digit++) {
        if (index <= 0) break;
        weight /= float(base);
        result += weight * float(index % base);
        index /= base;
    }
    return result;
}
float flameProbability(float distance, float height, float drift, float pulse, float intensity) {
    float sigma = 0.20;
    return clamp(exp(-distance * distance / (2.0 * sigma * sigma))
            * exp(-height * 0.5) * exp(-drift * 0.12) * pulse * intensity, 0.0, 1.0);
}
vec3 flameTint(vec3 tint, float heat, float age) {
    vec3 hot = mix(tint, vec3(1), smoothstep(0.5, 1.0, heat) * 0.72);
    vec3 ash = tint * 0.20;
    return mix(hot, ash, smoothstep(0.45, 1.0, age));
}
#endif
