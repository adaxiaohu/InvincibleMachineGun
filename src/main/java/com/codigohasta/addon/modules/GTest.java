package com.codigohasta.addon.modules;

import com.codigohasta.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.entity.SortPriority;
import meteordevelopment.meteorclient.utils.entity.TargetUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Blocks;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;

public class GTest extends Module {

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> scanBorder = sgGeneral.add(new IntSetting.Builder()
        .name("scan-range")
        .description("Range to detect enemies.")
        .defaultValue(5)
        .min(1)
        .max(10)
        .build()
    );

    // 这里的 Times 是"期望值"。代码会自动根据 Packet Limit 削减它，防止被踢。
    private final Setting<Integer> times = sgGeneral.add(new IntSetting.Builder()
        .name("target-times")
        .description("Desired attacks per tick. Will auto-reduce to prevent kicks.")
        .defaultValue(10) 
        .min(1)
        .max(20)
        .build()
    );

    // !!! 核心设置：数据包上限 !!!
    // 大多数服务器容忍度在 20-50 之间。
    // 如果你被踢，请调低这个数值 (比如 20)。
    // 如果没被踢，可以调高 (比如 50) 来获得更快的攻击速度。
    private final Setting<Integer> packetLimit = sgGeneral.add(new IntSetting.Builder()
        .name("max-packets-per-tick")
        .description("Safety limit. Server kicks if > 50 usually.")
        .defaultValue(40)
        .min(10)
        .max(100)
        .build()
    );

    private final Setting<Boolean> preventDeath = sgGeneral.add(new BoolSetting.Builder()
        .name("prevent-fall-damage")
        .description("Uses the +0.25 offset to stop fall damage.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> maxPower = sgGeneral.add(new BoolSetting.Builder()
        .name("max-power")
        .description("Goes up to 170 blocks.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> fallHeight = sgGeneral.add(new IntSetting.Builder()
        .name("fall-height")
        .description("Target height if Max Power is off.")
        .defaultValue(6) // 你现在的设置
        .min(3)
        .max(170)
        .visible(() -> !maxPower.get())
        .build()
    );

    private final Setting<Boolean> onlyMace = sgGeneral.add(new BoolSetting.Builder()
        .name("only-mace")
        .description("Only active when holding a Mace.")
        .defaultValue(true)
        .build()
    );

    private boolean isSpoofing = false;
    private double previousX, previousY, previousZ;

    public GTest() {
        super(AddonTemplate.CATEGORY, "GTest", "Auto-Throttle Anti-Kick.");
    }

    // --- 1. 攻击拦截 ---
    @EventHandler
    private void onSendPacket(PacketEvent.Send event) {
        if (mc.world == null || mc.player == null) return;
        if (isSpoofing) return;

        if (onlyMace.get() && mc.player.getMainHandStack().getItem() != Items.MACE) return;

        if (event.packet instanceof PlayerInteractEntityC2SPacket) {
            
            previousX = mc.player.getX();
            previousY = mc.player.getY();
            previousZ = mc.player.getZ();

            // 1. 计算安全高度
            int allowedHeight = getMaxHeightAbovePlayer();
            if (allowedHeight < 3) return; // 空间太小不欺骗

            isSpoofing = true; 
            ClientPlayNetworkHandler net = mc.getNetworkHandler();
            
            // 2. 计算单次攻击需要消耗多少包
            // 基础包: 1个瞬移去 + 1个瞬移回 = 2
            // 填充包: 看 doMacePacketLogic 的逻辑
            int fillerPackets = calculateFillerPackets(allowedHeight);
            int packetsPerHit = 2 + fillerPackets; // 每次重锤消耗的包数
            
            // 3. 计算最大安全连击数
            // 如果 MaxPower 开启，强制为 1
            // 否则：剩余预算 / 单次消耗
            int maxSafeLoops = maxPower.get() ? 1 : (packetLimit.get() / packetsPerHit);
            
            // 最终执行次数：取"用户设定值"和"安全值"中较小的一个
            int loop = Math.min(times.get(), maxSafeLoops);
            
            if (loop < 1) loop = 1; // 至少打一下

            // 4. 执行循环
            for (int i = 0; i < loop; i++) {
                doMacePacketLogic(net, allowedHeight, fillerPackets);
            }
            
            isSpoofing = false;
        }
    }

    // --- 2. 右键自动攻击 ---
    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null || mc.getNetworkHandler() == null) return;

        if (mc.options.useKey.isPressed()) {
            if (onlyMace.get() && mc.player.getMainHandStack().getItem() != Items.MACE) return;

            Entity target = TargetUtils.get(entity -> {
                if (!(entity instanceof LivingEntity)) return false;
                if (entity == mc.player) return false;
                if (entity.distanceTo(mc.player) > scanBorder.get()) return false;
                if (!entity.isAlive()) return false;
                return true;
            }, SortPriority.LowestDistance);

            if (target != null) {
                // 同样的预算逻辑
                int allowedHeight = getMaxHeightAbovePlayer();
                int fillerPackets = calculateFillerPackets(allowedHeight);
                int packetsPerHit = 2 + fillerPackets;
                int maxSafeLoops = maxPower.get() ? 1 : (packetLimit.get() / packetsPerHit);
                int loop = Math.min(times.get(), maxSafeLoops);
                if (loop < 1) loop = 1;

                for(int i = 0; i < loop; i++) {
                    mc.getNetworkHandler().sendPacket(PlayerInteractEntityC2SPacket.attack(target, mc.player.isSneaking()));
                    mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
                }
            }
        }
    }

