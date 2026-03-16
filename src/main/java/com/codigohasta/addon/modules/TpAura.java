package com.codigohasta.addon.modules;

import net.minecraft.util.math.Vec3d;

import com.codigohasta.addon.AddonTemplate;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.entity.EntityUtils;
import meteordevelopment.meteorclient.utils.entity.TargetUtils;
import meteordevelopment.meteorclient.utils.entity.SortPriority;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;

import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.Arrays;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.Set;

public class TpAura extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgTeleport = settings.createGroup("Teleport");
    private final SettingGroup sgAttack = settings.createGroup("Attack");
    private final SettingGroup sgTargeting = settings.createGroup("Targeting");
    private final SettingGroup sgWhitelist = settings.createGroup("Whitelist");
    private final SettingGroup sgRender = settings.createGroup("Render");

    // --- General ---
    private final Setting<Boolean> requireMace = sgGeneral.add(new BoolSetting.Builder()
        .name("require-mace")
        .description("只在手持Mace时启用")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> autoSwitch = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-switch")
        .description("自动切换到Mace")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> allowFlight = sgGeneral.add(new BoolSetting.Builder()
        .name("allow-flight")
        .description("允许在飞行或空中时发动攻击 (将使用空中寻路模式)。")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> swingHand = sgGeneral.add(new BoolSetting.Builder()
        .name("swing-hand")
        .description("是否显示手臂摆动动画")
        .defaultValue(true)
        .build()
    );

    // --- Teleport ---
    private final Setting<Double> maxRange = sgTeleport.add(new DoubleSetting.Builder()
        .name("max-range")
        .description("最大传送范围")
        .defaultValue(50.0)
        .min(0)
        .sliderRange(0, 100)
        .build()
    );

    private final Setting<TeleportMode> teleportMode = sgTeleport.add(new EnumSetting.Builder<TeleportMode>()
        .name("teleport-mode")
        .description("传送模式")
        .defaultValue(TeleportMode.Instant)
        .build()
    );

    // --- Attack ---
    private final Setting<Boolean> useCritical = sgAttack.add(new BoolSetting.Builder()
        .name("use-critical")
        .description("使用暴击/坠落伤害 (建议开启)")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> criticalHeight = sgAttack.add(new DoubleSetting.Builder()
        .name("critical-height")
        .description("坠落高度 (10-15格最佳)")
        .defaultValue(15.0)
        .min(1)
        .sliderRange(5, 50)
        .visible(useCritical::get)
        .build()
    );

    private final Setting<Boolean> autoHeight = sgAttack.add(new BoolSetting.Builder()
        .name("auto-height")
        .description("自动检测头顶方块并使用安全高度进行暴击。")
        .defaultValue(true)
        .visible(useCritical::get)
        .build()
    );

    // 保留设置项以兼容旧配置，但在代码中我们会自动计算最优解
    private final Setting<Integer> fallPackets = sgAttack.add(new IntSetting.Builder()
        .name("fall-packets")
        .description("下落插值包数量 (自动覆盖优化)")
        .defaultValue(2)
        .visible(useCritical::get)
        .build()
    );

    private final Setting<Integer> attackDelay = sgAttack.add(new IntSetting.Builder()
        .name("attack-delay")
        .description("攻击延迟（刻）。建议 15 左右。")
        .defaultValue(15)
        .min(0)
        .sliderRange(0, 40)
        .build()
    );

    // --- Targeting ---
    private final Setting<Boolean> players = sgTargeting.add(new BoolSetting.Builder()
        .name("players")
        .description("攻击玩家 (快捷开关)")
        .defaultValue(true)
        .build()
    );

    private final Setting<Set<EntityType<?>>> entities = sgTargeting.add(new EntityTypeListSetting.Builder()
        .name("entities")
        .description("选择要攻击的具体生物种类。")
        .onlyAttackable()
        .defaultValue(EntityType.PLAYER)
        .build()
    );

    // --- 新增：索敌模式 ---
    public enum SortMode {
        Closest("最近距离"),
        Furthest("最远距离"),
        LowestHealth("最低血量"),
        HighestHealth("最高血量"),
        Angle("最小角度");

        private final String title;
        SortMode(String title) { this.title = title; }
        @Override public String toString() { return title; }
    }

    private final Setting<SortMode> sortMode = sgTargeting.add(new EnumSetting.Builder<SortMode>()
        .name("sort-mode")
        .description("选择目标的优先级。")
        .defaultValue(SortMode.Angle)
        .build()
    );

    private final Setting<Boolean> throughWalls = sgTargeting.add(new BoolSetting.Builder()
        .name("through-walls")
        .description("穿墙攻击 (无视视线检测)")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> maxTargets = sgTargeting.add(new IntSetting.Builder()
        .name("max-targets")
        .description("最大目标数量")
        .defaultValue(1)
        .min(1)
        .max(1)
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




    private enum Stage {
        FindingTarget,
        Teleporting,
        Attacking,
        Returning
    }

    private int attackTimer;
    private int originalSlot = -1;
    private final List<Entity> targets = new ArrayList<>();
    private Stage stage = Stage.FindingTarget;
    private Entity currentTarget;
    private Vec3d startPos;
    private boolean isFluidAttack = false; // 新增标志位，判断是否为流体攻击
    private Vec3d attackPos;


    public TpAura() {
        super(AddonTemplate.CATEGORY, "如来神掌", "如来神掌。从天而降的掌法。娱乐功能，没有寻路能力");
    }

    @Override
    public void onActivate() {
        // 初始化所有状态
        attackTimer = 0;
        originalSlot = -1;
        targets.clear();
        stage = Stage.FindingTarget;
        currentTarget = null;
        startPos = null;
        isFluidAttack = false;
        attackPos = null;
    }

    @Override
    public void onDeactivate() {
        if (originalSlot != -1 && autoSwitch.get() && mc.player != null) {
            // 正确的切回物品方式
            InvUtils.swap(originalSlot, false);
            originalSlot = -1;
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        // 状态机驱动的逻辑
        switch (stage) {
            case FindingTarget -> findTarget();
            case Teleporting -> doTeleport();
            case Attacking -> doAttack();
            case Returning -> doReturn();
        }
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

    private void findTarget() {
        if (attackTimer > 0) {
            attackTimer--;
            return;
        }

        // 武器检查与切换
        if (requireMace.get() && !(mc.player.getMainHandStack().getItem().toString().contains("mace"))) {
            if (autoSwitch.get()) {
                FindItemResult mace = InvUtils.find(itemStack -> itemStack.getItem().toString().contains("mace"), 0, 8);
                if (mace.found()) {
                    if (originalSlot == -1) originalSlot = ((com.codigohasta.addon.mixin.InventoryAccessor) mc.player.getInventory()).getSelectedSlot();
                    mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(mace.slot()));
                } else {
                    return; // 找不到Mace则不继续
                }
            } else {
                return; // 不自动切换且没拿Mace则不继续
            }
        }

        // 索敌
        SortPriority priority = switch (sortMode.get()) {
            case Closest -> SortPriority.LowestDistance;
            case Furthest -> SortPriority.HighestDistance;
            case LowestHealth -> SortPriority.LowestHealth;
            case HighestHealth -> SortPriority.HighestHealth;
            case Angle -> SortPriority.ClosestAngle;
        };

        targets.clear();
        TargetUtils.getList(targets, this::entityCheck, priority, maxTargets.get());

        if (targets.isEmpty()) {
            currentTarget = null;

            return;
        }

        currentTarget = targets.get(0);

        startPos = new net.minecraft.util.math.Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ());

        // 寻找攻击位置
        this.attackPos = findOptimalAttackPos(currentTarget);
        if (this.attackPos == null) {
            info("找不到适合攻击的位置，已取消。");
            currentTarget = null;
            return;
        }

        // 进入下一阶段
        stage = Stage.Teleporting;
    }

    private void doTeleport() {
        teleportTo(startPos, attackPos, true);
        stage = Stage.Attacking;
    }

    private void doAttack() {
        // 计算高度
        double height = criticalHeight.get();
        isFluidAttack = currentTarget.isInLava() || currentTarget.isSubmergedInWater();

        // 如果开启了自动高度检测
        if (autoHeight.get() && useCritical.get() && !isFluidAttack) {
            double safeHeight = 0;
            // 从攻击位置的上方开始检测，为玩家身体留出2格空间
            BlockPos.Mutable checkPos = new BlockPos.Mutable(attackPos.getX(), attackPos.getY() + 2, attackPos.getZ());

            // 向上循环检测，直到遇到非空气方块
            for (int i = 0; i < 256; i++) {
                if (mc.world.getBlockState(checkPos.move(Direction.UP)).isAir()) {
                    safeHeight++;
                } else {
                    break; // 遇到障碍，停止计数
                }
            }
            height = Math.min(criticalHeight.get(), safeHeight);
        }

        boolean canSmash = useCritical.get() && height >= 2.0 && !isFluidAttack;
        double startY = attackPos.y + height;

        // 序列开始
        mc.player.setVelocity(0, 0, 0);

        // 旋转朝向
        double yaw = Rotations.getYaw(currentTarget);
        double pitch = Rotations.getPitch(currentTarget);
        mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(
            (float) yaw, (float) pitch, true, mc.player.horizontalCollision
        ));

        // 虚拟飞升
        if (canSmash) {
            double currentY = attackPos.y;
            double maxStep = 9.0;
            while (currentY < startY) {
                currentY = Math.min(currentY + maxStep, startY);
                sendPacket(attackPos.x, currentY, attackPos.z, false);
            }
        }

        // 虚拟下落
        if (canSmash) {
            double hitY = attackPos.y + 1.5;
            double droppingY = startY;
            double fallStep = 9.0;

            while (droppingY > hitY + fallStep) {
                droppingY -= fallStep;
                sendPacket(attackPos.x, droppingY, attackPos.z, false);
            }
            sendPacket(attackPos.x, hitY, attackPos.z, false);
        }

        // 攻击
        mc.getNetworkHandler().sendPacket(PlayerInteractEntityC2SPacket.attack(currentTarget, mc.player.isSneaking()));
        if (swingHand.get()) {
            mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
        }

        // 落地确认
        sendPacket(attackPos.x, attackPos.y, attackPos.z, true);

        // 进入下一阶段
        if (teleportMode.get() == TeleportMode.Instant) {
            stage = Stage.Returning;
        } else {
            attackTimer = attackDelay.get();
            stage = Stage.FindingTarget;
        }
    }

    private void doReturn() {
        teleportTo(attackPos, startPos, true);
        attackTimer = attackDelay.get();
        stage = Stage.FindingTarget;
    }

    private Vec3d findOptimalAttackPos(Entity target) {
        // 智能模式切换：
        // 1. 如果玩家开启了飞行模式
        // 2. 或者目标本身就在空中 (没有站在方块上)
        // 则强制使用为飞行目标优化的空战寻路逻辑
        if (allowFlight.get() || !target.isOnGround()) {
            // 如果目标在流体中，优先使用流体攻击逻辑
            if (target.isInLava() || target.isSubmergedInWater()) {
                Vec3d fluidPos = findFluidAttackPos(target);
                if (fluidPos != null) return fluidPos;
            }
            // 否则使用空战逻辑
            return findAirborneAttackPos(target);
        }

        // 动态降级策略：
        // 1. 优先尝试寻找能执行完整暴击的位置
        if (useCritical.get()) {
            Vec3d pos = findPositionForHeight(target, criticalHeight.get());
            if (pos != null) return pos;
        }

        // 2. 如果找不到，尝试寻找能执行普通小跳暴击的位置 (3格高)
        Vec3d pos = findPositionForHeight(target, 3.0);
        if (pos != null) return pos;

        // 3. 如果还找不到，只寻找一个能站立的平地攻击位置 (2格高)
        return findPositionForHeight(target, 2.0);
    }

    private Vec3d findPositionForHeight(Entity target, double heightNeeded) {
        // 螺旋搜索算法
        int radius = 4; // 扩大搜索半径
        int x = 0, z = 0, dx = 0, dz = -1;
        Vec3d targetPos = new net.minecraft.util.math.Vec3d(target.getX(), target.getY(), target.getZ());

        for (int i = 0; i < Math.pow(radius * 2 + 1, 2); i++) {
            if ((-radius <= x) && (x <= radius) && (-radius <= z) && (z <= radius)) {
                BlockPos.Mutable checkPos = new BlockPos.Mutable(targetPos.x + x, targetPos.y, targetPos.z + z);

                // 在更大的垂直范围内寻找安全的落脚点
                for (int yOffset = 3; yOffset >= -3; yOffset--) { // 稍微缩小垂直搜索范围，提高效率
                    checkPos.setY((int) Math.round(targetPos.y + yOffset));

                    // 1. 检查这个点本身是否安全
                    if (isSafeStandableBlock(checkPos)) {
                        Vec3d safePos = new Vec3d(checkPos.getX() + 0.5, checkPos.getY(), checkPos.getZ() + 0.5);

                        // 2. 检查从地面到所需高度的空间是否足够 (玩家身高2格)
                        Box spaceCheck = new Box(safePos.x - 0.5, safePos.y, safePos.z - 0.5, safePos.x + 0.5, safePos.y + heightNeeded - 0.1, safePos.z + 0.5);
                        if (mc.world.isSpaceEmpty(spaceCheck)) {
                            // 3. 确保这个位置在玩家的攻击范围内
                            if (new net.minecraft.util.math.Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ()).distanceTo(safePos) <= maxRange.get()) {
                                return safePos; // 找到了！
                            }
                        }
                    }
                }
            }

            // 螺旋算法步进
            if ((x == z) || ((x < 0) && (x == -z)) || ((x > 0) && (x == 1 - z))) {
                int temp = dx;
                dx = -dz;
                dz = temp;
            }
            x += dx;
            z += dz;
        }
        return null;
    }

    private Vec3d findFluidAttackPos(Entity target) {
        // 目标在流体中，我们直接传送到目标身边
        BlockPos targetBlockPos = target.getBlockPos();
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                for (int y = -1; y <= 1; y++) { // 在目标垂直方向上小范围搜索
                    BlockPos.Mutable checkPos = new BlockPos.Mutable(targetBlockPos.getX() + x, targetBlockPos.getY() + y, targetBlockPos.getZ() + z);

                    // 检查这个位置是否是安全的流体方块
                    if (isSafeFluidBlock(mc.world, checkPos)) {
                        Vec3d fluidPos = new Vec3d(checkPos.getX() + 0.5, checkPos.getY(), checkPos.getZ() + 0.5);
                        if (new net.minecraft.util.math.Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ()).distanceTo(fluidPos) <= maxRange.get()) {
                            return fluidPos; // 找到一个可以传送的流体点
                        }
                    }
                }
            }
        }
        return null; // 没找到
    }

    private boolean isSafeFluidBlock(World world, BlockPos pos) {
        if (world == null) return false;
        // 玩家需要两格空间，这两格都必须是可替换的方块（如空气、水、岩浆），不能是固体方块
        if (!world.getBlockState(pos).isReplaceable()) return false;
        if (!world.getBlockState(pos.up()).isReplaceable()) return false;
        // 并且至少脚部位置必须是流体
        if (world.getFluidState(pos).isEmpty()) return false;
        return true;
    }

    private Vec3d findAirborneAttackPos(Entity target) {
        // 空战模式：不再寻找“落脚点”，而是直接以目标为中心进行攻击。
        // 只要目标在攻击范围内，就认为找到了攻击位置。
        if (new net.minecraft.util.math.Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ()).distanceTo(new net.minecraft.util.math.Vec3d(target.getX(), target.getY(), target.getZ())) <= maxRange.get()) {
            return new net.minecraft.util.math.Vec3d(target.getX(), target.getY(), target.getZ());
        } else {
            return null;
        }
    }

    private boolean isSafeStandableBlock(BlockPos pos) {
        if (mc.world == null) return false;

        // 检查脚下方块是否为完整方块 (能站稳)
        BlockPos below = pos.down();
        if (!mc.world.getBlockState(below).isSideSolidFullSquare(mc.world, below, Direction.UP)) return false;
    
        // 检查玩家身体所在的2格空间是否安全。
        // 允许是空气或可替换的流体（如水），但不能是岩浆或固体方块。
        if (!mc.world.getBlockState(pos).isReplaceable()) {
            return false;
        }
        if (!mc.world.getBlockState(pos.up()).isReplaceable()) {
            return false;
        }
    
        return true;
    }

    private void teleportTo(Vec3d from, Vec3d to, boolean ignoreCollision) {
        // 彻底重写传送逻辑，采用路径点传送，防止 "moved too far"
        double distance = from.distanceTo(to);
        double steps = Math.ceil(distance / 8.0); // 使用更安全的步长

        for (int i = 1; i <= steps; i++) { // 使用 <= 确保最后一步被执行
            Vec3d waypoint = from.lerp(to, i / steps);
            // 传送过程中 onGround 必须为 false，否则会重置 fallDistance
            sendPacket(waypoint.x, waypoint.y, waypoint.z, false);
        }
    }

    private void sendPacket(double x, double y, double z, boolean onGround) {
        mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
            x, y, z, onGround, mc.player.horizontalCollision
        ));
    }
    
    private boolean entityCheck(Entity entity) {
        if (!(entity instanceof LivingEntity) || !entity.isAlive()) return false;
        if (entity.equals(mc.player)) return false;

        if (mc.player.distanceTo(entity) > maxRange.get()) return false;
        
        if (!throughWalls.get()) {
            if (!mc.player.canSee(entity)) return false;
        }

        // 玩家检查
        if (entity instanceof PlayerEntity p) {
            if (!players.get()) return false;
            if (p.isCreative()) return false;

            // 优先检查好友系统
            if (!Friends.get().shouldAttack(p)) return false;

            // 白名单/黑名单逻辑
            String name = p.getName().getString();
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

        return entities.get().contains(entity.getType()); // 最后检查实体类型是否在攻击列表中
    }

    @Override
    public String getInfoString() {
        if (currentTarget != null) {
            String status = switch (stage) {
                case FindingTarget -> "Finding...";
                case Teleporting -> "Teleporting...";
                case Attacking -> "Attacking...";
                case Returning -> "Returning...";
            };
            return status + " " + EntityUtils.getName(currentTarget);
        }
        return "Idle";
    }

    public enum TeleportMode {
        Instant("瞬间回传"),
        None("传送不回传");

        private final String title;

        TeleportMode(String title) {
            this.title = title;
        }

        @Override
        public String toString() {
            return title;
        }
    }
}