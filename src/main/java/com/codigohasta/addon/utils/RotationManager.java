package com.codigohasta.addon.utils;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.entity.player.SendMovementPacketsEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import com.codigohasta.addon.events.MovementInputEvent;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.MathHelper;
import org.joml.Vector2f;

import java.util.function.Function;

// 注意：请确保这些类在对应的路径下已创建，或者你根据实际路径修改 Import
import com.codigohasta.addon.utils.rotation.Priority;
import com.codigohasta.addon.utils.rotation.RotationUtils;
import com.codigohasta.addon.utils.player.MoveUtils;

public class RotationManager {

    public static final RotationManager INSTANCE = new RotationManager();

    private final MinecraftClient mc = MinecraftClient.getInstance();

    private final Vector2f offset = new Vector2f(0.0f, 0.0f);
    public Vector2f rotations;
    public Vector2f lastRotations = new Vector2f(0.0f, 0.0f);
    public Vector2f targetRotations;
    public Vector2f animationRotation = null;
    public Vector2f lastAnimationRotation = null;

    private boolean active;
    private boolean smoothed;
    private double rotationSpeed;
    private boolean correctMovement;
    private Function<Vector2f, Boolean> raycast;
    private float randomAngle;

    private int priority;

    private RotationManager() {
        // 注册到 Meteor 的事件总线
        MeteorClient.EVENT_BUS.subscribe(this);
    }

    public void setRotations(final Vector2f rotations, final double rotationSpeed, final boolean correctMovement) {
        setRotations(rotations, rotationSpeed, correctMovement, null, Priority.Lowest);
    }

    public void setRotations(final Vector2f rotations, final double rotationSpeed, final boolean correctMovement, Priority priority) {
        setRotations(rotations, rotationSpeed, correctMovement, null, priority);
    }

    public void setRotations(final Vector2f rotations, final double rotationSpeed, final boolean correctMovement, final Function<Vector2f, Boolean> raycast, Priority priority) {
        if (rotations == null || Double.isNaN(rotations.x) || Double.isNaN(rotations.y) || Double.isInfinite(rotations.x) || Double.isInfinite(rotations.y)) {
            return;
        }

        if (active && priority.priority < this.priority) {
            return;
        }

        this.targetRotations = rotations;
        this.rotationSpeed = rotationSpeed * 18.0; // 这里的 18.0 保持 Lumin 的倍率逻辑
        this.correctMovement = correctMovement;
        this.raycast = raycast;
        this.priority = priority.priority;
        active = true;

        smooth();
    }

    private void smooth() {
        if (mc.player == null) return;
        
        if (!smoothed) {
            float targetYaw = targetRotations.x;
            float targetPitch = targetRotations.y;

            if (raycast != null && (Math.abs(targetYaw - rotations.x) > 5.0f || Math.abs(targetPitch - rotations.y) > 5.0f)) {
                final Vector2f trueTargetRotations = new Vector2f(targetRotations.x, targetRotations.y);

                double speed = (Math.random() * Math.random() * Math.random()) * 20.0;
                randomAngle += (float) ((20.0 + (float) (Math.random() - 0.5) * (Math.random() * Math.random() * Math.random() * 360.0)) * (mc.player.age / 10 % 2 == 0 ? -1.0 : 1.0));

                if (Float.isNaN(randomAngle) || Float.isInfinite(randomAngle)) randomAngle = 0.0f;

                offset.x = ((float) (offset.x + -MathHelper.sin((float) Math.toRadians(randomAngle)) * speed));
                offset.y = ((float) (offset.y + MathHelper.cos((float) Math.toRadians(randomAngle)) * speed));

                if (Float.isNaN(offset.x) || Float.isInfinite(offset.x)) offset.x = 0.0f;
                if (Float.isNaN(offset.y) || Float.isInfinite(offset.y)) offset.y = 0.0f;

                targetYaw += offset.x;
                targetPitch += offset.y;

                if (!raycast.apply(new Vector2f(targetYaw, targetPitch))) {
                    randomAngle = (float) Math.toDegrees(Math.atan2(trueTargetRotations.x - targetYaw, targetPitch - trueTargetRotations.y)) - 180.0f;
                    if (Float.isNaN(randomAngle)) randomAngle = 0.0f;

                    targetYaw -= offset.x;
                    targetPitch -= offset.y;

                    offset.x = ((float) (offset.x + -MathHelper.sin((float) Math.toRadians(randomAngle)) * speed));
                    offset.y = ((float) (offset.y + MathHelper.cos((float) Math.toRadians(randomAngle)) * speed));

                    if (Float.isNaN(offset.x) || Float.isInfinite(offset.x)) offset.x = 0.0f;
                    if (Float.isNaN(offset.y) || Float.isInfinite(offset.y)) offset.y = 0.0f;

                    targetYaw = targetYaw + offset.x;
                    targetPitch = targetPitch + offset.y;
                }

                if (!raycast.apply(new Vector2f(targetYaw, targetPitch))) {
                    offset.x = 0.0f;
                    offset.y = 0.0f;

                    targetYaw = (float) (targetRotations.x + Math.random() * 2.0);
                    targetPitch = (float) (targetRotations.y + Math.random() * 2.0);
                }
            }

            // 调用工具类进行平滑处理
            rotations = RotationUtils.smooth(new Vector2f(targetYaw, targetPitch), (float) (rotationSpeed + Math.random()));

            if (Float.isNaN(rotations.x) || Float.isInfinite(rotations.x)) {
                rotations.x = mc.player.getYaw();
            }

            if (Float.isNaN(rotations.y) || Float.isInfinite(rotations.y)) {
                rotations.y = mc.player.getPitch();
            }
        }

        smoothed = true;
    }

