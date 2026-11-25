#version 150

layout(points) in;
layout(triangle_strip, max_vertices = 4) out;

uniform mat4 Projection;
uniform vec3 Style;

in vec4 endpoints[];
in vec4 fromTint[];
in vec4 toTint[];
in vec3 flow[];
in vec4 joinTint[];

flat out vec4 SourceColor;
flat out vec4 TargetColor;
flat out vec2 Flow;
flat out float span;
flat out int Wall;
flat out vec4 JoinTint;
out vec2 point;

void corner(vec2 origin, vec2 axis, float length, vec2 local) {
    vec2 normal = vec2(-axis.y, axis.x);
    vec2 guiPoint = origin + local.x * axis + local.y * normal;
    gl_Position = Projection * vec4(guiPoint, 0.0, 1.0);
    point = local;
    span = length;
    SourceColor = fromTint[0];
    TargetColor = toTint[0];
    Flow = flow[0].xy;
    Wall = int(flow[0].z);
    JoinTint = joinTint[0];
    EmitVertex();
}

void main() {
    vec2 origin = endpoints[0].xy;
    vec2 edge = endpoints[0].zw - origin;
    float lengthGui = length(edge);
    vec2 axis = edge / lengthGui;
    float padding = 10.0 * Style.x + 2.0;
    vec2 slopes = abs(joinTint[0].xy);
    vec2 capPadding = vec2(slopes.x < 4.0 ? slopes.x : 1.0, slopes.y < 4.0 ? slopes.y : 1.0) * padding;
    corner(origin, axis, lengthGui, vec2(-capPadding.x, -padding));
    corner(origin, axis, lengthGui, vec2(-capPadding.x, padding));
    corner(origin, axis, lengthGui, vec2(lengthGui + capPadding.y, -padding));
    corner(origin, axis, lengthGui, vec2(lengthGui + capPadding.y, padding));
    EndPrimitive();
}
