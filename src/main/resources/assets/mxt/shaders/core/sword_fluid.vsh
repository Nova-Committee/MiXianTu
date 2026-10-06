#version 330
#moj_import <mxt:flame_data.glsl>
in vec3 Position;
out vec2 surfaceUV;
flat out int itemIndex;
void main() {
    itemIndex = gl_InstanceID;
    surfaceUV = Position.xy;
    float slot = Items[itemIndex].tile.x;
    vec2 tile = vec2(mod(slot, 32.0), floor(slot / 32.0));
    gl_Position = vec4((tile + Position.xy) / 32.0 * 2.0 - 1.0, 0, 1);
}