    public float getYaw() {
        if (mc.player == null) return 0.0f;
        return active ? rotations.x : mc.player.getYaw();
    }

    public float getPitch() {
        if (mc.player == null) return 0.0f;
        return active ? rotations.y : mc.player.getPitch();
    }

    @EventHandler(priority = EventPriority.LOWEST)
    private void onTickPre(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        if (!active || rotations == null || lastRotations == null || targetRotations == null) {
            final Vector2f defaultRotation = new Vector2f(mc.player.getYaw(), mc.player.getPitch());
            targetRotations = defaultRotation;
            lastRotations = defaultRotation;
            rotations = defaultRotation;
        }

        if (active) {
            smooth();
        }
    }

    @EventHandler
    private void onMovementInput(MovementInputEvent event) {
        // 运动修正逻辑，确保在旋转状态下 WASD 依然指向正确的物理方向
        if (active && correctMovement && rotations != null) {
            MoveUtils.fixMovement(event, rotations.x);
        }
    }

    @EventHandler
    private void onSendMovementPacketsPre(SendMovementPacketsEvent.Pre event) {
        if (mc.player == null) return;
        
        if (active && rotations != null) {
            float yaw = rotations.x;
            float pitch = rotations.y;

            if (Float.isNaN(yaw) || Float.isInfinite(yaw)) yaw = mc.player.getYaw();
            if (Float.isNaN(pitch) || Float.isInfinite(pitch)) pitch = mc.player.getPitch();
            
            pitch = MathHelper.clamp(pitch, -90.0f, 90.0f);

            // 注入旋转到发包
            // 在 Meteor 中，我们可以通过修改 event 来实现静默旋转
            // 这种方式不会改变玩家本地的视觉视角，但发给服务器的是这个旋转
            // 注意：具体 event 字段名根据你的 Meteor 构建版本可能微调
            // 假设使用的是标准的 SendMovementPacketsEvent
            // event.setYaw(yaw); 
            // event.setPitch(pitch);
            
            // 如果旋转完成（接近目标），则释放控制
            if (Math.abs((rotations.x - mc.player.getYaw()) % 360.0f) < 1.0f && Math.abs((rotations.y - mc.player.getPitch())) < 1.0f) {
                active = false;
                priority = 0;
                correctDisabledRotations();
            }

            lastRotations = rotations;
        } else {
            lastRotations = new Vector2f(mc.player.getYaw(), mc.player.getPitch());
        }

        // 处理动画平滑逻辑
        updateAnimationRotations(active ? rotations : new Vector2f(mc.player.getYaw(), mc.player.getPitch()));
        
        targetRotations = new Vector2f(mc.player.getYaw(), mc.player.getPitch());
        smoothed = false;
    }

    private void updateAnimationRotations(Vector2f current) {
        lastAnimationRotation = animationRotation;
        if (lastAnimationRotation == null) {
            animationRotation = current;
        } else {
            float yawDiff = MathHelper.wrapDegrees(current.x - lastAnimationRotation.x);
            float pitchDiff = current.y - lastAnimationRotation.y;
            float smoothYaw = lastAnimationRotation.x + yawDiff * 0.5f;
            float smoothPitch = lastAnimationRotation.y + pitchDiff * 0.5f;
            animationRotation = new Vector2f(MathHelper.wrapDegrees(smoothYaw), MathHelper.clamp(smoothPitch, -90.0f, 90.0f));
        }
    }

    private void correctDisabledRotations() {
        if (lastRotations == null || mc.player == null) return;
        final Vector2f current = new Vector2f(mc.player.getYaw(), mc.player.getPitch());
        
        // 使用灵敏度补丁计算修正后的角度，防止旋转回弹或步进异常
        final Vector2f fixed = RotationUtils.resetRotation(RotationUtils.applySensitivityPatch(current, lastRotations));

        if (!Float.isNaN(fixed.x) && !Float.isNaN(fixed.y)) {
            mc.player.setYaw(fixed.x);
            mc.player.setPitch(fixed.y);
        }
    }

    public boolean isActive() {
        return active;
    }
}