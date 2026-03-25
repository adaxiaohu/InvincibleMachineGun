package com.codigohasta.addon.modules;

import com.codigohasta.addon.AddonTemplate;

import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.mixininterface.IPlayerMoveC2SPacket;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.util.math.Vec3d;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShapeContext;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.*;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.GameMode;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

import java.util.*;
import java.util.stream.Collectors;


public class TpAnchor extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgTP = settings.createGroup("传送");
    private final SettingGroup sgSafety = settings.createGroup("安全设置");
    private final SettingGroup sgTargets = settings.createGroup("目标设置");
    private final SettingGroup sgWhitelist = settings.createGroup("白名单设置");
    private final SettingGroup sgRender = settings.createGroup("渲染设置");

    // --- General ---
    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
        .name("打击距离")
        .description("搜寻目标的半径。")
        .defaultValue(49.0
        )
        .min(1.0)
        .sliderMax(100.0)
        .build()
    );

    private final Setting<Integer> attackDelay = sgGeneral.add(new IntSetting.Builder()
        .name("打击间隔")
        .defaultValue(2)
        .min(0)
        .sliderMax(20)
        .build()
    );

    private final Setting<Boolean> pauseOnEat = sgGeneral.add(new BoolSetting.Builder()
        .name("吃东西暂停")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> autoRefill = sgGeneral.add(new BoolSetting.Builder()
        .name("自动补货")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> placeRange = sgGeneral.add(new IntSetting.Builder()
        .name("放置半径")
        .defaultValue(5)
        .min(2)
        .sliderMax(8)
        .build()
    );

    // --- TPAura 核心设置 ---
    public enum Mode { Vanilla, Paper }
    private final Setting<Mode> tpMode = sgTP.add(new EnumSetting.Builder<Mode>()
        .name("模式")
        .description("Vanilla = 原版限制, Paper = 高发包绕过")
        .defaultValue(Mode.Vanilla)
        .build()
    );

    private final Setting<Boolean> goUp = sgTP.add(new BoolSetting.Builder()
        .name("VClip ")
        .description("执行垂直穿墙以绕过地形碰撞。")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> vanillaPackets = sgTP.add(new IntSetting.Builder()
        .name("垃圾包 (Vanilla)")
        .defaultValue(4)
        .visible(() -> tpMode.get() == Mode.Vanilla)
        .build()
    );

    private final Setting<Integer> paperPackets = sgTP.add(new IntSetting.Builder()
        .name("垃圾包 (Paper)")
        .defaultValue(5)
        .visible(() -> tpMode.get() == Mode.Paper)
        .build()
    );

    private final Setting<Double> horizontalOffset = sgTP.add(new DoubleSetting.Builder()
        .name("水平归位偏移")
        .defaultValue(0.05)
        .build()
    );

    private final Setting<Double> yOffset = sgTP.add(new DoubleSetting.Builder()
        .name("垂直归位偏移")
        .defaultValue(0.01)
        .build()
    );

    private final Setting<Boolean> skipCollisionCheck = sgTP.add(new BoolSetting.Builder()
        .name("船穿墙检查")
        .description("如果开启了 BoatNoclip，跳过方块碰撞检测。-来自裤子条纹的")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> airPlace = sgTP.add(new BoolSetting.Builder()
        .name("空中放置")
        .defaultValue(true)
        .build()
    );

    // --- Safety ---
    private final Setting<Boolean> autoProtect = sgSafety.add(new BoolSetting.Builder()
        .name("自动防护")
        .defaultValue(true)
        .build()
    );

    private final Setting<List<Block>> shieldBlocks = sgSafety.add(new BlockListSetting.Builder()
        .name("防护方块")
        .defaultValue(Blocks.OBSIDIAN, Blocks.CRYING_OBSIDIAN, Blocks.NETHERITE_BLOCK, Blocks.ANVIL)
        .build()
    );

    // --- Targets ---
    private final Setting<Set<EntityType<?>>> entities = sgTargets.add(new EntityTypeListSetting.Builder()
        .name("目标实体")
        .defaultValue(EntityType.PLAYER)
        .build()
    );
    private final Setting<Boolean> attackSurvival = sgTargets.add(new BoolSetting.Builder().name("攻击生存").defaultValue(true).build());
    private final Setting<Boolean> attackCreative = sgTargets.add(new BoolSetting.Builder().name("攻击创造").defaultValue(false).build());
    private final Setting<Boolean> attackAdventure = sgTargets.add(new BoolSetting.Builder().name("攻击冒险").defaultValue(false).build());

    // --- Whitelist ---
    public enum ListMode { Whitelist, Blacklist, Off }
    private final Setting<ListMode> listMode = sgWhitelist.add(new EnumSetting.Builder<ListMode>().name("白名单模式").defaultValue(ListMode.Off).build());
    private final Setting<String> playerList = sgWhitelist.add(new StringSetting.Builder().name("玩家名单").defaultValue("").visible(() -> listMode.get() != ListMode.Off).build());

    // --- Render ---
    private final Setting<Boolean> renderTarget = sgRender.add(new BoolSetting.Builder().name("渲染目标").defaultValue(true).build());
    private final Setting<SettingColor> sideColor = sgRender.add(new ColorSetting.Builder().name("侧面颜色").defaultValue(new SettingColor(255, 0, 0, 25)).build());
    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder().name("线条颜色").defaultValue(new SettingColor(255, 0, 0, 255)).build());
    // 渲染相关设置补充
        private final Setting<Boolean> renderPath = sgRender.add(new BoolSetting.Builder().name("渲染路径").defaultValue(true).build());
        private final Setting<SettingColor> targetColor = sgRender.add(new ColorSetting.Builder().name("目标颜色").defaultValue(new SettingColor(255, 0, 0, 255)).build());
        private final Setting<SettingColor> pathColor = sgRender.add(new ColorSetting.Builder().name("路径颜色").defaultValue(new SettingColor(0, 255, 255, 255)).build());
        private final Setting<Boolean> debug = sgGeneral.add(new BoolSetting.Builder().name("调试信息").defaultValue(true).build());
    
        // 存储当前Tick的传送路径节点
        private final List<Vec3d> renderPathNodes = new ArrayList<>();

    public TpAnchor() {
        super(AddonTemplate.CATEGORY, "如来神掌·生锚打击", "射程范围内瞬间发动能力放置并引爆重生锚打击目标");
    }

    private int delayTimer = 0;
    private Entity currentTarget = null;
    private static final int SLOT_ANCHOR = 6;
    private static final int SLOT_GLOWSTONE = 7;
    private static final int SLOT_SHIELD = 8;

    @EventHandler
    public void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        currentTarget = findTarget();
        if (delayTimer > 0) { delayTimer--; return; }
        if (pauseOnEat.get() && mc.player.isUsingItem()) return;
        if (currentTarget == null) return;

        // 物品检查与自动补货
        FindItemResult anchor = InvUtils.find(Items.RESPAWN_ANCHOR);
        FindItemResult glowstone = InvUtils.find(Items.GLOWSTONE);
        FindItemResult shield = InvUtils.find(stack -> stack.getItem() instanceof BlockItem && shieldBlocks.get().contains(((BlockItem) stack.getItem()).getBlock()));

        
        boolean missingItem = false;

        if (!anchor.found()) {
            if (debug.get() && delayTimer == 0) error("背包缺失: 重生锚 (Respawn Anchor)!");
            missingItem = true;
        }
        
        if (!glowstone.found()) {
            if (debug.get() && delayTimer == 0) error("背包缺失: 萤石 (Glowstone)!");
            missingItem = true;
        }

        // 注意这里变量名使用上文定义的 shield
        if (autoProtect.get() && !shield.found()) {
            if (debug.get() && delayTimer == 0) error("背包缺失: 防御方块 (Shield Block)!");
            missingItem = true;
        }

        if (missingItem) {
            delayTimer = 40; 
            return;
        }

        int aSlot = getSlot(anchor, SLOT_ANCHOR);
        int gSlot = getSlot(glowstone, SLOT_GLOWSTONE);
        int sSlot = autoProtect.get() ? getSlot(shield, SLOT_SHIELD) : -1;

        if (aSlot == -1 || gSlot == -1) return;

        AttackPos info = findBestPos(currentTarget);
        if (info == null) return;

        // 执行核心打击
        executeTPAuraAttack(info, aSlot, gSlot, sSlot);
        delayTimer = attackDelay.get();
    }

    private void executeTPAuraAttack(AttackPos info, int aSlot, int gSlot, int sSlot) {
        Entity baseEntity = mc.player.hasVehicle() ? mc.player.getVehicle() : mc.player;
        
        // 1.21.11 修正：使用 getPosVec() 替代 getPos()
         Vec3d startPos = new Vec3d(baseEntity.getX(), baseEntity.getY(), baseEntity.getZ());
        Vec3d targetStandPos = info.tpPos;
    
        if (invalid(targetStandPos)) {
            targetStandPos = findNearestPos(targetStandPos);
            if (targetStandPos == null) return;
        }
    
        // 1.21.11 修正：类型强转为 double，高度计算
        double clipHeight = Math.min(range.get(), (double)mc.world.getTopYInclusive() - startPos.y - 1.0);
        Vec3d upPos = startPos.add(0.0, clipHeight, 0.0);
        Vec3d targetUpPos = targetStandPos.add(0.0, clipHeight, 0.0);

        // --- 记录渲染路径 ---
        renderPathNodes.clear();
        renderPathNodes.add(startPos);
        if (goUp.get()) {
            renderPathNodes.add(upPos);
            renderPathNodes.add(targetUpPos);
        }
        renderPathNodes.add(targetStandPos);
        // ------------------
        
    
        // 发送垃圾包 (Packet Spamming)
        int spamCount = tpMode.get() == Mode.Vanilla ? vanillaPackets.get() : paperPackets.get();
        for (int i = 0; i < spamCount; i++) {
            if (mc.player.hasVehicle()) {
                mc.player.networkHandler.sendPacket(VehicleMoveC2SPacket.fromVehicle(mc.player.getVehicle()));
            } else {
                mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(mc.player.getYaw(), mc.player.getPitch(), false, mc.player.horizontalCollision));
            }
        }
    
        // 1.21.11 修正：Setting 必须使用 .get()
        if (goUp.get()) sendMove(baseEntity, upPos);
        if (goUp.get()) sendMove(baseEntity, targetUpPos);
        sendMove(baseEntity, targetStandPos);
    
        // --- 执行布置逻辑 ---
        InvUtils.swap(aSlot, true);
        placePacket(info.pos);
        if (autoProtect.get() && sSlot != -1) {
            BlockPos shieldPos = getShieldPos(info.pos, BlockPos.ofFloored(targetStandPos));
            if (shieldPos != null && mc.world.getBlockState(shieldPos).isReplaceable()) {
                InvUtils.swap(sSlot, true);
                placePacket(shieldPos);
            }
        }
        InvUtils.swap(gSlot, true);
        Direction side = findBestSide(info.pos);
        interactPacket(info.pos, side);
        InvUtils.swap(aSlot, true);
        interactPacket(info.pos, side);
        InvUtils.swapBack();
        // ------------------
    
        // 返回路径 (回溯)
        if (goUp.get()) {
            sendMove(baseEntity, targetUpPos);
            sendMove(baseEntity, upPos);
        }
        sendMove(baseEntity, startPos);
    
        // 归位偏移
        Vec3d finalOffset = getOffset(startPos);
        sendMove(baseEntity, finalOffset);
        baseEntity.setPosition(finalOffset.x, finalOffset.y, finalOffset.z);
    }

    // --- TPAura 核心工具函数 ---

   private void sendMove(Entity entity, Vec3d pos) {
    if (mc.getNetworkHandler() == null) return;
    if (entity instanceof PlayerEntity) {
        // 1.21.11 修正：Full 构造函数必须确保坐标是 double
        PlayerMoveC2SPacket packet = new PlayerMoveC2SPacket.Full(pos.x, pos.y, pos.z, mc.player.getYaw(), mc.player.getPitch(), false, mc.player.horizontalCollision);
        // 1.21.11 规则：Meteor Mixin 接口前缀必须是 meteor$
        ((IPlayerMoveC2SPacket) packet).meteor$setTag(1337);
        mc.player.networkHandler.sendPacket(packet);
    } else {
        // 车辆移动包
        mc.player.networkHandler.sendPacket(new VehicleMoveC2SPacket(pos, mc.player.getVehicle().getYaw(), mc.player.getVehicle().getPitch(), false));
    }
}

   private boolean invalid(Vec3d pos) {
    if (mc.world == null) return true;
    BlockPos bp = BlockPos.ofFloored(pos);
    if (mc.world.getChunk(bp.getX() >> 4, bp.getZ() >> 4) == null) return true;

    Entity entity = mc.player.hasVehicle() ? mc.player.getVehicle() : mc.player;
    // 1.21.11 修正：使用 getPosVec() 替代 getPos()
    Vec3d entityPos = new Vec3d(entity.getX(), entity.getY(), entity.getZ());
    Box box = entity.getBoundingBox().offset(pos.subtract(entityPos));

    for (BlockPos b : BlockPos.iterate(BlockPos.ofFloored(box.minX, box.minY, box.minZ), BlockPos.ofFloored(box.maxX, box.maxY, box.maxZ))) {
        BlockState state = mc.world.getBlockState(b);
        // 简单碰撞检查
        if (state.isOf(Blocks.LAVA)) return true;
        if (!state.getCollisionShape(mc.world, b).isEmpty()) return true;
    }
    return false;
}

    private Vec3d findNearestPos(Vec3d desired) {
        for (int x = -2; x <= 2; x++) {
            for (int y = -2; y <= 2; y++) {
                for (int z = -2; z <= 2; z++) {
                    Vec3d test = desired.add(x, y, z);
                    if (!invalid(test)) return test;
                }
            }
        }
        return null;
    }

    private Vec3d getOffset(Vec3d base) {
        double d = horizontalOffset.get();
        double dy = yOffset.get();
        Vec3d[] list = {
            base.add(d, dy, 0), base.add(-d, dy, 0), base.add(0, dy, d), base.add(0, dy, -d),
            base.add(d, dy, d), base.add(-d, dy, -d), base.add(-d, dy, d), base.add(d, dy, -d)
        };
        List<Vec3d> offsets = Arrays.asList(list);
        Collections.shuffle(offsets);
        for (Vec3d p : offsets) if (!invalid(p)) return p;
        return base.add(0, dy, 0);
    }

    private boolean hasClearPath(Vec3d start, Vec3d end) {
        int steps = Math.max(10, (int) (start.distanceTo(end) * 2.5));
        for (int i = 1; i < steps; i++) {
            if (invalid(start.lerp(end, (double) i / steps))) return false;
        }
        return true;
    }

    // --- 目标与锚点逻辑 ---

    private AttackPos findBestPos(Entity target) {
        BlockPos tPos = target.getBlockPos();
        double halfHeight = (target.getBoundingBox().maxY - target.getBoundingBox().minY) / 2.0;
        Vec3d targetCenter = new Vec3d(target.getX(), target.getY() + halfHeight, target.getZ());

        List<AttackPos> candidates = new ArrayList<>();
        int r = placeRange.get();
        double rSq = r * r; 

        for (int x = -r; x <= r; x++) {
            for (int y = -r; y <= r; y++) {
                for (int z = -r; z <= r; z++) {
                    if ((x*x + y*y + z*z) > rSq) continue;

                    BlockPos pos = tPos.add(x, y, z);

                    // 1. 检查该位置是否能放锚点
                    if (checkPlace(pos)) {
                        // 2. 寻找玩家 TP 到哪里才能摸到这个锚点且不卡墙
                        Vec3d validTpSpot = findSmartTpSpot(pos);
                        
                        if (validTpSpot != null) {
                            // 3. 计算该点位的分数（越小越优）
                            double score = calculateScore(target, targetCenter, pos);
                            candidates.add(new AttackPos(pos, validTpSpot, score));
                        }
                    }
                }
            }
        }

        if (!candidates.isEmpty()) {
            // 按分数升序排序，选分最低（最接近目标且暴露）的
            candidates.sort(Comparator.comparingDouble(p -> p.score));
            return candidates.get(0);
        }

        return null;
    }
    private Vec3d findSmartTpSpot(BlockPos anchorPos) {
        // 依次尝试：锚点上方2格、四周2格、上方1格、四周1格
        BlockPos[] testOffsets = {
            anchorPos.up(2), 
            anchorPos.north(2), anchorPos.south(2), anchorPos.east(2), anchorPos.west(2),
            anchorPos.up(1),
            anchorPos.north(1), anchorPos.south(1), anchorPos.east(1), anchorPos.west(1)
        };

        for (BlockPos p : testOffsets) {
            Vec3d testVec = new Vec3d(p.getX() + 0.5, p.getY(), p.getZ() + 0.5);
            // 使用 TPAura 的核心 invalid 方法检查该点是否会卡墙
            if (!invalid(testVec)) {
                return testVec;
            }
        }
        return null; 
    }
    private double calculateScore(Entity target, Vec3d targetCenter, BlockPos anchorPos) {
        Vec3d anchorVec = new Vec3d(anchorPos.getX() + 0.5, anchorPos.getY() + 0.5, anchorPos.getZ() + 0.5);
        double distSq = anchorVec.squaredDistanceTo(targetCenter);
        double score = distSq; 

        // 如果锚点和目标之间有方块阻挡（不暴露），分数大幅增加（劣化）
        if (!isExposed(target, anchorPos)) {
            score += 1000.0;
        }

        // 越开阔的地方越好放，计算周围空气数量
        int openness = 0;
        for (Direction dir : Direction.values()) {
            if (mc.world.getBlockState(anchorPos.offset(dir)).isReplaceable()) openness++;
        }
        score += (6 - openness) * 2.0;

        return score;
    }

    private boolean isExposed(Entity target, BlockPos anchorPos) {
        // 1.21.11 修正：手动构造 eyePos
        Vec3d start = new Vec3d(target.getX(), target.getY() + target.getEyeHeight(target.getPose()), target.getZ());
        Vec3d end = new Vec3d(anchorPos.getX() + 0.5, anchorPos.getY() + 0.5, anchorPos.getZ() + 0.5);
        
        BlockHitResult result = mc.world.raycast(new RaycastContext(
            start, end, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, target
        ));

        return result.getType() == HitResult.Type.MISS || result.getBlockPos().equals(anchorPos);
    }

    private boolean checkPlace(BlockPos pos) {
        if (!mc.world.isInBuildLimit(pos)) return false;
        
        BlockState state = mc.world.getBlockState(pos);
        // 必须是可替换方块（空气、草、雪等）
        if (!state.isReplaceable()) return false;
        
        // 1.21.11 修正：检查是否可以在此放置重生锚
        if (!mc.world.canPlace(Blocks.RESPAWN_ANCHOR.getDefaultState(), pos, ShapeContext.absent())) {
            return false;
        }
        
        // 如果没开启“空中放置”，检查是否有邻居方块支持
        if (!airPlace.get()) {
            boolean hasNeighbor = false;
            for (Direction d : Direction.values()) {
                if (!mc.world.getBlockState(pos.offset(d)).isReplaceable()) {
                    hasNeighbor = true;
                    break;
                }
            }
            if (!hasNeighbor) return false;
        }

        return true; 
    }

    private BlockPos getShieldPos(BlockPos anchor, BlockPos player) {
        int dx = player.getX() - anchor.getX();
        int dz = player.getZ() - anchor.getZ();
        if (dx == 0 && dz == 0) return anchor.up();
        return anchor.add(Integer.compare(dx, 0), 0, Integer.compare(dz, 0));
    }

    private Entity findTarget() {
        for (Entity e : mc.world.getEntities()) {
            if (e == mc.player || !e.isAlive() || !(e instanceof LivingEntity)) continue;
            if (!entities.get().contains(e.getType())) continue;
            if (e.distanceTo(mc.player) > range.get()) continue;
            if (e instanceof PlayerEntity p) {
                GameMode gm = getGameMode(p);
                if (gm == GameMode.SURVIVAL && !attackSurvival.get()) continue;
                if (gm == GameMode.CREATIVE && !attackCreative.get()) continue;
                if (gm == GameMode.ADVENTURE && !attackAdventure.get()) continue;
                if (!Friends.get().shouldAttack(p)) continue;
                if (listMode.get() != ListMode.Off) {
                    List<String> names = Arrays.stream(playerList.get().split(",")).map(String::trim).collect(Collectors.toList());
                    if (listMode.get() == ListMode.Whitelist && !names.contains(p.getName().getString())) continue;
                    if (listMode.get() == ListMode.Blacklist && names.contains(p.getName().getString())) continue;
                }
            }
            return e;
        }
        return null;
    }

    private GameMode getGameMode(PlayerEntity p) {
        PlayerListEntry entry = mc.getNetworkHandler().getPlayerListEntry(p.getUuid());
        return entry == null ? GameMode.SURVIVAL : entry.getGameMode();
    }

    private int getSlot(FindItemResult res, int pref) {
        if (res.isHotbar()) return res.slot();
        if (autoRefill.get() && res.found()) {
            InvUtils.move().from(res.slot()).toHotbar(pref);
            return pref;
        }
        return -1;
    }

    private Direction findBestSide(BlockPos p) {
        for (Direction d : Direction.values()) if (mc.world.getBlockState(p.offset(d)).isReplaceable()) return d;
        return Direction.UP;
    }

    private void placePacket(BlockPos p) {
        mc.getNetworkHandler().sendPacket(new PlayerInteractBlockC2SPacket(Hand.MAIN_HAND, new BlockHitResult(p.toCenterPos(), Direction.UP, p, false), 0));
        mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
    }

    private void interactPacket(BlockPos p, Direction d) {
        mc.getNetworkHandler().sendPacket(new PlayerInteractBlockC2SPacket(Hand.MAIN_HAND, new BlockHitResult(p.toCenterPos(), d, p, false), 0));
        mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
    }

   @EventHandler
    private void onRender(Render3DEvent event) {
        if (currentTarget != null) {
            event.renderer.box(currentTarget.getBoundingBox(), targetColor.get(), targetColor.get(), ShapeMode.Lines, 0);
        }
        if (renderPath.get() && !renderPathNodes.isEmpty()) {
            for (int i = 0; i < renderPathNodes.size() - 1; i++) {
                Vec3d n1 = renderPathNodes.get(i);
                Vec3d n2 = renderPathNodes.get(i+1);
                event.renderer.line(n1.x, n1.y + 1, n1.z, n2.x, n2.y + 1, n2.z, pathColor.get());
                event.renderer.box(new Box(n1.x - 0.2, n1.y, n1.z - 0.2, n1.x + 0.2, n1.y + 2, n1.z + 0.2), pathColor.get(), pathColor.get(), ShapeMode.Lines, 0);
            }
        }
    }

    private static class AttackPos {
        BlockPos pos;
        Vec3d tpPos;
        double score;
        public AttackPos(BlockPos p, Vec3d tp, double s) { this.pos = p; this.tpPos = tp; this.score = s; }
    }
}