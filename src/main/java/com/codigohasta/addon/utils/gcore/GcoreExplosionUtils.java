package com.codigohasta.addon.utils.gcore;

import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.TntEntity;
import net.minecraft.fluid.FluidState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.explosion.Explosion;

import java.util.function.Function;

public final class GcoreExplosionUtils {

    private static final double INF = 1.7976931348623157E308;

    private GcoreExplosionUtils() {
    }

    private static boolean defaultStatePredicate(BlockState state, FluidState fluid, BlockPos pos) {
        return !state.isAir();
    }

    /** 爆炸伤害的伤害源。 */
    public static net.minecraft.entity.damage.DamageSource getDamageSource() {
        return MinecraftClient.getInstance().world.getDamageSources().explosion(null);
    }

    /** 构造该实体对应的射线上下文。 */
    public static RaycastContext raycastContext(Entity entity) {
        return new GcoreSimpleRaycastContext(
            RaycastContext.ShapeType.COLLIDER,
            RaycastContext.FluidHandling.NONE,
            ShapeContext.of(entity));
    }

    // ─────────────────────── 入口 ───────────────────────

    /**
     * 算爆炸伤害（便捷版）。
     *
     * <p>对应源 {@code getExpDamage$default(..., 56, null)} 的展开：
     * <ul>
     *     <li>{@code position} 默认取 {@code entity.getEntityPos()}
     *         （1.20.4 叫 {@code getPos()}，1.21.11 改名）</li>
     *     <li>{@code blockStateProvider} 默认取 {@code world::getBlockState}</li>
     *     <li>{@code statePredicate} 默认取 {@code !isAir}</li>
     * </ul>
     */
    public static float getExpDamage(Vec3d source, Entity entity, Explosion explosion) {
        return getExpDamage(source, entity, entity.getEntityPos(), null, explosion);
    }

    /** 见 {@link #getExpDamage(Vec3d, Entity, Explosion)}，可指定位置与方块查询器。 */
    public static float getExpDamage(Vec3d source, Entity entity, Vec3d position,
                                     Function<BlockPos, BlockState> blockStateProvider,
                                     Explosion explosion) {
        Function<BlockPos, BlockState> provider = blockStateProvider != null
            ? blockStateProvider
            : pos -> MinecraftClient.getInstance().world.getBlockState(pos);

        return calculateExplosionDamage(
            source, explosion, entity, position,
            raycastContext(entity),
            ExplosionVersion.MC1_20,
            provider,
            GcoreExplosionUtils::defaultStatePredicate);
    }

    // ─────────────────────── 主公式 ───────────────────────

