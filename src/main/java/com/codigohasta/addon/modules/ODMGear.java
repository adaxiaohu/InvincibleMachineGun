package com.codigohasta.addon.modules;

import com.codigohasta.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.misc.Keybind;
import meteordevelopment.meteorclient.utils.render.color.Color; // 修复 1: 必须使用 Meteor 的 Color
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.ClipContext;

public class ODMGear extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgPhysics = settings.createGroup("Physics");
    private final SettingGroup sgInput = settings.createGroup("Controls");

    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder().name("range").description("最大射程").defaultValue(64).min(10).build());
    private final Setting<Boolean> checkGear = sgGeneral.add(new BoolSetting.Builder().name("require-gear").description("只在穿着ODM/鞘翅时激活").defaultValue(true).build());

    private final Setting<Double> pullSpeed = sgPhysics.add(new DoubleSetting.Builder().name("pull-speed").description("基础拉力速度").defaultValue(0.15).build());
    private final Setting<Double> orbitSpeed = sgPhysics.add(new DoubleSetting.Builder().name("orbit-speed").description("A/D 键甩尾/绕圈速度").defaultValue(0.25).build());
    private final Setting<Double> lift = sgPhysics.add(new DoubleSetting.Builder().name("upward-lift").description("向上的额外升力(防触地)").defaultValue(0.08).build());
    private final Setting<Double> dualMultiplier = sgPhysics.add(new DoubleSetting.Builder().name("dual-multiplier").description("双钩时的速度倍率").defaultValue(1.5).build());
    private final Setting<Double> drag = sgPhysics.add(new DoubleSetting.Builder().name("drag").description("空气阻力(惯性保持)").defaultValue(0.98).build());

    private final Setting<Keybind> leftKey = sgInput.add(new KeybindSetting.Builder().name("left-hook").description("左钩爪按键").action(this::fireLeft).build());
    private final Setting<Keybind> rightKey = sgInput.add(new KeybindSetting.Builder().name("right-hook").description("右钩爪按键").action(this::fireRight).build());

    private final HookState leftHook = new HookState();
    private final HookState rightHook = new HookState();

    public ODMGear() {
        super(AddonTemplate.CATEGORY, "立体机动装置", "就像有抓钩一样飞行，立体机动装置飞来飞去相当自由。娱乐功能 ");
    }

    @Override
    public void onDeactivate() {
        leftHook.reset();
        rightHook.reset();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.level == null) return;

        if (checkGear.get()) {
            String chestId = mc.player.getItemBySlot(EquipmentSlot.CHEST).getItem().toString();
            if (!chestId.contains("odm_gear") && !chestId.contains("elytra")) {
                leftHook.reset();
                rightHook.reset();
                return;
            }
        }

        updateHookState(leftHook, leftKey.get());
        updateHookState(rightHook, rightKey.get());
        applyPhysics();
    }

    private void updateHookState(HookState hook, Keybind key) {
        boolean isPressed = key.isPressed();

        if (isPressed && !hook.active) {
            fireHook(hook);
        } else if (!isPressed && hook.active) {
            hook.reset();
        }

        if (hook.active && hook.entity != null) {
            if (!hook.entity.isAlive()) {
                hook.reset();
            } else {
                // 修复 2: 使用 getX/Y/Z 手动构建 Vec3，避免 getPos() 映射错误
                Vec3 entityPos = new Vec3(hook.entity.getX(), hook.entity.getY(), hook.entity.getZ());
                hook.pos = entityPos.add(0, hook.entity.getBbHeight() * 0.7, 0);
            }
        }
    }

    private void fireHook(HookState hook) {
        double dist = range.get();
        Vec3 eyes = mc.player.getEyePosition();
        Vec3 look = mc.player.getViewVector(1.0F);
        Vec3 end = eyes.add(look.scale(dist));

        AABB box = mc.player.getBoundingBox().expandTowards(look.scale(dist)).inflate(1.0);
        EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(
            mc.player,
            eyes,
            end,
            box,
            (entity) -> !entity.isSpectator() && entity.isPickable(),
            dist * dist
        );

        BlockHitResult blockHit = mc.level.clip(new ClipContext(
            eyes, end,
            ClipContext.Block.COLLIDER,
            ClipContext.Fluid.NONE,
            mc.player
        ));

        boolean hitEntity = entityHit != null;
        boolean hitBlock = blockHit.getType() != HitResult.Type.MISS;

        if (hitEntity && hitBlock) {
            if (eyes.distanceToSqr(entityHit.getLocation()) < eyes.distanceToSqr(blockHit.getLocation())) {
                setHook(hook, entityHit.getEntity(), entityHit.getLocation());
            } else {
                setHook(hook, null, blockHit.getLocation());
            }
        } else if (hitEntity) {
            setHook(hook, entityHit.getEntity(), entityHit.getLocation());
        } else if (hitBlock) {
            setHook(hook, null, blockHit.getLocation());
        }
    }

    private void setHook(HookState hook, Entity entity, Vec3 pos) {
        hook.active = true;
        hook.entity = entity;
        hook.pos = pos;
    }

    private void applyPhysics() {
        if (!leftHook.active && !rightHook.active) return;

        // 修复 2: 同样对 player 使用手动 Vec3 构建
        Vec3 playerPos = new Vec3(mc.player.getX(), mc.player.getY(), mc.player.getZ());
        Vec3 targetPos;
        boolean dualActive = leftHook.active && rightHook.active;

        if (dualActive) {
            targetPos = leftHook.pos.add(rightHook.pos).scale(0.5);
        } else if (leftHook.active) {
            targetPos = leftHook.pos;
        } else {
            targetPos = rightHook.pos;
        }

        Vec3 toHook = targetPos.subtract(playerPos);
        Vec3 direction = toHook.normalize();

        boolean pressingForward = mc.player.input.keyPresses.forward();
        boolean pressingA = mc.player.input.keyPresses.left();
        boolean pressingD = mc.player.input.keyPresses.right();
        boolean isOrbiting = (pressingA || pressingD) && !dualActive;

        double currentPullSpeed = pullSpeed.get();
        if (dualActive) currentPullSpeed *= dualMultiplier.get();
        if (pressingForward) currentPullSpeed *= 1.2;

        Vec3 pullForce = direction.scale(currentPullSpeed);
        
        if (playerPos.y < targetPos.y) {
            pullForce = pullForce.add(0, lift.get(), 0);
        }

        Vec3 orbitForce = Vec3.ZERO;
        if (isOrbiting) {
            Vec3 up = new Vec3(0, 1, 0);
            Vec3 right = direction.cross(up).normalize();
            
            if (right.lengthSqr() < 0.01) {
                right = direction.cross(new Vec3(1, 0, 0)).normalize();
            }

            double orbitMag = orbitSpeed.get();
            if (pressingD) orbitForce = right.scale(orbitMag);
            if (pressingA) orbitForce = right.scale(-orbitMag);
        }

        Vec3 currentVel = mc.player.getDeltaMovement();
        Vec3 newVelocity = currentVel.scale(drag.get()).add(pullForce).add(orbitForce);
        
        mc.player.setDeltaMovement(newVelocity);
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (leftHook.active) renderLine(event, leftHook.pos);
        if (rightHook.active) renderLine(event, rightHook.pos);
    }

    private void renderLine(Render3DEvent event, Vec3 target) {
        if (target == null) return;
        // 修复 2: 获取玩家位置避免 getPos
        Vec3 start = new Vec3(mc.player.getX(), mc.player.getY() + 0.8, mc.player.getZ());
        // 修复 1: 使用正确的 Color.WHITE
        event.renderer.line(start.x, start.y, start.z, target.x, target.y, target.z, Color.WHITE);
    }

    private void fireLeft() {}
    private void fireRight() {}

    private static class HookState {
        boolean active = false;
        Vec3 pos = Vec3.ZERO;
        Entity entity = null;

        void reset() {
            active = false;
            pos = Vec3.ZERO;
            entity = null;
        }
    }
}