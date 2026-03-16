package com.codigohasta.addon.modules;

import net.minecraft.util.math.Vec3d;

import com.codigohasta.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;


public class AutoJump extends Module {

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    // --- 设置项 ---

    /**
     * 控制是否在跳跃时潜行的开关
     */
    private final Setting<Boolean> sneakWhileJumping = sgGeneral.add(new BoolSetting.Builder()
        .name("反复潜行")
        .description("娱乐用的。在跳跃的同时反复进行潜行和站立。好好笑哈哈")
        .defaultValue(false)
        .build() 
    );
    /**
     * 控制站立持续时间的设置
     */
    private final Setting<Integer> sneakStandDelay = sgGeneral.add(new IntSetting.Builder()
        .name("潜行站立时间")
        .description("每次站立状态的持续时间 (ticks)。")
        .defaultValue(5)
        .min(0) 
        .sliderRange(0, 20)
        .visible(sneakWhileJumping::get) // 仅当“潜行跳”开启时可见
        .build()
    ); 

    /**
     * 控制潜行持续时间的设置
     */
    private final Setting<Integer> sneakDuration = sgGeneral.add(new IntSetting.Builder()
        .name("潜行持续时间")
        .description("每次潜行按下的持续时间 (ticks)。")
        .defaultValue(1)
        .min(1) 
        .sliderRange(1, 20)
        .visible(sneakWhileJumping::get) // 仅当“潜行跳”开启时可见
        .build()
    );
    /**
     * 控制跳跃频率的速率设置
     */ 
    private final Setting<Integer> jumpDelay = sgGeneral.add(new IntSetting.Builder()
        .name("跳跃延迟")
        .description("两次跳跃之间的间隔时间 (ticks)。数值越小，跳得越快。")
        .defaultValue(10)
        .min(0) 
        .sliderRange(0, 40)
        .build()
    );

    private int jumpTimer; 
    private int sneakActionTimer;
    private boolean isCurrentlySneaking;


    public AutoJump() {
        super(AddonTemplate.CATEGORY, "反复横跳", "自动持续跳跃。娱乐用哈哈");
    }
 
    @Override
    public void onActivate() {
        // 模块激活时，重置计时器
        jumpTimer = 0;
        sneakActionTimer = 0;
        isCurrentlySneaking = false;
        // 强制释放潜行键，确保状态一致
        if (mc.options != null) {
            mc.options.sneakKey.setPressed(false);
        }
    }

    @Override
    public void onDeactivate() {
        // 模块关闭时，确保潜行键被释放，以防卡住
        if (mc.options != null) {
            mc.options.sneakKey.setPressed(false); // 直接设置为 false
            isCurrentlySneaking = false; // 同时重置内部状态
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        // --- 反复潜行逻辑 ---
        if (sneakWhileJumping.get()) {
            if (sneakActionTimer > 0) {
                sneakActionTimer--;
            } else {
                isCurrentlySneaking = !isCurrentlySneaking;
                mc.options.sneakKey.setPressed(isCurrentlySneaking);
                sneakActionTimer = isCurrentlySneaking ? sneakDuration.get() : sneakStandDelay.get();
            }
        } else {
            if (isCurrentlySneaking) {
                mc.options.sneakKey.setPressed(false);
                isCurrentlySneaking = false;
            }
        }

        // --- 跳跃逻辑 ---
        if (jumpTimer > 0) {
            jumpTimer--;
        } else {
            if (mc.player.isOnGround()) {
                mc.player.jump();
                jumpTimer = jumpDelay.get();
            }
        }
    }
}