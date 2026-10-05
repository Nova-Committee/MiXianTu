#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:globals.glsl>
#moj_import <minecraft:projection.glsl>

in vec3 Position;
in vec4 Color;

out vec4 vertexColor;
out vec3 localPosition;

void main() {
    // The renderer rotates local +Y onto the entity velocity, so the axial wave follows flight direction.
    float phase = Position.y * 6.4 + GameTime * 2.6;
    float radial = min(length(Position.xz) * 2.0, 1.0);
    float flowAlongAxis = sin(Position.y * 4.8 + GameTime * 2.0);
    float gust = sin(phase + Position.x * 9.0) * 0.052
            + cos(phase * 0.63 + Position.z * 11.0) * 0.034;
    float twist = cos(phase * 0.77 + Position.z * 8.0) * 0.044
            + sin(phase * 0.51 + Position.x * 13.0) * 0.029;
    vec3 animatedPosition = Position;
    float flowStrength = 0.72 + 0.35 * flowAlongAxis;
    animatedPosition.x += gust * (0.55 + radial) * flowStrength;
    animatedPosition.z += twist * (0.55 + radial) * flowStrength;
    animatedPosition.y += sin(phase * 0.45 + Position.x * 4.0) * 0.022 * (0.35 + radial)
            + flowAlongAxis * 0.014;

    gl_Position = ProjMat * ModelViewMat * vec4(animatedPosition, 1.0);
    vertexColor = Color;
    localPosition = Position;
}
