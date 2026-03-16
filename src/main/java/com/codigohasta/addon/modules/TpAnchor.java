package com.codigohasta.addon.modules;

import com.codigohasta.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShapeContext;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.world.RaycastContext;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class TpAnchor extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgBypass = settings.createGroup("绕过与破坏");
    private final SettingGroup sgSafety = settings.createGroup("安全设置");
    private final SettingGroup sgTargets = settings.createGroup("目标设置");
    private final SettingGroup sgWhitelist = settings.createGroup("白名单设置");
    private final SettingGroup sgRender = settings.createGroup("渲染设置");

    // --- General ---
    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
        .name("目标范围")
        .description("搜寻目标的最大范围。")
        .defaultValue(50.0)
        .sliderMax(100.0)
        .build()
    );

    private final Setting<Integer> attackDelay = sgGeneral.add(new IntSetting.Builder()
        .name("攻击间隔")
        .description("每次攻击之间的延迟（刻）。0 = 无延迟。")
        .defaultValue(2)
        .min(0)
        .sliderMax(20)
        .build()
    );

    private final Setting<Boolean> pauseOnEat = sgGeneral.add(new BoolSetting.Builder()
        .name("进食时暂停")
        .description("当正在吃东西/喝药水时暂停攻击。")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> autoRefill = sgGeneral.add(new BoolSetting.Builder()
        .name("自动补货")
        .description("如果快捷栏没有所需物品，自动从背包移动到指定槽位。")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> placeRange = sgGeneral.add(new IntSetting.Builder()
        .name("放置半径")
        .description("以目标为中心，搜索可放置锚点位置的半径。")
        .defaultValue(5)
        .min(2)
        .sliderMax(8)
        .build()
    );

    // --- Bypass ---
    private final Setting<Integer> tpStep = sgBypass.add(new IntSetting.Builder()
        .name("传送步长")
        .description("分段传送时每步移动的距离。")
        .defaultValue(8)
        .min(1)
        .sliderMax(20)
        .build()
    );

    private final Setting<Boolean> airPlace = sgBypass.add(new BoolSetting.Builder()
        .name("空中放置")
        .description("允许在没有邻近方块的情况下放置锚点（悬空放置）。")
        .defaultValue(true)
        .build()
    );

    // --- Safety ---
    private final Setting<Boolean> autoProtect = sgSafety.add(new BoolSetting.Builder()
        .name("自动防护")
        .description("防自爆：在引爆锚点前，自动在玩家与锚点之间放置一个方块。")
        .defaultValue(true)
        .build()
    );

    private final Setting<List<Block>> shieldBlocks = sgSafety.add(new BlockListSetting.Builder()
        .name("防护方块")
        .description("用于自动防护的方块列表。")
        .defaultValue(Blocks.OBSIDIAN, Blocks.CRYING_OBSIDIAN, Blocks.NETHERITE_BLOCK, Blocks.ANVIL)
        .build()
    );

    // --- Targets ---
    private final Setting<Set<EntityType<?>>> entities = sgTargets.add(new EntityTypeListSetting.Builder()
        .name("实体类型")
        .description("选择要攻击的实体类型。")
        .defaultValue(EntityType.PLAYER)
        .build()
    );

    // 新增：攻击生存模式
    private final Setting<Boolean> attackSurvival = sgTargets.add(new BoolSetting.Builder()
        .name("攻击生存模式")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> attackCreative = sgTargets.add(new BoolSetting.Builder()
        .name("攻击创造模式")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> attackAdventure = sgTargets.add(new BoolSetting.Builder()
        .name("攻击冒险模式")
        .defaultValue(true)
        .build()
    );

    // --- Whitelist ---
    public enum ListMode {
        Whitelist,
        Blacklist,
        Off
    }

    private final Setting<ListMode> listMode = sgWhitelist.add(new EnumSetting.Builder<ListMode>()
        .name("模式")
        .description("白名单/黑名单模式。白名单：只打名单内。黑名单：不打名单内。")
        .defaultValue(ListMode.Off)
        .build()
    );

    private final Setting<String> playerList = sgWhitelist.add(new StringSetting.Builder()
        .name("玩家列表")
        .description("玩家列表，用英文逗号(,)分隔。")
        .defaultValue("")
        .visible(() -> listMode.get() != ListMode.Off)
        .build()
    );

    // --- Render ---
    private final Setting<Boolean> renderTarget = sgRender.add(new BoolSetting.Builder()
        .name("渲染目标")
        .defaultValue(true)
        .build()
    );

    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("形状模式")
        .defaultValue(ShapeMode.Lines)
        .build()
    );

    private final Setting<SettingColor> sideColor = sgRender.add(new ColorSetting.Builder()
        .name("侧面颜色")
        .defaultValue(new SettingColor(255, 0, 0, 20))
        .build()
    );

    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder()
        .name("线条颜色")
        .defaultValue(new SettingColor(255, 0, 0, 255))
        .build()
    );

    private final Setting<Boolean> debug = sgGeneral.add(new BoolSetting.Builder()
        .name("调试信息")
        .defaultValue(true)
        .build()
    );

    public TpAnchor() {
        super(AddonTemplate.CATEGORY, "如来神掌·生锚打击", "射程范围内瞬间发动能力放置并引爆重生锚打击目标");
    }

    private int delayTimer = 0;
    private Entity currentTarget = null;

    // 指定用于自动补货的槽位 (索引 0-8)
    private static final int SLOT_ANCHOR_REFILL = 6;
    private static final int SLOT_GLOWSTONE_REFILL = 7;
    private static final int SLOT_SHIELD_REFILL = 8;

    @EventHandler
    public void onTick(TickEvent.Pre event) {
        // 1. 每 tick 更新目标
        Entity target = findTarget();
        currentTarget = target;

        // 2. 冷却检查
        if (delayTimer > 0) {
            delayTimer--;
            return;
        }

        // 3. 进食检查
        if (pauseOnEat.get() && mc.player.isUsingItem()) {
            return;
        }

        // 4. 如果没有目标，停止
        if (target == null) return;

        // 5. 查找物品
        FindItemResult anchor = InvUtils.find(Items.RESPAWN_ANCHOR);
        FindItemResult glowstone = InvUtils.find(Items.GLOWSTONE);
        FindItemResult shieldBlock = InvUtils.find(itemStack -> {
            if (!(itemStack.getItem() instanceof BlockItem)) return false;
            Block block = ((BlockItem) itemStack.getItem()).getBlock();
            return shieldBlocks.get().contains(block);
        });

        // 6. 具体缺失检查
        boolean missingItem = false;

        if (!anchor.found()) {
            if (debug.get() && delayTimer == 0) error("背包缺失: 重生锚 (Respawn Anchor)!");
            missingItem = true;
        }
        
        if (!glowstone.found()) {
            if (debug.get() && delayTimer == 0) error("背包缺失: 萤石 (Glowstone)!");
            missingItem = true;
        }

        if (autoProtect.get() && !shieldBlock.found()) {
            if (debug.get() && delayTimer == 0) error("背包缺失: 防御方块 (Shield Block)!");
            missingItem = true;
        }

        if (missingItem) {
            delayTimer = 40; 
            return;
        }

        // 7. 准备槽位
        int anchorSlot = getUsableSlot(anchor, SLOT_ANCHOR_REFILL);
        int glowSlot = getUsableSlot(glowstone, SLOT_GLOWSTONE_REFILL);
        int shieldSlot = -1;
        if (autoProtect.get()) {
            shieldSlot = getUsableSlot(shieldBlock, SLOT_SHIELD_REFILL);
        }

        if (anchorSlot == -1 || glowSlot == -1 || (autoProtect.get() && shieldSlot == -1)) {
            return;
        }

        // 8. 计算位置
        AttackPos posInfo = findBestPos(target);
        if (posInfo == null) return;

        // 9. 执行攻击
        performGodAttack(posInfo, anchorSlot, glowSlot, shieldSlot);

        delayTimer = attackDelay.get();
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (!renderTarget.get() || currentTarget == null) return;
        event.renderer.box(currentTarget.getBoundingBox(), sideColor.get(), lineColor.get(), shapeMode.get(), 0);
    }

    private int getUsableSlot(FindItemResult result, int preferredSlot) {
        if (result.isHotbar()) {
            return result.slot();
        }
        
        if (autoRefill.get() && result.found()) {
            InvUtils.move().from(result.slot()).toHotbar(preferredSlot);
            return preferredSlot;
        }
        
        return -1;
    }

    private void performGodAttack(AttackPos info, int anchorSlot, int glowSlot, int shieldSlot) {
        Vec3d startPos = mc.player.getPos();
        Vec3d endPos = info.tpPos;
        BlockPos playerStandPos = BlockPos.ofFloored(endPos);

        List<Vec3d> path = computePath(startPos, endPos, tpStep.get());
        for (Vec3d point : path) {
            sendTpPacket(point);
        }
        
        // 1. 放锚
        InvUtils.swap(anchorSlot, true); 
        placePacket(info.pos);

        // 2. 防御
        if (autoProtect.get() && shieldSlot != -1) {
            BlockPos shieldPos = getShieldPos(info.pos, playerStandPos);
            if (shieldPos != null && !shieldPos.equals(playerStandPos)) {
                if (mc.world.getBlockState(shieldPos).isReplaceable()) {
                    InvUtils.swap(shieldSlot, true);
                    placePacket(shieldPos);
                }
            }
        }

        // 3. 充能
        InvUtils.swap(glowSlot, true);
        Direction interactSide = findBestInteractSide(info.pos);
        interactPacket(info.pos, interactSide);

        // 4. 引爆
        InvUtils.swap(anchorSlot, true);
        interactPacket(info.pos, interactSide);

        // 5. 返回
        for (int i = path.size() - 2; i >= 0; i--) {
            sendTpPacket(path.get(i));
        }
        sendTpPacket(startPos);

        // 6. 还原手持
        InvUtils.swapBack();
    }

    // --- Utils ---

    private Entity findTarget() {
        Entity bestTarget = null;
        float bestDist = Float.MAX_VALUE;

        for (Entity e : mc.world.getEntities()) {
            if (e == mc.player) continue;
            if (!e.isAlive()) continue;
            if (!(e instanceof LivingEntity)) continue;

            // 1. 实体类型检查
            if (!entities.get().contains(e.getType())) continue;

            // 2. 玩家特有检查
            if (e instanceof PlayerEntity) {
                PlayerEntity p = (PlayerEntity) e;
                GameMode gm = getGameMode(p);
                
                // 游戏模式检查
                if (gm == GameMode.SURVIVAL && !attackSurvival.get()) continue;
                if (gm == GameMode.CREATIVE && !attackCreative.get()) continue;
                if (gm == GameMode.SPECTATOR) continue; // 永远不攻击旁观者
                if (gm == GameMode.ADVENTURE && !attackAdventure.get()) continue;

                // 好友检查
                if (!Friends.get().shouldAttack(p)) continue;

                // 白名单/黑名单逻辑
                if (listMode.get() != ListMode.Off) {
                    String name = p.getGameProfile().getName();
                    List<String> validNames = Arrays.stream(playerList.get().split(","))
                                                 .map(String::trim)
                                                 .collect(Collectors.toList());

                    if (listMode.get() == ListMode.Whitelist) {
                        if (!validNames.contains(name)) continue;
                    } else if (listMode.get() == ListMode.Blacklist) {
                        if (validNames.contains(name)) continue;
                    }
                }
            }

            float dist = e.distanceTo(mc.player);
            if (dist > range.get()) continue;

            if (dist < bestDist) {
                bestDist = dist;
                bestTarget = e;
            }
        }
        return bestTarget;
    }

    private GameMode getGameMode(PlayerEntity player) {
        if (player == null) return GameMode.SURVIVAL;
        PlayerListEntry playerListEntry = mc.getNetworkHandler().getPlayerListEntry(player.getUuid());
        return playerListEntry != null ? playerListEntry.getGameMode() : GameMode.SURVIVAL;
    }

    private BlockPos getShieldPos(BlockPos anchorPos, BlockPos playerPos) {
        int dx = playerPos.getX() - anchorPos.getX();
        int dy = playerPos.getY() - anchorPos.getY();
        int dz = playerPos.getZ() - anchorPos.getZ();

        if (dx == 0 && dz == 0 && dy >= 2) return anchorPos.up();
        
        if (Math.abs(dx) >= 2 || Math.abs(dz) >= 2) {
             int xOff = Integer.compare(dx, 0);
             int zOff = Integer.compare(dz, 0);
             return anchorPos.add(xOff, 0, zOff);
        }
        return null;
    }

    private Vec3d findSmartTpSpot(BlockPos anchorPos) {
        if (isStrictlySafe(anchorPos.up(2)) && isBlockPassable(anchorPos.up())) {
            return anchorPos.toCenterPos().add(0, 2.0, 0);
        }

        for (Direction dir : Direction.Type.HORIZONTAL) {
            BlockPos sideTwo = anchorPos.offset(dir, 2);
            BlockPos sideOne = anchorPos.offset(dir, 1);
            if (isStrictlySafe(sideTwo) && isBlockPassable(sideOne)) {
                return sideTwo.toCenterPos();
            }
        }
        
        if (isStrictlySafe(anchorPos.up())) {
             return anchorPos.toCenterPos().add(0, 1.0, 0);
        }
        
        for (Direction dir : Direction.Type.HORIZONTAL) {
            BlockPos sideOne = anchorPos.offset(dir);
            if (isStrictlySafe(sideOne)) {
                return sideOne.toCenterPos();
            }
        }

        return null; 
    }

    private boolean isBlockPassable(BlockPos pos) {
        return mc.world.getBlockState(pos).isReplaceable();
    }

    private boolean isStrictlySafe(BlockPos pos) {
        if (!isPassable(pos)) return false;
        if (!isPassable(pos.up())) return false;
        return true;
    }

    private boolean isPassable(BlockPos pos) {
        if (!mc.world.getBlockState(pos).isReplaceable()) return false;
        if (!mc.world.getBlockState(pos).getCollisionShape(mc.world, pos).isEmpty()) return false;
        return true;
    }

    private Direction findBestInteractSide(BlockPos pos) {
        for (Direction dir : Direction.values()) {
            if (mc.world.getBlockState(pos.offset(dir)).isReplaceable()) {
                return dir;
            }
        }
        return Direction.UP;
    }

    private AttackPos findBestPos(Entity target) {
        BlockPos tPos = target.getBlockPos();
        Vec3d targetCenter = target.getBoundingBox().getCenter();

        if (target.getType() == EntityType.ENDER_DRAGON) {
            Box box = target.getBoundingBox();
            int topY = (int) Math.ceil(box.maxY);
            BlockPos dragonTop = new BlockPos(tPos.getX(), topY, tPos.getZ());
            if (checkPlace(dragonTop)) return new AttackPos(dragonTop, dragonTop.toCenterPos().add(0, 2, 0), 0.0);
        }

        List<AttackPos> candidates = new ArrayList<>();
        int r = placeRange.get();
        double rSq = r * r; 

        for (int x = -r; x <= r; x++) {
            for (int y = -r; y <= r; y++) {
                for (int z = -r; z <= r; z++) {
                    if ((x*x + y*y + z*z) > rSq) continue;

                    BlockPos pos = tPos.add(x, y, z);

                    if (checkPlace(pos)) {
                        Vec3d validTpSpot = findSmartTpSpot(pos);
                        
                        if (validTpSpot != null) {
                            double score = calculateScore(target, targetCenter, pos);
                            candidates.add(new AttackPos(pos, validTpSpot, score));
                        }
                    }
                }
            }
        }

        if (!candidates.isEmpty()) {
            candidates.sort(Comparator.comparingDouble(p -> p.score));
            return candidates.get(0);
        }

        return null;
    }

    private double calculateScore(Entity target, Vec3d targetCenter, BlockPos anchorPos) {
        double distSq = anchorPos.toCenterPos().squaredDistanceTo(targetCenter);
        double score = distSq; 

        if (!isExposed(target, anchorPos)) {
            score += 1000.0;
        }

        int openness = calculateOpenness(anchorPos);
        score += (6 - openness) * 2.0;

        return score;
    }

    private int calculateOpenness(BlockPos pos) {
        int open = 0;
        for (Direction dir : Direction.values()) {
            if (mc.world.getBlockState(pos.offset(dir)).isReplaceable()) {
                open++;
            }
        }
        return open;
    }

    private boolean isExposed(Entity target, BlockPos anchorPos) {
        Vec3d start = target.getEyePos();
        Vec3d end = anchorPos.toCenterPos();
        
        BlockHitResult result = mc.world.raycast(new RaycastContext(
            start, end, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, target
        ));

        if (result.getType() == HitResult.Type.MISS) return true;
        if (result.getBlockPos().equals(anchorPos)) return true;
        return false;
    }

    private boolean checkPlace(BlockPos pos) {
        if (!mc.world.isInBuildLimit(pos)) return false;
        if (!mc.world.getBlockState(pos).isReplaceable()) return false;
        if (!mc.world.canPlace(Blocks.RESPAWN_ANCHOR.getDefaultState(), pos, ShapeContext.absent())) {
            return false;
        }
        
        if (!airPlace.get()) {
             if (!hasNeighbor(pos)) return false;
        }

        return true; 
    }

    private boolean hasNeighbor(BlockPos pos) {
        for (Direction d : Direction.values()) {
            if (!mc.world.getBlockState(pos.offset(d)).isReplaceable()) return true;
        }
        return false;
    }

    private List<Vec3d> computePath(Vec3d start, Vec3d end, double step) {
        List<Vec3d> path = new ArrayList<>();
        double distance = start.distanceTo(end);
        if (distance <= step) {
            path.add(end);
            return path;
        }
        Vec3d vec = end.subtract(start).normalize();
        int jumps = (int) Math.ceil(distance / step);
        for (int i = 1; i <= jumps; i++) {
            path.add(i == jumps ? end : start.add(vec.multiply(i * step)));
        }
        return path;
    }

    private void sendTpPacket(Vec3d pos) {
        mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
            pos.x, pos.y, pos.z, true, false
        ));
    }

    private void placePacket(BlockPos pos) {
        Vec3d hitVec = pos.toCenterPos();
        BlockHitResult hit = new BlockHitResult(hitVec, Direction.UP, pos, false);
        mc.getNetworkHandler().sendPacket(new PlayerInteractBlockC2SPacket(Hand.MAIN_HAND, hit, 0));
        mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
    }

    private void interactPacket(BlockPos pos, Direction side) {
        Vec3d hitVec = pos.toCenterPos().add(
            side.getOffsetX() * 0.5, 
            side.getOffsetY() * 0.5, 
            side.getOffsetZ() * 0.5
        );
        BlockHitResult hit = new BlockHitResult(hitVec, side, pos, false);
        mc.getNetworkHandler().sendPacket(new PlayerInteractBlockC2SPacket(Hand.MAIN_HAND, hit, 0));
        mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
    }
    
    private static class AttackPos {
        BlockPos pos;
        Vec3d tpPos;
        double score;

        public AttackPos(BlockPos pos, Vec3d tpPos, double score) {
            this.pos = pos;
            this.tpPos = tpPos;
            this.score = score;
        }
    }
}