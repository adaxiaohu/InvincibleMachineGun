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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.item.Items;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.ClipContext;

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
        super(AddonTemplate.CATEGORY, "Legit自动挡锚", "自动挡锚、充能与引爆宏.暂不能绕过grimac.希望大神优化");
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
            mc.getConnection().send(new ServerboundSetCarriedItemPacket(slot));
        }
    }

    private int getSlot() {
        return ((InventoryAccessor) mc.player.getInventory()).getSelectedSlot();
    }

    @EventHandler
    private void onPacketSend(PacketEvent.Send event) {
        if (event.packet instanceof ServerboundUseItemOnPacket packet && currentState == State.IDLE) {
            if (mc.player.getItemInHand(packet.getHand()).getItem() != Items.RESPAWN_ANCHOR) return;

            BlockHitResult hitResult = packet.getHitResult();
            BlockPos placedPos = hitResult.getBlockPos().relative(hitResult.getDirection());

            BlockState clickedState = mc.level.getBlockState(hitResult.getBlockPos());
            if (clickedState.canBeReplaced()) {
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
            blockerPos = calculateBlockerPos(anchorPos, mc.player.getEyePosition());
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

    private BlockPos calculateBlockerPos(BlockPos anchor, Vec3 playerEye) {
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
        BlockState state = mc.level.getBlockState(pos);
        if (!state.canBeReplaced()) return false;
        return mc.level.isUnobstructed(Blocks.GLOWSTONE.defaultBlockState(), pos, CollisionContext.of(mc.player));
    }

    // 补全 placeBlockLegit 方法
    // 在 placeBlockLegit 中移除 Shift 包
    private boolean placeBlockLegit(BlockPos targetPos, BlockPos excludePos, Runnable onDone) {
        Direction[] priorities = {Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.UP};
        
        for (Direction side : priorities) {
            BlockPos neighborPos = targetPos.relative(side);
            if (neighborPos.equals(excludePos)) continue;

            BlockState state = mc.level.getBlockState(neighborPos);
            boolean isSolid = state.isCollisionShapeFullBlock(mc.level, neighborPos);
            
            if (isSolid) {
                Direction hitSide = side.getOpposite();
                Vec3 hitVec = new Vec3(
                    neighborPos.getX() + 0.5 + hitSide.getStepX() * 0.49,
                    neighborPos.getY() + 0.5 + hitSide.getStepY() * 0.49,
                    neighborPos.getZ() + 0.5 + hitSide.getStepZ() * 0.49
                );
                
                // 强制 Shift 包已删除
                Rotations.rotate(Rotations.getYaw(hitVec), Rotations.getPitch(hitVec), 50, () -> {
                    BlockHitResult result = new BlockHitResult(hitVec, hitSide, neighborPos, false);
                    mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, result);
                    mc.player.swing(InteractionHand.MAIN_HAND);
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
        Vec3 eyePos = mc.player.getEyePosition();

        for (Direction dir : Direction.values()) {
            Vec3 faceCenter = new Vec3(
                pos.getX() + 0.5 + dir.getStepX() * 0.5,
                pos.getY() + 0.5 + dir.getStepY() * 0.5,
                pos.getZ() + 0.5 + dir.getStepZ() * 0.5
            );
            
            // 射线检测
            HitResult hit = mc.level.clip(new ClipContext(eyePos, faceCenter, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
            
            // 修复：确保 hit 是 BlockHitResult 类型，然后再获取 BlockPos
            if (hit instanceof BlockHitResult blockHit) {
                if (hit.getType() != HitResult.Type.MISS && !blockHit.getBlockPos().equals(pos)) {
                    continue;
                }
            }

            double dist = eyePos.distanceToSqr(faceCenter);
            if (dist < shortestDist) {
                shortestDist = dist;
                bestSide = dir;
            }
        }

        if (bestSide == null) bestSide = Direction.UP;

        Vec3 hitVec = new Vec3(
            pos.getX() + 0.5 + bestSide.getStepX() * 0.49,
            pos.getY() + 0.5 + bestSide.getStepY() * 0.49,
            pos.getZ() + 0.5 + bestSide.getStepZ() * 0.49
        );

        Direction finalBestSide = bestSide;
        Rotations.rotate(Rotations.getYaw(hitVec), Rotations.getPitch(hitVec), 50, () -> {
            BlockHitResult result = new BlockHitResult(hitVec, finalBestSide, pos, false);
            mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, result);
            mc.player.swing(InteractionHand.MAIN_HAND);
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