package com.codigohasta.addon.modules;

import com.codigohasta.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.utils.entity.EntityUtils;
import meteordevelopment.meteorclient.utils.entity.TargetUtils;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.entity.SortPriority;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.MaceItem;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class TotemBypass extends Module {

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgTargeting = settings.createGroup("Targeting");
    private final SettingGroup sgWhitelist = settings.createGroup("Whitelist");
    private final SettingGroup sgRender = settings.createGroup("Render");

    public enum SlamMode {
        Single,
        Double
    }

    // --- General ---
    private final Setting<SlamMode> slamMode = sgGeneral.add(new EnumSetting.Builder<SlamMode>()
        .name("slam-mode")
        .description("攻击模式。Double模式会连续攻击两次以击破两个图腾。")
        .defaultValue(SlamMode.Single)
        .build()
    );

    // --- General ---
    private final Setting<Double> height = sgGeneral.add(new DoubleSetting.Builder()
        .name("spoof-height")
        .description("伪造高度。150格足以碎掉全套下界合金。")
        .defaultValue(150.0)
        .min(20.0)
        .sliderMax(300.0)
        .max(2000.0)
        .build()
    );

    private final Setting<Boolean> autoHeight = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-height")
        .description("自动检测头顶方块并使用安全高度进行暴击。")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> doubleTapDelay = sgGeneral.add(new IntSetting.Builder()
        .name("double-tap-delay")
        .description("二连击的延迟（刻）。必须 > 10 以绕过无敌帧。")
        .defaultValue(12)
        .min(11)
        .sliderRange(11, 20)
        .visible(() -> slamMode.get() == SlamMode.Double)
        .build()
    );

    private final Setting<Boolean> autoSwitch = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-switch")
        .description("自动切换到重锤")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> autoToggle = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-toggle")
        .description("攻击一次后自动关闭")
        .defaultValue(true)
        .build()
    );

    // --- Targeting ---
    private final Setting<Double> range = sgTargeting.add(new DoubleSetting.Builder()
        .name("range")
        .description("索敌范围")
        .defaultValue(10.0)
        .min(0)
        .sliderMax(50)
        .build()
    );

    private final Setting<Boolean> throughWalls = sgTargeting.add(new BoolSetting.Builder()
        .name("through-walls")
        .description("穿墙攻击 (无视视线检测)")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> ignoreNamed = sgTargeting.add(new BoolSetting.Builder()
        .name("ignore-named")
        .description("不攻击被命名的生物。")
        .defaultValue(true)
        .build()
    );

    // --- Whitelist/Blacklist ---
    public enum ListMode {
        Whitelist,
        Blacklist,
        Off
    }

    private final Setting<ListMode> listMode = sgWhitelist.add(new EnumSetting.Builder<ListMode>()
        .name("mode")
        .description("白名单/黑名单模式。")
        .defaultValue(ListMode.Off)
        .build()
    );

    private final Setting<String> playerList = sgWhitelist.add(new StringSetting.Builder()
        .name("player-list")
        .description("玩家列表，用英文逗号(,)分隔。")
        .defaultValue("")
        .visible(() -> listMode.get() != ListMode.Off)
        .build()
    );


    // --- Render ---
    private final Setting<Boolean> render = sgRender.add(new BoolSetting.Builder()
        .name("render")
        .description("渲染当前目标")
        .defaultValue(true)
        .build()
    );

    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode")
        .description("渲染模式")
        .defaultValue(ShapeMode.Lines)
        .visible(render::get)
        .build()
    );

    private final Setting<SettingColor> sideColor = sgRender.add(new ColorSetting.Builder()
        .name("side-color")
        .description("填充颜色")
        .defaultValue(new SettingColor(255, 0, 0, 75))
        .visible(render::get)
        .build()
    );

    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder()
        .name("line-color")
        .description("线框颜色")
        .defaultValue(new SettingColor(255, 0, 0, 255))
        .visible(render::get)
        .build()
    );

    private int originalSlot = -1;
    private Entity currentTarget;
    private int slamCount;
    private int delayTimer;
    private final List<Entity> targets = new ArrayList<>();

    public TotemBypass() {
        super(AddonTemplate.CATEGORY, "Manual-Mace-Slam", "对准星目标执行一次性的、最大化的重锤坠落攻击。");
    }

    @Override
    public void onActivate() {
        // 初始化状态
        originalSlot = -1;
        currentTarget = null;
        slamCount = 0;
        delayTimer = 0;
    }

    @Override
    public void onDeactivate() {
        currentTarget = null;
        // 确保在任何时候关闭模块都能切回物品
        if (originalSlot != -1 && autoSwitch.get() && mc.player != null) {
            InvUtils.swap(originalSlot, false);
            originalSlot = -1;
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) {
            toggle();
            return;
        }

        // 延迟计时器逻辑
        if (delayTimer > 0) {
            delayTimer--;
            return;
        }

        // 查找目标
        findTarget();
        if (currentTarget == null) {
            if (slamCount == 0) info("未找到目标。"); // 只在第一次攻击前提示
            toggle();
            return;
        }

        // 切换武器
        if (!checkAndSwapWeapon()) {
            error("未找到重锤。");
            toggle();
            return;
        }

        // 执行攻击
        performMaceSlam(currentTarget);
        slamCount++;

        // 根据模式决定下一步
        if (slamMode.get() == SlamMode.Double && slamCount == 1) {
            // 如果是二连击模式且刚打完第一下，则设置延迟
            info("第一击完成，准备执行第二击...");
            delayTimer = doubleTapDelay.get();
        } else {
            // 如果是单击模式，或二连击已完成
            if (autoToggle.get()) {
                if (slamMode.get() == SlamMode.Double) {
                    info("二连击完成！");
                } else {
                    info("已对 " + EntityUtils.getName(currentTarget) + " 执行手动重锤攻击。");
                }
                toggle();
            }
        }
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (!render.get() || currentTarget == null) return;
        event.renderer.box(currentTarget.getBoundingBox(), sideColor.get(), lineColor.get(), shapeMode.get(), 0);
    }

    private void findTarget() {
        // 如果开启穿墙，则使用范围搜索
        if (throughWalls.get()) {
            targets.clear();
            TargetUtils.getList(targets, this::entityCheck, SortPriority.ClosestAngle, 1);
            if (!targets.isEmpty()) {
                currentTarget = targets.get(0);
            } else {
                currentTarget = null;
            }
        } else {
            // 否则，使用准星精确索敌
            if (mc.crosshairTarget == null || mc.crosshairTarget.getType() != HitResult.Type.ENTITY) {
                currentTarget = null;
                return;
            }

            Entity entity = ((EntityHitResult) mc.crosshairTarget).getEntity();
            if (entityCheck(entity)) {
                currentTarget = entity;
            } else {
                currentTarget = null;
            }
        }
    }

    private boolean entityCheck(Entity entity) {
        if (!(entity instanceof LivingEntity) || !entity.isAlive() || entity.equals(mc.player)) return false;
        if (mc.player.distanceTo(entity) > range.get()) return false;

        if (entity instanceof PlayerEntity p) {
            if (p.isCreative()) return false;

            // 优先检查好友系统
            if (!Friends.get().shouldAttack(p)) return false;

            // 白名单/黑名单逻辑
            String name = p.getGameProfile().getName();
            List<String> players = Arrays.stream(playerList.get().split(","))
                .map(String::trim)
                .collect(Collectors.toList());
            switch (listMode.get()) {
                case Whitelist -> { return players.contains(name); }
                case Blacklist -> { return !players.contains(name); }
            }
            return true; // 如果是玩家且通过所有检查
        }

        // 其他实体检查 (非玩家)
        if (ignoreNamed.get() && entity.hasCustomName()) {
            return false;
        }
        if (entity instanceof TameableEntity && ((TameableEntity) entity).isTamed()) {
            return false;
        }

        // 如果不是玩家，则总是攻击
        return true;
    }

    private boolean checkAndSwapWeapon() {
        if (mc.player.getMainHandStack().getItem() instanceof MaceItem) return true;

        if (autoSwitch.get()) {
            FindItemResult mace = InvUtils.find(itemStack -> itemStack.getItem() instanceof MaceItem, 0, 8);
            if (mace.found()) {
                originalSlot = mc.player.getInventory().selectedSlot;
                InvUtils.swap(mace.slot(), false);
                return true;
            }
        }
        return false;
    }

    private void performMaceSlam(Entity target) {
        // 记录原始位置和状态
        final Vec3d startPos = mc.player.getPos();
        final boolean wasOnGround = mc.player.isOnGround();
        double spoofHeight = height.get();

        // 如果开启了自动高度
        if (autoHeight.get()) {
            double safeHeight = 0;
            // 从玩家眼睛上方开始检测，这样更精确
            BlockPos.Mutable checkPos = new BlockPos.Mutable(mc.player.getX(), mc.player.getEyeY(), mc.player.getZ());

            // 向上检测，直到遇到非空气方块
            for (int i = 0; i < 256; i++) {
                if (mc.world.getBlockState(checkPos.move(0, 1, 0)).isAir()) {
                    safeHeight++;
                } else {
                    break; // 遇到非空气方块，停止检测
                }
            }
            spoofHeight = Math.min(height.get(), safeHeight);
        }

        // 冻结客户端速度，防止自身移动干扰
        mc.player.setVelocity(0, 0, 0);

        // --- 1. 旋转与基准点确认 ---
        // 无论是否在地面，都先向服务器发送一个 onGround=true 的数据包。
        // 这会“欺骗”服务器，让它认为我们的下落距离计算是从当前位置开始的，这是绕过飞行检测的关键。
        double yaw = Rotations.getYaw(target);
        double pitch = Rotations.getPitch(target);
        mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.LookAndOnGround((float) yaw, (float) pitch, true, mc.player.horizontalCollision));

        // --- 2. 路径点传送至高空 (Path Teleport Up) ---
        // 将一次性的大跳分解为多个小跳，模拟路径移动，降低被检测的概率。
        Vec3d highPos = new Vec3d(startPos.x, startPos.y + spoofHeight, startPos.z);
        teleportAlongPath(startPos, highPos, false);

        // --- 3. 路径点传送至攻击位置 (Path Teleport Down) ---
        // 从高空传送到目标头顶上方一个适合攻击的位置。
        // 这个位置既能保证在攻击范围内，又能最大化下落距离。
        Vec3d attackPos = new Vec3d(startPos.x, startPos.y + 1.5, startPos.z);
        teleportAlongPath(highPos, attackPos, false);

        // --- 4. 最终攻击 ---
        // 此时，客户端位置在 attackPos，服务器也认为我们从 highPos 掉落到了 attackPos，
        // 从而计算出了巨大的坠落伤害。
        // 再次发送精确的朝向。
        mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.LookAndOnGround((float) yaw, (float) pitch, false, mc.player.horizontalCollision));

        // 发送攻击包
        mc.getNetworkHandler().sendPacket(PlayerInteractEntityC2SPacket.attack(target, mc.player.isSneaking()));
        mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));

        // --- 5. 确认落地并返回原位 (Land & Return) ---
        // 攻击后，立即告诉服务器我们已经“落地”在原始位置，这会结算伤害并防止我们真的摔死。
        // 无论之前是否在地面，最后一步都应发送 onGround=true 来完成整个序列。
        sendPacket(startPos.x, startPos.y, startPos.z, true);
    }

    private void sendPacket(double x, double y, double z, boolean onGround) {
        mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(x, y, z, onGround, mc.player.horizontalCollision));
    }

    // 新增：路径点传送方法
    private void teleportAlongPath(Vec3d from, Vec3d to, boolean finalOnGround) {
        double distance = from.distanceTo(to);
        // 使用更安全的步长 8.0
        double steps = Math.ceil(distance / 8.0);

        if (steps <= 1) {
            sendPacket(to.x, to.y, to.z, finalOnGround);
            return;
        }

        for (int i = 1; i < steps; i++) {
            Vec3d waypoint = from.lerp(to, i / steps);
            // 在路径中间的所有点，onGround 都必须为 false
            sendPacket(waypoint.x, waypoint.y, waypoint.z, false);
        }

        // 发送最后一个路径点
        sendPacket(to.x, to.y, to.z, finalOnGround);
    }
}