package com.codigohasta.addon.modules;

import com.codigohasta.addon.AddonTemplate;
import com.codigohasta.addon.mixin.InventoryAccessor;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShapeContext;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

public class MacroAnchor extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> delaySetting = sgGeneral.add(new IntSetting.Builder()
        .name("action-delay")
        .description("每个动作之间的Tick延迟 (绕过Grim的关键).")
        .defaultValue(2)
        .min(1)
        .sliderMax(10)
        .build()
    );

    private final Setting<Boolean> blockMode = sgGeneral.add(new BoolSetting.Builder()
        .name("block-anchor")
        .description("启用挡锚模式（自动放置萤石阻挡射线）.")
        .defaultValue(true)
        .build()
    );

    // 【新增】自定义引爆槽位：彻底解决副手图腾导致搜索返回 45槽 造成的充能两次Bug
    private final Setting<Integer> detonateSlot = sgGeneral.add(new IntSetting.Builder()
        .name("detonate-slot")
        .description("最后引爆锚时强行切换的快捷栏槽位 (1-9)")
        .defaultValue(7)
        .min(1)
        .max(9)
        .sliderMin(1)
        .sliderMax(9)
        .build()
    );

    private enum State {
        IDLE,
        PLACE_BLOCKER,
        CHARGE,
        DETONATE,
        SWITCH_BACK
    }

    private State currentState = State.IDLE;
    private int timer = 0;
    private boolean isRotating = false; 
    
    private BlockPos anchorPos = null;
    private BlockPos blockerPos = null;
    private int originalSlot = -1;

    public MacroAnchor() {
        super(AddonTemplate.CATEGORY, "macro-anchor", "自动挡锚、充能与引爆宏.不能绕过grimac.谁知道怎么绕过么");
    }

    @Override
    public void onDeactivate() {
        currentState = State.IDLE;
        isRotating = false;
    }

    private void setSlot(int slot) {
        if (slot < 0 || slot > 8) return;
        if (((InventoryAccessor) mc.player.getInventory()).getSelectedSlot() != slot) {
            ((InventoryAccessor) mc.player.getInventory()).setSelectedSlot(slot);
            mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(slot));
        }
    }

    private int getSlot() {
        return ((InventoryAccessor) mc.player.getInventory()).getSelectedSlot();
    }

    @EventHandler
    private void onPacketSend(PacketEvent.Send event) {
        if (event.packet instanceof PlayerInteractBlockC2SPacket packet && currentState == State.IDLE) {
            if (mc.player.getStackInHand(packet.getHand()).getItem() != Items.RESPAWN_ANCHOR) return;

            BlockHitResult hitResult = packet.getBlockHitResult();
            BlockPos placedPos = hitResult.getBlockPos().offset(hitResult.getSide());

            BlockState clickedState = mc.world.getBlockState(hitResult.getBlockPos());
            if (clickedState.isReplaceable()) {
                placedPos = hitResult.getBlockPos();
            }

            startMacro(placedPos);
        }
    }

    private void startMacro(BlockPos pos) {
        anchorPos = pos;
        originalSlot = getSlot();
        timer = delaySetting.get();
        isRotating = false;

        if (blockMode.get()) {
            blockerPos = calculateBlockerPos(anchorPos, mc.player.getEyePos());
            if (blockerPos != null && canPlaceBlocker(blockerPos)) {
                currentState = State.PLACE_BLOCKER;
            } else {
                currentState = State.CHARGE; 
            }
        } else {
            currentState = State.CHARGE;
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (currentState == State.IDLE) return;
        if (isRotating) return;

        if (timer > 0) {
            timer--;
            return;
        }

        switch (currentState) {
            case PLACE_BLOCKER -> {
                FindItemResult glowstone = InvUtils.findInHotbar(Items.GLOWSTONE);
                if (!glowstone.found()) { abort(); return; }
                
                setSlot(glowstone.slot());
                isRotating = true;
                
                boolean canPlace = placeBlockLegit(blockerPos, anchorPos, () -> {
                    isRotating = false;
                    currentState = State.CHARGE;
                    timer = delaySetting.get();
                });

                if (!canPlace) {
                    isRotating = false;
                    currentState = State.CHARGE;
                    timer = delaySetting.get(); 
                }
            }
            case CHARGE -> {
                FindItemResult glowstone = InvUtils.findInHotbar(Items.GLOWSTONE);
                if (!glowstone.found()) { abort(); return; }
                
                setSlot(glowstone.slot());
                isRotating = true;
                
                interactBlockLegit(anchorPos, () -> {
                    isRotating = false;
                    currentState = State.DETONATE;
                    timer = delaySetting.get();
                });
            }
            case DETONATE -> {
                // 【修复Bug 1】使用自定义引爆槽位，不再依赖查找图腾，直接避免副手图腾返回 45导致的不切槽充能两次
                int slot = detonateSlot.get() - 1; 
                setSlot(slot);
                isRotating = true;

                interactBlockLegit(anchorPos, () -> {
                    isRotating = false;
                    currentState = State.SWITCH_BACK;
                    timer = delaySetting.get();
                });
            }
            case SWITCH_BACK -> {
                setSlot(originalSlot);
                currentState = State.IDLE;
            }
        }
    }

    private BlockPos calculateBlockerPos(BlockPos anchor, Vec3d playerEye) {
        double centerX = anchor.getX() + 0.5;
        double centerZ = anchor.getZ() + 0.5;
        double dx = playerEye.x - centerX;
        double dz = playerEye.z - centerZ;

        if (Math.abs(dx) < 0.1 && Math.abs(dz) < 0.1) return null;

        double angle = Math.atan2(dz, dx);
        long sector = Math.round(angle / (Math.PI / 4.0));

        int offsetX = 0;
        int offsetZ = 0;

        if (sector == 0) { offsetX = 1; offsetZ = 0; }
        else if (sector == 1) { offsetX = 1; offsetZ = 1; }
        else if (sector == 2) { offsetX = 0; offsetZ = 1; }
        else if (sector == 3) { offsetX = -1; offsetZ = 1; }
        else if (sector == 4 || sector == -4) { offsetX = -1; offsetZ = 0; }
        else if (sector == -3) { offsetX = -1; offsetZ = -1; }
        else if (sector == -2) { offsetX = 0; offsetZ = -1; }
        else if (sector == -1) { offsetX = 1; offsetZ = -1; }

        return new BlockPos(anchor.getX() + offsetX, anchor.getY(), anchor.getZ() + offsetZ);
    }

    private boolean canPlaceBlocker(BlockPos pos) {
        BlockState state = mc.world.getBlockState(pos);
        if (!state.isReplaceable()) return false;
        return mc.world.canPlace(Blocks.GLOWSTONE.getDefaultState(), pos, ShapeContext.of(mc.player));
    }

    // 补全 placeBlockLegit 方法
    // 在 placeBlockLegit 中移除 Shift 包
    private boolean placeBlockLegit(BlockPos targetPos, BlockPos excludePos, Runnable onDone) {
        Direction[] priorities = {Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.UP};
        
        for (Direction side : priorities) {
            BlockPos neighborPos = targetPos.offset(side);
            if (neighborPos.equals(excludePos)) continue;

            BlockState state = mc.world.getBlockState(neighborPos);
            boolean isSolid = state.isFullCube(mc.world, neighborPos);
            
            if (isSolid) {
                Direction hitSide = side.getOpposite();
                Vec3d hitVec = new Vec3d(
                    neighborPos.getX() + 0.5 + hitSide.getOffsetX() * 0.49,
                    neighborPos.getY() + 0.5 + hitSide.getOffsetY() * 0.49,
                    neighborPos.getZ() + 0.5 + hitSide.getOffsetZ() * 0.49
                );
                
                // 强制 Shift 包已删除
                Rotations.rotate(Rotations.getYaw(hitVec), Rotations.getPitch(hitVec), 50, () -> {
                    BlockHitResult result = new BlockHitResult(hitVec, hitSide, neighborPos, false);
                    mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, result);
                    mc.player.swingHand(Hand.MAIN_HAND);
                    if (onDone != null) onDone.run();
                });
                return true;
            }
        }
        return false;
    }

    // 在 interactBlockLegit 中移除 Shift 包
    private void interactBlockLegit(BlockPos pos, Runnable onDone) {
        Direction bestSide = null;
        double shortestDist = Double.MAX_VALUE;
        Vec3d eyePos = mc.player.getEyePos();

        for (Direction dir : Direction.values()) {
            Vec3d faceCenter = new Vec3d(
                pos.getX() + 0.5 + dir.getOffsetX() * 0.5,
                pos.getY() + 0.5 + dir.getOffsetY() * 0.5,
                pos.getZ() + 0.5 + dir.getOffsetZ() * 0.5
            );
            
            // 射线检测
            HitResult hit = mc.world.raycast(new RaycastContext(eyePos, faceCenter, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc.player));
            
            // 修复：确保 hit 是 BlockHitResult 类型，然后再获取 BlockPos
            if (hit instanceof BlockHitResult blockHit) {
                if (hit.getType() != HitResult.Type.MISS && !blockHit.getBlockPos().equals(pos)) {
                    continue;
                }
            }

            double dist = eyePos.squaredDistanceTo(faceCenter);
            if (dist < shortestDist) {
                shortestDist = dist;
                bestSide = dir;
            }
        }

        if (bestSide == null) bestSide = Direction.UP;

        Vec3d hitVec = new Vec3d(
            pos.getX() + 0.5 + bestSide.getOffsetX() * 0.49,
            pos.getY() + 0.5 + bestSide.getOffsetY() * 0.49,
            pos.getZ() + 0.5 + bestSide.getOffsetZ() * 0.49
        );

        Direction finalBestSide = bestSide;
        Rotations.rotate(Rotations.getYaw(hitVec), Rotations.getPitch(hitVec), 50, () -> {
            BlockHitResult result = new BlockHitResult(hitVec, finalBestSide, pos, false);
            mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, result);
            mc.player.swingHand(Hand.MAIN_HAND);
            if (onDone != null) onDone.run();
        });
    }
    private void abort() {
        currentState = State.IDLE;
        isRotating = false;
        if (originalSlot != -1) {
            setSlot(originalSlot);
        }
        // 强制 Shift 包已删除
    }
}