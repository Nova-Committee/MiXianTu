#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:globals.glsl>
#moj_import <minecraft:matrix.glsl>

uniform sampler2D Sampler0;

in vec4 vertexColor;
in vec4 texProj0;

// One weight per sample of the texture, in the order they are accumulated. Deliberately dark and slightly blue:
// the colour of a rift comes from its vertex colour, these only decide how bright each layer of the pattern is.
const vec3[] LAYER_WEIGHTS = vec3[](
    vec3(0.050, 0.070, 0.100),
    vec3(0.060, 0.090, 0.130),
    vec3(0.070, 0.100, 0.150),
    vec3(0.080, 0.110, 0.160),
    vec3(0.090, 0.120, 0.170),
    vec3(0.100, 0.130, 0.180),
    vec3(0.110, 0.140, 0.190),
    vec3(0.120, 0.150, 0.200),
    vec3(0.130, 0.160, 0.210)
);

// Maps the projected position onto texture space: the projection lands in [-1, 1] and the texture wants [0, 1].
const mat4 SCALE_TRANSLATE = mat4(
    0.5, 0.0, 0.0, 0.25,
    0.0, 0.5, 0.0, 0.25,
    0.0, 0.0, 1.0, 0.0,
    0.0, 0.0, 0.0, 1.0
);

// One layer of the pattern: every layer is shifted, rotated and scaled differently, and the vertical shift rides
// on game time so the layers drift at different speeds. Stacking those samples is what makes the surface move.
mat4 rift_layer(float layer) {
    mat4 translate = mat4(
        1.0, 0.0, 0.0, 3.0 / layer,
        0.0, 1.0, 0.0, (1.0 + layer * 0.5) * GameTime * 0.35,
        0.0, 0.0, 1.0, 0.0,
        0.0, 0.0, 0.0, 1.0
    );
    mat2 rotate = mat2_rotate_z(radians((layer * layer * 8642.0 + layer * 37.0) * 2.0));
    mat2 scale = mat2((5.0 + layer * 1.5) * 2.0);
    return mat4(scale * rotate) * translate * SCALE_TRANSLATE;
}

out vec4 fragColor;

void main() {
    vec4 base = textureProj(Sampler0, texProj0);
    vec3 pattern = base.rgb * LAYER_WEIGHTS[0];
    float coverage = base.a;
    for (int i = 0; i < 8; i++) {
        vec4 sample0 = textureProj(Sampler0, texProj0 * rift_layer(float(i + 1)));
        pattern += sample0.rgb * LAYER_WEIGHTS[i + 1];
        coverage = max(coverage, sample0.a);
    }
    vec4 color = vec4(pattern, coverage) * vertexColor * ColorModulator;
    if (color.a < 0.02) {
        discard;
    }
    fragColor = color;
}
