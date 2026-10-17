#version 330
#moj_import <minecraft:projection.glsl>
#moj_import <mxt:flame_data.glsl>
#moj_import <mxt:flame_meteor.glsl>
in vec3 Position;
out vec3 viewRay;
flat out int itemIndex;
void main() {
    itemIndex = gl_InstanceID;
    FlameItem item = Items[itemIndex];
    vec3 lower = meteorBoundsMin(item);
    vec3 upper = meteorBoundsMax(item);
    vec2 minimum = vec2(1e6);
    vec2 maximum = vec2(-1e6);
    bool crossesEye = false;
    for (int corner = 0; corner < 8; corner++) {
        vec3 position = vec3((corner & 1) == 0 ? lower.x : upper.x,
                             (corner & 2) == 0 ? lower.y : upper.y,
                             (corner & 4) == 0 ? lower.z : upper.z);
        vec4 projected = ProjMat * ViewRotation * vec4(localToWorld(position, item), 1);
        crossesEye = crossesEye || projected.w <= 0.05;
        vec2 ndc = projected.xy / max(projected.w, 0.05);
        minimum = min(minimum, ndc);
        maximum = max(maximum, ndc);
    }
    // Cover the viewport when the camera enters the bounds; rays still reject empty pixels.
    minimum = crossesEye ? vec2(-1) : clamp(minimum, vec2(-1), vec2(1));
    maximum = crossesEye ? vec2(1) : clamp(maximum, vec2(-1), vec2(1));
    vec2 screen = mix(minimum, maximum, Position.xy);
    gl_Position = vec4(screen, 0, 1);
    viewRay = (inverse(ProjMat) * vec4(screen, 1, 1)).xyz;
}
