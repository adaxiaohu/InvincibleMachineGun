#version 330 core

//
// 1.21.11 说明：Meteor 只提供 UI_TEXTURED（2D 位置）管线，没有「世界空间 + 贴图」管线。
// 这里仿照 Meteor 自己的 pos_tex_color.vert，但把位置改成 3 分量，从而可以在 3D 事件里
// 用绝对世界坐标绘制。MeshData 这个 std140 UBO 由 Meteor 的 MeshRenderer 负责写入
// （u_Proj / u_ModelView），与 MeteorRenderPipelines 的约定一致。

layout (location = 0) in vec3 pos;
layout (location = 1) in vec2 texCoords;
layout (location = 2) in vec4 color;

layout (std140) uniform MeshData {
    mat4 u_Proj;
    mat4 u_ModelView;
};

out vec2 v_TexCoord;
out vec4 v_Color;

void main() {
    gl_Position = u_Proj * u_ModelView * vec4(pos, 1.0);

    v_TexCoord = texCoords;
    v_Color = color;
}
