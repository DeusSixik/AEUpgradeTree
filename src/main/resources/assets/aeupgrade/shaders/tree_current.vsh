#version 150

in vec4 Edge;
in vec4 FromColor;
in vec4 ToColor;
in vec3 LinkFlow;
in vec4 JoinTint;

out vec4 endpoints;
out vec4 fromTint;
out vec4 toTint;
out vec3 flow;
out vec4 joinTint;

void main() {
    endpoints = Edge;
    fromTint = FromColor;
    toTint = ToColor;
    flow = LinkFlow;
    joinTint = JoinTint;
    gl_Position = vec4(0.0, 0.0, 0.0, 1.0);
}
