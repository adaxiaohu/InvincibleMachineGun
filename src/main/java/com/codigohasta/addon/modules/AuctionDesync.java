package com.codigohasta.addon.modules;

import com.codigohasta.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.common.KeepAliveC2SPacket;
import net.minecraft.network.packet.c2s.play.*;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Direction;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class AuctionDesync extends Module {
    
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<String> command = sgGeneral.add(new StringSetting.Builder()
        .name("执行指令")
        .defaultValue("/paimai 10000000 1")
        .build()
    );

    private final Setting<Integer> fakeLagTime = sgGeneral.add(new IntSetting.Builder()
        .name("伪造延迟 (ms)")
        .description("模拟网络卡顿的时间。美国人用的那种方法通常在 200ms - 500ms 之间。")
        .defaultValue(300)
        .min(50)
        .max(2000)
        .sliderMax(1000)
        .build()
    );

    private final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("并发操作")
        .defaultValue(Mode.Place)
        .build()
    );

    private final Setting<Hand> hand = sgGeneral.add(new EnumSetting.Builder<Hand>()
        .name("交互手部")
        .defaultValue(Hand.MAIN_HAND)
        .build()
    );

    // 使用线程安全的列表，防止并发报错
    private final List<Packet<?>> packetQueue = new CopyOnWriteArrayList<>();
    private boolean isBlinking = false; // 是否处于"伪造延迟"状态
    private boolean isFlushing = false; // 是否正在释放包

    public AuctionDesync() {
        super(AddonTemplate.CATEGORY, "Auction Desync", "通过伪造高延迟(Clumsy)来刷物品。");
    }

    @Override
    public void onActivate() {
        if (mc.player == null || mc.world == null) {
            toggle();
            return;
        }

        // 1. 初始化状态
        packetQueue.clear();
        isBlinking = true; // 开始拦截所有包
        isFlushing = false;

        String cmd = command.get();
        if (cmd.startsWith("/")) cmd = cmd.substring(1);
        final String finalCmd = cmd;
        final HitResult currentTarget = mc.crosshairTarget;

        info("开始模拟延迟... 包将被积压 " + fakeLagTime.get() + "ms");

        // 2. 执行操作 (这些操作产生的包现在会被 onSendPacket 拦截)
        
        // 步骤 A: 发送指令
        mc.getNetworkHandler().sendChatCommand(finalCmd);
        
        // 步骤 B: 稍微等一下下(50ms)，让客户端生成指令包并进入我们的队列
        // 这一步很重要，因为指令生成是异步的
        new Thread(() -> {
            try {
                Thread.sleep(50);
                
                // 步骤 C: 发送放置/丢弃包
                if (mc.player != null) {
                    switch (mode.get()) {
                        case Drop -> doDrop();
                        case Place -> doPlace(currentTarget);
                    }
                }

                // 步骤 D: 保持“断网”状态一段时间 (模拟高延迟)
                Thread.sleep(fakeLagTime.get());

                // 步骤 E: 瞬间释放所有包
                flushPackets();

            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }).start();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    private void onSendPacket(PacketEvent.Send event) {
        // 如果没有开启拦截，或者正在释放，或者发的是心跳包(防止掉线)，就不拦截
        if (!isBlinking || isFlushing || event.packet instanceof KeepAliveC2SPacket) {
            return;
        }

        // 拦截一切 C2S (Client to Server) 包
        // 这模拟了拔掉网线的效果
        event.cancel();
        packetQueue.add(event.packet);
        
        // 可选：在聊天栏显示拦截了什么（调试用，正式用可以注释掉）
        // info("已拦截: " + event.packet.getClass().getSimpleName());
    }

    private void flushPackets() {
        if (packetQueue.isEmpty()) {
            isBlinking = false;
            info("没有拦截到任何包，可能指令生成太慢或出错。");
            toggle();
            return;
        }

        isFlushing = true; // 标记为释放中，防止自己拦截自己
        var connection = mc.getNetworkHandler().getConnection();

        // --- 关键修改：强制排序 ---
        // 我们在发送前，重新整理队列。
        // 必须确保：指令包(Command) 在前，交互包(Interact/Action) 在后。
        
        List<Packet<?>> sortedPackets = new ArrayList<>(packetQueue);
        sortedPackets.sort((p1, p2) -> {
            boolean p1IsCommand = isCommandPacket(p1);
            boolean p2IsCommand = isCommandPacket(p2);
            
            if (p1IsCommand && !p2IsCommand) return -1; // p1排前面
            if (!p1IsCommand && p2IsCommand) return 1;  // p2排前面
            return 0; // 保持原样
        });

        // 暴力瞬发
        int count = 0;
        for (Packet<?> packet : sortedPackets) {
            connection.send(packet);
            count++;
        }

        info("瞬间释放了 " + count + " 个包 (已强制指令优先)。");

        // 清理并关闭
        packetQueue.clear();
        isBlinking = false;
        isFlushing = false;
        toggle();
    }
    
    // 判断是否为指令相关包 (适配 1.21.4)
    private boolean isCommandPacket(Packet<?> packet) {
        return packet instanceof ChatCommandSignedC2SPacket || 
               packet instanceof ChatMessageC2SPacket;
    }

    private void doDrop() {
        mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(
            PlayerActionC2SPacket.Action.DROP_ALL_ITEMS, 
            mc.player.getBlockPos(), 
            Direction.DOWN
        ));
    }

    private void doPlace(HitResult target) {
        if (target instanceof BlockHitResult hitResult) {
            int sequence = 0; 
            mc.getNetworkHandler().sendPacket(new PlayerInteractBlockC2SPacket(
                hand.get(),
                hitResult,
                sequence
            ));
        }
    }
    
    public enum Mode {
        Place,
        Drop
    }
}