package com.codigohasta.addon.modules;

import com.codigohasta.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.render.Render3DEvent;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.entity.EntityUtils;
import meteordevelopment.meteorclient.utils.entity.SortPriority;
import meteordevelopment.meteorclient.utils.entity.TargetUtils;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.MaceItem;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.Arrays;
import java.util.stream.Collectors;
import java.util.Set;

public class MaceAura extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgCrit = settings.createGroup("Criticals");
    private final SettingGroup sgTargeting = settings.createGroup("Targeting");
    private final SettingGroup sgWhitelist = settings.createGroup("Whitelist");
    private final SettingGroup sgRender = settings.createGroup("Render");

    // --- General ---
    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
        .name("range")
        .description("攻击范围")
        .defaultValue(4.5)
        .min(0)
        .sliderMax(6)
        .build()
    );

    private final Setting<Boolean> autoSwitch = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-switch")
        .description("自动切换到 Mace")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> allowFlight = sgGeneral.add(new BoolSetting.Builder()
        .name("allow-flight")
        .description("允许在飞行或空中时发动攻击。")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> swingHand = sgGeneral.add(new BoolSetting.Builder()
        .name("swing-hand")
        .description("显示挥手动画")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> attackDelay = sgGeneral.add(new IntSetting.Builder()
        .name("attack-delay")
        .description("攻击延迟 (刻)。建议 15 左右。")
        .defaultValue(15)
        .min(0)
        .sliderRange(0, 40)
        .build()
    );
    
    // --- Criticals (暴击参数) ---
    private final Setting<Double> critHeight = sgCrit.add(new DoubleSetting.Builder()
        .name("crit-height")
        .description("伪造下落高度。注意：太高(>100)可能会导致超时踢出。")
        .defaultValue(15.0)
        .min(2.0)
        .sliderMax(50.0)
        .max(300.0)
        .build()
    );

    private final Setting<Boolean> autoHeight = sgCrit.add(new BoolSetting.Builder()
        .name("auto-height")
        .description("自动检测头顶方块并使用安全高度进行暴击。")
        .defaultValue(false)
        .build()
    );

    // --- Targeting (目标选择) ---
    private final Setting<Boolean> players = sgTargeting.add(new BoolSetting.Builder()
        .name("players")
        .description("攻击玩家")
        .defaultValue(true)
        .build()
    );

    private final Setting<Set<EntityType<?>>> entities = sgTargeting.add(new EntityTypeListSetting.Builder()
        .name("entities")
        .description("选择要攻击的具体生物种类")
        .onlyAttackable()
        .defaultValue(EntityType.PLAYER)
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

    private final Setting<Boolean> ignoreTamed = sgTargeting.add(new BoolSetting.Builder()
        .name("ignore-tamed")
        .description("不攻击被驯服的生物 (宠物)。")
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

    private int timer;
    private int originalSlot = -1;
    private final List<Entity> targets = new ArrayList<>();
    private Entity currentTarget;
    private int switchCooldown; // 新增：切换武器后的冷却计时器

    public MaceAura() {
        super(AddonTemplate.CATEGORY, "mace-aura", "近距离重锤光环");
    }

    @Override
    public void onActivate() {
        timer = 0;
        originalSlot = -1;
        targets.clear();
        currentTarget = null;
        switchCooldown = 0;
    }

    @Override
    public void onDeactivate() {
        if (originalSlot != -1 && autoSwitch.get() && mc.player != null) {
            // 修复：使用 InvUtils.swap 来同时更新客户端和服务器
            InvUtils.swap(originalSlot, false);
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        if (timer > 0) {
            timer--;
            return;
        }

        // 新增：如果正在等待武器切换同步，则不执行任何操作
        if (switchCooldown > 0) {
            switchCooldown--;
            return;
        }

        // 安全检查
        // 如果没有开启允许飞行，并且玩家不在地面上，则不执行
        if (!allowFlight.get() && !mc.player.isOnGround()) {
            return;
        }

        // 武器检查与切换
        if (autoSwitch.get()) {
            if (!checkAndSwapWeapon()) return; // 如果开启自动切换但找不到Mace，则不执行后续操作
        } else if (!(mc.player.getMainHandStack().getItem() instanceof MaceItem)) {
            return; // 如果不自动切换且当前没拿Mace，则不执行
        }

        // 寻找目标
        targets.clear();
        TargetUtils.getList(targets, this::entityCheck, SortPriority.ClosestAngle, 1);

        if (targets.isEmpty()) {
            currentTarget = null;
            return;
        }
        currentTarget = targets.get(0);

        // 执行攻击
        doMaceCritAttack(currentTarget);
        
        timer = attackDelay.get();
    }

    private boolean checkAndSwapWeapon() {
        if (mc.player.getMainHandStack().getItem() instanceof MaceItem) return true;

        FindItemResult mace = InvUtils.find(itemStack -> itemStack.getItem() instanceof MaceItem, 0, 8);
        if (mace.found()) {
            originalSlot = mc.player.getInventory().selectedSlot;
            InvUtils.swap(mace.slot(), false); // 执行切换
            switchCooldown = 2; // 设置2个tick的冷却，等待服务器同步
            return true;
        }
        return false;
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (!render.get() || currentTarget == null) return;

        event.renderer.box(
            currentTarget.getBoundingBox(),
            sideColor.get(),
            lineColor.get(),
            shapeMode.get(),
            0
        );
    }

    private void doMaceCritAttack(Entity target) {
        double x = mc.player.getX();
        double y = mc.player.getY();
        double z = mc.player.getZ();
        
        double height = critHeight.get();

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
            height = Math.min(critHeight.get(), safeHeight);
        }

        // 关键逻辑：无论当前是否飞行，我们都必须把当前位置标记为"Ground Zero"
        // 这样服务器才会开始计算后续的 Fall Distance
        boolean wasOnGround = mc.player.isOnGround();

        // 1. 冻结速度 (防止位置偏移)
        mc.player.setVelocity(0, 0, 0);

        // 2. 旋转朝向 (提前对准)
        double yaw = Rotations.getYaw(target);
        double pitch = Rotations.getPitch(target);
        mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(
            (float) yaw, (float) pitch, wasOnGround, mc.player.horizontalCollision
        ));
        
        // --- 3. 建立起跳基准 (Ground Zero) ---
        // 强制发一个 onGround=true 的包。
        // 这告诉服务器："我现在站稳了"。接下来的上升包会被视为起跳。
        // 这解决了飞行状态下 fallDistance 不计算的问题。
        sendPacket(x, y, z, true);

        // --- 4. 分段飞升 (Segmented Ascent) ---
        double currentY = y;
        double targetY = y + height;
        double maxStep = 8.0; // 步长

        while (currentY < targetY) {
            currentY = Math.min(currentY + maxStep, targetY);
            // 只要我们还在往上爬，就是 OnGround = false
            sendPacket(x, currentY, z, false);
        }

        // --- 5. 分段下落 (Segmented Descent) ---
        // 我们需要回到目标头顶附近。
        // 目标头顶 1.1 格是最佳攻击点 (既不会判定太远，也不会被方块遮挡)
        double hitY = y + 1.1; 
        double droppingY = targetY;
        
        while (droppingY > hitY + maxStep) {
            droppingY -= maxStep;
            sendPacket(x, droppingY, z, false);
        }

        // --- 6. 强制锚点 (The Anchor) ---
        // 此时我们处于高空，且 fallDistance 巨大。
        // 我们强制把位置拉到目标头顶 1.1 格。
        sendPacket(x, hitY, z, false);

        // --- 7. 攻击 (Attack) ---
        // 此时：
        // 1. 位置在 hitY (距离目标 < 2格，必定命中)
        // 2. onGround = false (处于空中)
        // 3. FallDistance = height (足够触发 Smash)
        mc.getNetworkHandler().sendPacket(PlayerInteractEntityC2SPacket.attack(target, mc.player.isSneaking()));
        if (swingHand.get()) {
            mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
        }

        // --- 8. 落地确认 (Land) ---
        // 攻击完成后，立即告诉服务器我们落地了。
        // 这一步清除了摔落伤害。
        // 无论原本是否飞行，这里都必须发 true 来结算伤害。
        sendPacket(x, y, z, true);
        
        // 如果原本在飞行，为了不掉下去，可以在下一帧恢复飞行状态(但这通常由Flight模块接管)
        // 这个瞬间的 true 不会影响你的飞行模块
    }

    private void sendPacket(double x, double y, double z, boolean onGround) {
        mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
            x, y, z, onGround, mc.player.horizontalCollision
        ));
    }

    private boolean entityCheck(Entity entity) {
        if (!(entity instanceof LivingEntity) || !entity.isAlive()) return false;
        if (entity.equals(mc.player)) return false;

        // 距离检查
        if (mc.player.distanceTo(entity) > range.get()) return false;

        // 视线检查
        if (!throughWalls.get() && !mc.player.canSee(entity)) {
            return false;
        }

        // 玩家检查
        if (entity instanceof PlayerEntity p) {
            if (!players.get()) return false;
            if (p.isCreative()) return false;

            // 优先检查好友系统
            if (!Friends.get().shouldAttack(p)) return false;

            // 白名单/黑名单逻辑
            String name = p.getGameProfile().getName();
            // 修复：分割后去除每个名字前后的空格，防止匹配失败
            List<String> players = Arrays.stream(playerList.get().split(","))
                                         .map(String::trim)
                                         .collect(Collectors.toList());
            switch (listMode.get()) {
                case Whitelist -> {
                    return players.contains(name);
                }
                case Blacklist -> {
                    return !players.contains(name);
                }
            }
            return true; // Off 模式或检查通过
        }
        
        // 其他实体检查 (非玩家)
        if (ignoreNamed.get() && entity.hasCustomName()) {
            return false; // 如果开启了忽略命名且实体有名字，则不攻击
        }

        // 宠物检查
        if (ignoreTamed.get() && entity instanceof TameableEntity && ((TameableEntity) entity).isTamed()) {
            return false;
        }

        return entities.get().contains(entity.getType()); // 最后检查实体类型是否在攻击列表中
    }

    @Override
    public String getInfoString() {
        return currentTarget != null ? EntityUtils.getName(currentTarget) : null;
    }
}