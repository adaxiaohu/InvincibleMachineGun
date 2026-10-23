#version 150

// 贴图 SDF 圆角矩形 —— 用于头像（皮肤 / 生物贴图）的圆角裁剪。
//
// 移植自 CatLean 的 core/rect/textured_rect.fsh 思路，但这里做了两处关键调整：
//
//  1. **采样方式用 texelFetch 而不是 texture()**。MC 的皮肤贴图默认是 LINEAR + mipmap，
//     采样时会被插值糊掉（正是「头像模糊」的成因）。texelFetch 直接取单个纹素，
//     不受贴图过滤参数影响，也不受 1.21.9+ sampler object 的影响，像素精确。
//
//  2. 保留本项目既有的「全屏四边形 + gl_FragCoord + uLocation/uSize」约定，
//     与 blur_rect.fsh / rect.fsh 一致，便于共用 Shader2DUtil 的绘制路径。
//
// UV 语义与 NanoVG 的 nvgImagePattern 保持一致：从贴图左上角 (uUvOrigin) 开始，
// 按 uUnit（cover 缩放系数，像素/贴图像素）映射到头像矩形。

uniform sampler2D u_Texture;

uniform vec2 uLocation;   // 头像矩形左下角（帧缓冲像素）
uniform vec2 uSize;       // 头像矩形尺寸（像素）
uniform float radius;     // 圆角半径（像素）
uniform vec2 uTexSize;    // 贴图像素尺寸
uniform vec2 uUvOrigin;   // 贴图上的起始像素（uv.u, uv.v）
uniform float uUnit;      // cover 缩放系数：头像像素 / 贴图像素
uniform vec4 uColor;      // 染色（含整体 alpha）
uniform float uRotation;  // ★ 矩形内旋转角（弧度）；0 = 不旋转

out vec4 fragColor;

float roundedBoxSDF(vec2 center, vec2 halfSize, float r) {
    return length(max(abs(center) - halfSize + r, 0.0)) - r;
}

void main() {
    vec2 halfSize = uSize / 2.0;

    // 头像内的像素偏移（gl_FragCoord 原点在左下，p.y 向上）
    vec2 p = gl_FragCoord.xy - uLocation;

    // 转成「左上为原点、y 向下」的头像内坐标，与 UV 约定一致
    vec2 face = vec2(p.x, uSize.y - p.y);

    // ★ 旋转：把【采样坐标】绕矩形中心反向旋转，视觉上等价于把图像正向旋转。
    //
    //   为什么旋转必须在这里做（而不是在 Java 侧变换几何）：
    //     本项目所有 2D 绘制都走「全屏四边形 + 片元着色器按 gl_FragCoord 裁剪」，
    //     顶点固定是 NDC ±1，所以在 Java 侧做矩阵旋转【完全无效】。
    //     放在采样坐标上还有个大好处：SVG 只需栅格化【一次】，
    //     任意角度复用同一张纹理 —— 不会像「按角度重新栅格化」那样泄漏纹理。
    if (uRotation != 0.0) {
        vec2 c = face - halfSize;                 // 移到以中心为原点
        float s = sin(uRotation), co = cos(uRotation);
        c = mat2(co, -s, s, co) * c;              // 反向旋转
        face = c + halfSize;
    }

    // 映射到贴图像素坐标
    vec2 texelPos = face / uUnit + uUvOrigin;

    // 采样必须夹在本矩形覆盖的贴图区域内。
    // 抗锯齿带（dist > 0）有 1 个像素落在矩形之外，那里 face 会变成负数或超过 uSize，
    // 于是 texelPos 落到区域之外，texelFetch 取到贴图上一行/一列无关的内容——
    // 表现为头像边缘（尤其下边缘）浮出一条 1px 细线。夹取等价于 CLAMP_TO_EDGE：
    // 区域内的采样完全不变，只有边缘那圈改用边界纹素。
    vec2 region = uSize / uUnit;                                  // 本矩形覆盖的贴图像素数
    ivec2 texelMin = ivec2(floor(uUvOrigin));
    ivec2 texelMax = ivec2(floor(uUvOrigin + max(region - 1.0, 0.0)));

    ivec2 texel = clamp(ivec2(floor(texelPos)), texelMin, texelMax);

    vec4 tex = texelFetch(u_Texture, texel, 0);

    // 圆角抗锯齿
    float dist = roundedBoxSDF(p - halfSize, halfSize, radius);
    float alpha = 1.0 - smoothstep(0.0, 1.0, dist);
    if (alpha <= 0.001) discard;

    fragColor = vec4(tex.rgb * uColor.rgb, tex.a * uColor.a * alpha);
}
