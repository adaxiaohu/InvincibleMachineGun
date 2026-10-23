package com.codigohasta.addon.utils.gcore;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

public final class GcorePlayerUtil {

    private GcorePlayerUtil() {
    }

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
