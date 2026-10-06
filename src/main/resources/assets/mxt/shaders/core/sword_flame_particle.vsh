#version 330
#moj_import <minecraft:projection.glsl>
#moj_import <mxt:flame_data.glsl>
#moj_import <mxt:flame_surface.glsl>
#moj_import <mxt:flame_probability.glsl>
#moj_import <mxt:flame_flow.glsl>
in vec3 Position;
out vec2 particleUV;
out vec4 particleColor;
out float particleAge;
flat out float particleLOD;
flat out float particlePhase;
void main() {
    FlameItem item = Items[gl_InstanceID];
    int index = int(Position.z) + 1;
    float seed = item.style.z;
    float time = item.centerAge.w;
    float lifetime = mix(0.45, 0.85, halton(index, 7));
    float clock = time / lifetime + halton(index, 11) + seed * 5.0;
    float age = fract(clock);
    float cycle = floor(clock);
    vec2 shift = vec2(flameHash(vec3(seed * 317.0, cycle, 1)), flameHash(vec3(seed * 317.0, cycle, 2)));
    vec2 uv = fract(vec2(halton(index, 2), halton(index, 3)) + shift);
    vec3 normal;
    vec3 surface = swordSurface(uv, item, normal);
    bool distant = item.style.w > 1.5;
    vec4 field = distant ? vec4(0.75, 1, 0.65, 1) : fluidAt(uv, item);
    float sampleDistance = halton(index, 5) * 0.22;
    float elapsed = age * lifetime;
    vec3 flowDirection = flameFlowDirection(item, surface, normal);
    float travel = elapsed * (0.18 + elapsed) * 0.32;
    vec3 drift = item.wind.xyz * travel;
    vec3 curl = vec3(sin(time * 5.0 + index * 2.1), cos(time * 4.1 + index),
                     sin(time * 4.7 + index * 1.7)) * elapsed * 0.07;
    vec3 position = surface + normal * (0.012 + sampleDistance + elapsed * 0.14)
                   + flowDirection * elapsed * elapsed * 0.85 + drift + curl;
    float pulse = 0.85 + 0.15 * sin(time * 7.0 + uv.y * 14.0 + seed * 19.0);
    float density = flameProbability(sampleDistance, elapsed * 0.8, length(drift), pulse, field.b);
    float threshold = fract(halton(index, 13) + flameHash(vec3(seed, cycle, 9)));
    float accepted = smoothstep(threshold - 0.14, threshold + 0.14, density);
    float fade = smoothstep(0.0, 0.10, age) * (1.0 - smoothstep(0.55, 1.0, age));
    float count = ParticleCounts[int(item.style.w)];
    float compensation = sqrt(200.0 / max(count, 1.0));
    float size = (0.055 + 0.12 * field.b) * (0.5 + 0.5 * sin(age * 3.14159)) * compensation;
    size *= length(item.basisX.xyz);
    vec3 flow = localVectorToWorld(item.wind.xyz * 0.22 + flowDirection * 1.2 + normal * 0.3, item);
    vec2 flowScreen = vec2(dot(flow, CameraRight.xyz), dot(flow, CameraUp.xyz));
    vec2 along = length(flowScreen) > 0.001 ? normalize(flowScreen) : vec2(0, 1);
    vec2 across = vec2(along.y, -along.x);
    float stretch = clamp(1.5 + length(flowScreen) * 0.25, 1.5, 3.5);
    vec2 offset = (across * Position.x + along * Position.y * stretch) * size;
    vec3 world = localToWorld(position, item) + CameraRight.xyz * offset.x + CameraUp.xyz * offset.y;
    gl_Position = ProjMat * ViewRotation * vec4(world, 1);
    particleUV = Position.xy * 0.5 + 0.5;
    particleAge = age;
    particleLOD = item.style.w;
    particlePhase = seed * 53.0 + float(index) * 0.618;
    particleColor = vec4(flameTint(item.tint.rgb, field.r, age),
                         accepted * fade * item.tint.a * FrameInfo.z * 0.26);
}
