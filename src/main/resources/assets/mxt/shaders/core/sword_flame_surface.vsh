#version 330
#moj_import <minecraft:projection.glsl>
#moj_import <mxt:flame_data.glsl>
#moj_import <mxt:flame_surface.glsl>
in vec3 Position;
out vec2 surfaceUV;
flat out int itemIndex;
void main() {
    itemIndex = gl_InstanceID;
    FlameItem item = Items[itemIndex];
    float y = surfaceY(Position.y, item);
    surfaceUV = vec2(Position.x, (y - swordBottom(item) + 0.035) / swordSpan(item));
    vec3 normal;
    vec3 position = swordSurface(surfaceUV, item, normal);
    vec4 field = fluidAt(surfaceUV, item);
    float time = item.centerAge.w;
    float flutter = flameNoise(vec3(surfaceUV * vec2(10,18), time * 4.0 + item.style.z * 21.0));
    float extrusion = 0.012 + (0.018 + 0.065 * flutter) * field.b;
    position += normal * extrusion;
    position += item.wind.xyz * 0.004 * field.b * flutter;
    gl_Position = ProjMat * ViewRotation * vec4(localToWorld(position, item), 1);
}
