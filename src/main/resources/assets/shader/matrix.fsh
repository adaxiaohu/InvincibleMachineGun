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

// 矩阵风格的数据核心（替代太阳）
float matrixSun(vec2 uv, float time) {
    float d = length(uv);
    float core = smoothstep(0.3, 0.0, d);
    float pulse = sin(time * 5.0) * 0.1 + 0.9;
    // 增加一点扫描线感
    float lines = sin(uv.y * 100.0 - time * 20.0) * 0.5 + 0.5;
    return core * pulse * (0.8 + 0.2 * lines);
}

void main() {
    float rawDepth = texture(MainDepthSampler, texCoord).r;
    vec4 sceneColor = texture(MainColorSampler, texCoord);
    vec3 matrixColor = vec3(0.0);

    // 矩阵配色配置
    vec3 mGreen = vec3(0.0, 1.0, 0.2);     // 亮绿
    vec3 mDarkGreen = vec3(0.0, 0.1, 0.0); // 深绿背景
    vec3 mBlack = vec3(0.0, 0.0, 0.0);     // 纯黑

    if (rawDepth >= 1.0) {
        // 天空：黑暗代码空间
        vec3 viewPos = clipToView(texCoord, 1.0);
        vec3 worldViewDir = normalize(mat3(U_InverseViewMatrix) * normalize(viewPos));
        
        vec3 sunDir = normalize(vec3(-1.0, 0.3, 0.0));
        float facingSun = dot(worldViewDir, sunDir);
        float sunShape = 0.0;
        if(facingSun > 0.0) {
            vec3 sunRight = normalize(cross(sunDir, vec3(0.0, 1.0, 0.0)));
            vec3 sunUp = cross(sunRight, sunDir);
            vec2 sunUV = vec2(dot(worldViewDir, sunRight), dot(worldViewDir, sunUp)) * 1.5;
            sunShape = matrixSun(sunUV, U_GameTime * U_SunSpeed);
        }

        vec3 skyColor = mix(mBlack, mDarkGreen, 1.0 - texCoord.y);
        matrixColor = skyColor + mGreen * sunShape;
    } else {
        // 地面：数字网格
        vec3 viewPos = clipToView(texCoord, rawDepth);
        vec3 normal = reconstructNormal(viewPos, texCoord);
        float NdotV = dot(normal, normalize(-viewPos));

        vec3 worldPos = (U_InverseViewMatrix * vec4(viewPos, 1.0)).xyz + U_CameraPosition;
        vec3 worldNormal = normalize(mat3(U_InverseViewMatrix) * normal);

        float gridVal = 0.0;
        if (max(max(abs(worldNormal.x), abs(worldNormal.y)), abs(worldNormal.z)) > 0.9) {
            worldPos.z += U_GameTime * U_GridSpeed; // 只有Z轴流动感
            gridVal = grid(worldPos, worldNormal, 2.0, 0.5);
        }

        float rim = pow(1.0 - max(NdotV, 0.0), 3.0);
        float fog = exp(-length(viewPos) * 0.01);
        
        vec3 base = mix(mBlack, mDarkGreen, 0.5);
        vec3 finalColor = base + mGreen * rim * 0.5 + mGreen * gridVal;
        matrixColor = mix(mBlack, finalColor, clamp(fog, 0.0, 1.0));
    }

    // 雷达扫描：改色
    float timeCycle = U_GameTime * U_ScanSpeed;
    if (U_LoopEnabled > 0.5) timeCycle = mod(timeCycle, U_ScanDuration);
    float mainRadius = pow(max(timeCycle, 0.0), 5.0);

    float scanMetric = (rawDepth >= 1.0) ? normalize(clipToView(texCoord, 1.0)).y : length(clipToView(texCoord, rawDepth));
    float scanThreshold = (rawDepth >= 1.0) ? mix(-0.8, 1.1, smoothstep(0.0, 1500.0, mainRadius)) : mainRadius;
    float scanSoftness = (rawDepth >= 1.0) ? 0.1 : 8.0;

    float alphaMask = 1.0 - smoothstep(scanThreshold, scanThreshold + scanSoftness, scanMetric);
    float scanLine = smoothstep(scanThreshold, scanThreshold + scanSoftness, scanMetric) * 
                     smoothstep(scanThreshold + (rawDepth >= 1.0 ? 0.1 : 10.0), scanThreshold, scanMetric);
    
    // 最终输出混合
    vec3 finalOutput = mix(sceneColor.rgb, matrixColor, alphaMask) + mGreen * scanLine * 2.0;
    fragColor = vec4(finalOutput, 1.0);
}