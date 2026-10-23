package com.codigohasta.addon.utils.gcore;

import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public final class GcoreExtensionUtil {

    private GcoreExtensionUtil() {
    }

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
