package com.codigohasta.addon.utils.gcore;

import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 坐标遍历工具。
 *
 * <p>移植自 {@code dev.godden.core.Utils.ExtensionUtil}（GCore）。
 * 源是 Kotlin `object`，Java 里改为静态方法。
 *
 * <p>三个遍历方法对应源里的 {@code forEachXYZ} / {@code forEachAToB} / {@code forEachTpVec}。
 * 注意源里用 Kotlin lambda，Java 里换成 {@link GcoreTriConsumer} 与 {@link Consumer}。
 */
public final class GcoreExtensionUtil {

    private GcoreExtensionUtil() {
    }

    /**
     * 以 {@code (x, y, z)} 为中心，在 {@code ±scanBorder} 范围内遍历所有整数坐标。
     *
     * <p>等价于源里三层嵌套的 {@code while} 循环（协程风格的展开），
     * Java 里用普通 {@code for} 表达，边界与顺序完全一致。
     */
    public static void forEachXYZ(int x, int y, int z, int scanBorder,
                                  GcoreTriConsumer<Integer, Integer, Integer> func) {
        for (int xf = x - scanBorder; xf <= x + scanBorder; xf++) {
            for (int yf = y - scanBorder; yf <= y + scanBorder; yf++) {
                for (int zf = z - scanBorder; zf <= z + scanBorder; zf++) {
                    func.accept(xf, yf, zf);
                }
            }
        }
    }

    /**
     * 遍历由 {@code (x,y,z)} 到 {@code (toX,toY,toZ)} 围成的长方体（含两端）。
     *
     * <p>源先把两端取 min/max 归一化，再遍历 —— 所以顺序无关。
     */
    public static void forEachAToB(int x, int y, int z, int toX, int toY, int toZ,
                                   GcoreTriConsumer<Integer, Integer, Integer> func) {
        int minX = Math.min(x, toX), maxX = Math.max(x, toX);
        int minY = Math.min(y, toY), maxY = Math.max(y, toY);
        int minZ = Math.min(z, toZ), maxZ = Math.max(z, toZ);

        for (int xf = minX; xf <= maxX; xf++) {
            for (int yf = minY; yf <= maxY; yf++) {
                for (int zf = minZ; zf <= maxZ; zf++) {
                    func.accept(xf, yf, zf);
                }
            }
        }
    }

    /**
     * 遍历「可传送的点」：先收集所有不卡方块的坐标，分两批回调。
     *
     * <p>源的两阶段设计（很重要，保持原样）：
     * <ol>
     *     <li><b>第一批</b>：与 {@code fromVec} 之间**能互相通行**的点，立即回调</li>
     *     <li><b>第二批</b>：其余点（视线被挡），在第一批之后回调</li>
     * </ol>
     * 这样调用方就能「优先选直达的点」，而把需要穿墙的点留到后面。
     */
    public static void forEachTpVec(Vec3d fromVec, int x, int y, int z, int scanBorder,
                                    Consumer<Vec3d> func) {
        List<Vec3d> list = new ArrayList<>();

        // 阶段 1：收集所有不卡方块的坐标（y > -63）
        for (int xf = x - scanBorder; xf <= x + scanBorder; xf++) {
            for (int yf = y - scanBorder; yf <= y + scanBorder; yf++) {
                for (int zf = z - scanBorder; zf <= z + scanBorder; zf++) {
                    if (yf < -63) continue;
                    Vec3d vec = new Vec3d(xf + 0.5, yf + 0.0, zf + 0.5);
                    if (!GcoreBlockUtil.checkNoPosCollie(vec)) continue;
                    list.add(vec);
                }
            }
        }

        // 阶段 2：能直达的先回调，其余延后
        List<Vec3d> noLineTpVec = new ArrayList<>();
        for (Vec3d it : list) {
            if (GcoreBlockUtil.checkCanMove(it, fromVec) && GcoreBlockUtil.checkCanMove(fromVec, it)) {
                func.accept(it);
                continue;
            }
            noLineTpVec.add(it);
        }
        for (Vec3d it : noLineTpVec) {
            func.accept(it);
        }
    }
}
