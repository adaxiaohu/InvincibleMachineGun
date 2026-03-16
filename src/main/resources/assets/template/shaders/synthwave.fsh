#version 150

uniform float U_GameTime;
uniform vec2 ScreenSize;
uniform vec2 FboSize; // 关键：真实的缓冲区纹理大小
uniform sampler2D MainDepthSampler;
uniform sampler2D MainColorSampler; 
uniform mat4 U_InverseProjectionMatrix;
uniform mat4 U_InverseViewMatrix;
uniform vec3 U_CameraPosition;

uniform float U_GridSpeed;
uniform float U_SunSpeed;
uniform float U_PulseSpeed;
uniform float U_ScanSpeed;
uniform float U_ScanDuration;

out vec4 fragColor;

// 接收 screen_uv 而不是使用全局 v_uv
vec3 getWPos(vec2 screen_uv, float depth) {
    if (depth >= 0.9999) return vec3(0.0); // 边缘安全处理
    vec4 ndc = vec4(screen_uv * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    vec4 viewPos = U_InverseProjectionMatrix * ndc;
    float w = (viewPos.w == 0.0) ? 0.00001 : viewPos.w;
    vec3 vPos = viewPos.xyz / w;
    return (U_InverseViewMatrix * vec4(vPos, 1.0)).xyz + U_CameraPosition;
}

// 接收 tex_uv 用于采样，screen_uv 用于计算
vec3 getNormal(vec2 tex_uv, vec2 screen_uv, float d, vec3 p) {
    vec2 tex_off = 1.0 / FboSize;     // 纹理采样偏移
    vec2 scr_off = 1.0 / ScreenSize;  // 坐标计算偏移
    
    float dR = texture(MainDepthSampler, tex_uv + vec2(tex_off.x, 0.0)).r;
    float dU = texture(MainDepthSampler, tex_uv + vec2(0.0, tex_off.y)).r;
    
    vec3 pR = getWPos(screen_uv + vec2(scr_off.x, 0.0), dR);
    vec3 pU = getWPos(screen_uv + vec2(0.0, scr_off.y), dU);
    
    if (length(pR) == 0.0 || length(pU) == 0.0) return vec3(0.0, 1.0, 0.0);
    return normalize(cross(pR - p, pU - p));
}

void main() {
    // 核心修复：分离采样 UV 和 屏幕运算 UV
    // gl_FragCoord.xy 完美对应当前视口内的绝对像素坐标
    vec2 tex_uv = gl_FragCoord.xy / FboSize;     // 用于从大纹理中准确抠出当前画面
    vec2 screen_uv = gl_FragCoord.xy / ScreenSize; // 用于正确反算 3D 视角透视矩阵

    vec4 sceneCol = texture(MainColorSampler, tex_uv);
    float depth = texture(MainDepthSampler, tex_uv).r;
    
    if (isnan(depth)) {
        fragColor = sceneCol;
        return;
    }

    vec3 finalCol;

    // --- 天空部分 (太阳效果) ---
    if (depth >= 0.9999) {
        // 天空渲染使用 screen_uv 保证其在屏幕上的位置固定不变形
        vec3 sky = mix(vec3(0.1, 0.0, 0.2), vec3(0.4, 0.0, 0.4), screen_uv.y);
        float sunDist = length(screen_uv - vec2(0.5, 0.7));
        float sunMask = smoothstep(0.18, 0.175, sunDist);
        float sunStripes = step(0.5, sin(screen_uv.y * 60.0 + U_GameTime * U_SunSpeed));
        sunMask *= (screen_uv.y > 0.7) ? 1.0 : sunStripes;
        
        vec3 sunColor = mix(vec3(1.0, 0.0, 0.8), vec3(1.0, 1.0, 0.0), (screen_uv.y - 0.5) * 3.0);
        finalCol = mix(sky, sunColor, sunMask * 0.9);
    } 
    // --- 世界/地面部分 ---
    else {
        vec3 wPos = getWPos(screen_uv, depth);
        vec3 norm = getNormal(tex_uv, screen_uv, depth, wPos);
        
        float gridMask = step(0.7, abs(norm.y)) + step(0.7, abs(norm.x)) + step(0.7, abs(norm.z));
        gridMask = clamp(gridMask, 0.0, 1.0);

        vec3 gCoord = wPos * 0.5;
        gCoord.x -= U_GameTime * U_GridSpeed;
        
        vec3 fw = max(fwidth(gCoord), 0.0001);
        vec3 lines = smoothstep(0.0, 0.06, abs(fract(gCoord - 0.5) - 0.5) / fw);
        float edge = 1.0 - min(min(lines.x, lines.y), lines.z);
        
        float pulse = sin(U_GameTime * U_PulseSpeed) * 0.2 + 0.8;
        vec3 neon = vec3(0.0, 1.0, 1.0) * edge * pulse * gridMask;
        
        float fog = clamp(exp(-length(wPos - U_CameraPosition) * 0.02), 0.0, 1.0);
        finalCol = mix(sceneCol.rgb, sceneCol.rgb + neon, fog * 0.7);
    }

    // 扫描线波纹效果
    float dist = (depth >= 0.9999) ? 0.0 : length(getWPos(screen_uv, depth) - U_CameraPosition);
    float scan = mod(U_GameTime * U_ScanSpeed, U_ScanDuration);
    float wave = smoothstep(scan - 1.0, scan, dist) * smoothstep(scan + 1.0, scan, dist);
    finalCol += vec3(0.5, 1.0, 1.0) * wave * 2.0;

    fragColor = vec4(clamp(finalCol, 0.0, 1.0), 1.0);
}