#version 150

//
// 与同目录 blur_rect.fsh 的区别（**这是两者观感差异的根源**）：
//   * blur_rect.fsh     —— 16 方向 × 5 半径 = 81 次径向采样，且无 uMix
//   * 本文件（glass_rect）—— 12 抽头旋转采样 + uMix，取样数降到 1/7
//
// 的离屏渲染链（每级 5~8 抽头），需要额外的 FBO/纹理管理与多次 render target 切换。
// 本项目的 2D 绘制是「直接叠加到主帧缓冲」的模型（见 Shader2DUtil 的设计说明），
// 引入多 pass 离屏链会与它冲突。改用**单 pass 12 抽头**：
// 抽头数远低于 blur_rect 的 81，观感接近 Kawase，且不改变现有渲染模型。
//
// uMix 是让面板「磨砂而不糊成一团」的关键：

uniform sampler2D InputSampler;
uniform vec2 InputResolution;

uniform vec2 uSize;        // 矩形尺寸（帧缓冲像素）
uniform vec2 uLocation;    // 矩形左下角（帧缓冲像素，原点在左下）
uniform float radius;      // 圆角半径（帧缓冲像素）
uniform vec4 uColor;       // 主色
uniform float uBorderWidth;
uniform vec4 uBorderColor;
uniform float uMix;        // 模糊 ↔ 纯色的混合比例
uniform float uBlurRadius; // 模糊采样半径（帧缓冲像素）

out vec4 fragColor;

float roundedBoxSDF(vec2 center, vec2 halfSize, float r) {
    return length(max(abs(center) - halfSize + r, 0.0)) - r;
}

/**
 * 12 抽头旋转采样。
 *
 * 用「黄金角」把 12 个方向均匀铺开（每步 137.5°），再让半径在 0.35~1.0 之间
 * 交错取值 —— 这样少量抽头也能覆盖整个圆盘，不会出现明显的方向性条纹。
 * 中心额外取一次，避免面板整体偏暗。
 */
vec4 blurSample(vec2 uv, vec2 texel) {
    vec4 sum = texture(InputSampler, uv);
    float total = 1.0;

    const float GOLDEN = 2.39996323;   // 弧度，黄金角
    for (int i = 0; i < 12; i++) {
        float fi = float(i);
        float ang = fi * GOLDEN;
        // 半径在 0.35 / 0.675 / 1.0 三档间轮转，兼顾近距与远距
        float r = 0.35 + 0.325 * float(i % 3);
        vec2 off = vec2(cos(ang), sin(ang)) * r * uBlurRadius * texel;
        sum += texture(InputSampler, uv + off);
        total += 1.0;
    }
    return sum / total;
}

void main() {
    vec2 halfSize = uSize / 2.0;

    // 以矩形中心为原点的片元坐标
    vec2 p = gl_FragCoord.xy - uLocation - halfSize;

    float dist = roundedBoxSDF(p, halfSize, radius);

    // 边缘多半个像素的过渡，看起来更柔和。
    float smoothedAlpha = 1.0 - smoothstep(-1.0, 1.0, dist);
    if (smoothedAlpha <= 0.001) discard;

    vec2 uv = gl_FragCoord.xy / InputResolution;
    vec4 texColor = blurSample(uv, 1.0 / InputResolution);

    vec4 mixedColor = mix(texColor * uColor, uColor, clamp(uMix, 0.0, 1.0));
    mixedColor.a *= smoothedAlpha;

    // 描边
    if (uBorderWidth > 0.0) {
        float borderAlpha = 1.0 - smoothstep(uBorderWidth - 1.0, uBorderWidth, abs(dist));
        mixedColor = mix(mixedColor,
                         vec4(uBorderColor.rgb, uBorderColor.a * borderAlpha),
                         borderAlpha);
    }

    fragColor = vec4(mixedColor.rgb, mixedColor.a);
}
