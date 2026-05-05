#version 150

uniform float U_GameTime;
uniform vec2 ScreenSize;
uniform sampler2D MainDepthSampler;
uniform sampler2D MainColorSampler;
uniform mat4 U_InverseProjectionMatrix;
uniform mat4 U_InverseViewMatrix;
uniform vec3 U_CameraPosition;

// 寂静岭参数
uniform float U_FogDensity;
uniform float U_GrainIntensity;
uniform float U_ScanSpeed;
uniform float U_LoopEnabled;
uniform float U_ScanDuration;
uniform float U_StyleMode; // 0.0 = 表世界 (Fog), 1.0 = 里世界 (Otherworld)

in vec2 texCoord;
out vec4 fragColor;

float noise(vec2 co) {
    return fract(sin(dot(co.xy ,vec2(12.9898,78.233))) * 43758.5453);
}

vec3 clipToView(vec2 uv, float depth) {
    vec4 clipPos = vec4(uv * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    vec4 viewPos = U_InverseProjectionMatrix * clipPos;
    return viewPos.xyz / viewPos.w;
}

void main() {
    float rawDepth = texture(MainDepthSampler, texCoord).r;
    vec4 sceneColor = texture(MainColorSampler, texCoord);
    
    // 基础参数配置
    vec3 fogColor;
    vec3 tintColor;
    float grainMult;
    float brightness;

    if (U_StyleMode < 0.5) {
        // --- 表世界 (Fog World) ---
        fogColor = vec3(0.55, 0.55, 0.58);    // 灰白色大雾
        tintColor = vec3(0.9, 0.9, 0.95);    // 轻微冷色调
        grainMult = 1.0;                     // 标准噪点
        brightness = 0.8;                    // 较亮
    } else {
        // --- 里世界 (Otherworld) ---
        fogColor = vec3(0.15, 0.05, 0.02);   // 深铁锈红色
        tintColor = vec3(0.6, 0.2, 0.1);     // 血色偏移
        grainMult = 2.5;                     // 剧烈噪点
        brightness = 0.4;                    // 极暗
    }

    // 1. 调色逻辑
    vec3 gray = vec3(dot(sceneColor.rgb, vec3(0.299, 0.587, 0.114)));
    vec3 horrorBase = mix(sceneColor.rgb, gray * tintColor, 0.6) * brightness;

    // 2. 雾气计算
    float dist = (rawDepth >= 1.0) ? 100.0 : length(clipToView(texCoord, rawDepth));
    float fogFactor = exp(-dist * U_FogDensity);
    
    // 3. 噪点
    float g = noise(texCoord + (U_GameTime * 0.1));
    float grain = (g - 0.5) * U_GrainIntensity * grainMult;

    // 4. 侵蚀/扫描逻辑 (Reveal Effect)
    float timeCycle = U_GameTime * U_ScanSpeed;
    if (U_LoopEnabled > 0.5) timeCycle = mod(timeCycle, U_ScanDuration);
    float mainRadius = pow(max(timeCycle, 0.0), 4.0);
    
    float noiseOffset = noise(texCoord * 4.0 + U_GameTime * 0.1) * 3.0;
    float alphaMask = 1.0 - smoothstep(mainRadius, mainRadius + 12.0, dist + noiseOffset);

    // 5. 最终合成
    vec3 styledScene = mix(fogColor, horrorBase, fogFactor) + grain;
    
    // 扫描边缘 (表世界为深灰边，里世界为血红边)
    vec3 edgeColor = (U_StyleMode < 0.5) ? vec3(0.1) : vec3(0.25, 0.0, 0.0);
    float edge = smoothstep(mainRadius, mainRadius + 4.0, dist + noiseOffset) * 
                 smoothstep(mainRadius + 6.0, mainRadius + 2.0, dist + noiseOffset);

    float vignette = smoothstep(0.9, 0.2, length(texCoord - 0.5));
    vec3 finalColor = mix(sceneColor.rgb, styledScene, alphaMask) + edgeColor * edge;
    finalColor *= (vignette + 0.1);

    fragColor = vec4(finalColor, 1.0);
}