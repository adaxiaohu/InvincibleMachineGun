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
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
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

public class TpBowAura extends Module {

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
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
        .name("等待无敌帧")
        .description("等待目标受击的红色无敌帧结束后再射击，防吞箭。")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> shootDelay = sgGeneral.add(new IntSetting.Builder()
        .name("射击延迟")
        .description("两次射击之间的最小延迟(毫秒)。")
        .defaultValue(450)
        .min(0)
        .sliderMax(2000)
        .build()
    );

    private final Setting<SortPriority> sortPriority = sgGeneral.add(new EnumSetting.Builder<SortPriority>()
        .name("目标优先级")
        .description("选择目标的优先级排序方式。")
        .defaultValue(SortPriority.LowestDistance)
        .build()
    );

    // =================[ TP Settings ] =================
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
    private long lastShootTime = 0;
    private Entity target;
    private final List<Vec3d> renderPath = new ArrayList<>();

    public TpBowAura() {
        super(AddonTemplate.CATEGORY, "明枪暗箭", "一瞬间传送到目标身边用弓射箭打他。娱乐功能，抄袭了gcore，裤子条纹");
    }

    @Override
    public String getInfoString() {
        return target != null ? EntityUtils.getName(target) : null;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        // 1. 检查是否拿着弓且正在拉弓
        String mainHandName = mc.player.getMainHandStack().getItem().toString().toLowerCase();
        String offHandName = mc.player.getOffHandStack().getItem().toString().toLowerCase();
        boolean holdingBow = mainHandName.contains("bow") || offHandName.contains("bow");

        if (!holdingBow || !mc.player.isUsingItem()) {
            target = null;
            return;
        }

        // 2. 射击延迟
        if (System.currentTimeMillis() - lastShootTime < shootDelay.get()) return;

        // 3. 获取目标
        updateTarget();
        if (target == null) return;

        // 4. 无敌帧判定
        if (waitForHurtTime.get() && ((LivingEntity) target).hurtTime > 1) return;

        // 5. 获取爆头射击点 (必须能看到目标)
        Vec3d shootPos = getShootPos(target);
        if (shootPos == null) return;

        // 6. 寻路并执行瞬移射击
        doTpShoot(shootPos);
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

    private void updateTarget() {
        List<Entity> potentialTargets = new ArrayList<>();
        TargetUtils.getList(potentialTargets, this::entityCheck, sortPriority.get(), 1);
        target = potentialTargets.isEmpty() ? null : potentialTargets.get(0);
    }

    private boolean entityCheck(Entity entity) {
        if (!(entity instanceof LivingEntity) || !entity.isAlive() || entity == mc.player) return false;
        if (!entities.get().contains(entity.getType())) return false;
        if (mc.player.distanceTo(entity) > range.get()) return false;
        if (entity instanceof PlayerEntity p && (p.isCreative() || p.isSpectator())) return false;
        return true;
    }

    private void doTpShoot(Vec3d shootPos) {
        // [修复1] 手动构造玩家坐标
        Vec3d playerPos = new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ());

        Vec3d vClipStart = null;
        Vec3d vClipEnd = null;
        boolean foundPath = false;

        // === 寻路逻辑 (VClip 结合直连) ===
        // 1. 先尝试直线连接 (无障碍物时最节省包)
        if (hasClearPath(playerPos, shootPos)) {
            vClipStart = playerPos;
            vClipEnd = shootPos;
            foundPath = true;
        } 
        // 2. 直线被挡，使用 VClip 往上寻找天花板空间跨过墙壁
        else {
            double maxHeight = Math.max(playerPos.y, shootPos.y);
            double startSearchHeight = maxHeight + 1.0; // 从高点往上找

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

        if (!foundPath) {
            // 完全找不到路 (例如在地底且头顶没有空间)
            return; 
        }

        // === 渲染路径保存 ===
        renderPath.clear();
        renderPath.add(playerPos);
        if (vClipStart != playerPos) renderPath.add(vClipStart);
        if (vClipEnd != shootPos) renderPath.add(vClipEnd);
        renderPath.add(shootPos);

        // === 发包计算与防作弊绕过 ===
        double totalDist = playerPos.distanceTo(vClipStart) + vClipStart.distanceTo(vClipEnd) + vClipEnd.distanceTo(shootPos);
        int packetsRequired = (int) Math.ceil(totalDist / moveDistance.get()) + 3;

        if (packetsRequired > limitPacket.get()) {
            ChatUtils.info("§c[TpBow] 目标过远或墙壁过厚，超过最大发包限制 (" + packetsRequired + ")");
            return;
        }

        // 1. 发送原地滞留包 (降低移动均速，绕过NCP/Grim检测)
        for (int i = 0; i < packetsRequired; i++) {
            sendPosPacket(playerPos.x, playerPos.y, playerPos.z, false);
        }

        // 2. 发送路径包 (前往射击点)
        if (vClipStart != playerPos) sendPosPacket(vClipStart.x, vClipStart.y, vClipStart.z, false);
        if (vClipStart.distanceTo(vClipEnd) > 0.1) sendPosPacket(vClipEnd.x, vClipEnd.y, vClipEnd.z, false);
        sendPosPacket(shootPos.x, shootPos.y, shootPos.z, false);

        // 3. 计算爆头角度
        Vec3d targetCenter = target.getBoundingBox().getCenter();
        double dX = targetCenter.x - shootPos.x;
        double dY = targetCenter.y - (shootPos.y + 1.62); // 减去视角高度
        double dZ = targetCenter.z - shootPos.z;
        float yaw = (float) (Math.toDegrees(Math.atan2(dZ, dX)) - 90.0);
        float pitch = (float) -Math.toDegrees(Math.atan2(dY, Math.sqrt(dX * dX + dZ * dZ)));

        // 4. 发送转身与开火包
        mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.Full(shootPos.x, shootPos.y, shootPos.z, yaw, pitch, false, mc.player.horizontalCollision));
        
        // 发送松手开弓包 (1.21.x sequence = 0)
        mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(
            PlayerActionC2SPacket.Action.RELEASE_USE_ITEM, BlockPos.ORIGIN, Direction.DOWN, 0 
        ));
        mc.player.stopUsingItem();
        lastShootTime = System.currentTimeMillis();

