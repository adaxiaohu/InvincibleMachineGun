#version 150

in vec3 Position;

void main() {
    // 全屏三角形对：片元着色器自行判断圆角矩形范围
    gl_Position = vec4(Position.xy, 0.0, 1.0);
}
