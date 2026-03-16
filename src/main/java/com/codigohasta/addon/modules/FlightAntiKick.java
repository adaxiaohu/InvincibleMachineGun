package com.codigohasta.addon.modules;

import net.minecraft.util.math.Vec3d;

import com.codigohasta.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.math.Vec3d;

import java.util.Random;

public class FlightAntiKick extends Module {
    public enum Mode {
        物理下沉,    // 修改速度（你现在用的，最稳但有抖动感）
        数据包下潜,  // 发送一个稍低坐标的包（不影响视觉，较隐蔽）
        垂直重力     // 模拟微弱下坠（最像真实玩家）
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRandom = settings.createGroup("随机化 (更安全)");

    // --- 设置项 ---
    private final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("反踢模式")
        .description("物理下沉：直接修改速度；数据包下潜：只给服务器发下落包；垂直重力：始终保持微弱下落。")
        .defaultValue(Mode.物理下沉)
        .build()
    );

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("基础频率")
        .description("触发反踢动作的间隔。建议25-35。")
        .defaultValue(25)
        .min(5)
        .build()
    );

    private final Setting<Double> dipDistance = sgGeneral.add(new DoubleSetting.Builder()
        .name("下沉幅度")
        .description("向下位移的距离。建议 0.04（这是绕过原版检测的黄金数值）。")
        .defaultValue(0.04)
        .min(0.01)
        .sliderMax(0.1)
        .build()
    );

    private final Setting<Boolean> onlyIfActive = sgGeneral.add(new BoolSetting.Builder()
        .name("联动飞行模块")
        .description("仅在 Meteor Flight 开启时生效。")
        .defaultValue(true)
        .build()
    );

    // --- 随机化 ---
    private final Setting<Boolean> useRandom = sgRandom.add(new BoolSetting.Builder().name("随机延迟").defaultValue(true).build());
    private final Setting<Integer> randomRange = sgRandom.add(new IntSetting.Builder().name("随机范围").defaultValue(5).min(1).visible(useRandom::get).build());

    private final Random random = new Random();
    private int timer = 0;
    private int currentMaxDelay;

    public FlightAntiKick() {
        super(AddonTemplate.CATEGORY, "flight-anti-kick", "飞行反踢。");
    }

    @Override
    public void onActivate() {
        timer = 0;
        resetDelay();
    }

    private void resetDelay() {
        currentMaxDelay = useRandom.get() ? delay.get() + (random.nextInt(randomRange.get() * 2) - randomRange.get()) : delay.get();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null) return;

        if (onlyIfActive.get()) {
            Module flight = Modules.get().get("flight");
            if (flight == null || !flight.isActive()) return;
        }

        if (mc.player.isOnGround()) return;

        // 模式 3：垂直重力逻辑（每一帧都生效）
        if (mode.get() == Mode.垂直重力) {
            Vec3d v = mc.player.getVelocity();
            if (v.y >= 0) { // 只要没在下落，就强行给一个微小的向下分量
                mc.player.setVelocity(v.x, -0.005, v.z);
            }
            return;
        }

        timer++;
        if (timer >= currentMaxDelay) {
            executeKickAction();
            timer = 0;
            resetDelay();
        }
    }

    private void executeKickAction() {
        double dip = dipDistance.get();
        
        if (mode.get() == Mode.物理下沉) {
            // 物理修改 Velocity，这会刷新服务器计时器，但视角会抖一下
            Vec3d v = mc.player.getVelocity();
            mc.player.setVelocity(v.x, -dip, v.z);
        } 
        else if (mode.get() == Mode.数据包下潜) {
            // --- 核心修复：针对 1.21.4 的数据包下潜 ---
            // 我们不修改玩家的实际位置，而是发送一个“我往下跳了一下又回来了”的伪造包
            // 这种方式不会让你的镜头抖动，但在服务器看来你已经产生过负 Y 轴位移了
            double x = mc.player.getX();
            double y = mc.player.getY();
            double z = mc.player.getZ();
            
            // 发送一个稍微低一点的坐标包，且 OnGround 设为 false 以保持真实
            mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(x, y - dip, z, false, mc.player.horizontalCollision));
        }
    }

    @Override
    public String getInfoString() {
        return mode.get().toString();
    }
}