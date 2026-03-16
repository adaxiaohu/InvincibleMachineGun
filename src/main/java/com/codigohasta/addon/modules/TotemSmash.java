package com.codigohasta.addon.modules;

import com.codigohasta.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.utils.entity.EntityUtils;
import meteordevelopment.meteorclient.utils.entity.SortPriority;
import meteordevelopment.meteorclient.utils.entity.TargetUtils;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.mixininterface.IPlayerMoveC2SPacket;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.MaceItem;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.lang.Math;

public class TotemSmash extends Module {

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgAttack = settings.createGroup("Attack");
    private final SettingGroup sgTargeting = settings.createGroup("Targeting");
    private final SettingGroup sgRender = settings.createGroup("Render");

    // --- 新增：目标选择模式 ---
    public enum TargetMode {
        Crosshair, // 准星
        Aura       // 光环
    }

    private final Setting<TargetMode> TpSelectTarget = sgGeneral.add(new EnumSetting.Builder<TargetMode>()
        .name("TpSelectTarget")
        .description("选择目标的方式。")
        .defaultValue(TargetMode.Aura)
        .build()
    );

    // --- Attack ---
    private final Setting<Integer> attackPackets = sgAttack.add(new IntSetting.Builder()
        .name("attack-packets")
        .description("在一次爆发中发送的攻击包数量。")
        .defaultValue(200)
        .min(1)
        .sliderRange(1, 1000)
        .build()
    );

    private final Setting<Integer> times = sgAttack.add(new IntSetting.Builder()
        .name("times")
        .description("攻击次数：在一次触发中连续攻击的次数。")
        .defaultValue(1)
        .min(1)
        .sliderRange(1, 5)
        .build()
    );

    private final Setting<Integer> attackDelay = sgAttack.add(new IntSetting.Builder()
        .name("attack-delay")
        .description("每次爆发攻击之间的冷却时间（刻）。")
        .defaultValue(20)
        .min(0)
        .sliderRange(0, 60)
        .build()
    );

    private final Setting<Integer> repetitionDelay = sgAttack.add(new IntSetting.Builder()
        .name("repetition-delay")
        .description("多次攻击之间的延迟（刻）。")
        .defaultValue(5)
        .min(0)
        .sliderRange(0, 20)
        .build()
    );

    // --- Targeting ---
    private final Setting<Double> range = sgTargeting.add(new DoubleSetting.Builder()
        .name("range")
        .description("索敌范围。")
        .defaultValue(6.0)
        .min(0)
        .sliderMax(10)
        .build()
    );

    private final Setting<Boolean> playersOnly = sgTargeting.add(new BoolSetting.Builder()
        .name("players-only")
        .description("只攻击玩家。")
        .defaultValue(true)
        .build()
    );

