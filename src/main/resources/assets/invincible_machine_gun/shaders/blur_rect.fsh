#version 150

// 圆角矩形背景模糊（从 Sakura 的 core/blur.fsh 移植）
//
// 1.21.11 差异：核心着色器不再有 .json 描述文件，取样器名与 uniform 名
// 直接由 GLSL 声明 + Java 侧 glGetUniformLocation 决定。
// gl_FragCoord 使用帧缓冲像素坐标，原点在左下角，与 Java 侧传入的 uLocation 一致。

uniform sampler2D InputSampler;
uniform vec2 InputResolution;
uniform vec2 uSize;
uniform vec2 uLocation;

uniform float radius;
uniform float Brightness;
uniform float Quality;
uniform vec4 color1;

out vec4 fragColor;

float roundedBoxSDF(vec2 center, vec2 size, float radius) {
    return length(max(abs(center) - size + radius, 0.0)) - radius;
}

vec4 blur() {
    #define TAU 6.28318530718

    vec2 Radius = Quality / InputResolution.xy;

    vec2 uv = gl_FragCoord.xy / InputResolution.xy;
    vec4 Color = texture(InputSampler, uv);

    float step = TAU / 16.0;

    for (float d = 0.0; d < TAU; d += step) {
        for (float i = 0.2; i <= 1.0; i += 0.2) {
            Color += texture(InputSampler, uv + vec2(cos(d), sin(d)) * Radius * i);
        }
    }

    Color /= 81.0;
    return (Color + color1);
}

void main() {
    vec2 halfSize = uSize / 2.0;
    float dist = roundedBoxSDF(gl_FragCoord.xy - uLocation - halfSize, halfSize, radius);
    float smoothedAlpha = 1.0 - smoothstep(0.0, 1.0, dist);

    // ★ 必须在做 81 次采样【之前】就丢弃矩形外的片元。
    //
    // 原实现是「先无条件算完整 blur()，再在最后乘 alpha」—— 于是每个调用
    // 都把整个屏幕（1920×1080 ≈ 207 万片元）都跑满 81 次纹理采样，
    // 而矩形实际只占其中很小一块。30 个面板时约 52 亿次采样/帧，直接卡死。
    //
    // 提前 discard 后，只有矩形内的片元才付出采样成本，开销与面板面积成正比。
    // discard 不影响结果：矩形外的 alpha 本来就是 0。
    if (smoothedAlpha <= 0.001) discard;

    fragColor = vec4(blur().rgb, smoothedAlpha * Brightness);
}