    /**
     * 爆炸伤害主公式。
     *
     * <p>逐行对应源：
     * <pre>
     * if (entity.isImmuneToExplosion(explosion)) return 0;
     * size = explosion.getPower() * 2.0f;
     * dist = position.distanceTo(source) / size;
     * if (dist &gt; 1.0) return 0;
     * xDist = position.x - source.x;
     * yDist = position.y + (entity instanceof TntEntity ? 0.0 : entity.getStandingEyeHeight()) - source.y;
     * eyesDist = sqrt(xDist^2 + yDist^2 + zDist^2);
     * if (eyesDist == 0.0) return 0;
     * box = (position != entity.getEntityPos()) ? box.position(position) : entity.getBoundingBox();
     * strength = (1.0 - dist) * exposure;
     * </pre>
     *
     * <p><b>注意</b>：源算了 {@code eyesDist} 并检查是否为 0，但**从未用它参与
     * 后续计算** —— 保留这段（可能是源作者的遗留）。
     */
    public static float calculateExplosionDamage(Vec3d source, Explosion explosion, Entity entity,
                                                 Vec3d position, RaycastContext context,
                                                 ExplosionVersion version,
                                                 Function<BlockPos, BlockState> blockStateProvider,
                                                 GcoreStatePredicate statePredicate) {
        if (entity.isImmuneToExplosion(explosion)) {
            return 0.0f;
        }

        float size = explosion.getPower() * 2.0f;
        double dist = position.distanceTo(source) / (double) size;
        if (dist > 1.0) {
            return 0.0f;
        }

        double xDist = position.x - source.x;
        double yDist = position.y
            + (entity instanceof TntEntity ? 0.0 : (double) entity.getStandingEyeHeight())
            - source.y;
        double zDist = position.z - source.z;
        double eyesDist = Math.sqrt(xDist * xDist + yDist * yDist + zDist * zDist);
        if (eyesDist == 0.0) {
            return 0.0f;
        }

        Box box2 = entity.getBoundingBox();
        // ★ 引用比较（不是值比较）—— 忠实于源。
        //   而 CFR 把它误渲染成了 `!=`，容易被误导。
        //
        //   所以调用方传 entity.getEntityPos() 时引用必然相同 → 走 box2 分支；
        //   而 TP 锚的调用点传的都是独立 new 的 tpVec/targetVec/clickerVec，
        //   引用必然不同 → 走 position() 分支。与值比较结果一致。
        Box box = (position != entity.getEntityPos())
            ? GcoreMathUtilsKt.position(box2, position)
            : box2;

        float strengthScale = exposureAmount(source, box, context, version,
            blockStateProvider, statePredicate);
        double strength = (1.0 - dist) * (double) strengthScale;
        return (float) strength;
    }

    // ─────────────────────── 遮挡比例 ───────────────────────

    private static float exposureAmount(Vec3d source, Box box, RaycastContext context,
                                        ExplosionVersion version,
                                        Function<BlockPos, BlockState> blockStateProvider,
                                        GcoreStatePredicate statePredicate) {
        double xStep = 1.0 / ((box.maxX - box.minX) * 2.0 + 1.0);
        double yStep = 1.0 / ((box.maxY - box.minY) * 2.0 + 1.0);
        double zStep = 1.0 / ((box.maxZ - box.minZ) * 2.0 + 1.0);
        if (xStep < 0.0 || yStep < 0.0 || zStep < 0.0) {
            return 0.0f;
        }

        double xOffset = (1.0 - Math.floor(1.0 / xStep) * xStep) / 2.0;
        double zOffset = (1.0 - Math.floor(1.0 / zStep) * zStep) / 2.0;

        int[] noBlockLines = new int[1];
        int[] totalLines = new int[1];

        if (version == ExplosionVersion.MC1_8) {
            float xt = 0.0f;
            while (xt <= 1.0f) {
                float yt = 0.0f;
                while (yt <= 1.0f) {
                    float zt = 0.0f;
                    while (zt <= 1.0f) {
                        exposureAmountBlock(box, xOffset, zOffset, source, context,
                            blockStateProvider, statePredicate, noBlockLines, totalLines,
                            xt, yt, zt);
                        zt = (float) ((double) zt + zStep);
                    }
                    yt = (float) ((double) yt + yStep);
                }
                xt = (float) ((double) xt + xStep);
            }
        } else {
            GcoreMathUtils.forEachPoint(xStep, yStep, zStep, (x, y, z) ->
                exposureAmountBlock(box, xOffset, zOffset, source, context,
                    blockStateProvider, statePredicate, noBlockLines, totalLines, x, y, z));
        }

        return (float) noBlockLines[0] / (float) totalLines[0];
    }

