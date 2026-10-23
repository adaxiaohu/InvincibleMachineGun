package com.codigohasta.addon.modules;

import com.codigohasta.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class ArrowDmg extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgAuto = settings.createGroup("连射");
     private final SettingGroup sgTotem = settings.createGroup("图腾绕过");
    private final SettingGroup sgAim = settings.createGroup("自瞄");
    private final SettingGroup sgRender = settings.createGroup("渲染");

    // ================= 基础高伤与环境检测设置 =================
    private final Setting<Double> strength = sgGeneral.add(new DoubleSetting.Builder()
        .name("力量")
        .description("1")
        .defaultValue(10.0)
        .min(0.1)
        .sliderMax(20)
        .build()
    );

    private final Setting<Boolean> vertical = sgGeneral.add(new BoolSetting.Builder()
        .name("垂直修正")
        .description("开启后就可以自由角度射击")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> useOffset = sgGeneral.add(new BoolSetting.Builder()
        .name("防摔")
        .description("防止自己摔死。这样可以飞在天上随意射击，不会受到摔伤。")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> smartStrength = sgGeneral.add(new BoolSetting.Builder()
        .name("自动空间检测")
        .description("向后方发送射线，如果身后2格高的空间有方块阻挡，自动缩短发包距离，减少无效箭。")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> yeetTridents = sgGeneral.add(new BoolSetting.Builder()
        .name("三叉戟模式")
        .description("是否对三叉戟也应用高伤。")
        .defaultValue(false)
        .build()
    );

    // ================= 连射设置 =================
    private final Setting<Boolean> autoShoot = sgAuto.add(new BoolSetting.Builder()
        .name("开启连射")
        .description("自动蓄力并松开射击，没敌机关枪。")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> charge = sgAuto.add(new IntSetting.Builder()
        .name("蓄力时间")
        .description("蓄力多少Tick后自动发射。高伤模式下用4。")
        .defaultValue(4)
        .min(1)
        .sliderMax(20)
        .visible(autoShoot::get)
        .build()
    );

    private final Setting<Boolean> onlyWhenHoldingRightClick = sgAuto.add(new BoolSetting.Builder()
        .name("仅按右键时连射")
        .description("开启后只有长按右键才会连射。关闭则自动狂射。")
        .defaultValue(true)
        .visible(autoShoot::get)
        .build()
    );

    private final Setting<Boolean> totemCompat = sgTotem.add(new BoolSetting.Builder()
        .name("破图腾双发扳机")
        .description("释放时自动连射两箭。第一箭取基础力量破图腾，第二箭高伤在无敌帧内秒杀。")
        .defaultValue(false)
        .build()
    );

    private final Setting<Double> compatStrength = sgTotem.add(new DoubleSetting.Builder()
        .name("第二箭力量")
        .description("第二箭的力量倍数（必须比基础力量高才能产生伤害差）,也就是第一箭力量要低于第二箭力量。")
        .defaultValue(20.0)
        .min(0.1)
        .sliderMax(30.0)
        .visible(totemCompat::get)
        .build()
    );

    private final Setting<Integer> compatDelay = sgTotem.add(new IntSetting.Builder()
        .name("第二箭延迟")
        .description("第一箭射出后，自动蓄力多少Tick再射第二箭（推荐 4）。")
        .defaultValue(4)
        .min(1)
        .sliderMax(10)
        .visible(totemCompat::get)
        .build()
    );

    // ================= 自瞄设置 =================
    public enum TargetPriority { Angle, Distance, Health }

    private final Setting<Boolean> aimbot = sgAim.add(new BoolSetting.Builder()
        .name("开启自瞄")
        .description("直接修改客户端视角，自动锁定目标。")
        .defaultValue(false)
        .build()
    );

    private final Setting<TargetPriority> priority = sgAim.add(new EnumSetting.Builder<TargetPriority>()
        .name("优先度")
        .description("自瞄选择目标的优先逻辑。")
        .defaultValue(TargetPriority.Angle)
        .visible(aimbot::get)
        .build()
    );

    private final Setting<Double> aimRange = sgAim.add(new DoubleSetting.Builder()
        .name("自瞄范围")
        .description("自动锁定目标的最大距离。")
        .defaultValue(40.0)
        .min(1.0)
        .sliderMax(100.0)
        .visible(aimbot::get)
        .build()
    );

    private final Setting<Boolean> aimOnlyWhenHoldingRightClick = sgAim.add(new BoolSetting.Builder()
        .name("仅拉弓时自瞄")
        .description("只有在准备射击（按住右键或自动射击）时才锁定视角。")
        .defaultValue(true)
        .visible(aimbot::get)
        .build()
    );

    private final Setting<Boolean> ignoreWalls = sgAim.add(new BoolSetting.Builder()
        .name("忽略墙后目标")
        .description("开启后，只有当目标暴露在视线内时才会自瞄。")
        .defaultValue(true)
        .visible(aimbot::get)
        .build()
    );

    private final Setting<Set<EntityType<?>>> entities = sgAim.add(new EntityTypeListSetting.Builder()
        .name("自瞄目标")
        .description("选择你要自动瞄准的实体类型。")
        .defaultValue(EntityType.PLAYER)
        .visible(aimbot::get)
        .build()
    );

    // ================= 渲染设置 =================
    private final Setting<Boolean> doRender = sgRender.add(new BoolSetting.Builder()
        .name("开启渲染")
        .description("开启物理预测曲线和目标框渲染。")
        .defaultValue(true)
        .build()
    );

    private final Setting<SettingColor> boxColor = sgRender.add(new ColorSetting.Builder()
        .name("目标框颜色")
        .description("被锁定或瞄准目标的渲染框颜色。")
        .defaultValue(new SettingColor(255, 0, 0, 100))
        .visible(doRender::get)
        .build()
    );
    // ===================================================

    private boolean isShooting = false;
    private boolean forcedPressed = false;
    private Entity currentTarget = null; 
    private boolean isSecondShot = false;
    private int compatTimer = -1;

    public ArrowDmg() {
        super(AddonTemplate.CATEGORY, "没敌机关枪", "32k弓，或者叫arrowDMG。使自己射出的弓箭，伤害变高，原理来自wurst。使得在1.21以上得到实现。");
    }

    @Override
    public void onDeactivate() {
        if (forcedPressed) {
            mc.options.useKey.setPressed(false);
            forcedPressed = false;
        }
        currentTarget = null;
        isSecondShot = false;
        compatTimer = -1;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        // --- 图腾绕过 ---
        if (totemCompat.get() && compatTimer > 0) {
            mc.options.useKey.setPressed(true); // 强行按住右键，极速蓄力第二箭
            compatTimer--;
            
            // 倒计时结束，瞬间松开发射第二箭
            if (compatTimer == 0) {
                if (mc.player.isUsingItem() && isValidItem(mc.player.getActiveItem())) {
                    mc.interactionManager.stopUsingItem(mc.player); 
                } else {
                    isSecondShot = false; // 如果手里切成了别的物品，直接取消第二箭
                }
                mc.options.useKey.setPressed(false);
            }
            return; // 双发扳机倒计时期间，阻断下方的普通连射逻辑，防止冲突
        }

        if (mc.player == null || mc.world == null) return;

        boolean validMainHand = isValidItem(mc.player.getMainHandStack());
        boolean validOffHand = isValidItem(mc.player.getOffHandStack());
        boolean hasValidItem = validMainHand || validOffHand;

        currentTarget = null; 

        // 1. 自瞄逻辑
        if (aimbot.get() && hasValidItem) {
            boolean isPressingRightClick = mc.options.useKey.isPressed() || forcedPressed;

            if (!aimOnlyWhenHoldingRightClick.get() || isPressingRightClick) {
                Entity bestTarget = null;
                double bestScore = Double.MAX_VALUE;

                for (Entity entity : mc.world.getEntities()) {
                    if (entity == mc.player) continue;
                    if (!(entity instanceof LivingEntity living) || living.isDead() || living.getHealth() <= 0) continue;
                    if (!entities.get().contains(entity.getType())) continue;

                    if (entity instanceof PlayerEntity player) {
                        if (player.isCreative() || player.isSpectator() || Friends.get().isFriend(player)) continue;
                    }

                    double dist = mc.player.distanceTo(entity);
                    if (dist > aimRange.get()) continue;
                    if (ignoreWalls.get() && !mc.player.canSee(entity)) continue;

                    double score = 0;
                    switch (priority.get()) {
                        case Distance:
                            score = dist;
                            break;
                        case Health:
                            score = living.getHealth();
                            break;
                        case Angle:
                            Vec3d targetPos = entity.getBoundingBox().getCenter();
                            double dX = targetPos.x - mc.player.getX();
                            double dY = targetPos.y - mc.player.getEyeY();
                            double dZ = targetPos.z - mc.player.getZ();
                            double yawDiff = Math.toDegrees(Math.atan2(dZ, dX)) - 90.0 - mc.player.getYaw();
                            double pitchDiff = -Math.toDegrees(Math.atan2(dY, Math.sqrt(dX * dX + dZ * dZ))) - mc.player.getPitch();
                            score = Math.abs(MathHelper.wrapDegrees((float)yawDiff)) + Math.abs(MathHelper.wrapDegrees((float)pitchDiff));
                            break;
                    }

                    if (score < bestScore) {
                        bestScore = score;
                        bestTarget = entity;
                    }
                }

                if (bestTarget != null) {
                    currentTarget = bestTarget;
                    Vec3d targetPos = bestTarget.getBoundingBox().getCenter();
                    double dX = targetPos.x - mc.player.getX();
                    double dY = targetPos.y - mc.player.getEyeY();
                    double dZ = targetPos.z - mc.player.getZ();

                    double distXZ = Math.sqrt(dX * dX + dZ * dZ);
                    float yaw = (float) Math.toDegrees(Math.atan2(dZ, dX)) - 90.0F;
                    float pitch = (float) -Math.toDegrees(Math.atan2(dY, distXZ));

                    pitch = MathHelper.clamp(pitch, -90.0F, 90.0F);
                    mc.player.setYaw(yaw);
                    mc.player.setPitch(pitch);
                }
            }
        }

        // --- 连射逻辑 ---
        ItemStack activeStack = mc.player.getActiveItem();
        if (mc.player.isUsingItem() && !isValidItem(activeStack)) {
            if (forcedPressed) {
                mc.options.useKey.setPressed(false);
                forcedPressed = false;
            }
            return;
        }

        if (!autoShoot.get() || !hasValidItem) {
            if (forcedPressed) {
                mc.options.useKey.setPressed(false);
                forcedPressed = false;
            }
            return;
        }

        if (!onlyWhenHoldingRightClick.get() && !mc.player.isUsingItem()) {
            mc.options.useKey.setPressed(true);
            forcedPressed = true;
        }

        if (mc.player.isUsingItem() && isValidItem(activeStack)) {
            if (mc.player.getItemUseTime() >= charge.get()) {
                mc.interactionManager.stopUsingItem(mc.player);
            }
        }
    }

    @EventHandler
    private void onSendPacket(PacketEvent.Send event) {
        if (isShooting) return;
        if (event.packet instanceof PlayerActionC2SPacket packet) {
            if (packet.getAction() == PlayerActionC2SPacket.Action.RELEASE_USE_ITEM) {
                if (mc.player != null && (isValidItem(mc.player.getMainHandStack()) || isValidItem(mc.player.getOffHandStack()))) {
                    event.cancel();
                    processShoot(packet);
                }
            }
        }
    }

    private void processShoot(PlayerActionC2SPacket releasePacket) {
        if (mc.player == null || mc.world == null || mc.getNetworkHandler() == null) return;

        isShooting = true;
        mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_SPRINTING));

        double x = mc.player.getX(), y = mc.player.getY(), z = mc.player.getZ();
        double currentStrength = (isSecondShot && totemCompat.get()) ? compatStrength.get() : strength.get();
        double adjustedStrength = (currentStrength / 10.0) * Math.sqrt(500.0);
        Vec3d lookVec = mc.player.getRotationVector().multiply(adjustedStrength);

        Vec3d spoofOffset = new Vec3d(-lookVec.x, vertical.get() ? -lookVec.y : 0, -lookVec.z);

        if (smartStrength.get()) {
            double safeDist = getSafeSpoofDistance(new Vec3d(x, y, z), spoofOffset);
            double adjustedDist = Math.max(0.01, safeDist - 0.5);
            if (adjustedDist < spoofOffset.length()) {
                spoofOffset = spoofOffset.normalize().multiply(adjustedDist);
            }
        }

        double targetX = x + spoofOffset.x, targetY = y + spoofOffset.y, targetZ = z + spoofOffset.z;

        for (int i = 0; i < 4; i++) sendPos(x, y, z, true);
        sendPos(targetX, targetY, targetZ, false);
        sendPos(x, y, z, false);

        mc.getNetworkHandler().sendPacket(releasePacket);

        if (vertical.get() && useOffset.get() && spoofOffset.y > 0) {
            sendPos(x, y + 0.01, z, false);
        }

        isShooting = false;

        // --- 状态切换逻辑 ---
        if (totemCompat.get()) {
            if (!isSecondShot) {
                // 第一箭刚射完，马上标记为第二箭并启动倒计时
                isSecondShot = true;
                compatTimer = compatDelay.get();
            } else {
                // 第二箭也射完了，恢复为平时的第一箭状态
                isSecondShot = false;
            }
        } else {
            isSecondShot = false;
        }
    }

     // ================= 轨迹与渲染 ================
    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (!doRender.get() || mc.player == null || mc.world == null) return;
        if (!isValidItem(mc.player.getMainHandStack()) && !isValidItem(mc.player.getOffHandStack())) return;

        float tickDelta = event.tickDelta;

        // 1. 平滑角度计算视线向量
        float pitchInterp = MathHelper.lerp(tickDelta, mc.player.lastPitch, mc.player.getPitch());
        float yawInterp = MathHelper.lerp(tickDelta, mc.player.lastYaw, mc.player.getYaw());
        
        float radPitch = pitchInterp * 0.017453292F;
        float radYaw = -yawInterp * 0.017453292F;
        float cosYaw = MathHelper.cos(radYaw);
        float sinYaw = MathHelper.sin(radYaw);
        float cosPitch = MathHelper.cos(radPitch);
        float sinPitch = MathHelper.sin(radPitch);
        
        // 这里的方向向量 lookVec 是渲染用的平滑向量
        Vec3d lookVec = new Vec3d((double)(sinYaw * cosPitch), (double)(-sinPitch), (double)(cosYaw * cosPitch));

        // 2. 动量计算与颜色警报
        double baseStr = (strength.get() / 10.0) * Math.sqrt(500.0);
        Vec3d spoofOffset = new Vec3d(-lookVec.x * baseStr, vertical.get() ? -lookVec.y * baseStr : 0, -lookVec.z * baseStr);
        
        double maxD = spoofOffset.length();
        double finalVelAdd = maxD;
        Color laserColor = new Color(0, 255, 0, 255); // 默认绿色
        
        if (smartStrength.get()) {
            // 空间检测使用当前时刻的真实坐标
            double sDist = getSafeSpoofDistance(new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ()), spoofOffset);
            double adjDist = Math.max(0.01, sDist - 0.5);
            if (adjDist < maxD) finalVelAdd = adjDist;
            
            float ratio = (float) MathHelper.clamp(sDist / maxD, 0.0, 1.0);
            laserColor = new Color((int) ((1.0f - ratio) * 255), (int) (ratio * 255), 0, 255);
        }

        
        double renderX = MathHelper.lerp(tickDelta, mc.player.lastX, mc.player.getX());
        double renderY = MathHelper.lerp(tickDelta, mc.player.lastY, mc.player.getY()) + (mc.player.getEyeY() - mc.player.getY());
        double renderZ = MathHelper.lerp(tickDelta, mc.player.lastZ, mc.player.getZ());
        
        
        Vec3d simPos = new Vec3d(renderX, renderY - 0.1, renderZ);
      
        Vec3d simVel = lookVec.normalize().multiply(3.0 + finalVelAdd);
        
        List<Vec3d> points = new ArrayList<>();
        points.add(simPos);
        Entity hitEnt = null;

       
        for (int step = 0; step < 150; step++) {
            Vec3d nextSimPos = simPos.add(simVel);
            
          
            RaycastContext bCtx = new RaycastContext(simPos, nextSimPos, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc.player);
            HitResult bHit = mc.world.raycast(bCtx);
            if (bHit != null && bHit.getType() == HitResult.Type.BLOCK) {
                nextSimPos = bHit.getPos();
            }

       
            Box segBox = new Box(simPos.x, simPos.y, simPos.z, nextSimPos.x, nextSimPos.y, nextSimPos.z).expand(0.5);
            double nearest = Double.MAX_VALUE;
            
            for (Entity e : mc.world.getOtherEntities(mc.player, segBox)) {
                if (!(e instanceof LivingEntity living) || !living.isAlive()) continue;
             
                if (e instanceof PlayerEntity p && (p.isCreative() || p.isSpectator() || Friends.get().isFriend(p))) continue;

                Optional<Vec3d> clip = e.getBoundingBox().expand(0.3).raycast(simPos, nextSimPos);
                if (clip.isPresent()) {
                    double d = simPos.squaredDistanceTo(clip.get());
                    if (d < nearest) {
                        nearest = d;
                        nextSimPos = clip.get();
                        hitEnt = e; 
                    }
                }
            }

            points.add(nextSimPos);
            if (hitEnt != null || (bHit != null && bHit.getType() == HitResult.Type.BLOCK)) break; 
            
            simPos = nextSimPos;
       
            simVel = simVel.multiply(0.99).subtract(0, 0.05, 0); 
        }

   
        if (points.size() >= 2) {
            Vec3d pStart = points.get(0);
            Vec3d pNext = points.get(1);
            if (pStart.distanceTo(pNext) > 8) {
                Vec3d dir = pNext.subtract(pStart).normalize();
                points.set(0, pStart.add(dir.multiply(1.5)));
            }
        }

      
        for (int renderIdx = 0; renderIdx < points.size() - 1; renderIdx++) {
            Vec3d p1 = points.get(renderIdx);
            Vec3d p2 = points.get(renderIdx + 1);
            event.renderer.line(p1.x, p1.y, p1.z, p2.x, p2.y, p2.z, laserColor);
        }

    
        if (hitEnt != null) {
            event.renderer.box(
                hitEnt.getBoundingBox(),
                boxColor.get(),
                boxColor.get(),
                meteordevelopment.meteorclient.renderer.ShapeMode.Lines,
                0
            );
        }
    }

    private double getSafeSpoofDistance(Vec3d start, Vec3d offset) {
        Vec3d end = start.add(offset);
        double maxDist = offset.length();

        RaycastContext footContext = new RaycastContext(start, end, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc.player);
        HitResult footHit = mc.world.raycast(footContext);

        Vec3d headOffsetVec = new Vec3d(0, 1.8, 0);
        Vec3d headStart = start.add(headOffsetVec);
        Vec3d headEnd = end.add(headOffsetVec);
        RaycastContext headContext = new RaycastContext(headStart, headEnd, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc.player);
        HitResult headHit = mc.world.raycast(headContext);

        double safeDist = maxDist;

        if (footHit != null && footHit.getType() == HitResult.Type.BLOCK) {
            safeDist = Math.min(safeDist, start.distanceTo(footHit.getPos()));
        }
        if (headHit != null && headHit.getType() == HitResult.Type.BLOCK) {
            safeDist = Math.min(safeDist, headStart.distanceTo(headHit.getPos()));
        }
        return safeDist;
    }

    private void sendPos(double x, double y, double z, boolean onGround) {
        mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(x, y, z, onGround, mc.player.horizontalCollision));
    }

    private boolean isValidItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        String name = stack.getItem().toString();
        return name.contains("bow") || (yeetTridents.get() && name.contains("trident"));
    }
}