// Selects the flame's travel direction relative to the sword surface.
// Supports upward flow and radial flow toward the sword's center.
#ifndef MXT_FLAME_FLOW
#define MXT_FLAME_FLOW
bool isRadialFlame(FlameItem item) {
    return item.style.y > 0.5;
}

vec3 flameFlowDirection(FlameItem item, vec3 surface, vec3 normal) {
    vec3 upward = worldVectorToLocal(vec3(0, 1, 0), item);
    if (!isRadialFlame(item)) return upward;

    float lengthSquared = dot(surface, surface);
    vec3 radial = lengthSquared > 1.0e-6 ? normalize(surface) : normal;
    return normalize(radial * 0.75 + normal * 0.25);
}
#endif
