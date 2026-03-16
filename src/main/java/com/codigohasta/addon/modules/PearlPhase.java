package com.codigohasta.addon.modules;

import net.minecraft.util.math.Vec3d;

import com.codigohasta.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;

public class PearlPhase extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    public final Setting<Boolean> antiPush = sgGeneral.add(new BoolSetting.Builder()
        .name("anti-push")
        .description("禁止客户端将你挤出方块 。")
        .defaultValue(true)
        .build()
    );

    // --- 改名：移除遮挡 ---
    // 这个选项控制是否移除方块内的"窒息"贴图
    public final Setting<Boolean> removeOverlay = sgGeneral.add(new BoolSetting.Builder()
        .name("remove-overlay")
        .description("移除方块内的视觉遮挡，让你像在玻璃里一样看外面。")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> speed = sgGeneral.add(new DoubleSetting.Builder()
        .name("speed")
        .description("方块内移动倍率。")
        .defaultValue(5.0) 
        .min(0.0)
        .max(20.0)
        .sliderMax(10.0)
        .build()
    );

    public PearlPhase() {
        super(AddonTemplate.CATEGORY, "珍珠卡墙", "可以用珍珠卡进方块里");
    }

    @Override
    public void onDeactivate() {
        if (mc.player != null) {
            mc.player.noClip = false;
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        if (isInsideBlock()) {
            mc.player.noClip = true;
            mc.player.fallDistance = 0;
            mc.player.setOnGround(true);

            // 删除了所有夜视代码，现在这里只处理移动
            handleMove();
        } else {
            mc.player.noClip = false;
        }
    }

    private void handleMove() {
        // --- 速度调整区 ---
        // 再次降速！0.0001 是之前的 1/5
        // 如果还觉得快，你可以把滑块拉到 0.1
        double baseSpeed = 0.0001; 
        
        double finalSpeed = baseSpeed * speed.get();

        double n = mc.player.input.playerInput.forward() ? 1.0 : 0.0;
        double n2 = mc.player.input.playerInput.backward() ? 1.0 : 0.0;
        double n3 = mc.player.getYaw();

        if (n == 0.0 && n2 == 0.0) {
            mc.player.setVelocity(0, 0, 0);
            return;
        }

        if (n != 0.0 && n2 != 0.0) {
            n *= Math.sin(Math.PI / 4);
            n2 *= Math.cos(Math.PI / 4);
        }

        double motionX = n * finalSpeed * -Math.sin(Math.toRadians(n3)) + n2 * finalSpeed * Math.cos(Math.toRadians(n3));
        double motionZ = n * finalSpeed * Math.cos(Math.toRadians(n3)) - n2 * finalSpeed * -Math.sin(Math.toRadians(n3));

        mc.player.setVelocity(motionX, 0, motionZ);
    }

    private boolean isInsideBlock() {
        return mc.world.getBlockCollisions(mc.player, mc.player.getBoundingBox().contract(0.001)).iterator().hasNext();
    }
}