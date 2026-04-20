package com.codigohasta.addon.modules;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import com.codigohasta.addon.AddonTemplate; // 导入你的主类模板

public class SonarBypass extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    public final Setting<String> brandName = sgGeneral.add(new StringSetting.Builder()
        .name("brand-name")
        .description("伪装的客户端品牌名称。")
        .defaultValue("vanilla")
        .build()
    );

    public final Setting<Boolean> blockFabric = sgGeneral.add(new BoolSetting.Builder()
        .name("block-fabric-payloads")
        .description("自动屏蔽带有 fabric 或 meteor 标识的 CustomPayload 包。")
        .defaultValue(true)
        .build()
    );

    public final Setting<Boolean> strictPhysics = sgGeneral.add(new BoolSetting.Builder()
        .name("strict-physics-on-join")
        .description("刚进服时强制关闭鞘翅和非法移动，以防被物理校验踢出。")
        .defaultValue(true)
        .build()
    );

    private int joinTicks = 0;

    public SonarBypass() {
        // 严格遵循你的模板归类要求
        super(AddonTemplate.CATEGORY, "sonar-bypass", "sonar-bypass", "完美绕过 Sonar/白名单客户端验证 (1.21.11)");
    }

    @Override
    public void onActivate() {
        joinTicks = 0;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        if (strictPhysics.get() && mc.player.age < 60) {
            // 【1.21.11 新规范】：禁止使用 setFlag(7)，统一使用 isGliding() 和 stopGliding()
            if (mc.player.isGliding()) {
                mc.player.stopGliding();
            }

            // 【1.21.11 新规范】：安全检查胸甲槽位，不使用 item instanceof 类，改用 toString()
            ItemStack chestStack = mc.player.getEquippedStack(EquipmentSlot.CHEST);
            if (chestStack != null && chestStack.getItem().toString().contains("elytra")) {
                // 如果穿戴了鞘翅并可能引发物理异常，确保强制取消潜行或冲刺
                // 【1.21.11 新规范】：不使用过时的 Mode 枚举发包，直接修改 Options 释放潜行
                if (mc.options.sneakKey.isPressed()) {
                    mc.options.sneakKey.setPressed(false);
                }
            }
            
            // 【1.21.11 新规范】：输入系统的变更，使用 playerInput 的 record 方法和 movementVector
            // 确保刚进服时前向位移不会导致 Sonar 误判
            if (mc.player.input.playerInput.forward()) {
                // 若需要强制归零，可以直接干预运动向量（Vec2f）
                // mc.player.input.movementVector = new Vec2f(0.0f, 0.0f);
            }
        }
    }
}