    private static void exposureAmountBlock(Box box, double xOffset, double zOffset,
                                            Vec3d source, RaycastContext context,
                                            Function<BlockPos, BlockState> blockStateProvider,
                                            GcoreStatePredicate statePredicate,
                                            int[] noBlockLines, int[] totalLines,
                                            double xt, double yt, double zt) {
        double x = MathHelper.lerp(xt, box.minX, box.maxX);
        double y = MathHelper.lerp(yt, box.minY, box.maxY);
        double z = MathHelper.lerp(zt, box.minZ, box.maxZ);
        Vec3d vector = new Vec3d(x + xOffset, y, z + zOffset);

        Object hitResult = null;

        if (!vector.equals(source)) {
            // 起止各内缩 1e-7（避免起止点正好落在方块边界上）
            double startX = MathHelper.lerp(-1.0E-7, vector.x, source.x);
            double startY = MathHelper.lerp(-1.0E-7, vector.y, source.y);
            double startZ = MathHelper.lerp(-1.0E-7, vector.z, source.z);
            double endX = MathHelper.lerp(-1.0E-7, source.x, vector.x);
            double endY = MathHelper.lerp(-1.0E-7, source.y, vector.y);
            double endZ = MathHelper.lerp(-1.0E-7, source.z, vector.z);

            int currentX = MathHelper.floor(startX);
            int currentY = MathHelper.floor(startY);
            int currentZ = MathHelper.floor(startZ);

            var world = MinecraftClient.getInstance().world;
            BlockPos.Mutable pos = new BlockPos.Mutable(currentX, currentY, currentZ);

            // ── 起点所在格 ──
            BlockState state = blockStateProvider.apply(pos);
            if (statePredicate.test(state, null, pos)) {
                var shape = context.getBlockShape(state, world, pos);
                hitResult = world.raycastBlock(vector, source, pos, shape, state);
            }

            if (hitResult == null) {
                // ── DDA 步进 ──
                double deltaX = endX - startX;
                double deltaY = endY - startY;
                double deltaZ = endZ - startZ;
                int signX = MathHelper.sign(deltaX);
                int signY = MathHelper.sign(deltaY);
                int signZ = MathHelper.sign(deltaZ);

                double stepX = signX == 0 ? INF : (double) signX / deltaX;
                double stepY = signY == 0 ? INF : (double) signY / deltaY;
                double stepZ = signZ == 0 ? INF : (double) signZ / deltaZ;

                double progressX = stepX * (signX > 0
                    ? 1.0 - MathHelper.fractionalPart(startX) : MathHelper.fractionalPart(startX));
                double progressY = stepY * (signY > 0
                    ? 1.0 - MathHelper.fractionalPart(startY) : MathHelper.fractionalPart(startY));
                double progressZ = stepZ * (signZ > 0
                    ? 1.0 - MathHelper.fractionalPart(startZ) : MathHelper.fractionalPart(startZ));

                while (true) {
                    if (progressX > 1.0 && progressY > 1.0 && progressZ > 1.0) {
                        break;
                    }

                    if (progressX < progressY) {
                        if (progressX < progressZ) {
                            currentX += signX;
                            progressX += stepX;
                        } else {
                            currentZ += signZ;
                            progressZ += stepZ;
                        }
                    } else if (progressY < progressZ) {
                        currentY += signY;
                        progressY += stepY;
                    } else {
                        currentZ += signZ;
                        progressZ += stepZ;
                    }

                    pos.set(currentX, currentY, currentZ);
                    state = blockStateProvider.apply(pos);

                    if (!statePredicate.test(state, null, pos)) {
                        break;
                    }

                    var shape = context.getBlockShape(state, world, pos);
                    Object hit = world.raycastBlock(vector, source, pos, shape, state);
                    if (hit != null) {
                        hitResult = hit;
                        break;
                    }
                }
            }
        }

        if (hitResult == null) {
            noBlockLines[0] = noBlockLines[0] + 1;
        }
        totalLines[0] = totalLines[0] + 1;
    }

    /** 爆炸算法版本。默认 {@code MC1_20}。 */
    public enum ExplosionVersion {
        MC1_8("1.8"),
        MC1_18("1.18"),
        MC1_20("1.20");

        private final String choiceName;

        ExplosionVersion(String choiceName) {
            this.choiceName = choiceName;
        }

        public String getChoiceName() {
            return this.choiceName;
        }

        @Override
        public String toString() {
            return this.choiceName;
        }
    }
}
