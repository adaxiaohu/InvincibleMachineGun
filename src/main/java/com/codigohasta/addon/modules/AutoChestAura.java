package com.codigohasta.addon.modules;

import net.minecraft.util.math.Vec3d;

import com.codigohasta.addon.AddonTemplate;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import net.minecraft.block.BlockState;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.network.packet.c2s.play.CloseHandledScreenC2SPacket;
import net.minecraft.network.packet.s2c.play.InventoryS2CPacket;
import net.minecraft.network.packet.s2c.play.OpenScreenS2CPacket;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AutoChestAura extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
        .name("开启距离")
        .description("开启箱子的最大距离。")
        .defaultValue(5.0)
        .min(0)
        .sliderMax(6)
        .build()
    );

    private final Setting<List<BlockEntityType<?>>> blocks = sgGeneral.add(new StorageBlockListSetting.Builder()
        .name("容器类型")
        .description("选择要开启的容器。")
        .defaultValue(BlockEntityType.CHEST, BlockEntityType.BARREL, BlockEntityType.SHULKER_BOX)
        .build()
    );

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("开启间隔")
        .description("开启下一个箱子前的冷却 (Tick)。")
        .defaultValue(3) // 提速：默认改小
        .min(1)
        .sliderMax(20)
        .build()
    );
    
    private final Setting<Integer> waitTime = sgGeneral.add(new IntSetting.Builder()
        .name("记录等待")
        .description("收到数据包后等待多久关闭 (Tick)。给ChestTracker反应时间。")
        .defaultValue(2) // 提速：默认改小
        .min(1)
        .sliderMax(20)
        .build()
    );

    private final Setting<Integer> timeout = sgGeneral.add(new IntSetting.Builder()
        .name("强制超时")
        .description("如果箱子卡住没关闭，多少 Tick 后强制关闭。解决卡死问题。")
        .defaultValue(15)
        .min(5)
        .sliderMax(60)
        .build()
    );

    private final Setting<Boolean> rotate = sgGeneral.add(new BoolSetting.Builder()
        .name("自动面向")
        .description("开启箱子时自动面向。")
        .defaultValue(true)
        .build()
    );

    private final Map<BlockPos, Long> openedBlocks = new HashMap<>();
    private final PacketListener packetListener = new PacketListener();

    private int timer = 0;
    private int packetTimer = 0; // 等待数据包或关闭的计时器
    private int stuckTimer = 0;  // 卡死检测计时器
    
    private boolean isPending = false; // 是否处于“已点击但未完成”的状态

    public AutoChestAura() {
        super(AddonTemplate.CATEGORY, "自动开箱子光环", "高速自动开箱，添加ChestTraker列表用。不知道能不能绕反作弊");
    }

    @Override
    public void onActivate() {
        timer = 0;
        packetTimer = 0;
        stuckTimer = 0;
        isPending = false;
        openedBlocks.clear();
        MeteorClient.EVENT_BUS.subscribe(packetListener);
    }

    @Override
    public void onDeactivate() {
        MeteorClient.EVENT_BUS.unsubscribe(packetListener);
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.world == null || mc.player == null) return;

        // --- 1. 卡死检测与状态维护 ---
        
        // 如果当前处于等待状态 (已点击箱子)
        if (isPending) {
            stuckTimer++;
            
            // 如果玩家手动关闭了界面，或者因为距离太远界面自动关了
            if (mc.currentScreen == null && stuckTimer > 5) {
                // 重置状态，准备开下一个
                resetState();
                return;
            }

            // 倒计时关闭 (只有当 packetTimer 被 packetListener 激活后才开始倒数)
            if (packetTimer > 0) {
                packetTimer--;
                if (packetTimer <= 0) {
                    forceClose();
                    return;
                }
            }

            // 强制超时保护 (解决“不自动关闭”的问题)
            if (stuckTimer >= timeout.get()) {
                forceClose();
                return;
            }
            
            // 如果正在处理中，不要去开新箱子
            return;
        }

        // --- 2. 寻找新箱子 ---

        if (timer > 0) {
            timer--;
            return;
        }

        // 只有当前没打开任何界面时才操作
        if (mc.currentScreen != null) return;

        for (BlockEntity block : Utils.blockEntities()) {
            if (!blocks.get().contains(block.getType())) continue;
            
            if (mc.player.getEyePos().distanceTo(Vec3d.ofCenter(block.getPos())) >= range.get()) continue;

            BlockPos pos = block.getPos();
            if (openedBlocks.containsKey(pos)) continue;

            // --- 3. 执行开启 ---
            
            Runnable click = () -> mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, new BlockHitResult(new Vec3d(pos.getX(), pos.getY(), pos.getZ()), Direction.UP, pos, false));
            
            if (rotate.get()) {
                Rotations.rotate(Rotations.getYaw(pos), Rotations.getPitch(pos), click);
            } else {
                click.run();
            }

            // 标记双人箱子
            markOpened(block, pos);

            // 进入等待状态
            isPending = true;
            stuckTimer = 0;
            packetTimer = 0; // 此时还没收到包，设为0，等待包来了再设置
            timer = delay.get(); // 设置下一个箱子的冷却
            
            break; // 每 Tick 只开一个
        }
    }

    private void markOpened(BlockEntity block, BlockPos pos) {
        openedBlocks.put(pos, System.currentTimeMillis());
        
        BlockState state = block.getCachedState();
        if (state.contains(ChestBlock.CHEST_TYPE)) {
            Direction direction = state.get(ChestBlock.FACING);
            switch (state.get(ChestBlock.CHEST_TYPE)) {
                case LEFT -> openedBlocks.put(pos.offset(direction.rotateYClockwise()), System.currentTimeMillis());
                case RIGHT -> openedBlocks.put(pos.offset(direction.rotateYCounterclockwise()), System.currentTimeMillis());
            }
        }
    }

    private void forceClose() {
        if (mc.player != null) {
            mc.player.closeHandledScreen();
            if (mc.player.currentScreenHandler != null) {
                mc.getNetworkHandler().sendPacket(new CloseHandledScreenC2SPacket(mc.player.currentScreenHandler.syncId));
            }
        }
        resetState();
    }

    private void resetState() {
        isPending = false;
        stuckTimer = 0;
        packetTimer = 0;
    }

    private class PacketListener {
        @EventHandler(priority = EventPriority.HIGH)
        private void onPacket(PacketEvent.Receive event) {
            if (!isPending) return;

            // 监听 InventoryS2CPacket (箱子内容包)
            // ChestTracker 依赖这个包来记录数据
            if (event.packet instanceof InventoryS2CPacket packet) {
                ScreenHandler handler = mc.player.currentScreenHandler;
                if (handler != null && packet.syncId() == handler.syncId) {
                    // 收到数据了！设置关闭倒计时
                    // 如果 waitTime 是 2，那就等 2 个 tick 后关闭
                    packetTimer = waitTime.get();
                }
            }
            // 备用：有时候箱子是空的或者 lag，只发了 OpenScreen
            else if (event.packet instanceof OpenScreenS2CPacket packet) {
                // 如果已经过了一半的超时时间还没收到内容包，就开始倒计时关闭
                // 防止箱子打开了但因为没物品包而一直挂着
                if (packetTimer == 0) { 
                    packetTimer = timeout.get() - 5; 
                }
            }
        }
    }
}