#version 150

// 纯色 SDF 圆角矩形（移植自 CatLean 的 core/rect/rect.fsh 思路）。
//
// 为什么用 SDF 而不是「三角函数三角化」或「逐行填充」：
//   * 顶点恒定 6 个（全屏四边形），与圆角大小无关
//   * 边缘用 smoothstep 做抗锯齿，任意缩放都不会有锯齿或台阶
//   * 描边（uBorderWidth/uBorderColor）天然支持——到边界距离本来就算出来了
//
// 坐标系约定（与 blur_rect.fsh 一致）：gl_FragCoord 原点在帧缓冲左下角，
// 单位为像素；uLocation 是矩形左下角、uSize 是矩形尺寸，两者都在同一像素空间。

uniform vec2 uLocation;
uniform vec2 uSize;
uniform float radius;
uniform vec4 uColor;
uniform float uBorderWidth;
uniform vec4 uBorderColor;

out vec4 fragColor;

/** 圆角矩形的有符号距离：<0 在内部，0 在边界，>0 在外部。 */
float roundedBoxSDF(vec2 center, vec2 halfSize, float r) {
    return length(max(abs(center) - halfSize + r, 0.0)) - r;
}

void main() {
    vec2 halfSize = uSize / 2.0;

    // 以矩形中心为原点的片元坐标
    vec2 p = gl_FragCoord.xy - uLocation - halfSize;

    // 抗锯齿：在边界 1 像素内做渐变
    float dist = roundedBoxSDF(p, halfSize, radius);
    float alpha = 1.0 - smoothstep(0.0, 1.0, dist);

    if (alpha <= 0.001) discard;

    vec4 col = uColor;

    // 描边：到边界距离落在 [0, uBorderWidth] 内时混入描边色
    if (uBorderWidth > 0.0) {
        float borderAlpha = 1.0 - smoothstep(uBorderWidth - 1.0, uBorderWidth, abs(dist));
        col = mix(col, vec4(uBorderColor.rgb, uBorderColor.a), borderAlpha);
    }

    fragColor = vec4(col.rgb, col.a * alpha);
}
