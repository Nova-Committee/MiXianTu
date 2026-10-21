#version 330

#moj_import <minecraft:fog.glsl>
#moj_import <mxt:light_beam_draw.glsl>
#moj_import <mxt:scene_depth.glsl>

in vec3 viewPosition;
in vec2 surfaceCoord;
in vec4 vertexColor;
flat in float haloLayer;
flat in float tapered;
flat in float capSurface;
flat in float curvedSurface;
in vec3 glowCoord;
flat in vec2 glowSize;

out vec4 fragColor;

float columnGlow(vec3 rayOrigin, vec3 direction, float sceneDistance) {
    vec3 start = rayOrigin + glowCoord - viewPosition;
    float radius = glowSize.x, height = glowSize.y;
    bool inside = dot(start.xz, start.xz) < radius * radius && start.y > 0.0 && start.y < height;
    if ((haloLayer < 0.5) != inside) return 0.0;
    float from = 0.0, to = sceneDistance;
    float a = dot(direction.xz, direction.xz);
    if (a > 1.0e-12) {
        float b = dot(start.xz, direction.xz);
        float c = dot(start.xz, start.xz) - radius * radius;
        float discriminant = b * b - a * c;
        if (discriminant <= 0.0) return 0.0;
        float root = sqrt(discriminant);
        from = max(from, (-b - root) / a);
        to = min(to, (-b + root) / a);
    } else if (dot(start.xz, start.xz) >= radius * radius) return 0.0;
    if (abs(direction.y) > 1.0e-8) {
        float lower = -start.y / direction.y, upper = (height - start.y) / direction.y;
        from = max(from, min(lower, upper));
        to = min(to, max(lower, upper));
    } else if (start.y <= 0.0 || start.y >= height) return 0.0;
    if (to <= from) return 0.0;
    float stepLength = (to - from) * 0.25;
    float opticalDepth = 0.0;
    for (int i = 0; i < 4; i++) {
        vec3 p = start + direction * (from + (float(i) + 0.5) * stepLength);
        float radial = max(0.0, 1.0 - dot(p.xz, p.xz) / (radius * radius));
        float axial = smoothstep(0.0, 0.08, p.y / height) * (1.0 - smoothstep(0.92, 1.0, p.y / height));
        opticalDepth += radial * radial * axial;
    }
    return 1.0 - exp(-0.18 * opticalDepth * stepLength / radius);
}

void main() {
#ifdef MXT_SCENE_DEPTH
    MxtSceneRay sceneRay = mxt_scene_ray(BeamDrawParams.y > 0.5);
    if (!sceneRay.valid) discard;
#endif
    float fog = total_fog_value(fog_spherical_distance(viewPosition), fog_cylindrical_distance(viewPosition),
                               FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd);
    if (tapered > 1.5) {
#ifdef MXT_SCENE_DEPTH
        float glow = columnGlow(sceneRay.origin, sceneRay.direction, sceneRay.distance);
#else
        float glow = columnGlow(vec3(0), viewPosition / max(length(viewPosition), 0.00001), 1e20);
#endif
        float alpha = vertexColor.a * glow * (1.0 - fog);
        if (alpha < 0.002) discard;
        fragColor = vec4(vertexColor.rgb, alpha);
        return;
    }
    float angle = surfaceCoord.x * 6.28318530718;
    vec3 faceNormal = cross(dFdx(viewPosition), dFdy(viewPosition));
    faceNormal /= max(length(faceNormal), 0.00000001);
    bool cap = capSurface > 0.5;
    float slope = faceNormal.y / max(length(faceNormal.xz), 0.00001);
    vec3 normal = cap || curvedSurface > 0.5 ? faceNormal : normalize(vec3(cos(angle), slope, sin(angle)));
    vec3 towardCamera = -viewPosition / max(length(viewPosition), 0.00001);
    float facing = abs(dot(normal, towardCamera));
    float radial = mix(0.35 + 0.65 * facing, pow(facing, 1.4), haloLayer);
    float endFade = mix(1.0, smoothstep(0.0, 0.16, surfaceCoord.y)
            * (1.0 - smoothstep(0.84, 1.0, surfaceCoord.y)), tapered);
    float axial = abs(towardCamera.y);
    endFade = cap ? 1.0 : mix(endFade, 1.0, axial * axial * axial * axial * (1.0 - curvedSurface));
    float alpha = vertexColor.a * radial * endFade;
    alpha *= 1.0 - fog;
#ifdef MXT_SCENE_DEPTH
    float fragmentDistance = dot(viewPosition - sceneRay.origin, sceneRay.direction);
    alpha *= clamp((sceneRay.distance - fragmentDistance) / max(BeamDrawParams.z, 1e-5), 0.0, 1.0);
#endif
    if (alpha < 0.002) discard;
    fragColor = vec4(vertexColor.rgb * (0.75 + 0.25 * facing), alpha);
}
