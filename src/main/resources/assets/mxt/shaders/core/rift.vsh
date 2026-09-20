#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

in vec3 Position;
in vec4 Color;

out vec4 vertexColor;
out vec4 texProj0;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);

    vertexColor = Color;
    // Screen-projected position: the fragment shader samples the rift texture through this instead of a UV, so
    // the pattern always faces the viewer whatever angle the surface is seen from.
    texProj0 = projection_from_position(gl_Position);
}
