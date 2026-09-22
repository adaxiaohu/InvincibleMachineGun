package com.codigohasta.addon.utils.slimefunhelper.utils;
import net.minecraft.text.Text;
import net.minecraft.entity.player.PlayerEntity;
import org.jetbrains.annotations.Nullable;
import com.codigohasta.addon.modules.slimefunhelper.PlayerStateManager;
import com.codigohasta.addon.utils.slimefunhelper.utils.entity.PlayerInputUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.UseEffectsComponent;
import net.minecraft.entity.*;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.*;
import net.minecraft.util.math.*;
public final class EntityUtils {
private static final net.minecraft.client.MinecraftClient mc=net.minecraft.client.MinecraftClient.getInstance();
    public static Vec2f rotationToPitchYaw(Vec3d vec) {
        return new Vec2f(rotationToPitch(vec), rotationToYaw(vec));
    }
    public static Vec3d simulateTravelInFluidVelocity(
            Vec3d velocity, boolean lastInWater, boolean lastInLava, boolean hasGravity) {
        if (lastInWater) {
            return simulateTravelInWaterVelocity(velocity, hasGravity);
        }
        if (lastInLava) {
            return simulateTravelInLavaVelocity(velocity, hasGravity);
        }
        return velocity;
    }
    public static float normalizeYaw(float yaw) {
        if (yaw > -1E-5 && yaw < 360 + 1E-5) {
            return yaw;
        }
        return ((yaw % 360.0F + 720.0F + 180.0F) % 360.0F) - 180.0F;
    }
    public static Vec3d rotateVec(Vec3d vec, float pitch, float yaw) {
        Vec3d facing = vec.normalize();
        double len = vec.length();
        Vec2f py = rotationToPitchYaw(facing);
        Vec3d rotated = pitchYawToRotation(py.x + pitch, py.y + yaw);
        return rotated.normalize().multiply(len);
    }
    public static float getSafeYawDiff(float oldYaw, float newYaw) {
        float diff = newYaw - oldYaw;
        float normalizedDiff = (diff % 360.0F + 720.0F + 180.0F) % 360.0F - 180.0F; // 归一化到 [-180,180]
        if (oldYaw > 1000 && normalizedDiff > 179) {
            normalizedDiff -= 360.0F;
        } else if (oldYaw < -1000 && normalizedDiff < -179) {
            normalizedDiff += 360.0F;
        }
        return normalizedDiff;
    }
    public static Vec3d pitchYawToRotation(float pitch, float yaw) {
        float f = pitch * 0.017453292F;
        float g = -yaw * 0.017453292F;
        float h = MathHelper.cos(g);
        float i = MathHelper.sin(g);
        float j = MathHelper.cos(f);
        float k = MathHelper.sin(f);
        return new Vec3d((double) (i * j), (double) (-k), (double) (h * j));
    }
    public static void setEntityPitchSafe(Entity entity, float newPitch) {
        entity.setPitch(getSafePitch(newPitch));
    }
    public static Vec3d withStrafe(Vec3d self, double speed, double strength, PlayerInputUtils.Input input, float yaw) {
        // 输入无效（无移动输入）时水平速度清零
        if (input != null && !input.hasWASDMovement()) {
            return new Vec3d(0.0, self.y, 0.0);
        }

        // 保留部分原有水平速度
        double prevX = self.x * (1.0 - strength);
        double prevZ = self.z * (1.0 - strength);
        double useSpeed = speed * strength;

        // 根据 yaw 计算新方向的单位向量，并叠加原速度
        double angle = Math.toRadians(yaw);
        double x = -Math.sin(angle) * useSpeed + prevX;
        double z = Math.cos(angle) * useSpeed + prevZ;

        return new Vec3d(x, self.y, z);
    }
    public static Vec3d withStrafe(Vec3d self, double speed) {

        return withStrafe(self, speed, 1.0D);
    }
    public static Vec3d withStrafe(Vec3d self, double speed, double strength) {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        PlayerInputUtils.Input input = PlayerInputUtils.of(player);
        float yaw = getMovementDirectionOfInput(player.getYaw(), input);
        return withStrafe(self, speed, strength, input, yaw);
    }
    public static Vec3d lookCoordToPos(float pitch, float yaw, double x, double y, double z) {
        Vec2f vec2f = new Vec2f(pitch, yaw);

        float f = MathHelper.cos((vec2f.y + 90.0F) * 0.017453292F);

        float g = MathHelper.sin((vec2f.y + 90.0F) * 0.017453292F);

        float h = MathHelper.cos(-vec2f.x * 0.017453292F);

        float i = MathHelper.sin(-vec2f.x * 0.017453292F);
        float j = MathHelper.cos((-vec2f.x + 90.0F) * 0.017453292F);
        float k = MathHelper.sin((-vec2f.x + 90.0F) * 0.017453292F);
        Vec3d vec3d2 = new Vec3d((double) (f * h), (double) i, (double) (g * h));
        Vec3d vec3d3 = new Vec3d((double) (f * j), (double) k, (double) (g * j));
        Vec3d vec3d4 = vec3d2.crossProduct(vec3d3).multiply(-1.0);
        double d = vec3d2.x * z + vec3d3.x * y + vec3d4.x * x;
        double e = vec3d2.y * z + vec3d3.y * y + vec3d4.y * x;
        double l = vec3d2.z * z + vec3d3.z * y + vec3d4.z * x;
        return new Vec3d(d, e, l);
    }
    public static Vec2f applyMovementFactors(Entity entity, Vec2f vec2f) {
        if (vec2f.lengthSquared() == 0) {
            return vec2f;
        }
        if (entity instanceof ClientPlayerEntity p) {
            vec2f = vec2f.multiply(0.98F);
            if (p.isUsingItem() && !p.hasVehicle()) {
                vec2f = vec2f.multiply(p.getActiveItem()
                        .getOrDefault(DataComponentTypes.USE_EFFECTS, UseEffectsComponent.DEFAULT)
                        .speedMultiplier());
            }
            if (p.shouldSlowDown()) {
                float f = (float) p.getAttributeValue(EntityAttributes.SNEAKING_SPEED);
                vec2f = vec2f.multiply(f);
            }
            float f = vec2f.length();
            vec2f = vec2f.multiply(1.0F / f);
            float g = getDirectionalMovementSpeedMultiplier(vec2f);
            float h = Math.min(f * g, 1.0F);
            return vec2f.multiply(h);
        } else {
            return vec2f;
        }
    }
    public static float getSafePitch(float newPitch) {
        // fix: 当玩家低头的时候不要改成抬头
        return normalizePitch(newPitch);
    }
    public static float rotationToYaw(Vec3d vec) {
        return (float) Math.toDegrees(Math.atan2(-vec.x, vec.z));
    }
    public static float rotationToYaw(Direction direction) {
        switch (direction) {
            case SOUTH:
                return 0.0F;
            case WEST:
                return 90.0F;
            case NORTH:
                return 180.0F;
            case EAST:
                return -90.0F;
            default:
                // 对于UP/DOWN，返回0或任意值，但通常不会调用
                return 0.0F;
        }
    }
    public static void setEntityYawSafe(Entity entity, float newYaw) {
        newYaw = getSafeYaw(entity.getYaw(), newYaw);
        entity.setYaw(newYaw);
    }
    private static float getDirectionalMovementSpeedMultiplier(Vec2f vec) {
        float f = Math.abs(vec.x);
        float g = Math.abs(vec.y);
        float h = g > f ? f / g : g / f;
        return MathHelper.sqrt(1.0F + MathHelper.square(h));
    }
    public static float rotationToPitch(Vec3d vec) {
        return (float) Math.toDegrees(Math.asin(-vec.y));
    }
    public static float getSafeYaw(float oldYaw, float newYaw) {
        //        if(newYaw == -180.0 || newYaw == 180.0)return newYaw;
        //        float oldYaw = entity.getYaw();
        float diff = getSafeYawDiff(oldYaw, newYaw);
        return oldYaw + diff;
    }
    public static double getEffectiveGravity(ClientPlayerEntity player) {
        boolean bl = player.getVelocity().y <= 0.0;
        return bl && player.hasStatusEffect(StatusEffects.SLOW_FALLING)
                ? Math.min(player.getFinalGravity(), 0.01)
                : player.getFinalGravity();
    }
    public static Vec3d movementInputToVelocity(Vec3d movementInput, float speed, float yaw) {
        double d = movementInput.lengthSquared();
        if (d < 1.0E-7) {
            return Vec3d.ZERO;
        } else {
            Vec3d vec3d = (d > 1.0 ? movementInput.normalize() : movementInput).multiply((double) speed);
            float f = MathHelper.sin(yaw * 0.017453292F);
            float g = MathHelper.cos(yaw * 0.017453292F);
            return new Vec3d(
                    vec3d.x * (double) g - vec3d.z * (double) f, vec3d.y, vec3d.z * (double) g + vec3d.x * (double) f);
        }
    }
    public static boolean isRotationDifferent(float lastPitch, float pitch, float lastYaw, float yaw) {
        return Math.abs(pitch - lastPitch) > 1e-2 || Math.abs(EntityUtils.getSafeYawDiff(lastYaw, yaw)) > 1e-2;
    }
    private static Vec3d simulateTravelInLavaVelocity(Vec3d velocity, boolean hasGravity) {
        PlayerInputUtils.Input input = PlayerStateManager.INSTANCE.lastInput;
        Vec3d movementInput = new Vec3d(input.sidewaysSpeed(), input.upwardSpeed(), input.forwardSpeed());
        boolean falling = velocity.y <= 0.0;
        double y = mc.player.getY();
        double gravity = EntityUtils.getEffectiveGravity(mc.player);
        Vec3d nextVelocity =
                velocity.add(EntityUtils.movementInputToVelocity(movementInput, 0.02F, mc.player.getYaw()));
        if (mc.player.getFluidHeight(net.minecraft.registry.tag.FluidTags.LAVA) <= mc.player.getSwimHeight()) {
            nextVelocity = nextVelocity.multiply(0.5, 0.800000011920929, 0.5);
            nextVelocity =
                    simulateApplyFluidMovingSpeed(gravity, falling, nextVelocity, hasGravity, mc.player.isSprinting());
        } else {
            nextVelocity = nextVelocity.multiply(0.5);
        }
        if (gravity != 0.0) {
            nextVelocity = nextVelocity.add(0.0, -gravity / 4.0, 0.0);
        }
        return simulateResetVerticalVelocityInFluid(nextVelocity, y);
    }
    public static float normalizePitch(float newPitch) {
        if (newPitch < 90.0F + 1E-5 && newPitch > -90.0F - 1E-5) return newPitch;
        return (newPitch % 180.0F + 720.0F + 90.0F) % 180.0F - 90.0F; // 归一化到 [-90, 90]
    }
    public static float getMovementDirectionOfInput(float facingYaw, PlayerInputUtils.Input input) {
        boolean forwards = input.forward() && !input.backward();
        boolean backwards = input.backward() && !input.forward();
        boolean left = input.left() && !input.right();
        boolean right = input.right() && !input.left();

        float actualYaw = facingYaw;
        float forward = 1.0f;

        if (backwards) {
            actualYaw += 180f;
            forward = -0.5f;
        } else if (forwards) {
            forward = 0.5f;
        }

        if (left) {
            actualYaw -= 90f * forward;
        }
        if (right) {
            actualYaw += 90f * forward;
        }

        return MathHelper.wrapDegrees(actualYaw);
    }
    public static Vec3d calculateGlidingVelocity(
            ClientPlayerEntity player, Vec3d oldVelocity, Vec3d rotationVector, boolean hasGravity) {
        Vec3d look = rotationVector;
        float pitch = rotationToPitch(rotationVector);
        float pitchRad = pitch * 0.017453292F;
        double lookHorizLen = Math.sqrt(look.x * look.x + look.z * look.z);
        double initialHorizSpeed = oldVelocity.horizontalLength();
        double gravity = hasGravity ? getEffectiveGravity(player) : 0;
        double cosPitchSq = MathHelper.square(Math.cos(pitchRad));

        // 1. 重力影响
        double newY = oldVelocity.y + gravity * (cosPitchSq * 0.75 - 1.0);
        Vec3d vel = new Vec3d(oldVelocity.x, newY, oldVelocity.z);

        // 2. 下降时的抬升效应
        if (newY < 0.0 && lookHorizLen > 0.0) {
            double lift = newY * -0.1 * cosPitchSq;
            vel = vel.add(look.x * lift / lookHorizLen, lift, look.z * lift / lookHorizLen);
        }

        // 3. 俯冲加速（向下看时）
        if (pitchRad < 0.0F && lookHorizLen > 0.0) {
            double dive = initialHorizSpeed * (-MathHelper.sin(pitchRad)) * 0.04;
            vel = vel.add(-look.x * dive / lookHorizLen, dive * 3.2, -look.z * dive / lookHorizLen);
        }

        // 4. 水平速度向视线方向修正
        if (lookHorizLen > 0.0) {
            double targetScale = initialHorizSpeed / lookHorizLen;
            vel = vel.add((look.x * targetScale - vel.x) * 0.1, 0.0, (look.z * targetScale - vel.z) * 0.1);
        }

        // 5. 空气阻力（水平0.99，垂直0.98）
        return vel.multiply(0.99, 0.98, 0.99);
    }
    public static boolean isEntityValid(@Nullable Entity entity) {
        return entity != null
                && entity.isAlive()
                && !entity.isRemoved()
                && mc.world != null
                && mc.world == entity.getEntityWorld()
                && mc.world.getEntityLookup().get(entity.getUuid()) == entity;
    }
    private static Vec3d simulateTravelInWaterVelocity(Vec3d velocity, boolean hasGravity) {
        PlayerInputUtils.Input input = PlayerStateManager.INSTANCE.lastInput;

        Vec2f vec2f =
                EntityUtils.applyMovementFactors(mc.player, new Vec2f(input.sidewaysSpeed(), input.forwardSpeed()));
        Vec3d movementInput = new Vec3d(vec2f.x, 0, vec2f.y);
        boolean falling = velocity.y <= 0.0;
        double y = mc.player.getY();
        double gravity = EntityUtils.getEffectiveGravity(mc.player);
        float drag = mc.player.isSprinting() ? 0.9F : 0.8F;
        float acceleration = 0.02F;
        float efficiency = (float)
                mc.player.getAttributeValue(net.minecraft.entity.attribute.EntityAttributes.WATER_MOVEMENT_EFFICIENCY);
        if (!mc.player.isOnGround()) {
            efficiency *= 0.5F;
        }
        if (efficiency > 0.0F) {
            drag += (0.54600006F - drag) * efficiency;
            acceleration += (mc.player.getMovementSpeed() - acceleration) * efficiency;
        }
        if (mc.player.hasStatusEffect(StatusEffects.DOLPHINS_GRACE)) {
            drag = 0.96F;
        }
        Vec3d nextVelocity =
                velocity.add(EntityUtils.movementInputToVelocity(movementInput, acceleration, mc.player.getYaw()));
        if (mc.player.horizontalCollision && mc.player.isClimbing()) {
            nextVelocity = new Vec3d(nextVelocity.x, 0.2, nextVelocity.z);
        }
        nextVelocity = nextVelocity.multiply((double) drag, 0.800000011920929, (double) drag);
        nextVelocity =
                simulateApplyFluidMovingSpeed(gravity, falling, nextVelocity, hasGravity, mc.player.isSprinting());
        return simulateResetVerticalVelocityInFluid(nextVelocity, y);
    }
    private static Vec3d simulateResetVerticalVelocityInFluid(Vec3d velocity, double y) {
        if (mc.player.horizontalCollision
                && mc.player.doesNotCollide(
                        velocity.x, velocity.y + 0.6000000238418579 - mc.player.getY() + y, velocity.z)) {
            return new Vec3d(velocity.x, 0.30000001192092896, velocity.z);
        }
        return velocity;
    }
    private static Vec3d simulateApplyFluidMovingSpeed(
            double gravity, boolean falling, Vec3d velocity, boolean hasGravity, boolean isSprinting) {
        if (gravity != 0.0 && hasGravity && !isSprinting) {
            double nextY;
            if (falling && Math.abs(velocity.y - 0.005) >= 0.003 && Math.abs(velocity.y - gravity / 16.0) < 0.003) {
                nextY = -0.003;
            } else {
                nextY = velocity.y - gravity / 16.0;
            }
            return new Vec3d(velocity.x, nextY, velocity.z);
        }
        return velocity;
    }
public static Text getEntityDisplayable(Entity target) {
        return target instanceof PlayerEntity player
                ? Text.literal(player.getNameForScoreboard())
                : target.getDisplayName();
    }

