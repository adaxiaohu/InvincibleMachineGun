package com.codigohasta.addon.utils.gcore;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/**
 * 玩家状态与碰撞箱工具。
 *
 * <p>移植自 {@code dev.godden.core.Utils.PlayerUtil}（GCore）。
 *
 * <p><b>关键点</b>：{@link PlayerState#getBBox} 里的 {@code Box} 构造**先传 max 再传 min**
 * （如 {@code new Box(x+0.3, y+1.8, z+0.3, x-0.3, y, z-0.3)}）。
 * 这不是笔误 —— MC 的 {@code Box} 构造器内部用 {@code Math.min/max} 归一化
 * （已 javap 核实），所以两种传法等价。**保持原样以便与源码逐行对应。**
 */
public final class GcorePlayerUtil {

    private GcorePlayerUtil() {
    }

    /**
     * 玩家姿态：决定碰撞箱高度与眼高。
     *
     * <p>三种状态的尺寸与眼高与源一致：
     * <ul>
     *     <li>{@code Normal} —— 高 1.8，眼高 1.62（站立）</li>
     *     <li>{@code Sneak}  —— 高 1.5，眼高 1.27（潜行）</li>
     *     <li>{@code Creep}  —— 高 0.6，眼高 0.40（爬行 / 游泳 / 鞘翅）</li>
     * </ul>
     */
    public enum PlayerState {
        Normal,
        Sneak,
        Creep;

        /** 该姿态在 {@code vec} 处的碰撞箱。 */
        public Box getBBox(Vec3d vec) {
            return getBBox(vec.x, vec.y, vec.z);
        }

        /** 该姿态在 {@code (x, y, z)} 处的碰撞箱。 */
        public Box getBBox(double x, double y, double z) {
            return switch (this) {
                case Normal -> new Box(x + 0.3, y + 1.8, z + 0.3, x - 0.3, y, z - 0.3);
                case Sneak -> new Box(x + 0.3, y + 1.5, z + 0.3, x - 0.3, y, z - 0.3);
                case Creep -> new Box(x + 0.3, y + 0.6, z + 0.3, x - 0.3, y, z - 0.3);
            };
        }

        /** 该姿态的眼高。 */
        public double getEyeY() {
            return switch (this) {
                case Normal -> 1.62;
                case Sneak -> 1.27;
                case Creep -> 0.40;
            };
        }

        /**
         * 由玩家当前状态推断姿态。
         *
         * <p>版本差异：1.20.4 的 {@code isFallFlying()} 在 1.21.11 改名为
         * {@code isGliding()}（{@code method_6128}），语义相同。
         */
        public static PlayerState getStateFromPlayer(PlayerEntity player) {
            if (player.isGliding() || player.isInSwimmingPose()) return Creep;
            return player.isSneaking() ? Sneak : Normal;
        }
    }
}
