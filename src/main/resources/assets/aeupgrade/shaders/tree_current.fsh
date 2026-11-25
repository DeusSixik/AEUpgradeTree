#version 150

uniform vec2 Pulse; // Shared pulse position and spacing in map units
uniform vec3 Style; // GUI scale, opacity and half-width of the underlying line

flat in vec4 SourceColor;
flat in vec4 TargetColor;
flat in vec2 Flow; // Distances from core at both ends
flat in float span;
flat in int Wall; // 0: none, 1: powered, 2: unpowered
flat in vec4 JoinTint; // Start and end cut slopes, then the color transition interval
in vec2 point;
out vec4 fragColor;

float coverage(float distanceToEdge) {
    float pixel = max(0.18, fwidth(distanceToEdge) * 0.7);
    return 1.0 - smoothstep(-pixel, pixel, distanceToEdge);
}

float boxDistance(vec2 point, vec2 halfSize) {
    vec2 outside = abs(point) - halfSize;
    return length(max(outside, 0.0)) + min(max(outside.x, outside.y), 0.0);
}

void paint(inout vec4 result, vec3 color, float opacity) {
    float alpha = clamp(opacity, 0.0, 1.0);
    result = vec4(color * alpha, alpha) + result * (1.0 - alpha);
}

void paintPulse(inout vec4 result, vec2 point, float span, float size) {
    bvec2 joined = lessThan(abs(JoinTint.xy), vec2(4.0)); // 4 marks a capped endpoint
    float start = joined.x ? JoinTint.x * point.y : 0.0;
    float end = joined.y ? span + JoinTint.y * point.y : span;
    float along = clamp(point.x, min(start, end), max(start, end));
    float progress = clamp((along - start) / max(end - start, 0.001), 0.0, 1.0);
    float lineDistance = length(point - vec2(along, 0.0));
    vec4 tint = mix(SourceColor, TargetColor, mix(JoinTint.z, JoinTint.w, progress));
    float distanceFromCore = mix(Flow.x, Flow.y, progress);
    float behind = mod(Pulse.x - distanceFromCore + 5.0, Pulse.y) - 5.0;
    float pulse = smoothstep(-5.0, 2.0, behind) * (1.0 - smoothstep(8.0, 48.0, behind));
    float core = coverage(lineDistance - Style.z);
    float glow = 1.0 - smoothstep(Style.z, Style.z + 3.1 * size, lineDistance);
    // Both sides meet on the same bisector. Give the boundary to only one segment.
    if ((joined.x && point.x < start) || (joined.y && point.x >= end)) {
        return;
    }
    paint(result, tint.rgb, glow * pulse * 0.44 * tint.a);
    paint(result, mix(tint.rgb, vec3(0.91, 1.0, 0.95), 0.40), core * pulse * tint.a);
}

void paintWall(inout vec4 result, vec2 point, float size) {
    float height = 6.0 * size;
    vec3 amber = vec3(1.0, 0.55, 0.22);
    vec3 face = vec3(0.31, 0.20, 0.19);
    float body = coverage(boxDistance(point + vec2(1.25 * size, 0.0), vec2(1.25 * size, height)));
    float rim = coverage(boxDistance(point + vec2(0.54 * size, 0.0), vec2(0.54 * size, height)));
    paint(result, face, body);
    paint(result, amber, rim * (Wall == 1 ? 0.74 : 0.50));

    if (Wall == 1) {
        float elapsed = mod(Pulse.x - Flow.y, Pulse.y);
        float flash = 1.0 - smoothstep(1.5, 17.0, elapsed);
        float travel = min(elapsed / 12.0, 1.4);
        // The flash expands sideways along the obstacle and particles bounce back from its face.
        float front = abs(point.y) - (1.2 + 4.5 * travel) * size;
        float splash = coverage(boxDistance(vec2(point.x + size, front), vec2(1.1, 1.6) * size));
        float halo = 1.0 - smoothstep(0.0, 4.3 * size, length(vec2(point.x * 1.7, front)));
        paint(result, mix(TargetColor.rgb, amber, 0.67), halo * flash * 0.88);
        paint(result, vec3(1.0, 0.95, 0.68), splash * flash);
        for (int i = 0; i < 3; i++) {
            float side = float(i - 1);
            vec2 spark = vec2(-(2.6 + abs(side) * 1.4) * travel, side * (1.5 + 3.7 * travel)) * size;
            paint(result, amber, coverage(boxDistance(point - spark, vec2(0.66 * size))) * flash);
        }
    }
}

void main() {
    float size = Style.x;
    vec4 result = vec4(0.0);
    if (Wall != 2) {
        paintPulse(result, point, span, size);
    }
    if (Wall != 0) {
        // Keep light on the upstream side so neither the pulse nor its reflection enters the closed trace.
        float beforeWall = coverage(point.x - span);
        paintWall(result, point - vec2(span, 0.0), size);
        result *= beforeWall;
    }
    fragColor = vec4(result.rgb / max(result.a, 0.001), result.a * Style.y);
}
