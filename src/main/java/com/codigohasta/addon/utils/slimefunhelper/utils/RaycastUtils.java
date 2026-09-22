package com.codigohasta.addon.utils.slimefunhelper.utils;
import com.google.common.base.Preconditions;
import java.util.*;
import java.util.function.Predicate;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.RaycastContext;
public final class RaycastUtils {
private static final net.minecraft.client.MinecraftClient mc=net.minecraft.client.MinecraftClient.getInstance();
    public static BlockHitResult raycastSolidBlockResult(Entity e, Vec3d from, Vec3d to) {
        return mc.world.raycast(
                new RaycastContext(from, to, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, e));
    }
    public static boolean canRaycastHit(PlayerEntity player, float pitch, float yaw, Entity target) {
        return canRaycastHit(player, pitch, yaw, target, player.getEntityInteractionRange());
    }
    public static boolean canRaycastHit(PlayerEntity player, float pitch, float yaw, Entity target, double distance) {
        Vec3d vec3d = player.getEyePos();
        Vec3d look = EntityUtils.pitchYawToRotation(pitch, yaw);
        Vec3d raycast = look.normalize().multiply(distance);
        Box targetBox = target.getBoundingBox();
        return targetBox.raycast(vec3d, vec3d.add(raycast)).isPresent();
    }
    public static boolean canRaycastHit(PlayerEntity player, float pitch, float yaw, BlockPos pos, double distance) {
        Vec3d vec3d = player.getEyePos();
        Vec3d look = EntityUtils.pitchYawToRotation(pitch, yaw);
        Vec3d raycast = look.normalize().multiply(distance);
        Box targetBox = new Box(pos);
        return targetBox.raycast(vec3d, vec3d.add(raycast)).isPresent();
    }
    public static HitResult createEntityOnlyCrossHairResult(
            Entity camera, double entityInteractionRange, float tickDelta, Predicate<Entity> filter) {
        double d = entityInteractionRange;
        double e = MathHelper.square(d);
        Vec3d vec3d = camera.getCameraPosVec(tickDelta);
        Vec3d vec3d2 = camera.getRotationVec(tickDelta);
        Vec3d vec3d3 = vec3d.add(vec3d2.x * d, vec3d2.y * d, vec3d2.z * d);
        Box box = camera.getBoundingBox().stretch(vec3d2.multiply(d)).expand(1.0, 1.0, 1.0);
        EntityHitResult entityHitResult = ProjectileUtil.raycast(
                camera,
                vec3d,
                vec3d3,
                box,
                (entity) -> {
                    return !entity.isSpectator() && entity.canHit() && (filter == null || filter.test(entity));
                },
                e);
        return entityHitResult != null && entityHitResult.getPos().squaredDistanceTo(vec3d) < e
                ? ensureTargetInRange(entityHitResult, vec3d, entityInteractionRange)
                : null;
    }
    private static HitResult ensureTargetInRange(HitResult hitResult, Vec3d cameraPos, double interactionRange) {
        Vec3d vec3d = hitResult.getPos();
        if (!vec3d.isInRange(cameraPos, interactionRange)) {
            Vec3d vec3d2 = hitResult.getPos();
            Direction direction =
                    Direction.getFacing(vec3d2.x - cameraPos.x, vec3d2.y - cameraPos.y, vec3d2.z - cameraPos.z);
            return BlockHitResult.createMissed(vec3d2, direction, BlockPos.ofFloored(vec3d2));
        } else {
            return hitResult;
        }
    }
    public static boolean raycastAnySolidBlock(Entity e, Vec3d from, Vec3d to) {
        BlockHitResult bResult = raycastSolidBlockResult(e, from, to);
        return bResult != null && bResult.getType() != HitResult.Type.MISS;
    }
    public static Iterable<BlockPos> createRaycastBlockPoses(Vec3d start, Vec3d end) {
        return () -> createRaycastBlockPosIterator(start, end);
    }
    public static Iterable<BlockPos> createRaycastBlockPoses(Vec3d start, Vec3d end, boolean enableThreshold) {
        return () -> createRaycastBlockPosIterator(start, end, enableThreshold);
    }
    public static Iterator<BlockPos> createRaycastBlockPosIterator(Vec3d start, Vec3d end) {
        return createRaycastBlockPosIterator(start, end, false);
    }
    public static Iterator<BlockPos> createRaycastBlockPosIterator(Vec3d start, Vec3d end, boolean enableThreshold) {

        // 起点与终点重合时，只返回起点所在方块
        if (start.equals(end)) {
            List<BlockPos> blocks = new ArrayList<>();
            blocks.add(BlockPos.ofFloored(start));
            return blocks.iterator();
        }

        double threshold = enableThreshold ? -1.0E-7D : 0.0D;
        double d = MathHelper.lerp(threshold, end.x, start.x);
        double e = MathHelper.lerp(threshold, end.y, start.y);
        double f = MathHelper.lerp(threshold, end.z, start.z);
        double g = MathHelper.lerp(threshold, start.x, end.x);
        double h = MathHelper.lerp(threshold, start.y, end.y);
        double i = MathHelper.lerp(threshold, start.z, end.z);

        // check pos = start + t * (end - start)

        // origin
        BlockPos pos = BlockPos.ofFloored(g, h, i);

        // direction
        double m = d - g;
        double n = e - h;
        double o = f - i;
        int p = MathHelper.sign(m);
        int q = MathHelper.sign(n);
        int r = MathHelper.sign(o);

        // t + 这么多， 则在该轴上前进1单位
        // 1/d * d = 1;
        double s = p == 0 ? Double.MAX_VALUE : (double) p / m;
        double t = q == 0 ? Double.MAX_VALUE : (double) q / n;
        double u = r == 0 ? Double.MAX_VALUE : (double) r / o;

        return new Iterator<BlockPos>() {
            int j = pos.getX();
            int k = pos.getY();
            int l = pos.getZ();

            // 到达下一个整数边界所需要的值, 我们需要比较三者较小的,同时在更新的时候同步更新这些, 当三者均达到1的时候说明不再有方块
            double v = s * (p > 0 ? 1.0 - MathHelper.fractionalPart(g) : MathHelper.fractionalPart(g));
            double w = t * (q > 0 ? 1.0 - MathHelper.fractionalPart(h) : MathHelper.fractionalPart(h));
            double x = u * (r > 0 ? 1.0 - MathHelper.fractionalPart(i) : MathHelper.fractionalPart(i));

            BlockPos next = pos;

            @Override
            public boolean hasNext() {
                if (next != null) {
                    return true;
                } else {
                    if (v <= 1.0 || w <= 1.0 || x <= 1.0) {
                        if (v < w) {
                            if (v < x) {
                                j += p;
                                v += s;
                            } else {
                                l += r;
                                x += u;
                            }
                        } else if (w < x) {
                            k += q;
                            w += t;
                        } else {
                            l += r;
                            x += u;
                        }
                        next = (new BlockPos(j, k, l));
                        return true;
                    }
                }
                return false;
            }

            @Override
            public BlockPos next() {
                BlockPos nextPos = next;
                Preconditions.checkNotNull(nextPos);
                next = null;
                return nextPos;
            }
        };
    }

