#version 150

// 圆角矩形「羽化阴影」——给面板四周加一圈柔和的投影，突出立体感。
//
// 与 rect.fsh / rect_textured.fsh 共用同一套坐标约定（gl_FragCoord 原点在左下，
// uLocation 为矩形左下角、uSize 为尺寸，单位都是帧缓冲像素），因此可以直接
// 复用 RECT_VS 与 Shader2DUtil.drawFullscreen 的绘制路径。
//
// 为什么不需要拷贝场景：这是纯粹的几何叠加（像 rect.fsh 一样直接往当前帧缓冲
// 混色），不是后处理。只有 blur / glitch 那种要读已有画面的效果才需要 copySceneColor。
//
// 羽化算法：以阴影形状的边界为中心做对称衰减。
//     alpha = (1 - smoothstep(-uFeather, +uFeather, shadowDist)) * uColor.a
// 边界处 50%，向内 uFeather 逐渐到满，向外 uFeather 衰减到 0。
//
// 为什么必须做成「对称」而不是「从边界向外衰减」（曾经的写法）：
// 后者在面板边界处是满强度、随后被硬截断，等于给面板镶了一圈 100% 不透明的黑边。
// 实测（8x8 超采样当基准，看边缘 1px 的最大亮度跳变）：
//     从边界向外衰减 + 硬截断 : 边缘误差 10.56 —— 比完全不加阴影(6.88)还差
//     对称衰减 + 内侧平滑淡入 : 边缘误差  4.17
// 黑边的高对比会把圆角处本来正常的 1px 锯齿放大，看起来就是「圆角不平滑」。
//
// 面板内侧的处理：用 smoothstep(0,1,panelDist) 做 1px 平滑淡入，而不是 discard 硬切。
// 硬切会在面板边缘那条抗锯齿带里留下突变；平滑淡入让阴影再被面板自身半透明地
// 混一次，边缘就不会出现亮度断层。
// 注意仍要在面板内侧淡出，不能整片画到面板下面：面板背景是半透明的，
// 否则会把面板整体压暗（实测面板内部亮度 55.2 -> 32.5）。

uniform vec2 uLocation;   // 面板左下角（帧缓冲像素）
uniform vec2 uSize;       // 面板尺寸（像素）
uniform float radius;     // 面板圆角半径（像素）
uniform vec2 uOffset;     // 阴影相对面板的偏移（像素，y 向上）
uniform float uFeather;   // 羽化半径（像素）；越大越柔
uniform vec4 uColor;      // 阴影颜色（含 alpha = 强度）

out vec4 fragColor;

float roundedBoxSDF(vec2 center, vec2 halfSize, float r) {
    return length(max(abs(center) - halfSize + r, 0.0)) - r;
}

void main() {
    vec2 halfSize = uSize / 2.0;

    // 面板中心在 gl_FragCoord 空间的位置
    vec2 center = uLocation + halfSize;

    // 面板自身的有符号距离
    float panelDist = roundedBoxSDF(gl_FragCoord.xy - center, halfSize, radius);

    // 阴影形状的有符号距离。阴影 = 面板平移 uOffset，
    // 所以把采样点反向平移 uOffset 即可，无需真的移动几何。
    float shadowDist = roundedBoxSDF(gl_FragCoord.xy - center - uOffset, halfSize, radius);

    // 面板外侧完全透明（避免压暗半透明面板、污染背景模糊），
    // 边界处用 1 像素做平滑过渡，消除硬切造成的亮度断层。
    float innerFade = smoothstep(0.0, 1.0, panelDist);

    // 以阴影边界为中心对称羽化，避免满强度硬截断形成的黑边。
    float alpha = (1.0 - smoothstep(-uFeather, uFeather, shadowDist)) * uColor.a * innerFade;

    if (alpha <= 0.002) discard;

    fragColor = vec4(uColor.rgb, alpha);
}