    public static Vec2f directionToPitchYaw(Direction direction) {
        switch (direction) {
            case DOWN:
                return new Vec2f(89.9F, 0);
            case UP:
                return new Vec2f(-89.9F, 0);
            case NORTH:
                return new Vec2f(0, 180);
            case SOUTH:
                return new Vec2f(0, 0);
            case WEST:
                return new Vec2f(0, 90);
            case EAST:
                return new Vec2f(0, -90);
            default:
                throw new IllegalArgumentException("Unknown direction: " + direction);
        }
    }

    public static Direction pitchYawToDirection(Vec2f pitchYaw) {
        float pitch = pitchYaw.x;
        float yaw = pitchYaw.y;

        double radPitch = Math.toRadians(pitch);
        double radYaw = Math.toRadians(yaw);

        double cosPitch = Math.cos(radPitch);
        double sinPitch = Math.sin(radPitch);
        double cosYaw = Math.cos(radYaw);
        double sinYaw = Math.sin(radYaw);

        double x = -cosPitch * sinYaw;
        double y = -sinPitch;
        double z = cosPitch * cosYaw;

        double x2 = x * x;
        double y2 = y * y;
        double z2 = z * z;

        if (x2 > y2 && x2 > z2) {
            return x > 0 ? Direction.EAST : Direction.WEST;
        } else if (y2 > x2 && y2 > z2) {
            return y > 0 ? Direction.UP : Direction.DOWN;
        } else {
            return z > 0 ? Direction.SOUTH : Direction.NORTH;
        }
    }
}
