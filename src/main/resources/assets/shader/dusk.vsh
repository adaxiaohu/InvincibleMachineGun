#version 150

in vec3 Position;
out vec2 texCoord;

void main() {
    // 强制全屏 NDC 坐标，解决错位问题
    gl_Position = vec4(Position.xy, 0.0, 1.0);
    texCoord = Position.xy * 0.5 + 0.5;
}