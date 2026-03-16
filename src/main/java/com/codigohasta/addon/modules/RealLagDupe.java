package com.codigohasta.addon.modules;

import com.codigohasta.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import net.minecraft.network.packet.c2s.play.ChatCommandSignedC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class RealLagDupe extends Module {
    
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<String> command = sgGeneral.add(new StringSetting.Builder()
        .name("执行指令")
        .defaultValue("/paimai 10000000 1")
        .build()
    );

    // 这个延迟是相对于“指令包真正离开客户端”的时间
    private final Setting<Integer> smartDelay = sgGeneral.add(new IntSetting.Builder()
        .name("智能延迟 (ms)")
        .description("指令包真正发出后，等待多久再发放置包？建议 50-200ms 之间微调。")
        .defaultValue(60)
        .min(0)
        .max(1000)
        .sliderMax(300)
        .build()
    );

    private final Setting<Integer> packetBurst = sgGeneral.add(new IntSetting.Builder()
        .name("放置包连发 (Burst)")
        .description("为了覆盖服务器的处理窗口，一次性发送多少个放置包？")
        .defaultValue(10)
        .min(1)
        .max(50)
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

    // 线程池，用于精确调度
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private boolean waitingForCommand = false; // 是否正在等待指令发出

    public RealLagDupe() {
        super(AddonTemplate.CATEGORY, "Real Lag Dupe", "监听真实发包时刻，精准延迟放置。");
    }

    @Override
    public void onActivate() {
        if (mc.player == null || mc.world == null) {
            toggle();
            return;
        }

        String cmd = command.get();
        if (cmd.startsWith("/")) cmd = cmd.substring(1);

        // 1. 标记状态：我们正在等待指令包经过网络层
        waitingForCommand = true;
        info("正在发送指令，等待网络层确认...");

        // 2. 发送指令 (这会触发签名，需要一点时间)
        mc.getNetworkHandler().sendChatCommand(cmd);

        // 注意：我们这里不发放置包！放置包的逻辑在 onSendPacket 里触发。
    }

    @Override
    public void onDeactivate() {
        waitingForCommand = false;
    }

    // 核心逻辑：监听发出的包
    @EventHandler(priority = EventPriority.HIGHEST)
    private void onSendPacket(PacketEvent.Send event) {
        if (!waitingForCommand) return;

        // 检测：这是不是我们刚才发的指令包？
        // 1.21.4 的指令通常是 ChatCommandSignedC2SPacket
        if (event.packet instanceof ChatCommandSignedC2SPacket) {
            
            info("检测到指令包已发出！启动延迟逻辑...");
            waitingForCommand = false; // 锁定，防止重复触发
            
            // 保存当前的鼠标指向（因为延迟期间你可能会移动鼠标）
            final HitResult target = mc.crosshairTarget;
            final Hand h = hand.get();
            final int burst = packetBurst.get();
            final int delay = smartDelay.get();

            // 3. 启动异步任务
            scheduler.schedule(() -> {
                try {
                    if (mc.player == null) return;
                    
                    // 4. 暴力连发放置包
                    for (int i = 0; i < burst; i++) {
                        // 每一个包使用不同的 sequence id，防止被客户端预测拦截
                        int seq = i; 
                        
                        switch (mode.get()) {
                            case Place -> sendPlacePacket(target, h, seq);
                            case Drop -> sendDropPacket();
                        }
                        
                        // 极微小的间隔 (0.1ms)，防止网络层合并包
                        Thread.sleep(0, 100000); 
                    }
                    
                    // 在主线程通知
                    mc.execute(() -> {
                        info("已发送 " + burst + " 个并发包。延迟: " + delay + "ms");
                        toggle();
                    });

                } catch (Exception e) {
                    e.printStackTrace();
                    toggle();
                }
            }, delay, TimeUnit.MILLISECONDS);
        }
    }

    private void sendPlacePacket(HitResult target, Hand hand, int seq) {
        if (target instanceof BlockHitResult hitResult) {
            // 直接通过 connection 发送，绕过客户端逻辑
            mc.getNetworkHandler().getConnection().send(new PlayerInteractBlockC2SPacket(
                hand,
                hitResult,
                seq
            ));
        }
    }

    private void sendDropPacket() {
        mc.getNetworkHandler().getConnection().send(new PlayerActionC2SPacket(
            PlayerActionC2SPacket.Action.DROP_ALL_ITEMS, 
            mc.player.getBlockPos(), 
            net.minecraft.util.math.Direction.DOWN
        ));
    }

    public enum Mode {
        Place,
        Drop
    }
}