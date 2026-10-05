#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:globals.glsl>

uniform sampler2D Sampler0;

in vec4 vertexColor;
in vec3 localPosition;
out vec4 fragColor;

void main() {
    float coverage = 0.0;
    vec3 energy = vec3(0.0);
    for (int i = 0; i < 4; i++) {
        float layer = float(i);
        vec2 uv = vec2(
                localPosition.y * (0.19 + layer * 0.035) + localPosition.x * (1.80 + layer * 0.17),
                localPosition.y * (0.47 + layer * 0.060) + localPosition.z * (2.00 + layer * 0.13));
        uv += vec2(GameTime * (0.09 + layer * 0.035), -GameTime * (0.16 + layer * 0.045));
        uv += vec2(layer * 0.21, layer * 0.37);
        vec4 sample0 = texture(Sampler0, fract(uv));
        float luminance = dot(sample0.rgb, vec3(0.299, 0.587, 0.114));
        float line = smoothstep(0.22, 0.82, sample0.a)
                * smoothstep(0.70 + layer * 0.015, 0.97, luminance);
        float weight = 0.36 - layer * 0.055;
        coverage += line * weight;
        energy += sample0.rgb * line * weight;
    }

    float radial = length(localPosition.xz);
    float centerFade = smoothstep(0.05, 0.28, radial);
    coverage = clamp(coverage, 0.0, 1.0) * mix(0.12, 1.0, centerFade);
    float pulse = 0.84 + 0.16 * sin(GameTime * 4.2 + localPosition.y * 4.0);
    float highlight = smoothstep(0.32, 0.86, coverage);
    vec3 tint = mix(vertexColor.rgb, vec3(1.0, 0.90, 0.98), highlight * 0.58);
    vec3 brightness = clamp(vec3(0.70) + energy * 1.35, 0.0, 1.40);
    float alpha = coverage * vertexColor.a * (0.16 + highlight * 0.84) * pulse;
    vec4 color = vec4(tint * brightness, alpha) * ColorModulator;
    if (color.a < 0.025) discard;
    fragColor = color;
}
