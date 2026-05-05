#version 150

uniform float U_GameTime;
uniform vec2 ScreenSize;
uniform sampler2D MainDepthSampler;
uniform sampler2D MainColorSampler;
uniform mat4 U_InverseProjectionMatrix;
uniform mat4 U_InverseViewMatrix;
uniform vec3 U_CameraPosition;

uniform float U_PetalDensity; // 花瓣密度
uniform float U_PinkIntensity; // 粉色滤镜强度
uniform float U_ScanSpeed;
uniform float U_LoopEnabled;
uniform float U_ScanDuration;

in vec2 texCoord;
out vec4 fragColor;

// 伪随机函数
float hash(vec2 p) {
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

// 模拟飘落的花瓣
float drawPetals(vec2 uv, float time) {
    uv *= 4.0; // 缩放网格
    vec2 id = floor(uv);
    vec2 gv = fract(uv) - 0.5;

    float mask = 0.0;
    for (float y = -1.0; y <= 1.0; y++) {
        for (float x = -1.0; x <= 1.0; x++) {
            vec2 offs = vec2(x, y);
            float n = hash(id + offs);
            
            // 随时间移动：向下飘落 + 左右晃动
            float t = time * 0.5 + n * 6.28;
            vec2 p = offs + vec2(sin(t) * 0.3, cos(t * 0.5) - fract(time * 0.2 + n));
            
            // 绘制小椭圆（花瓣）
            float d = length(gv - p);
            float petal = smoothstep(0.05, 0.02, d);
            mask += petal;
        }
    }
    return mask;
}

vec3 clipToView(vec2 uv, float depth) {
    vec4 clipPos = vec4(uv * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    vec4 viewPos = U_InverseProjectionMatrix * clipPos;
    return viewPos.xyz / viewPos.w;
}

void main() {
    float rawDepth = texture(MainDepthSampler, texCoord).r;
    vec4 sceneColor = texture(MainColorSampler, texCoord);
    
    // --- 1. 基础美化：提升亮度和粉色偏移 ---
    vec3 pinkTint = vec3(1.0, 0.75, 0.85); // 经典樱花粉
    vec3 aestheticColor = sceneColor.rgb * vec3(1.1, 1.05, 1.05); // 稍微加亮
    aestheticColor = mix(aestheticColor, aestheticColor * pinkTint, U_PinkIntensity);

    // --- 2. 天空逻辑 ---
    vec3 finalColor;
    if (rawDepth >= 1.0) {
        vec2 skyUV = texCoord;
        vec3 skyBlue = vec3(0.6, 0.8, 1.0);
        vec3 skyPink = vec3(1.0, 0.8, 0.9);
        finalColor = mix(skyPink, skyBlue, skyUV.y); // 粉蓝渐变天空
    } else {
        finalColor = aestheticColor;
    }

    // --- 3. 樱花瓣层 ---
    // 使用屏幕坐标计算花瓣，使其看起来像是飘在屏幕前的
    vec2 petalUV = texCoord * vec2(ScreenSize.x / ScreenSize.y, 1.0);
    float petals = drawPetals(petalUV, U_GameTime) * U_PetalDensity;
    vec3 petalCol = vec3(1.0, 0.8, 0.9); // 花瓣颜色
    finalColor = mix(finalColor, petalCol, petals * 0.8);

    // --- 4. 扩张波纹 (花开瞬间) ---
    float timeCycle = U_GameTime * U_ScanSpeed;
    if (U_LoopEnabled > 0.5) timeCycle = mod(timeCycle, U_ScanDuration);
    float mainRadius = pow(max(timeCycle, 0.0), 4.0);
    
    float dist = (rawDepth >= 1.0) ? 50.0 : length(clipToView(texCoord, rawDepth));
    // 增加一点波纹起伏感
    float wave = sin(dist * 0.2 - U_GameTime * 2.0) * 0.5;
    float alphaMask = 1.0 - smoothstep(mainRadius, mainRadius + 15.0, dist + wave);

    // 扫描边缘：白色的亮光边
    float edge = smoothstep(mainRadius, mainRadius + 2.0, dist + wave) * 
                 smoothstep(mainRadius + 4.0, mainRadius, dist + wave);
    
    // 最终混合：原版游戏 vs 樱花世界
    vec3 mixedColor = mix(sceneColor.rgb, finalColor, alphaMask);
    mixedColor += edge * vec3(1.0, 1.0, 1.0) * 0.5; // 增加白色亮边

    // 软化四周（暗角，但这里用明亮的白色渐变）
    float vignette = smoothstep(1.2, 0.5, length(texCoord - 0.5));
    mixedColor = mix(mixedColor, mixedColor * 1.1, 1.0 - vignette);

    fragColor = vec4(mixedColor, 1.0);
}