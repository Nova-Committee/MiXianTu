#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>
#moj_import <mxt:light_beam_draw.glsl>
#define MXT_DEPTH_VERTEX
#moj_import <mxt:scene_depth.glsl>

struct Beam {
    vec4 origin;
    vec4 path;
    vec4 shape;
    vec4 ends;
    vec4 tint;
    vec4 style;
};
layout(std140) uniform LightBeamInstances {
    Beam Beams[64];
};

in vec3 Position;
in vec2 UV0;

out vec3 viewPosition;
out vec2 surfaceCoord;
out vec4 vertexColor;
flat out float haloLayer;
flat out float tapered;
flat out float capSurface;
flat out float curvedSurface;
out vec3 glowCoord;
flat out vec2 glowSize;

float curveRing(float ring, float lod) {
    if (lod < 0.5) return ring;
    if (lod < 1.5) {
        const float rings[9] = float[9](0, 1, 4, 7, 8, 9, 12, 15, 16);
        return rings[int(ring)];
    }
    const float rings[6] = float[6](0, 1, 7, 9, 15, 16);
    return rings[int(ring)];
}

float curvedPosition(Beam beam, float ring) {
    float b = beam.ends.w;
    float h = beam.origin.w;
    float first = beam.ends.x > 0.5 ? b * (1.0 - 1.57079632679) : 0.0;
    float second = beam.ends.x > 0.5 ? b : 0.0;
    float third = beam.ends.y > 0.5 ? h - b : h;
    float fourth = beam.ends.y > 0.5 ? h + b * (1.57079632679 - 1.0) : h;
    vec4 knots = clamp(vec4(first, second, third, fourth), beam.style.z, beam.style.w);
    if (ring <= 1.0) return mix(beam.style.z, knots.x, ring);
    if (ring <= 7.0) return mix(knots.x, knots.y, (ring - 1.0) / 6.0);
    if (ring <= 9.0) return mix(knots.y, knots.z, (ring - 7.0) / 2.0);
    if (ring <= 15.0) return mix(knots.z, knots.w, (ring - 9.0) / 6.0);
    return beam.style.w;
}

float smoothRange(float from, float to, float value) {
    float t = clamp((value - from) / (to - from), 0.0, 1.0);
    return t * t * (3.0 - 2.0 * t);
}

void main() {
    int instance = gl_InstanceID;
#ifdef MXT_SCENE_DEPTH
    instance += int(BeamDrawParams.x);
    mxt_depth_vertex(ProjMat * ModelViewMat);
#endif
    Beam beam = Beams[instance];
    float lod = floor(UV0.x / 4.0);
    float flags = UV0.x - lod * 4.0;
    haloLayer = mod(flags, 2.0);
    capSurface = floor(flags / 2.0);
    tapered = beam.shape.z;
    bool glow = tapered > 1.5;
    bool spread = !glow && tapered > 0.5;
    bool curved = (beam.ends.x > 0.5 && beam.path.x < beam.ends.w)
            || (beam.ends.y > 0.5 && beam.path.x + beam.path.y > beam.origin.w - beam.ends.w);
    curvedSurface = curved ? 1.0 : 0.0;
    float y;
    if (capSurface > 0.5) {
        y = Position.z > 0.5 ? beam.style.w : beam.style.z;
    } else if (curved) {
        y = curvedPosition(beam, curveRing(Position.z, lod));
    } else {
        const float pointed[5] = float[5](0, 0.2, 0.5, 0.8, 1);
        int ring = int(min(Position.z, 4.0));
        float ringProgress = spread ? pointed[ring] : float(ring) * 0.25;
        y = clamp(beam.path.x + beam.path.y * ringProgress, beam.style.z, beam.style.w);
    }
    float progress = clamp((y - beam.path.x) / beam.path.y, 0.0, 1.0);
    vec3 center = vec3(0, y, 0);
    float sine = 0.0, cosine = 1.0;
    bool bottom = beam.ends.x > 0.5 && y < beam.ends.w;
    bool top = beam.ends.y > 0.5 && y > beam.origin.w - beam.ends.w;
    if (bottom || top) {
        float along = bottom ? beam.ends.w - y : y - beam.origin.w + beam.ends.w;
        float angle = min(along / beam.ends.w, 1.57079632679);
        sine = sin(angle);
        cosine = cos(angle);
        float outward = beam.ends.w * (1.0 - cosine) + max(0.0, along - beam.ends.w * 1.57079632679);
        float planeDistance = beam.ends.w * (1.0 - sine);
        center = vec3(beam.shape.x * outward, bottom ? planeDistance : beam.origin.w - planeDistance, beam.shape.y * outward);
        if (bottom) sine = -sine;
    }
    float radius = mix(beam.path.z, beam.path.w, haloLayer);
    if (spread) radius *= capSurface > 0.5 ? 0.0 : 4.0 * progress * (1.0 - progress);
    vec2 local = Position.xy * radius;
    if (glow && haloLayer < 0.5) local.x = -local.x;
    float projected = dot(beam.shape.xy, local);
    viewPosition = beam.origin.xyz + center + vec3(local.x + (cosine - 1.0) * beam.shape.x * projected,
            -sine * projected, local.y + (cosine - 1.0) * beam.shape.y * projected);
    gl_Position = ProjMat * ModelViewMat * vec4(viewPosition, 1.0);
    glowCoord = vec3(local.x, y, local.y);
    glowSize = vec2(beam.path.z, beam.origin.w);
    surfaceCoord = vec2(UV0.y, progress);
    float extension = beam.ends.z + (1.57079632679 - 2.0) * beam.ends.w;
    float fadeRange = min(beam.origin.w * 0.08, beam.shape.w * 2.0);
    bool plain = capSurface > 0.5 || tapered < 0.5;
    float lowerFade = beam.ends.x > 0.5 ? smoothRange(-extension, -extension + beam.ends.z * 0.3, y)
            : plain ? 1.0 : clamp(y / fadeRange, 0.0, 1.0);
    float upperFade = beam.ends.y > 0.5 ? 1.0 - smoothRange(beam.origin.w + extension - beam.ends.z * 0.3, beam.origin.w + extension, y)
            : plain ? 1.0 : clamp((beam.origin.w - y) / fadeRange, 0.0, 1.0);
    float strength = mix(0.85 - 0.45 * beam.style.y, 0.12 + 0.16 * beam.style.y, haloLayer);
    float alpha = floor(beam.tint.a * 255.0 * beam.style.x * lowerFade * upperFade * strength + 0.00001) / 255.0;
    vertexColor = vec4(beam.tint.rgb, glow ? beam.tint.a * beam.style.x : alpha) * ColorModulator;
}