    // --- Render ---
    private final Setting<Boolean> render = sgRender.add(new BoolSetting.Builder()
        .name("render")
        .description("渲染当前目标。")
        .defaultValue(true)
        .build()
    );

    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode")
        .description("渲染模式。")
        .defaultValue(ShapeMode.Lines)
        .visible(render::get)
        .build()
    );

    private final Setting<SettingColor> sideColor = sgRender.add(new ColorSetting.Builder()
        .name("side-color")
        .description("填充颜色。")
        .defaultValue(new SettingColor(255, 0, 0, 75))
        .visible(render::get)
        .build()
    );

    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder()
        .name("line-color")
        .description("线框颜色。")
        .defaultValue(new SettingColor(255, 0, 0, 255))
        .visible(render::get)
        .build()
    );

    // --- 新增：范围渲染 ---
    private final Setting<Boolean> ScanBorder = sgRender.add(new BoolSetting.Builder()
        .name("ScanBorder")
        .description("渲染索敌范围的边界。")
        .defaultValue(true)
        .build()
    );

    private final Setting<SettingColor> borderColor = sgRender.add(new ColorSetting.Builder()
        .name("border-color")
        .description("范围边界的颜色。")
        .defaultValue(new SettingColor(255, 255, 255, 100))
        .visible(ScanBorder::get)
        .build()
    );

    private Entity currentTarget;
    private final List<Entity> targets = new ArrayList<>();
    private int cooldown;
    private int originalSlot = -1;
    private double accumulatedFallDistance = 0;
    private int repetitionTimer = 0;

    public TotemSmash() {
        super(AddonTemplate.CATEGORY, "totem-smash", "通过爆发式攻击绕过自动图腾。");
    }

    @Override
    public void onActivate() {
        cooldown = 0;
        currentTarget = null;
        originalSlot = -1;
        accumulatedFallDistance = 0;
        repetitionTimer = 0;
    }

    @Override
    public void onDeactivate() {
        // 关闭时不需要做任何事，因为我们只在攻击瞬间发包
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        // 1. 攻击冷却
        if (cooldown > 0) {
            cooldown--;
        }

        // 2. 检查右键触发
        if (mc.options.useKey.isPressed()) {
            // 如果正在冷却或已在攻击，则不执行
            if (cooldown > 0 && repetitionTimer == 0) return;

            // 如果玩家正在看一个方块，可能是想正常交互，则不触发攻击
            if (mc.crosshairTarget != null && mc.crosshairTarget.getType() == HitResult.Type.BLOCK) {
                return;
            }

            // 4. 寻找目标
            // 只有在第一次攻击时才寻找新目标
            if (repetitionTimer == 0) {
                findTarget();
                if (currentTarget == null) {
                    if (cooldown == 0) info("右键点击时未在范围内找到目标。");
                    cooldown = 10;
                    return;
                }
                // 初始化攻击次数
                repetitionTimer = times.get();
            }

            // 5. 执行攻击序列
            if (repetitionTimer > 0) {
                performAttackSequence(currentTarget);
                repetitionTimer--;

                // 如果攻击完成，则进入主冷却；否则进入重复攻击的短冷却
                if (repetitionTimer == 0) {
                    cooldown = attackDelay.get();
                } else {
                    cooldown = repetitionDelay.get();
                }
            }
        }
    }

    @EventHandler
    private void onSendPacket(PacketEvent.Send event) {
        // 移除所有数据包拦截逻辑，以确保稳定性和自由移动
    }

    private void performAttackSequence(Entity target) {
        // 1. 记录原始位置和旋转
        Vec3d originalPos = mc.player.getPos();
        double yaw = Rotations.getYaw(target);
        double pitch = Rotations.getPitch(target);

        // 定义伪造高度
        double spoofHeight = 40.0;

        // --- 核心重锤暴击序列 ---

        // 2. 建立基准点：无论当前状态如何，都先发送一个 onGround=true 的包。
        // 这会“欺骗”服务器，让它认为我们的下落距离计算是从当前位置开始的。
        mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.LookAndOnGround((float) yaw, (float) pitch, true, mc.player.horizontalCollision));

        // 3. 瞬移至高空：将玩家位置移动到高空，onGround=false 表示正在空中。
        // 为了稳定，我们直接在目标上方的高空伪造位置。
        Vec3d targetPos = target.getPos();
        sendPacket(targetPos.x, targetPos.y + spoofHeight, targetPos.z, false);

        // 4. 瞬移至攻击点：从高空移动到目标头顶附近，准备攻击。
        // 此时服务器已经计算了巨大的 fallDistance。
        sendPacket(targetPos.x, targetPos.y + 1.5, targetPos.z, false);

        // 5. 爆发攻击：在服务器认为我们处于高坠落状态时，倾泻攻击包。
        // 再次发送朝向以确保命中。
        mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.LookAndOnGround((float) yaw, (float) pitch, false, mc.player.horizontalCollision));

        info("对 %s 发送 %d 个攻击包...", EntityUtils.getName(target), attackPackets.get());
        for (int i = 0; i < attackPackets.get(); i++) {
            mc.getNetworkHandler().sendPacket(PlayerInteractEntityC2SPacket.attack(target, mc.player.isSneaking()));
            mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
        }

        // 6. 安全着陆
        // 在所有攻击包发送完毕后，立即发送一个在原地的落地包，以结算对敌人的伤害并清除自己的摔落伤害。
        sendPacket(originalPos.x, originalPos.y, originalPos.z, true);
    }

    private void sendPacket(double x, double y, double z, boolean onGround) {
        PlayerMoveC2SPacket packet = new PlayerMoveC2SPacket.PositionAndOnGround(x, y, z, onGround, mc.player.horizontalCollision);
        ((IPlayerMoveC2SPacket) packet).meteor$setTag(1337); // 给我们自己发的包打上标记
        mc.player.networkHandler.sendPacket(packet);
    }

    private boolean checkAndSwapWeapon() {
        // 移除武器检查，始终返回 true
        return true;
    }

    private void findTarget() {
        targets.clear();
        if (TpSelectTarget.get() == TargetMode.Aura) {
            TargetUtils.getList(targets, this::entityCheck, SortPriority.LowestDistance, 1);
            currentTarget = targets.isEmpty() ? null : targets.get(0);
        } else { // Crosshair
            if (mc.crosshairTarget != null && mc.crosshairTarget.getType() == HitResult.Type.ENTITY) {
                Entity entity = ((EntityHitResult) mc.crosshairTarget).getEntity();
                if (entityCheck(entity)) {
                    currentTarget = entity;
                } else {
                    currentTarget = null;
                }
            } else {
                currentTarget = null;
            }
        }
    }

    private boolean entityCheck(Entity entity) {
        if (!(entity instanceof LivingEntity) || !entity.isAlive() || entity.equals(mc.player)) return false;
        if (mc.player.distanceTo(entity) > range.get()) return false;

        if (entity instanceof PlayerEntity p) {
            if (p.isCreative() || p.isSpectator()) return false;
            // 检查好友
            if (!Friends.get().shouldAttack(p)) return false;
            return true;
        }

        // 如果不是仅玩家模式，则允许攻击其他生物
        return !playersOnly.get();
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (render.get() && currentTarget != null) {
            // 渲染目标包围盒
            event.renderer.box(
                currentTarget.getBoundingBox(),
                sideColor.get(),
                lineColor.get(),
                shapeMode.get(),
                0
            );
        }
        // 渲染范围边界
        if (ScanBorder.get() && TpSelectTarget.get() == TargetMode.Aura) {
            double r = range.get();
            Vec3d center = mc.player.getPos();
            int segments = 50;

            for (int i = 0; i < segments; i++) {
                double angle1 = ((double) i / segments) * 2 * Math.PI;
                double angle2 = ((double) (i + 1) / segments) * 2 * Math.PI;

                double x1 = center.x + Math.cos(angle1) * r;
                double z1 = center.z + Math.sin(angle1) * r;
                double x2 = center.x + Math.cos(angle2) * r;
                double z2 = center.z + Math.sin(angle2) * r;

                event.renderer.line(x1, center.y, z1, x2, center.y, z2, borderColor.get());
            }
        }
    }

    @Override
    public String getInfoString() {
        if (currentTarget != null) {
            return EntityUtils.getName(currentTarget);
        }
        return null;
    }
}