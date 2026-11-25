#version 150

uniform sampler2D SourceTexture;
uniform vec2 DrawParams; // Camera zoom and opacity
uniform float Time;

in vec2 worldPoint;
in vec2 lightUv;
out vec4 fragColor;

// ---------------------------------------------------------------- hashing / noise

float hash12(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

vec3 hash32(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * vec3(0.1031, 0.1030, 0.0973));
    p3 += dot(p3, p3.yxz + 33.33);
    return fract((p3.xxy + p3.yzz) * p3.zyx);
}

float valueNoise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    float a = hash12(i);
    float b = hash12(i + vec2(1.0, 0.0));
    float c = hash12(i + vec2(0.0, 1.0));
    float d = hash12(i + vec2(1.0, 1.0));
    return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
}

float fbm(vec2 p) {
    const mat2 ROT = mat2(0.8, -0.6, 0.6, 0.8);
    float sum = 0.0;
    float amp = 0.5;
    for (int i = 0; i < 4; i++) {
        sum += amp * valueNoise(p);
        p = ROT * p * 2.03 + 17.1;
        amp *= 0.5;
    }
    return sum;
}

// Cool blue -> white -> warm orange.
vec3 starTemperature(float t) {
    const vec3 COOL  = vec3(0.62, 0.76, 1.00);
    const vec3 WHITE = vec3(0.96, 0.97, 1.00);
    const vec3 WARM  = vec3(1.00, 0.82, 0.62);
    return t < 0.5 ? mix(COOL, WHITE, t * 2.0) : mix(WHITE, WARM, t * 2.0 - 1.0);
}

// ---------------------------------------------------------------- stars

// One star per cell, center kept far enough from the cell edge that nothing gets clipped.
// Returns premultiplied rgb + coverage.
vec4 starLayer(vec2 p, float cellSize, float aa, float density, vec2 sizeRange,
               float flare, float reach, float salt, vec3 tint) {
    vec2 cells = p / cellSize;
    vec2 cell = floor(cells) + salt;
    vec3 h = hash32(cell);
    if (h.z > density) {
        return vec4(0.0);
    }
    float seed = hash12(cell + 3.7);

    float margin = min(reach / cellSize, 0.45);
    vec2 center = mix(vec2(margin), vec2(1.0 - margin), h.xy);
    vec2 d = (fract(cells) - center) * cellSize;
    float r = length(d);

    float size = mix(sizeRange.x, sizeRange.y, seed * seed);
    float core = 1.0 - smoothstep(max(0.0, size - aa), size + aa, r);
    float glow = exp(-r * r / (size * size * 2.5)) * (1.0 - smoothstep(reach * 0.7, reach, r));

    // Diffraction spikes only on the brightest stars of the layer.
    float spikes = 0.0;
    float spikeAmount = flare * smoothstep(0.82, 1.0, seed);
    if (spikeAmount > 0.0) {
        float len = min(size * 9.0, reach);
        float w = 0.16 + aa * 0.5;
        vec2 a = abs(d);
        float axial = exp(-pow(min(a.x, a.y) / w, 2.0))
                * pow(max(0.0, 1.0 - max(a.x, a.y) / len), 2.0);
        vec2 dd = abs(vec2(d.x + d.y, d.x - d.y)) * 0.70710678;
        float diag = exp(-pow(min(dd.x, dd.y) / w, 2.0))
                * pow(max(0.0, 1.0 - max(dd.x, dd.y) / (len * 0.45)), 2.0);
        spikes = (axial + diag * 0.35) * spikeAmount;
    }

    float speed = 0.7 + h.y * 1.6;
    float phase = seed * 37.0 + h.x * 11.0;
    float wave = 0.5 * sin(Time * speed + phase) + 0.5 * sin(Time * speed * 2.31 + phase * 1.7);
    float twinkle = 0.8 + 0.2 * wave;

    float brightness = mix(0.35, 1.0, seed);
    float intensity = (core + glow * 0.35 + spikes * 0.55 * (0.75 + 0.25 * wave)) * brightness * twinkle;

    vec3 base = mix(starTemperature(fract(h.x * 7.13 + seed)), tint, 0.3);
    vec3 color = mix(base, vec3(1.0), core * 0.6);
    return vec4(color * intensity, intensity);
}

// ---------------------------------------------------------------- main

void main() {
    // Measure the pixel footprint before branching, including at the edges of lit areas.
    float antialias = clamp(length(dFdx(worldPoint)) * 0.58, 0.12, 1.15);
    vec4 lighting = texture(SourceTexture, lightUv);
    float light = lighting.a;
    if (light <= 0.0) {
        fragColor = vec4(0.0);
        return;
    }
    vec3 tint = lighting.rgb / light;
    float zoomFade = smoothstep(0.05, 0.20, DrawParams.x);

    // Background: soft halo + slowly drifting, domain-warped nebula.
    vec2 np = worldPoint * 0.0065;
    vec2 warp = vec2(valueNoise(np * 1.7 + Time * 0.015),
                     valueNoise(np * 1.7 + 9.2 - Time * 0.012));
    float neb = smoothstep(0.30, 0.85, fbm(np + warp * 1.6 + vec2(Time * 0.004, 0.0)));
    vec3 nebColor = mix(mix(vec3(0.10, 0.16, 0.42), vec3(0.38, 0.20, 0.62), valueNoise(np * 0.6 + 31.0)),
                        tint, 0.55);
    vec3 haloColor = mix(vec3(0.19, 0.32, 0.62), tint, 0.67);

    float haloA = 0.08 * light * light;
    float nebA = neb * 0.16 * light;
    float baseA = haloA + nebA * (1.0 - haloA);
    vec3 basePremul = haloColor * haloA * (1.0 - nebA) + nebColor * nebA;

    // Three star layers; scaling worldPoint gives cheap parallax when panning.
    vec4 s = vec4(0.0);
    s += starLayer(worldPoint * 0.55 + 1000.0, 9.0, antialias * 0.55, 0.55,
                   vec2(0.25, 0.55), 0.0, 3.0, 11.0, tint) * 0.55;
    s += starLayer(worldPoint, 17.0, antialias, 0.65,
                   vec2(0.45, 1.15), 0.5, 5.5, 23.0, tint);
    s += starLayer(worldPoint * 1.15 - 500.0, 43.0, antialias * 1.15, 0.35,
                   vec2(0.9, 1.8), 1.0, 16.0, 47.0, tint);
    s *= zoomFade * light;
    s *= 1.0 / max(s.a, 1.0);

    // Stars over background (premultiplied), then un-premultiply for SRC_ALPHA blending.
    float alpha = baseA + s.a * (1.0 - baseA);
    vec3 color = (basePremul * (1.0 - s.a) + s.rgb) / max(alpha, 1e-4);

    // Dither to hide banding in the faint gradients.
    color += (hash12(gl_FragCoord.xy) - 0.5) / 255.0;

    fragColor = vec4(clamp(color, 0.0, 1.0), alpha * DrawParams.y);
}
