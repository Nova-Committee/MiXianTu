#version 330
#moj_import <mxt:flame_data.glsl>
#moj_import <mxt:flame_surface.glsl>
#moj_import <mxt:flame_flow.glsl>
in vec2 surfaceUV;
flat in int itemIndex;
out vec4 fragColor;
void main() {
    FlameItem item = Items[itemIndex];
    float time = FrameInfo.x + item.style.z * 19.0;
    float ignition = flameNoise(vec3(surfaceUV * vec2(7, 13), time * 2.0));
    if (item.tile.y > 0.5) {
        fragColor = vec4(0.65 + ignition * 0.2, 0.9, 0.3 + ignition * 0.4, 0.9);
        return;
    }
    if (item.tile.z < 0.5) {
        fragColor = fluidAt(surfaceUV, item);
        return;
    }
    vec3 normal;
    vec3 position = swordSurface(surfaceUV, item, normal);
    vec3 tangent = vec3(normal.z, 0, -normal.x);
    vec3 flowDirection = flameFlowDirection(item, position, normal);
    float perimeter = max(0.15, 2.0 * (item.shape.y + item.shape.z));
    vec2 surfaceWind = vec2(dot(item.wind.xyz, tangent) / perimeter, item.wind.y / swordSpan(item));
    surfaceWind = clamp(surfaceWind * 0.10, vec2(-2), vec2(2));
    vec2 flow = vec2(dot(flowDirection, tangent) / perimeter,
                     flowDirection.y / swordSpan(item));
    vec2 swirl = vec2(sin(surfaceUV.y * 24.0 + time * 2.4), cos(surfaceUV.x * 19.0 - time * 3.1)) * 0.035;
    float dt = item.tile.w;
    vec2 advectedUV = surfaceUV - (surfaceWind + flow * 0.04 + swirl) * dt;
    vec2 texel = vec2(1.0 / 64.0);
    vec4 field = fluidAt(advectedUV, item);
    float lapT = fluidAt(advectedUV + vec2(texel.x,0), item).r
               + fluidAt(advectedUV - vec2(texel.x,0), item).r
               + fluidAt(advectedUV + vec2(0,texel.y), item).r
               + fluidAt(advectedUV - vec2(0,texel.y), item).r - 4.0 * field.r;
    float reaction = 4.2 * field.g * field.a * exp(-0.55 / max(field.r, 0.03));
    float temperature = field.r + dt * (3.0 * lapT + 0.9 * reaction - 1.3 * field.r
                         + ignition * 0.52);
    float fuel = field.g + dt * (1.8 * (1.0 - field.g) - reaction * 0.55);
    float oxygen = field.a + dt * (2.5 * (1.0 - field.a) - reaction * 0.42);
    float intensity = mix(field.b, clamp(reaction * 0.62, 0.0, 1.0), min(dt * 12.0, 1.0));
    vec4 updated = clamp(vec4(temperature, fuel, intensity, oxygen), 0.0, 1.0);
    // Stochastic rounding prevents sub-byte cooling/reaction from freezing in RGBA8 at high FPS.
    vec4 random = vec4(flameHash(vec3(gl_FragCoord.xy, time * 61.0)),
                       flameHash(vec3(gl_FragCoord.xy + 17.0, time * 67.0)),
                       flameHash(vec3(gl_FragCoord.xy + 31.0, time * 71.0)),
                       flameHash(vec3(gl_FragCoord.xy + 43.0, time * 73.0)));
    fragColor = floor(updated * 255.0 + random) / 255.0;
}
