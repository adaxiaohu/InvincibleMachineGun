#version 150

uniform float U_GameTime;
uniform vec2 ScreenSize;
uniform sampler2D MainDepthSampler;
uniform sampler2D MainColorSampler; 
uniform mat4 U_InverseProjectionMatrix;
uniform mat4 U_InverseViewMatrix;
uniform vec3 U_CameraPosition;

uniform float U_GridSpeed;
uniform float U_SunSpeed;
uniform float U_PulseSpeed; // 新增：控制呼吸灯速度
uniform float U_ScanSpeed;
uniform float U_LoopEnabled;
uniform float U_ScanDuration;

in vec2 texCoord;
out vec4 fragColor;

vec3 clipToView(vec2 uv, float depth) {
    vec4 clipPos = vec4(uv * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    vec4 viewPos = U_InverseProjectionMatrix * clipPos;
    return viewPos.xyz / viewPos.w;
}

vec3 fetchViewPos(vec2 uv) {
    float rawDepth = texture(MainDepthSampler, uv).r;
    if (rawDepth >= 1.0) return vec3(0.0, 0.0, -1000.0);
    return clipToView(uv, rawDepth);
}

vec3 reconstructNormal(vec3 centerPos, vec2 uv) {
    float EDGE_THRESHOLD = 0.003;
    vec2 offset = 1.0 / ScreenSize;
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
    float cut = 5.0 * sin((uv.y + time * U_SunSpeed) * 60.0);
    cut += clamp(uv.y * 15.0, -6.0, 6.0);
    cut = clamp(cut, 0.0, 1.0);
    return clamp(val * cut, 0.0, 1.0) + bloom * 0.6;
}

void main() {
    float rawDepth = texture(MainDepthSampler, texCoord).r;
    vec4 sceneColor = texture(MainColorSampler, texCoord); 
    vec3 synthColor = vec3(0.0);

    if (rawDepth >= 1.0) {
        vec3 viewPos = clipToView(texCoord, 1.0);
        vec3 worldViewDir = normalize(mat3(U_InverseViewMatrix) * normalize(viewPos));
        vec3 sunDir = normalize(vec3(-1.0, 0.15, 0.0));
        float facingSun = dot(worldViewDir, sunDir);
        float sunShape = 0.0;
        vec2 sunUV = vec2(0.0);
        if(facingSun > 0.0) {
            vec3 sunRight = normalize(cross(sunDir, vec3(0.0, 1.0, 0.0)));
            vec3 sunUp = cross(sunRight, sunDir);
            sunUV = vec2(dot(worldViewDir, sunRight), dot(worldViewDir, sunUp)) * 1.0;
            sunShape = synthwaveSun(sunUV, U_GameTime * 1.0);
        }
        vec3 finalSunColor = mix(vec3(1.0, 0.0, 0.5), vec3(1.0, 0.8, 0.0), smoothstep(-0.3, 0.3, sunUV.y));
        synthColor = mix(vec3(0.4, 0.0, 0.4), vec3(0.05, 0.0, 0.1), texCoord.y) + finalSunColor * sunShape;
    } else {
        vec3 viewPos = clipToView(texCoord, rawDepth);
        vec3 normal = reconstructNormal(viewPos, texCoord);
        float NdotV = dot(normal, normalize(-viewPos));
        vec3 worldPos = (U_InverseViewMatrix * vec4(viewPos, 1.0)).xyz + U_CameraPosition;
        vec3 worldNormal = normalize(mat3(U_InverseViewMatrix) * normal);

        float gridVal = 0.0;
        if (smoothstep(0.9, 0.98, max(max(abs(worldNormal.x), abs(worldNormal.y)), abs(worldNormal.z))) > 0.01) {
            vec3 gPos = worldPos;
            gPos.x -= U_GameTime * U_GridSpeed;
            gPos.yz += U_GameTime * U_GridSpeed;
            gridVal = grid(gPos, worldNormal, 3.0, 0.5);
        }
        // 应用新增的呼吸灯速度 Uniform
        gridVal *= (sin(U_GameTime * U_PulseSpeed) * 0.4 + 0.6);

        vec3 finalColor = vec3(0.08, 0.05, 0.15) + (vec3(0.0, 1.0, 1.0) * pow(1.0 - max(NdotV, 0.0), 3.0)) + (vec3(1.0, 0.0, 0.8) * gridVal * 2.0);
        synthColor = mix(vec3(0.1, 0.0, 0.2), finalColor, clamp(exp(-length(viewPos) * 0.015), 0.0, 1.0));
    }

    float timeCycle = U_GameTime * U_ScanSpeed;
    if (U_LoopEnabled > 0.5) timeCycle = mod(timeCycle, U_ScanDuration);
    float mainRadius = pow(max(timeCycle, 0.0), 5.0);
    float scanMetric = (rawDepth >= 1.0) ? normalize(clipToView(texCoord, 1.0)).y : length(clipToView(texCoord, rawDepth));
    float scanThreshold = (rawDepth >= 1.0) ? mix(-0.8, 1.1, smoothstep(0.0, 1500.0, mainRadius)) : mainRadius;
    float alphaMask = 1.0 - smoothstep(scanThreshold, scanThreshold + ((rawDepth >= 1.0) ? 0.15 : 10.0), scanMetric);
    float scanLine = smoothstep(scanThreshold, scanThreshold + ((rawDepth >= 1.0) ? 0.15 : 10.0), scanMetric) * 
                     smoothstep(scanThreshold + ((rawDepth >= 1.0) ? 0.2 : 12.0), scanThreshold + ((rawDepth >= 1.0) ? 0.02 : 1.0), scanMetric);
    
    fragColor = vec4(mix(sceneColor.rgb, synthColor, alphaMask) + (vec3(0.5, 1.0, 1.2) * scanLine * 3.0), 1.0);
}