    // --- 辅助计算：预测消耗 ---
    private int calculateFillerPackets(int blocks) {
        if (blocks <= 22) {
            return 4; // 短距离固定4个填充包
        } else {
            int packets = (int) Math.ceil(Math.abs(blocks / 10.0));
            if (packets > 20) packets = 1;
            return packets - 1;
        }
    }

    // --- 核心发包逻辑 ---
    private void doMacePacketLogic(ClientPlayNetworkHandler net, int blocks, int fillerCount) {
        // 发送填充包
        for (int i = 0; i < fillerCount; i++) {
            net.sendPacket(new PlayerMoveC2SPacket.OnGroundOnly(false, mc.player.horizontalCollision));
        }

        // 上升
        net.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(previousX, previousY + blocks, previousZ, false, mc.player.horizontalCollision));

        // 下落 + 防摔
        double returnY = previousY;
        if (preventDeath.get()) {
            returnY += 0.25;
        }

        net.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(previousX, returnY, previousZ, false, mc.player.horizontalCollision));
        
        // 客户端视觉修复
        if (preventDeath.get()) {
            mc.player.fallDistance = 0;
            mc.player.setVelocity(mc.player.getVelocity().x, 0.1, mc.player.getVelocity().z);
        }
    }

    // --- 智能高度扫描 ---
    private int getMaxHeightAbovePlayer() {
        BlockPos playerPos = mc.player.getBlockPos();
        int targetDist = maxPower.get() ? 170 : fallHeight.get();
        int maxHeight = playerPos.getY() + targetDist;

        for (int i = maxHeight; i > playerPos.getY(); i--) {
            BlockPos check = new BlockPos(playerPos.getX(), i, playerPos.getZ());
            BlockPos checkUp = check.up();
            
            if (isSafe(check) && isSafe(checkUp)) {
                return i - playerPos.getY();
            }
        }
        return 0; // 如果头顶堵死，返回0，不执行欺骗
    }

    private boolean isSafe(BlockPos pos) {
        return mc.world.getBlockState(pos).isReplaceable() 
            && !mc.world.getBlockState(pos).isOf(Blocks.POWDER_SNOW);
    }
}