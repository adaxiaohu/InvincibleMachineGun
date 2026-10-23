#version 150

// 圆角矩形「双色渐变」——给血条做流动渐变。
//
// 为什么单开一个着色器而不是改 rect.fsh：
//   现有的 rect.fsh / rect_textured.fsh / shadow_rect.fsh 都有调用点（面板、头像、阴影），
//   改它们会波及全部；新增一个 .fsh 则现有调用点零影响。
//
// 坐标约定与 rect.fsh 完全一致（gl_FragCoord 原点在左下、uLocation 为矩形左下角、
// uSize 为尺寸，单位都是帧缓冲像素），因此共用 RECT_VS 和 drawFullscreen。
//
// 渐变方式：按矩形内的归一化 x 在两个颜色之间线性插值，再叠加一个随时间循环的
// 「流动」相位。用三角波（1 - |2*frac(u)-1|）而不是直接 frac，避免环绕处出现接缝
// —— 与 SakuraTextRenderer.gradT 的做法一致。
//
// uFlow：0 = 静态渐变；> 0 时参与流动相位推进（0~1 循环）。

uniform vec2 uLocation;   // 矩形左下角（帧缓冲像素）
uniform vec2 uSize;       // 矩形尺寸（像素）
uniform float radius;     // 圆角半径（像素）
uniform vec4 uColor1;     // 渐变左端色
uniform vec4 uColor2;     // 渐变右端色
uniform float uFlow;      // 流动相位 0~1；0 表示静态
uniform float uBands;     // 色带数量：>1 时整条内重复多段渐变

out vec4 fragColor;

float roundedBoxSDF(vec2 center, vec2 halfSize, float r) {
    return length(max(abs(center) - halfSize + r, 0.0)) - r;
}

void main() {
    vec2 halfSize = uSize / 2.0;
    vec2 p = gl_FragCoord.xy - uLocation - halfSize;

    // 抗锯齿：边界 1 像素内渐变
    float dist = roundedBoxSDF(p, halfSize, radius);
    float alpha = 1.0 - smoothstep(0.0, 1.0, dist);
    if (alpha <= 0.001) discard;

    // 矩形内的归一化 x（0 = 左缘，1 = 右缘）
    float u = clamp((gl_FragCoord.x - uLocation.x) / max(uSize.x, 1.0), 0.0, 1.0);

    float t;
    if (uFlow > 0.0) {
        // 流动：色带按 x 重复 uBands 次，并整体平移 uFlow
        float s = u * max(uBands, 1.0) + uFlow;
        float frac = s - floor(s);
        t = 1.0 - abs(2.0 * frac - 1.0);   // 三角波：左端色→右端色→左端色，无缝
    } else {
        t = u;
    }

    vec4 col = mix(uColor1, uColor2, t);
    fragColor = vec4(col.rgb, col.a * alpha);
}
