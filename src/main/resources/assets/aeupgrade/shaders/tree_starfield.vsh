#version 150

in vec3 Position;

uniform vec2 ScreenSize;
uniform vec4 SquareVertex;
uniform vec4 WorldRect;
uniform vec4 Map; // World-space map origin and inverse size

out vec2 worldPoint;
out vec2 lightUv;

void main() {
    vec2 corner = Position.xy * vec2(0.5, -0.5) + 0.5;
    vec2 guiPoint = mix(SquareVertex.xy, SquareVertex.zw, corner);
    worldPoint = mix(WorldRect.xy, WorldRect.zw, corner);
    lightUv = (worldPoint - Map.xy) * Map.zw;
    gl_Position = vec4(guiPoint / ScreenSize * vec2(2.0, -2.0) + vec2(-1.0, 1.0), 0.0, 1.0);
}
