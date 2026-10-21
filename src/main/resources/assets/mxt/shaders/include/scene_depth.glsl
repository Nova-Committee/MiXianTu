#ifdef MXT_SCENE_DEPTH

#ifdef MXT_DEPTH_VERTEX
flat out mat4 mxtClipToRelative;

void mxt_depth_vertex(mat4 clipFromRelative) {
    mxtClipToRelative = inverse(clipFromRelative);
}
#else
flat in mat4 mxtClipToRelative;
uniform sampler2D SceneDepth;

struct MxtSceneRay {
    vec3 origin;
    vec3 direction;
    float distance;
    bool valid;
};

bool mxt_finite(vec4 value) {
    return !any(isnan(value)) && !any(isinf(value));
}

MxtSceneRay mxt_scene_ray(bool zeroToOne) {
    vec2 uv = gl_FragCoord.xy / vec2(textureSize(SceneDepth, 0));
    float depth = texture(SceneDepth, uv).r;
    vec2 xy = uv * 2.0 - 1.0;
    float nearZ = zeroToOne ? 0.0 : -1.0;
    vec4 nearPoint = mxtClipToRelative * vec4(xy, nearZ, 1.0);
    vec4 middlePoint = mxtClipToRelative * vec4(xy, mix(nearZ, 1.0, 0.5), 1.0);
    if (!mxt_finite(nearPoint) || !mxt_finite(middlePoint) || abs(nearPoint.w) < 1e-8 || abs(middlePoint.w) < 1e-8)
        return MxtSceneRay(vec3(0), vec3(0, 0, 1), 0.0, false);
    vec3 origin = nearPoint.xyz / nearPoint.w;
    vec3 direction = middlePoint.xyz / middlePoint.w - origin;
    float lengthSquared = dot(direction, direction);
    if (isnan(lengthSquared) || isinf(lengthSquared) || lengthSquared < 1e-16)
        return MxtSceneRay(origin, vec3(0, 0, 1), 0.0, false);
    direction *= inversesqrt(lengthSquared);
    if (depth >= 1.0) return MxtSceneRay(origin, direction, 1e20, true);
    vec4 point = mxtClipToRelative * vec4(xy, zeroToOne ? depth : depth * 2.0 - 1.0, 1.0);
    if (!mxt_finite(point) || abs(point.w) < 1e-8)
        return MxtSceneRay(origin, direction, 0.0, false);
    float distance = dot(point.xyz / point.w - origin, direction);
    return MxtSceneRay(origin, direction, max(distance, 0.0), !isnan(distance) && !isinf(distance));
}
#endif

#endif