    public static BlockHitResult createRealHitResult(BlockPos pos) {
        Vec3d startVec = mc.player.getCameraPosVec(1.0f);
        Vec3d endVec = pos.toCenterPos();
        Vec3d ray = startVec.subtract(endVec);
        Direction dir = Direction.getFacing(ray.x, ray.y, ray.z);
        Vec3d crossTargetPose = ray.lengthSquared() > 0.25
                ? switch (dir) {
                    case DOWN -> startVec.subtract(ray.multiply((startVec.y - (endVec.y - 0.5)) / ray.y));
                    case UP -> startVec.subtract(ray.multiply((startVec.y - (endVec.y + 0.5)) / ray.y));
                    case NORTH -> startVec.subtract(ray.multiply((startVec.z - (endVec.z - 0.5)) / ray.z));
                    case SOUTH -> startVec.subtract(ray.multiply((startVec.z - (endVec.z + 0.5)) / ray.z));
                    case WEST -> startVec.subtract(ray.multiply((startVec.x - (endVec.x - 0.5)) / ray.x));
                    case EAST -> startVec.subtract(ray.multiply((startVec.x - (endVec.x + 0.5)) / ray.x));
                }
                : endVec.offset(dir, 0.5);
        return new BlockHitResult(crossTargetPose, dir, pos, false);
    }

    public static EntityHitResult createRealHitResult(Entity entity, Vec3d playerEyePos) {
        Box box = entity.getBoundingBox();
        Vec3d to = box.getCenter();
        var raycastSurface = box.raycast(playerEyePos, to);
        if (raycastSurface != null && raycastSurface.isPresent()) {
            return new EntityHitResult(entity, raycastSurface.get());
        } else {
            return new EntityHitResult(entity);
        }
    }

    public static BlockHitResult createHitResult(BlockPos pos, Vec3d playerEyePos) {
        Direction direction =
                Direction.getFacing(pos.toCenterPos().subtract(playerEyePos)).getOpposite();
        return createHitResult(pos, direction);
    }

    public static BlockHitResult createHitResult(BlockPos pos, Direction blockFace) {
        if (mc.player == null) return null;
        Vec3d endVec = pos.toCenterPos().offset(blockFace, 0.5);
        return new BlockHitResult(endVec, blockFace, pos, false);
    }

    public static Optional<Entity> rayTraceSpecificEntity(Predicate<Entity> entityPredicate) {
        if (mc.world == null || mc.player == null) return Optional.empty();
        if (mc.crosshairTarget != null && mc.crosshairTarget.getType() == HitResult.Type.ENTITY) {
            EntityHitResult entityHitResult = (EntityHitResult) mc.crosshairTarget;
            if (entityPredicate.test(entityHitResult.getEntity())) {
                return Optional.of(entityHitResult.getEntity());
            }
        }
        Vec3d rayCastStart = mc.player.getEyePos();
        Vec3d rayCastEnd =
                mc.player.getEyePos().add(mc.player.getRotationVector().multiply(6));
        Box including = new Box(rayCastStart, rayCastEnd);
        for (var re : mc.world.getOtherEntities(mc.player, including, entityPredicate)) {
            if (re.getBoundingBox().raycast(rayCastStart, rayCastEnd).isPresent()) {
                return Optional.of(re);
            }
        }
        return Optional.empty();
    }
}