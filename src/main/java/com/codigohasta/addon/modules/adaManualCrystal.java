package com.codigohasta.addon.modules;

import com.codigohasta.addon.AddonTemplate;
import com.codigohasta.addon.mixin.MinecraftClientAccessor;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.item.ItemStack;
import java.util.concurrent.ThreadLocalRandom;

public class adaManualCrystal extends Module {
    public adaManualCrystal() {
        super(AddonTemplate.CATEGORY, "比谁点的快", "让你放炸弹道具更快，只是快速放置水晶，需要自己点左键 grim应该可以用");
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> placeDelay = sgGeneral.add(new IntSetting.Builder()
        .name("放置延迟")
        .description("设置为 1 或 2 可以大幅提速且较安全。设为 0 极易被 Grim 检测。")
        .defaultValue(1)
        .min(0)
        .sliderMax(4)
        .build()
    );

    private final Setting<Boolean> jitter = sgGeneral.add(new BoolSetting.Builder()
        .name("随机抖动")
        .description("模拟人类点击，增加 Grim 绕过率。")
        .defaultValue(true)
        .build()
    );

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null) return;

        // 1.21.11 字符串判定：是否手持水晶
        boolean holdingCrystal = mc.player.getMainHandStack().getItem().toString().contains("end_crystal") 
                              || mc.player.getOffHandStack().getItem().toString().contains("end_crystal");

        if (!holdingCrystal) return;

        // 核心逻辑：如果右键按住，且冷却大于我们设定的目标值
        int currentCooldown = ((MinecraftClientAccessor) mc).getItemUseCooldown();
        
        // 计算目标延迟
        int targetDelay = placeDelay.get();
        if (jitter.get() && targetDelay < 3) {
            // 随机在目标延迟和原版延迟之间摆动，Grim 很难抓到规律
            if (ThreadLocalRandom.current().nextBoolean()) targetDelay++;
        }

        // 如果当前冷却比我们要的高（比如原版刚设为 4）
        if (currentCooldown > targetDelay) {
            // 只有在同时按住左右键，或者右键按住时才提速
            if (mc.options.useKey.isPressed()) {
                ((MinecraftClientAccessor) mc).setItemUseCooldown(targetDelay);
            }
        }
        
        // 解决“左键快导致放不出”：
        // 如果左键按下导致原版逻辑尝试阻塞右键，我们强行让冷却保持在可放置范围内
        if (mc.options.attackKey.isPressed() && mc.options.useKey.isPressed()) {
            if (currentCooldown > targetDelay) {
                ((MinecraftClientAccessor) mc).setItemUseCooldown(targetDelay);
            }
        }
    }
}