package com.codigohasta.addon.modules; // 根据你的 Addon 包名自行修改

import com.codigohasta.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.entity.EntityUtils;
import meteordevelopment.meteorclient.utils.entity.SortPriority;
import meteordevelopment.meteorclient.utils.entity.TargetUtils;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BowItem;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

public class TpMachineGun extends Module {

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgWeapon = settings.createGroup("武器连发设置 (Weapon)");
    private final SettingGroup sgTp = settings.createGroup("瞬移设置 (TP Options)");
    private final SettingGroup sgRender = settings.createGroup("渲染设置 (Render)");

    // ================= [ General Settings ] =================
    private final Setting<Set<EntityType<?>>> entities = sgGeneral.add(new EntityTypeListSetting.Builder()
        .name("目标实体")
        .description("选择要射击的实体类型。")
        .defaultValue(EntityType.PLAYER, EntityType.ZOMBIE, EntityType.SKELETON, EntityType.CREEPER)
        .build()
    );

    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
        .name("锁敌范围")
        .description("最大寻找目标的距离。")
        .defaultValue(30.0)
        .min(1.0)
        .sliderMax(100.0)
        .build()
    );

    private final Setting<Boolean> waitForHurtTime = sgGeneral.add(new BoolSetting.Builder()
        .name("等待无敌帧 (防吞箭)")
        .description("即使武器已上膛，也会等待目标红色受击动画结束后再瞬移开火。")
        .defaultValue(true)
        .build()
    );

    private final Setting<SortPriority> sortPriority = sgGeneral.add(new EnumSetting.Builder<SortPriority>()
        .name("目标优先级")
        .description("选择目标的优先级排序方式。")
        .defaultValue(SortPriority.LowestDistance)
        .build()
    );

    // ================= [ Weapon Settings ] =================
    private final Setting<Integer> delay = sgWeapon.add(new IntSetting.Builder()
        .name("射击冷却延迟")
        .description("每次开火后等待的 Tick 数量。越小射速越快，但容易被服务器吞包。")
        .defaultValue(3)
        .min(0)
        .sliderMax(10)
        .build()
    );

    private final Setting<Integer> tolerance = sgWeapon.add(new IntSetting.Builder()
        .name("拉弓/装填容错")
        .description("为了防止网络延迟导致弓没拉满，额外多拉的 Tick 数。")
        .defaultValue(4)
        .min(0)
        .sliderMax(10)
        .build()
    );

    // ================= [ TP Settings ] =================
    private final Setting<Double> moveDistance = sgTp.add(new DoubleSetting.Builder()
        .name("移动步长 (防拉回)")
        .description("计算滞留包时每次切片的最大距离，用于欺骗反作弊速度检测。")
        .defaultValue(8.0)
        .min(1.0)
        .sliderMax(20.0)
        .build()
    );

    private final Setting<Integer> limitPacket = sgTp.add(new IntSetting.Builder()
        .name("最大发包限制")
        .description("单次攻击允许发送的最大包数量，超过则取消攻击防Kick。")
        .defaultValue(50)
        .min(10)
        .sliderMax(200)
        .build()
    );

    private final Setting<Boolean> back = sgTp.add(new BoolSetting.Builder()
        .name("瞬移回原位 (Blink Back)")
        .description("射击后瞬间返回原位，实现幽灵狙击。")
        .defaultValue(true)
        .build()
    );

    // ================= [ Render Settings ] =================
    private final Setting<Boolean> render = sgRender.add(new BoolSetting.Builder()
        .name("渲染路径")
        .description("在世界中渲染你的穿墙寻路瞬移轨迹。")
        .defaultValue(true)
        .build()
    );

    // 内部状态
    private int timer = 0;
    private Entity target;
    private final List<Vec3d> renderPath = new ArrayList<>();

    public TpMachineGun() {
        super(AddonTemplate.CATEGORY, "tp-machine-gun", "试验模块，娱乐功能，本来设想是弩机关枪加上tp的，但是没弄成。抄袭了gcore，裤子条纹");
    }

    @Override
    public void onActivate() {
        timer = 0;
    }

    @Override
    public void onDeactivate() {
        mc.options.useKey.setPressed(false);
        if (mc.player != null) mc.interactionManager.stopUsingItem(mc.player);
    }

    @Override
    public String getInfoString() {
        return target != null ? EntityUtils.getName(target) : null;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        // 1. 冷却计时器
        if (timer > 0) {
            timer--;
            mc.options.useKey.setPressed(false);
            return;
        }

        // 2. 检查主副手武器
        Hand hand = Hand.MAIN_HAND;
        ItemStack stack = mc.player.getMainHandStack();
        
        if (!isWeapon(stack.getItem())) {
            stack = mc.player.getOffHandStack();
            hand = Hand.OFF_HAND;
        }
        
        if (!isWeapon(stack.getItem())) {
            target = null;
            mc.options.useKey.setPressed(false);
            return;
        }

        // 3. 获取目标
        updateTarget();
        if (target == null) {
            mc.options.useKey.setPressed(false);
            return;
        }

        boolean isCrossbow = stack.getItem() instanceof CrossbowItem;
        boolean isReadyToShoot = false;

        // 4. 自动拉弓与装填逻辑
        if (isCrossbow) {
            if (CrossbowItem.isCharged(stack)) {
                // 弩已上膛完毕，准备发射
                isReadyToShoot = true;
                mc.options.useKey.setPressed(false);
            } else {
                // 弩正在上膛
                mc.options.useKey.setPressed(true);
                if (!mc.player.isUsingItem()) {
                    mc.interactionManager.interactItem(mc.player, hand);
                } else {
                    int requiredTime = getPullTime(stack) + tolerance.get();
                    if (mc.player.getItemUseTime() >= requiredTime) {
                        mc.interactionManager.stopUsingItem(mc.player); // 完成上膛
                    }
                }
            }
        } else {
            // 弓的拉弦逻辑
            mc.options.useKey.setPressed(true);
            if (!mc.player.isUsingItem()) {
                mc.interactionManager.interactItem(mc.player, hand);
            } else {
                int requiredTime = getPullTime(stack) + tolerance.get();
                if (mc.player.getItemUseTime() >= requiredTime) {
                    // 弓已拉满，准备发射
                    isReadyToShoot = true;
                }
            }
        }

        // 5. 执行瞬移开火逻辑
        if (isReadyToShoot) {
            // 等待无敌帧 (防止箭射上去被弹开)
            if (waitForHurtTime.get() && ((LivingEntity) target).hurtTime > 1) {
                return; // 保持上膛/拉满状态，等待下一 Tick
            }

            // 获取合法的贴脸爆头位置
            Vec3d shootPos = getShootPos(target);
            if (shootPos == null) return;

            // 穿墙并发包射击
            doTpShoot(shootPos, hand, isCrossbow);
        }
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (render.get() && renderPath.size() >= 2) {
            for (int i = 0; i < renderPath.size() - 1; i++) {
                event.renderer.line(
                    renderPath.get(i).getX(), renderPath.get(i).getY(), renderPath.get(i).getZ(),
                    renderPath.get(i + 1).getX(), renderPath.get(i + 1).getY(), renderPath.get(i + 1).getZ(),
                    Color.RED
                );
            }
        }
    }

    // === 工具方法 ===

    private boolean isWeapon(Item item) {
        return item instanceof CrossbowItem || item instanceof BowItem;
    }

    private void updateTarget() {
        List<Entity> potentialTargets = new ArrayList<>();
        TargetUtils.getList(potentialTargets, this::entityCheck, sortPriority.get(), 1);
        target = potentialTargets.isEmpty() ? null : potentialTargets.get(0);
    }

    private boolean entityCheck(Entity entity) {
        if (!(entity instanceof LivingEntity) || !entity.isAlive() || entity == mc.player) return false;
        if (!entities.get().contains(entity.getType())) return false;
        
        // 使用手动构造坐标避免映射差异
        Vec3d myPos = new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ());
        Vec3d entityPos = new Vec3d(entity.getX(), entity.getY(), entity.getZ());
        if (myPos.distanceTo(entityPos) > range.get()) return false;
        
        if (entity instanceof PlayerEntity p && (p.isCreative() || p.isSpectator())) return false;
        return true;
    }

    private int getPullTime(ItemStack stack) {
        // 1. 如果是弓，原版满蓄力固定为 20 ticks (1秒)
        if (stack.getItem() instanceof BowItem) {
            return 20; 
        }
        // 2. 如果是弩，动态获取附魔等级
        try {
            var registry = mc.world.getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT);
            var quickChargeEntry = registry.getOrThrow(Enchantments.QUICK_CHARGE);
            int level = EnchantmentHelper.getLevel(quickChargeEntry, stack);
            return Math.max(0, 25 - 5 * level);
        } catch (Exception e) {
            return 25; // 失败时回退默认时间
        }
    }

    private void doTpShoot(Vec3d shootPos, Hand hand, boolean isCrossbow) {
        Vec3d playerPos = new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ());

        Vec3d vClipStart = null;
        Vec3d vClipEnd = null;
        boolean foundPath = false;

        // === 寻路逻辑 (直线与 VClip) ===
        if (hasClearPath(playerPos, shootPos)) {
            vClipStart = playerPos;
            vClipEnd = shootPos;
            foundPath = true;
        } else {
            double maxHeight = Math.max(playerPos.y, shootPos.y);
            double startSearchHeight = maxHeight + 1.0; 

            for (double yLevel = startSearchHeight; yLevel < startSearchHeight + 50.0; yLevel += 1.0) {
                Vec3d testUp = new Vec3d(playerPos.x, yLevel, playerPos.z);
                Vec3d testTargetUp = new Vec3d(shootPos.x, yLevel, shootPos.z);

                if (isSpaceEmpty(testUp) && isSpaceEmpty(testTargetUp) && hasClearPath(testUp, testTargetUp)) {
                    vClipStart = testUp;
                    vClipEnd = testTargetUp;
                    foundPath = true;
                    break;
                }
            }
        }

        if (!foundPath) return;

        // === 保存渲染轨迹 ===
        renderPath.clear();
        renderPath.add(playerPos);
        if (vClipStart != playerPos) renderPath.add(vClipStart);
        if (vClipEnd != shootPos) renderPath.add(vClipEnd);
        renderPath.add(shootPos);

        double totalDist = playerPos.distanceTo(vClipStart) + vClipStart.distanceTo(vClipEnd) + vClipEnd.distanceTo(shootPos);
        int packetsRequired = (int) Math.ceil(totalDist / moveDistance.get()) + 3;

        if (packetsRequired > limitPacket.get()) {
            ChatUtils.info("§c[TpMachineGun] 目标过远或墙壁过厚，超过最大发包限制 (" + packetsRequired + ")");
            return;
        }

        // 1. 发送原地滞留包 (绕过 NCP/Grim)
        for (int i = 0; i < packetsRequired; i++) {
            sendPosPacket(playerPos.x, playerPos.y, playerPos.z, false);
        }

        // 2. 发送路径包前往射击点
        if (vClipStart != playerPos) sendPosPacket(vClipStart.x, vClipStart.y, vClipStart.z, false);
        if (vClipStart.distanceTo(vClipEnd) > 0.1) sendPosPacket(vClipEnd.x, vClipEnd.y, vClipEnd.z, false);
        sendPosPacket(shootPos.x, shootPos.y, shootPos.z, false);

        // 3. 计算爆头瞄准角度
        Vec3d targetCenter = target.getBoundingBox().getCenter();
        double dX = targetCenter.x - shootPos.x;
        double dY = targetCenter.y - (shootPos.y + 1.62); // 减去视角高度
        double dZ = targetCenter.z - shootPos.z;
        float yaw = (float) (Math.toDegrees(Math.atan2(dZ, dX)) - 90.0);
        float pitch = (float) -Math.toDegrees(Math.atan2(dY, Math.sqrt(dX * dX + dZ * dZ)));

        // 4. 发送转身瞄准包
        mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.Full(shootPos.x, shootPos.y, shootPos.z, yaw, pitch, false, mc.player.horizontalCollision));
        
        // 5. 开火核心代码 (区分弓和弩)
        if (isCrossbow) {
            // 弩：通过发送交互包直接把装填好的箭射出去
            mc.interactionManager.interactItem(mc.player, hand);
        } else {
            // 弓：发送松手包把箭射出去
            mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(
                PlayerActionC2SPacket.Action.RELEASE_USE_ITEM, BlockPos.ORIGIN, Direction.DOWN, 0 
            ));
            mc.player.stopUsingItem();
        }

        mc.player.swingHand(hand); // 挥手动画
        mc.options.useKey.setPressed(false);
        timer = delay.get(); // 进入射击冷却，下一发准备

        // 6. 瞬间回闪与防摔落
        if (back.get()) {
            if (vClipStart.distanceTo(vClipEnd) > 0.1) sendPosPacket(vClipEnd.x, vClipEnd.y, vClipEnd.z, false);
            if (vClipStart != playerPos) sendPosPacket(vClipStart.x, vClipStart.y, vClipStart.z, false);
            
            // 落地防摔落包处理
            sendPosPacket(playerPos.x, playerPos.y + 0.01, playerPos.z, false);
            sendPosPacket(playerPos.x, playerPos.y, playerPos.z, true);
            
            mc.player.setPosition(playerPos.x, playerPos.y, playerPos.z);
        } else {
            mc.player.setPosition(shootPos.x, shootPos.y, shootPos.z);
        }

        mc.player.fallDistance = 0.0f; // 清理客户端摔落伤害
    }

    private Vec3d getShootPos(Entity target) {
        List<Vec3d> validPositions = new ArrayList<>();
        int centerX = (int) Math.floor(target.getX());
        int centerY = (int) Math.floor(target.getY());
        int centerZ = (int) Math.floor(target.getZ());
        int border = 2; // 搜索半径

        Vec3d targetPosVec = new Vec3d(target.getX(), target.getY(), target.getZ());

        for (int x = centerX - border; x <= centerX + border; x++) {
            for (int y = centerY - 1; y <= centerY + border; y++) {
                for (int z = centerZ - border; z <= centerZ + border; z++) {
                    Vec3d vec = new Vec3d(x + 0.5, y, z + 0.5);

                    if (vec.distanceTo(targetPosVec) > 4.0) continue;
                    if (!isSpaceEmpty(vec)) continue;

                    Vec3d eyePos = vec.add(0.0, 1.62, 0.0);
                    Vec3d targetCenter = target.getBoundingBox().getCenter();
                    
                    if (canSee(eyePos, targetCenter)) {
                        validPositions.add(vec);
                    }
                }
            }
        }

        Vec3d myPos = new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ());
        validPositions.sort(Comparator.comparingDouble(v -> v.distanceTo(myPos)));
        return validPositions.isEmpty() ? null : validPositions.get(0);
    }

    private boolean isSpaceEmpty(Vec3d pos) {
        Box box = new Box(pos.getX() - 0.3, pos.getY(), pos.getZ() - 0.3, pos.getX() + 0.3, pos.getY() + 1.8, pos.getZ() + 0.3);
        return mc.world.isSpaceEmpty(box);
    }

    private boolean hasClearPath(Vec3d start, Vec3d end) {
        double dist = start.distanceTo(end);
        int steps = (int) (dist * 2.5);
        for (int i = 0; i <= steps; i++) {
            Vec3d check = start.lerp(end, (double) i / steps);
            if (!isSpaceEmpty(check)) return false;
        }
        return true;
    }

    private boolean canSee(Vec3d start, Vec3d end) {
        RaycastContext context = new RaycastContext(
            start, end, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc.player
        );
        return mc.world.raycast(context).getType() == HitResult.Type.MISS;
    }

    private void sendPosPacket(double x, double y, double z, boolean onGround) {
        mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
            x, y, z, onGround, mc.player.horizontalCollision
        ));
    }
}