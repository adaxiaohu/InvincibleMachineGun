package com.codigohasta.addon.modules;

import net.minecraft.util.math.Vec3d;

import com.codigohasta.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.option.Perspective;
import net.minecraft.util.math.MathHelper;

import java.util.Random;

public class AutoNod extends Module {
    
    public AutoNod() {
        super(AddonTemplate.CATEGORY, "auto-nod", "自动点头：支持发包/可见模式切换及打瞌睡功能 娱乐用哈哈，打瞌睡的比较好玩，你站在原地角色就像要睡着一样。");
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    // --- 1. 动作模式 ---
    public enum Mode {
        Nod,        // 匀速点头
        Shake,      // 匀速摇头
        Circular,   // 画圆
        Diagonal,   // 对角线
        Random,     // 随机震动
        Sleepy      // 😪 打瞌睡
    }

    private final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("mode")
        .description("动作模式。")
        .defaultValue(Mode.Sleepy)
        .build()
    );

    // --- 2. 显示模式 (新增) ---
    public enum Visibility {
        Packet, // 仅发包 (自己看不见)
        Client, // 仅本地 (自己看得见)
        Smart   // 智能 (第一人称发包，第三人称本地)
    }

    private final Setting<Visibility> visibility = sgGeneral.add(new EnumSetting.Builder<Visibility>()
        .name("visibility")
        .description("显示方式：\nPacket = 静默，只有别人能看到\nClient = 可见，画面会跟着动\nSmart = 第一人称静默，第三人称可见")
        .defaultValue(Visibility.Smart)
        .build()
    );

    // --- 3. 基础参数 ---
    private final Setting<Double> speed = sgGeneral.add(new DoubleSetting.Builder()
        .name("speed")
        .description("速度。")
        .defaultValue(3.0)
        .min(0.1)
        .sliderMax(50.0)
        .max(100.0)
        .build()
    );

    private final Setting<Double> angle = sgGeneral.add(new DoubleSetting.Builder()
        .name("angle")
        .description("角度幅度。")
        .defaultValue(50.0)
        .min(1.0)
        .sliderMax(180.0)
        .max(360.0)
        .build()
    );

    // --- 4. 打瞌睡专属参数 ---
    private final Setting<Double> sleepWait = sgGeneral.add(new DoubleSetting.Builder()
        .name("sleep-wait")
        .description("熟睡时长：头低下后保持不动的时间比例(0.0-0.8)。")
        .defaultValue(0.3)
        .min(0.0)
        .max(0.8)
        .sliderMax(0.8)
        .visible(() -> mode.get() == Mode.Sleepy)
        .build()
    );

    private final Setting<Double> wakeUpSpeed = sgGeneral.add(new DoubleSetting.Builder()
        .name("wake-up-speed")
        .description("惊醒速度：数值越大抬头越快。")
        .defaultValue(20.0)
        .min(1.0)
        .sliderMax(50.0)
        .visible(() -> mode.get() == Mode.Sleepy)
        .build()
    );

    // --- 内部变量 ---
    private double timer = 0;
    private final Random random = new Random();
    private float lastAddedYaw = 0;
    private float lastAddedPitch = 0;

    @Override
    public void onActivate() {
        timer = 0;
        lastAddedYaw = 0;
        lastAddedPitch = 0;
    }

    @Override
    public void onDeactivate() {
        if (mc.player != null) {
            mc.player.setYaw(mc.player.getYaw() - lastAddedYaw);
            mc.player.setPitch(mc.player.getPitch() - lastAddedPitch);
        }
        lastAddedYaw = 0;
        lastAddedPitch = 0;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null) return;

        // 1. 还原上一帧偏移 (保证基于鼠标朝向)
        mc.player.setYaw(mc.player.getYaw() - lastAddedYaw);
        mc.player.setPitch(mc.player.getPitch() - lastAddedPitch);
        lastAddedYaw = 0;
        lastAddedPitch = 0;

        // 2. 计算动画
        timer += speed.get() * 0.05;
        double range = angle.get();
        
        float offsetX = 0;
        float offsetY = 0;

        switch (mode.get()) {
            case Nod:
                offsetY = (float) (Math.sin(timer) * range);
                break;
            case Shake:
                offsetX = (float) (Math.sin(timer) * range);
                break;
            case Circular:
                offsetX = (float) (Math.cos(timer) * range);
                offsetY = (float) (Math.sin(timer) * range);
                break;
            case Diagonal:
                offsetX = (float) (Math.sin(timer) * range);
                offsetY = (float) (Math.sin(timer) * range);
                break;
            case Random:
                if ((int)(timer * 10) % 2 == 0) { 
                    offsetX = (float) ((random.nextDouble() - 0.5) * range);
                    offsetY = (float) ((random.nextDouble() - 0.5) * range);
                }
                break;
            case Sleepy:
                // 三段式打瞌睡逻辑
                double cycle = 10.0;
                double t = (timer % cycle) / cycle;

                double wakeLen = 0.5 / wakeUpSpeed.get(); 
                double waitLen = sleepWait.get();
                if (waitLen + wakeLen > 0.95) waitLen = 0.95 - wakeLen;
                if (waitLen < 0) waitLen = 0;

                double endDrop = 1.0 - wakeLen - waitLen;
                double startWake = 1.0 - wakeLen;

                if (t < endDrop) { // 下坠
                    double p = t / endDrop;
                    offsetY = (float) (range * (p * p));
                } else if (t < startWake) { // 熟睡
                    offsetY = (float) (range);
                } else { // 惊醒
                    double p = (t - startWake) / wakeLen;
                    offsetY = (float) (range * (1.0 - p));
                }
                break;
        }

        // 3. 判断显示模式
        boolean usePacket = false;
        
        switch (visibility.get()) {
            case Packet:
                usePacket = true; // 强制静默
                break;
            case Client:
                usePacket = false; // 强制可见
                break;
            case Smart:
                // 第一人称静默，其他情况可见
                usePacket = mc.options.getPerspective() == Perspective.FIRST_PERSON;
                break;
        }

        float realYaw = mc.player.getYaw();
        float realPitch = mc.player.getPitch();

        float targetYaw = realYaw + offsetX;
        float targetPitch = realPitch + offsetY;
        targetPitch = MathHelper.clamp(targetPitch, -90, 90);

        if (usePacket) {
            // 发送数据包，不改变本地实体
            Rotations.rotate(targetYaw, targetPitch, 100);
        } else {
            // 改变本地实体，并记录偏移量以便下一帧还原
            mc.player.setYaw(targetYaw);
            mc.player.setPitch(targetPitch);
            
            lastAddedYaw = targetYaw - realYaw;
            lastAddedPitch = targetPitch - realPitch;
        }
    }
}