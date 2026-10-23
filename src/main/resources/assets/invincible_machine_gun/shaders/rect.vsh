#version 150

// SDF 圆角矩形专用的全屏四边形顶点着色器。
//
// 与 blur_rect.vsh 同款做法：直接把 NDC 坐标写出去，真正的形状由片元着色器用
// 有符号距离场（SDF）判断。因此无论圆角矩形多大、圆角多小，顶点永远只有 6 个
// （两个三角形拼成的全屏四边形），开销与圆角尺寸无关。
//
// 「全屏四边形 + gl_FragCoord + uLocation/uSize」约定（见 blur_rect.fsh），
// 以便复用 Shader2DUtil 里已有的 uniform 设置与 VAO 代码。

in vec3 Position;

void main() {
    gl_Position = vec4(Position.xy, 0.0, 1.0);
}
