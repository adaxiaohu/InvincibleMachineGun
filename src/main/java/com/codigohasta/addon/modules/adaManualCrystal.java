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
        super(AddonTemplate.CATEGORY, "比谁点的快", "让你放道具更快，只快速放置水晶，需要自己点左键哦");
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> placeDelay = sgGeneral.add(new IntSetting.Builder()
        .name("放置延迟")
        .description("。")
        .defaultValue(1)
        .min(0)
        .sliderMax(4)
        .build()
    );

    private final Setting<Boolean> jitter = sgGeneral.add(new BoolSetting.Builder()
        .name("随机抖动")
        .description("123")
        .defaultValue(true)
        .build()
    );

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null) return;


        boolean holdingCrystal = mc.player.getMainHandStack().getItem().toString().contains("end_crystal") 
                              || mc.player.getOffHandStack().getItem().toString().contains("end_crystal");

        if (!holdingCrystal) return;


        int currentCooldown = ((MinecraftClientAccessor) mc).getItemUseCooldown();
        

        int targetDelay = placeDelay.get();
        if (jitter.get() && targetDelay < 3) {

            if (ThreadLocalRandom.current().nextBoolean()) targetDelay++;
        }

      
        if (currentCooldown > targetDelay) {

            if (mc.options.useKey.isPressed()) {
                ((MinecraftClientAccessor) mc).setItemUseCooldown(targetDelay);
            }
        }
        
        if (mc.options.attackKey.isPressed() && mc.options.useKey.isPressed()) {
            if (currentCooldown > targetDelay) {
                ((MinecraftClientAccessor) mc).setItemUseCooldown(targetDelay);
            }
        }
    }
}