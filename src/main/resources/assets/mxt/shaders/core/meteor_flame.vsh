#version 330
#moj_import <minecraft:projection.glsl>
#moj_import <mxt:flame_data.glsl>
#moj_import <mxt:flame_meteor.glsl>
in vec3 Position;
out vec3 viewRay;
out vec2 screenUV;
flat out int itemIndex;
flat out float screenCoverage;
float nearDistance(vec4 projected) {
    return RenderInfo.y > 0.5 ? projected.z : projected.z + projected.w;
}
void includeProjection(vec4 projected, inout vec2 minimum, inout vec2 maximum) {
    vec2 ndc = projected.xy / projected.w;
    minimum = min(minimum, ndc);
    maximum = max(maximum, ndc);
}
void main() {
    itemIndex = gl_InstanceID;
    FlameItem item = Items[itemIndex];
    vec3 lower = meteorBoundsMin(item);
    vec3 upper = meteorBoundsMax(item);
    vec2 minimum = vec2(1e6);
    vec2 maximum = vec2(-1e6);
    vec4 corners[8];
    for (int corner = 0; corner < 8; corner++) {
        vec3 position = vec3((corner & 1) == 0 ? lower.x : upper.x,
                             (corner & 2) == 0 ? lower.y : upper.y,
                             (corner & 4) == 0 ? lower.z : upper.z);
        corners[corner] = ProjMat * ViewRotation * vec4(localToWorld(position, item), 1);
        if (nearDistance(corners[corner]) >= 0.0) includeProjection(corners[corner], minimum, maximum);
    }
    // Clip the twelve box edges instead of expanding the quad to the entire viewport.
    for (int corner = 0; corner < 8; corner++) {
        for (int axis = 0; axis < 3; axis++) {
            int bit = 1 << axis;
            if ((corner & bit) != 0) continue;
            vec4 a = corners[corner];
            vec4 b = corners[corner | bit];
            float da = nearDistance(a);
            float db = nearDistance(b);
            if ((da >= 0.0) != (db >= 0.0)) includeProjection(mix(a, b, da / (da - db)), minimum, maximum);
        }
    }
    if (any(greaterThan(minimum, maximum))) minimum = maximum = vec2(2);
    minimum = clamp(minimum, vec2(-1), vec2(1));
    maximum = clamp(maximum, vec2(-1), vec2(1));
    vec2 extent = maximum - minimum;
    screenCoverage = extent.x * extent.y * 0.25;
    vec2 screen = mix(minimum, maximum, Position.xy);
    gl_Position = vec4(screen, 0, 1);
    screenUV = screen * 0.5 + 0.5;
    viewRay = (inverse(ProjMat) * vec4(screen, 1, 1)).xyz;
}
