#version 330
layout(std140) uniform RiftParameters {
    vec4 Tint; vec4 EdgeTint; vec4 Motion; vec4 TextureTransform; vec4 Effects; vec4 Vertices[128];
};
in vec2 localUV;
in vec3 viewPosition;
out vec4 fragColor;
void main() { fragColor = vec4(Tint.rgb * 0.18, 1.0); }
