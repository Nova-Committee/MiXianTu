#version 330
#moj_import <minecraft:projection.glsl>
#moj_import <mxt:flame_data.glsl>
#moj_import <mxt:flame_depth.glsl>
in vec2 particleUV;
in vec4 particleColor;
in float particleAge;
flat in float particleLOD;
flat in float particlePhase;
out vec4 fragColor;
void main() {
    vec2 uv = particleUV;
    float time = FrameInfo.x;
    float noise = particleLOD > 1.5 ? 0.55 : flameNoise(vec3(uv * vec2(5,7)
            + vec2(particlePhase, -time * 4.0 - particleAge * 2.0), time * 0.5 + particlePhase));
    uv.x += sin(uv.y * 8.0 - time * 5.0) * 0.065 + (noise - 0.5) * 0.16;
    float width = mix(0.48, 0.06, smoothstep(0.15, 1.0, uv.y));
    float shape = 1.0 - smoothstep(width * 0.35, width, abs(uv.x - 0.5));
    shape *= smoothstep(0.0, 0.16, uv.y) * (1.0 - smoothstep(0.65, 1.0, uv.y));
    float tear = particleLOD > 1.5 ? 1.0 : smoothstep(0.15, 0.65, noise + (1.0 - uv.y) * 0.3);
    float alpha = particleColor.a * shape * tear * softFlame();
    if (alpha < 0.002) discard;
    fragColor = vec4(particleColor.rgb, min(alpha, 0.65));
}
