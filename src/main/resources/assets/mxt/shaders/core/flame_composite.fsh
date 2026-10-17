#version 330
#moj_import <minecraft:projection.glsl>
#moj_import <mxt:flame_frame.glsl>
#moj_import <mxt:flame_depth.glsl>
uniform sampler2D FlameColor;
in vec2 screenUV;
out vec4 fragColor;
void main() {
    ivec2 size = textureSize(FlameColor, 0);
    vec2 texel = screenUV * vec2(size) - 0.5;
    ivec2 base = ivec2(floor(texel));
    vec2 fraction = fract(texel);
    vec3 samples[4];
    vec3 sum = vec3(0);
    for (int index = 0; index < 4; index++) {
        ivec2 pixel = clamp(base + ivec2(index & 1, index >> 1), ivec2(0), size - 1);
        samples[index] = texelFetch(FlameColor, pixel, 0).rgb;
        sum += samples[index];
    }
    if (dot(sum, vec3(1)) <= 0.0) discard;
    float scene = viewDepth(texture(SceneDepth, screenUV).r);
    vec3 color = vec3(0);
    float total = 0.0;
    // Bilateral bilinear filtering prevents low-resolution fire from bleeding across scene silhouettes.
    for (int y = 0; y < 2; y++) {
        for (int x = 0; x < 2; x++) {
            ivec2 pixel = clamp(base + ivec2(x, y), ivec2(0), size - 1);
            vec2 uv = (vec2(pixel) + 0.5) / vec2(size);
            float neighbor = viewDepth(texture(SceneDepth, uv).r);
            vec2 weights = mix(vec2(1) - fraction, fraction, vec2(x, y));
            // FrameInfo.w bounds every flame; depth edges behind them cannot occlude this contribution.
            bool background = FrameInfo.w > 0.0 && min(neighbor, scene) > FrameInfo.w;
            float edgeWeight = background ? 1.0 : 1.0 - smoothstep(0.5, 1.0,
                    abs(neighbor - scene) / max(0.02, scene * 0.01));
            float weight = weights.x * weights.y * edgeWeight;
            color += samples[y * 2 + x] * weight;
            total += weight;
        }
    }
    fragColor = vec4(color / max(total, 0.0001), 0);
}
