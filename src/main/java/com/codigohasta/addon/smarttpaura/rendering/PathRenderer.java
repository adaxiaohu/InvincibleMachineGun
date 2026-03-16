package com.codigohasta.addon.smarttpaura.rendering;

import com.codigohasta.addon.smarttpaura.SmartTPAuraCore;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.renderer.Renderer3D;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.utils.render.color.Color;
import net.minecraft.util.math.Vec3d;
import java.util.List;

public class PathRenderer {
    // 玩家包围盒常量
    private static final double W = 0.3; // 半宽
    private static final double H = 1.8; // 高度

    /**
     * 基础渲染方法
     */
    public void renderPath(Renderer3D renderer, List<Vec3d> path, Color color, boolean isRaw) {
        if (path == null || path.size() < 2) return;

        // 1. 绘制连接线
        for (int i = 0; i < path.size() - 1; i++) {
            Vec3d s = path.get(i);
            Vec3d e = path.get(i + 1);
            renderer.line(s.x, s.y + 0.05, s.z, e.x, e.y + 0.05, e.z, color);
        }

        // 2. 绘制“人体”碰撞箱
        if (!isRaw) {
            for (Vec3d p : path) {
                renderer.box(
                    p.x - W, p.y, p.z - W, 
                    p.x + W, p.y + H, p.z + W, 
                    new Color(color.r, color.g, color.b, 40), color, ShapeMode.Both, 0
                );
            }
        }
    }

    /**
     * [核心修复] 必须添加这个方法，SmartTPAuraCore 依赖它
     * 同时渲染：原始A*路径(灰线)、优化后的发包点(亮框)、当前的虚影(红框)
     */
    public void renderFixedSnapshot(Render3DEvent event, List<Vec3d> path, Color pathColor, double step, SmartTPAuraCore core) {
        if (path == null || path.isEmpty()) return;

        // 1. 渲染原始 A* 路径 (作为背景参考，使用淡灰色线条)
        renderPath(event.renderer, path, new Color(150, 150, 150, 100), true);

        // 2. 计算并渲染真正发包的节点 (使用设定的高亮颜色)
        // 从 Core 获取切片后的路径
        List<Vec3d> chunked = core.getChunkedFromSnapshot(path, step);
        if (!chunked.isEmpty()) {
            renderPath(event.renderer, chunked, pathColor, false);
        }

        // 3. 渲染 Ghost 虚影 (当前数据包发到的位置)
        if (core.desyncPos != null) {
            Vec3d d = core.desyncPos;
            event.renderer.box(
                d.x - W, d.y, d.z - W, 
                d.x + W, d.y + H, d.z + W, 
                new Color(255, 0, 0, 80), new Color(255, 0, 0, 255), ShapeMode.Both, 0
            );
        }
    }
}