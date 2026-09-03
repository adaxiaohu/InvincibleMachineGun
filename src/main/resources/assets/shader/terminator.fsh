#version 150

uniform float U_GameTime;
uniform vec2 ScreenSize;
uniform sampler2D MainDepthSampler;
uniform sampler2D MainColorSampler;

uniform vec3 U_FilterColor;
uniform vec3 U_HudColor;
uniform float U_FilterStrength;
uniform float U_Contrast;
uniform float U_ScanlineStrength;
uniform float U_ScanSpeed;
uniform float U_NoiseStrength;
uniform float U_VignetteStrength;
uniform float U_HudOpacity;
uniform float U_ReticleEnabled;
uniform float U_ReticleSize;
uniform vec2 U_ReticleCenter;
uniform float U_DeathProgress;
uniform float U_SkyEnabled;
uniform float U_GroundEnabled;

in vec2 texCoord;
out vec4 fragColor;

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
}

float segment(vec2 p, vec2 a, vec2 b, float width) {
    vec2 pa = p - a;
    vec2 ba = b - a;
    float h = clamp(dot(pa, ba) / dot(ba, ba), 0.0, 1.0);
    float d = length(pa - ba * h);
    return 1.0 - smoothstep(width, width * 2.0, d);
}

float depthEdge(vec2 uv) {
    vec2 pixel = 1.0 / ScreenSize;
    float center = texture(MainDepthSampler, uv).r;
    float horizontal = abs(texture(MainDepthSampler, uv + vec2(pixel.x, 0.0)).r
        - texture(MainDepthSampler, uv - vec2(pixel.x, 0.0)).r);
    float vertical = abs(texture(MainDepthSampler, uv + vec2(0.0, pixel.y)).r
        - texture(MainDepthSampler, uv - vec2(0.0, pixel.y)).r);
    float scale = max(0.00002, (1.0 - center) * 0.02);
    return smoothstep(scale, scale * 4.0, horizontal + vertical);
}

float reticle(vec2 p, float size, float pixel) {
    float angle = atan(p.y, p.x);
    float ringMask = smoothstep(0.12, 0.32, abs(sin(angle * 4.0)));
    float ring = (1.0 - smoothstep(pixel, pixel * 2.5, abs(length(p) - size * 0.48))) * ringMask;
    float innerRing = 1.0 - smoothstep(pixel, pixel * 2.0, abs(length(p) - size * 0.12));
    float crosshair = 0.0;
    crosshair += segment(p, vec2(-size * 0.72, 0.0), vec2(-size * 0.18, 0.0), pixel);
    crosshair += segment(p, vec2( size * 0.18, 0.0), vec2( size * 0.72, 0.0), pixel);
    crosshair += segment(p, vec2(0.0, -size * 0.72), vec2(0.0, -size * 0.18), pixel);
    crosshair += segment(p, vec2(0.0,  size * 0.18), vec2(0.0,  size * 0.72), pixel);

    float corners = 0.0;
    float outer = size;
    float inner = size * 0.68;
    corners += segment(p, vec2(-outer, -outer), vec2(-inner, -outer), pixel * 1.4);
    corners += segment(p, vec2(-outer, -outer), vec2(-outer, -inner), pixel * 1.4);
    corners += segment(p, vec2( outer, -outer), vec2( inner, -outer), pixel * 1.4);
    corners += segment(p, vec2( outer, -outer), vec2( outer, -inner), pixel * 1.4);
    corners += segment(p, vec2(-outer,  outer), vec2(-inner,  outer), pixel * 1.4);
    corners += segment(p, vec2(-outer,  outer), vec2(-outer,  inner), pixel * 1.4);
    corners += segment(p, vec2( outer,  outer), vec2( inner,  outer), pixel * 1.4);
    corners += segment(p, vec2( outer,  outer), vec2( outer,  inner), pixel * 1.4);
    float diagonalTicks = 0.0;
    diagonalTicks += segment(p, vec2(-size * 0.43, -size * 0.43), vec2(-size * 0.35, -size * 0.35), pixel * 1.2);
    diagonalTicks += segment(p, vec2( size * 0.43, -size * 0.43), vec2( size * 0.35, -size * 0.35), pixel * 1.2);
    diagonalTicks += segment(p, vec2(-size * 0.43,  size * 0.43), vec2(-size * 0.35,  size * 0.35), pixel * 1.2);
    diagonalTicks += segment(p, vec2( size * 0.43,  size * 0.43), vec2( size * 0.35,  size * 0.35), pixel * 1.2);
    return clamp(ring + innerRing + crosshair + corners + diagonalTicks, 0.0, 1.0);
}

