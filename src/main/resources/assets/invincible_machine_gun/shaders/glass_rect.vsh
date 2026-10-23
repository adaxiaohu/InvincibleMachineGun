#version 150

in vec3 Position;

void main() {
    // 与 blur_rect.vsh 一致：全屏三角形对，直接把 NDC 坐标写出去，
    // 圆角矩形范围由片元着色器用 uLocation/uSize 自行判断。
    gl_Position = vec4(Position.xy, 0.0, 1.0);
}
