#version 330
#moj_import <minecraft:projection.glsl>
#moj_import <mxt:flame_data.glsl>
#moj_import <mxt:flame_surface.glsl>
#moj_import <mxt:flame_probability.glsl>
#moj_import <mxt:flame_depth.glsl>
#moj_import <mxt:flame_flow.glsl>
in vec2 surfaceUV;
flat in int itemIndex;
out vec4 fragColor;
void main() {
    FlameItem item = Items[itemIndex];
    vec4 field = fluidAt(surfaceUV, item);
    float time = item.centerAge.w;
    vec2 uv = surfaceUV * vec2(9, 16);
    if (!isRadialFlame(item)) {
        uv += vec2(sin(uv.y + time * 3.0) * 0.6, -time * 5.0);
    } else {
        vec3 normal;
        vec3 surface = swordSurface(surfaceUV, item, normal);
        vec3 flowDirection = flameFlowDirection(item, surface, normal);
        vec3 tangent = vec3(normal.z, 0, -normal.x);
        float perimeter = max(0.15, 2.0 * (item.shape.y + item.shape.z));
        vec2 flowUV = vec2(dot(flowDirection, tangent) / perimeter,
                           flowDirection.y / swordSpan(item));
        uv -= flowUV * time * 5.0;
        uv += vec2(sin(uv.y + time * 3.0) * 0.6, cos(uv.x - time * 2.0) * 0.35);
    }
    float noise = flameNoise(vec3(uv, time * 0.7 + item.style.z * 17.0));
    float tongues = smoothstep(0.27, 0.72, noise + field.b * 0.15);
    float alpha = tongues * field.b * item.tint.a * RenderInfo.z * FrameInfo.z * softFlame();
    fragColor = vec4(flameTint(item.tint.rgb, field.r, 0.2), clamp(alpha, 0.0, 0.65));
}