float dataBars(vec2 p, float pixel) {
    float bars = 0.0;
    for (int i = 0; i < 8; i++) {
        float y = -0.29 + float(i) * 0.035;
        float width = 0.035 + hash(vec2(float(i), 3.0)) * 0.075;
        bars += segment(p, vec2(-0.54, y), vec2(-0.54 + width, y), pixel * 1.5);
        bars += segment(p, vec2(0.54 - width, -y), vec2(0.54, -y), pixel * 1.5);
    }
    return clamp(bars, 0.0, 1.0);
}

void main() {
    float rawDepth = texture(MainDepthSampler, texCoord).r;
    vec4 sceneColor = texture(MainColorSampler, texCoord);
    bool sky = rawDepth >= 0.9999;
    if (((sky && U_SkyEnabled < 0.5) || (!sky && U_GroundEnabled < 0.5)) && U_DeathProgress <= 0.0) {
        fragColor = sceneColor;
        return;
    }

    float luminance = dot(sceneColor.rgb, vec3(0.299, 0.587, 0.114));
    vec3 machineVision = U_FilterColor * (0.08 + luminance * 1.25);
    machineVision += U_FilterColor * depthEdge(texCoord) * 0.45;
    machineVision = clamp((machineVision - 0.5) * U_Contrast + 0.5, 0.0, 1.0);
    vec3 color = mix(sceneColor.rgb, machineVision, U_FilterStrength);

    float scanPhase = (texCoord.y * ScreenSize.y + U_GameTime * U_ScanSpeed * 45.0) * 3.14159265;
    float scanline = 0.5 + 0.5 * sin(scanPhase);
    color *= 1.0 - (1.0 - scanline) * U_ScanlineStrength;

    float noise = hash(gl_FragCoord.xy + floor(U_GameTime * 60.0)) - 0.5;
    color += U_FilterColor * noise * U_NoiseStrength;

    float aspect = ScreenSize.x / max(ScreenSize.y, 1.0);
    vec2 screenP = (texCoord - 0.5) * vec2(aspect, 1.0);
    vec2 p = (texCoord - U_ReticleCenter) * vec2(aspect, 1.0);
    float vignette = smoothstep(0.25, 0.78, length(screenP));
    color *= 1.0 - vignette * U_VignetteStrength;

    if (U_ReticleEnabled > 0.5) {
        float pixel = 1.5 / max(ScreenSize.y, 1.0);
        float hud = reticle(p, U_ReticleSize, pixel);
        hud = max(hud, dataBars(p, pixel));

        float beamY = fract(U_GameTime * U_ScanSpeed * 0.12);
        float beam = 1.0 - smoothstep(0.0, 0.012, abs(texCoord.y - beamY));
        hud = max(hud, beam * 0.22);
        color = mix(color, max(color, U_HudColor), clamp(hud * U_HudOpacity, 0.0, 1.0));
    }

    if (U_DeathProgress > 0.0) {
        float shutdown = clamp(U_DeathProgress, 0.0, 1.0);
        if (shutdown < 0.64) {
            float phase = smoothstep(0.0, 1.0, shutdown / 0.64);
            float halfHeight = mix(0.5, 0.0022, phase);
            float band = 1.0 - step(halfHeight, abs(texCoord.y - 0.5));
            vec2 compressedUv = vec2(texCoord.x,
                clamp(0.5 + (texCoord.y - 0.5) / max(halfHeight * 2.0, 0.0005), 0.0, 1.0));
            vec3 compressedScene = texture(MainColorSampler, compressedUv).rgb;
            float compressedLum = dot(compressedScene, vec3(0.299, 0.587, 0.114));
            vec3 redImage = mix(compressedScene, U_FilterColor * (0.15 + compressedLum * 1.35), 0.78);
            float edge = 1.0 - smoothstep(0.0, 3.5 / ScreenSize.y,
                abs(abs(texCoord.y - 0.5) - halfHeight));
            color = redImage * band + U_FilterColor * edge * (0.45 + phase * 0.55);
        } else {
            float phase = smoothstep(0.0, 1.0, (shutdown - 0.64) / 0.36);
            float halfWidth = mix(0.5, 0.0, phase);
            float verticalDistance = abs(texCoord.y - 0.5) * ScreenSize.y;
            float horizontalDistance = abs(texCoord.x - 0.5);
            float line = (1.0 - smoothstep(1.0, 2.8, verticalDistance))
                * (1.0 - step(halfWidth, horizontalDistance));
            float dotRadius = mix(4.5, 0.8, phase);
            float dot = 1.0 - smoothstep(dotRadius, dotRadius + 1.5,
                length((texCoord - 0.5) * ScreenSize));
            vec3 shutdownColor = mix(U_FilterColor, vec3(1.0), smoothstep(0.30, 0.82, phase));
            float fade = 1.0 - smoothstep(0.90, 1.0, phase);
            color = shutdownColor * max(line, dot) * fade;
        }
    }

    fragColor = vec4(clamp(color, 0.0, 1.0), 1.0);
}
