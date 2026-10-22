#version 330
#moj_import <minecraft:globals.glsl>
layout(std140) uniform RiftParameters {
    vec4 Tint;
    vec4 EdgeTint;
    vec4 Motion;
    vec4 TextureTransform;
    vec4 Effects;
    vec4 Vertices[128];
};
in vec2 localUV;
in vec3 viewPosition;
out vec4 fragColor;
float hash(vec2 p) { return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453); }
float noise(vec2 p) {
    vec2 i = floor(p), f = fract(p); f = f*f*(3.0-2.0*f);
    return mix(mix(hash(i),hash(i+vec2(1,0)),f.x),mix(hash(i+vec2(0,1)),hash(i+vec2(1)),f.x),f.y);
}
void main() {
    float t = GameTime * 1200.0 * Motion.x;
    float c = cos(TextureTransform.y), s = sin(TextureTransform.y);
    vec2 uv = mat2(c,s,-s,c) * localUV * TextureTransform.x + TextureTransform.zw;
    vec2 parallax = viewPosition.xy / max(length(viewPosition), 0.0001) * 0.16;
    float field = 0.0;
    for (int i=0;i<4;i++) {
        float layer = float(i);
        vec2 p = uv * (1.3 + layer * 0.9) + parallax * layer;
        p += vec2(sin(p.y*1.3+t*0.15), cos(p.x+t*0.11)) * (0.2 + Motion.z*0.2);
        field += noise(p + vec2(t*0.07,-t*0.11) * (1.0+layer*0.2)) / (2.0+layer);
    }
    float edge = 1e10;
    int count = int(Motion.w);
    for (int i=0;i<128;i++) {
        if(i>=count) break;
        vec2 a = Vertices[i].xy, b = Vertices[(i+1)%count].xy;
        vec2 e = b-a;
        edge = min(edge, length(localUV-a-e*clamp(dot(localUV-a,e)/dot(e,e),0.0,1.0)));
    }
    float width = max(Motion.y, 0.0001);
    float rim = (1.0-smoothstep(0.0,width,edge)) * step(0.00001,Motion.y);
    float pulse = 0.8+0.2*sin(t*0.8+uv.y*3.0+noise(uv*4.0)*Motion.z*5.0);
    vec3 interior = Tint.rgb * (0.04 + pow(clamp(field,0.0,1.0),3.0)*0.5);
    float glow = exp(-edge / max(width * 4.0, 0.05)) * Effects.x;
    fragColor = vec4(interior + EdgeTint.rgb * (rim * pulse + glow * 0.45), 1.0);
}
