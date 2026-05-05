#version 330 core

in vec2 v_TexCoord;
in vec2 v_OneTexel;

uniform sampler2D MainColorSampler;
uniform sampler2D MainDepthSampler;

uniform vec2 u_Size;
uniform mat4 u_InverseProjectionMatrix;
uniform mat4 u_InverseViewMatrix;
uniform vec3 u_CameraPosition;
uniform float u_GameTime;
uniform float u_GridSpeed;
uniform float u_SunSpeed;
uniform float u_PulseSpeed;
uniform float u_ScanSpeed;
uniform float u_ScanDuration;

out vec4 fragColor;

vec3 clipToView(vec2 uv, float depth) {
    vec4 clipPos = vec4(uv * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    vec4 viewPos = u_InverseProjectionMatrix * clipPos;
    return viewPos.xyz / viewPos.w;
}

vec3 fetchViewPos(vec2 uv) {
    float rawDepth = texture(MainDepthSampler, uv).r;
    if (rawDepth >= 1.0) return vec3(0.0, 0.0, -1000.0);
    return clipToView(uv, rawDepth);
}

vec3 reconstructNormal(vec3 centerPos, vec2 uv) {
    float EDGE_THRESHOLD = 0.003;
    vec2 offset = v_OneTexel;
    vec3 posLeft  = fetchViewPos(uv + vec2(-offset.x, 0.0));
    vec3 posRight = fetchViewPos(uv + vec2( offset.x, 0.0));
    vec3 posUp    = fetchViewPos(uv + vec2(0.0,  offset.y));
    vec3 posDown  = fetchViewPos(uv + vec2(0.0, -offset.y));

    vec3 l = centerPos - posLeft;
    vec3 r = posRight - centerPos;
    vec3 d = centerPos - posDown;
    vec3 u = posUp - centerPos;

    float p = pow(max(abs(centerPos.z), 0.1), 2.0);
    if (abs(l.z / p) > EDGE_THRESHOLD || abs(r.z / p) > EDGE_THRESHOLD ||
        abs(d.z / p) > EDGE_THRESHOLD || abs(u.z / p) > EDGE_THRESHOLD) {
        return vec3(0.0, 1.0, 0.0);
    }
    vec3 hVec = (abs(l.z) < abs(r.z)) ? l : r;
    vec3 vVec = (abs(d.z) < abs(u.z)) ? d : u;
    return normalize(cross(hVec, vVec));
}

float grid(vec3 worldPos, vec3 normal, float lineWidth, float scale) {
    vec3 coord = worldPos * scale;
    vec3 gridDeriv = fwidth(coord);
    vec3 gridPattern = abs(fract(coord - 0.5) - 0.5);
    vec3 lineDist = gridPattern / (max(gridDeriv, 0.001) * lineWidth);
    vec3 gridLines = 1.0 - min(lineDist, 1.0);
    vec3 weight = abs(normal);
    return clamp(gridLines.x * (1.0 - weight.x) + gridLines.y * (1.0 - weight.y) + gridLines.z * (1.0 - weight.z), 0.0, 1.0);
}

float synthwaveSun(vec2 uv, float time) {
    float val = smoothstep(0.3, 0.29, length(uv));
    float bloom = smoothstep(0.5, 0.0, length(uv));
    float cut = 5.0 * sin((uv.y + time * u_SunSpeed) * 60.0);
    cut += clamp(uv.y * 15.0, -6.0, 6.0);
    cut = clamp(cut, 0.0, 1.0);
    return clamp(val * cut, 0.0, 1.0) + bloom * 0.6;
}

void main() {
    float rawDepth = texture(MainDepthSampler, v_TexCoord).r;
    vec4 sceneColor = texture(MainColorSampler, v_TexCoord);
    vec3 synthColor = vec3(0.0);

    if (rawDepth >= 1.0) {
        // --- 天空 ---
        vec3 viewPos = clipToView(v_TexCoord, 1.0);
        vec3 worldViewDir = normalize(mat3(u_InverseViewMatrix) * normalize(viewPos));
        vec3 sunDir = normalize(vec3(-1.0, 0.15, 0.0));
        float facingSun = dot(worldViewDir, sunDir);
        float sunShape = 0.0;
        vec2 sunUV = vec2(0.0);
        if (facingSun > 0.0) {
            vec3 sunRight = normalize(cross(sunDir, vec3(0.0, 1.0, 0.0)));
            vec3 sunUp = cross(sunRight, sunDir);
            sunUV = vec2(dot(worldViewDir, sunRight), dot(worldViewDir, sunUp)) * 1.0;
            sunShape = synthwaveSun(sunUV, u_GameTime * 1.0);
        }
        vec3 finalSunColor = mix(vec3(1.0, 0.0, 0.5), vec3(1.0, 0.8, 0.0), smoothstep(-0.3, 0.3, sunUV.y));
        synthColor = mix(vec3(0.4, 0.0, 0.4), vec3(0.05, 0.0, 0.1), v_TexCoord.y) + finalSunColor * sunShape;
    } else {
        // --- 世界/地面 ---
        vec3 viewPos = clipToView(v_TexCoord, rawDepth);
        vec3 normal = reconstructNormal(viewPos, v_TexCoord);
        float NdotV = dot(normal, normalize(-viewPos));
        vec3 worldPos = (u_InverseViewMatrix * vec4(viewPos, 1.0)).xyz + u_CameraPosition;
        vec3 worldNormal = normalize(mat3(u_InverseViewMatrix) * normal);

        float gridVal = 0.0;
        if (smoothstep(0.9, 0.98, max(max(abs(worldNormal.x), abs(worldNormal.y)), abs(worldNormal.z))) > 0.01) {
            vec3 gPos = worldPos;
            gPos.x -= u_GameTime * u_GridSpeed;
            gPos.yz += u_GameTime * u_GridSpeed;
            gridVal = grid(gPos, worldNormal, 3.0, 0.5);
        }
        gridVal *= (sin(u_GameTime * u_PulseSpeed) * 0.4 + 0.6);

        vec3 finalColor = vec3(0.08, 0.05, 0.15) + (vec3(0.0, 1.0, 1.0) * pow(1.0 - max(NdotV, 0.0), 3.0)) + (vec3(1.0, 0.0, 0.8) * gridVal * 2.0);
        synthColor = mix(vec3(0.1, 0.0, 0.2), finalColor, clamp(exp(-length(viewPos) * 0.015), 0.0, 1.0));
    }

    // --- 扫描线 ---
    float timeCycle = u_GameTime * u_ScanSpeed;
    timeCycle = mod(timeCycle, u_ScanDuration);
    float mainRadius = pow(max(timeCycle, 0.0), 5.0);
    float scanMetric = (rawDepth >= 1.0) ? normalize(clipToView(v_TexCoord, 1.0)).y : length(clipToView(v_TexCoord, rawDepth));
    float scanThreshold = (rawDepth >= 1.0) ? mix(-0.8, 1.1, smoothstep(0.0, 1500.0, mainRadius)) : mainRadius;
    float alphaMask = 1.0 - smoothstep(scanThreshold, scanThreshold + ((rawDepth >= 1.0) ? 0.15 : 10.0), scanMetric);
    float scanLine = smoothstep(scanThreshold, scanThreshold + ((rawDepth >= 1.0) ? 0.15 : 10.0), scanMetric) *
                     smoothstep(scanThreshold + ((rawDepth >= 1.0) ? 0.2 : 12.0), scanThreshold + ((rawDepth >= 1.0) ? 0.02 : 1.0), scanMetric);

    fragColor = vec4(mix(sceneColor.rgb, synthColor, alphaMask) + (vec3(0.5, 1.0, 1.2) * scanLine * 3.0), 1.0);
}