        // 5. 瞬间回闪与防摔落处理
        if (back.get()) {
            // 原路返回包
            if (vClipStart.distanceTo(vClipEnd) > 0.1) sendPosPacket(vClipEnd.x, vClipEnd.y, vClipEnd.z, false);
            if (vClipStart != playerPos) sendPosPacket(vClipStart.x, vClipStart.y, vClipStart.z, false);
            
            // 落地防摔落包处理 (稍微抬高0.01并发 onGround=true 重置服务器摔落判定)
            sendPosPacket(playerPos.x, playerPos.y + 0.01, playerPos.z, false);
            sendPosPacket(playerPos.x, playerPos.y, playerPos.z, true); // <== 防摔落核心包
            
            mc.player.setPosition(playerPos.x, playerPos.y, playerPos.z);
        } else {
            mc.player.setPosition(shootPos.x, shootPos.y, shootPos.z);
        }

        // 清理客户端摔落伤害缓存
        mc.player.fallDistance = 0.0f;
    }

    private Vec3d getShootPos(Entity target) {
        List<Vec3d> validPositions = new ArrayList<>();
        int centerX = (int) Math.floor(target.getX());
        int centerY = (int) Math.floor(target.getY());
        int centerZ = (int) Math.floor(target.getZ());
        int border = 2; // 搜索半径

        // [修复2] 提前构造目标坐标 Vec3d，防止多次调用
        Vec3d targetPosVec = new Vec3d(target.getX(), target.getY(), target.getZ());

        for (int x = centerX - border; x <= centerX + border; x++) {
            for (int y = centerY - 1; y <= centerY + border; y++) {
                for (int z = centerZ - border; z <= centerZ + border; z++) {
                    Vec3d vec = new Vec3d(x + 0.5, y, z + 0.5);

                    // 不能离目标太远
                    if (vec.distanceTo(targetPosVec) > 4.0) continue;

                    // 1. 坐标必须有落脚点或是空的，且不能卡墙
                    if (!isSpaceEmpty(vec)) continue;

                    // 2. 必须能看到目标的中心点 (保证箭能射中)
                    Vec3d eyePos = vec.add(0.0, 1.62, 0.0);
                    Vec3d targetCenter = target.getBoundingBox().getCenter();
                    
                    if (canSee(eyePos, targetCenter)) {
                        validPositions.add(vec);
                    }
                }
            }
        }

        // [修复3] 排序，找离自己最近的点，手动构造自身坐标
        Vec3d myPos = new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ());
        validPositions.sort(Comparator.comparingDouble(v -> v.distanceTo(myPos)));
        return validPositions.isEmpty() ? null : validPositions.get(0);
    }

    // === 核心工具方法 ===

    private boolean isSpaceEmpty(Vec3d pos) {
        // 模拟玩家碰撞箱 0.6宽 x 1.8高
        Box box = new Box(pos.getX() - 0.3, pos.getY(), pos.getZ() - 0.3, pos.getX() + 0.3, pos.getY() + 1.8, pos.getZ() + 0.3);
        return mc.world.isSpaceEmpty(box);
    }

    private boolean hasClearPath(Vec3d start, Vec3d end) {
        double dist = start.distanceTo(end);
        int steps = (int) (dist * 2.5); // 提高精度检测
        for (int i = 0; i <= steps; i++) {
            Vec3d check = start.lerp(end, (double) i / steps);
            if (!isSpaceEmpty(check)) return false;
        }
        return true;
    }

    private boolean canSee(Vec3d start, Vec3d end) {
        RaycastContext context = new RaycastContext(
            start, end,
            RaycastContext.ShapeType.COLLIDER,
            RaycastContext.FluidHandling.NONE,
            mc.player
        );
        return mc.world.raycast(context).getType() == HitResult.Type.MISS;
    }

    private void sendPosPacket(double x, double y, double z, boolean onGround) {
        // 1.21.x Record Data 发包规范
        mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
            x, y, z, onGround, mc.player.horizontalCollision
        ));
    }
}