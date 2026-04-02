package com.codigohasta.addon.modules;

import com.codigohasta.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.renderer.Renderer2D;
import meteordevelopment.meteorclient.renderer.text.TextRenderer;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;

public class SprintStatusModule extends Module {
    // --- 设置分组 ---
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgDisplay = settings.createGroup("内容设置");
    private final SettingGroup sgColors = settings.createGroup("颜色设置");

    // --- 1. 基础设置 ---
    private final Setting<Double> scale = sgGeneral.add(new DoubleSetting.Builder()
        .name("文字大小")
        .defaultValue(1.5)
        .min(0.5)
        .sliderMax(5)
        .build()
    );

    private final Setting<Double> posX = sgGeneral.add(new DoubleSetting.Builder()
        .name("位置-X")
        .defaultValue(500)
        .min(0)
        .sliderMax(2500)
        .build()
    );

    private final Setting<Double> posY = sgGeneral.add(new DoubleSetting.Builder()
        .name("位置-Y")
        .defaultValue(230)
        .min(0)
        .sliderMax(2500)
        .build()
    );

   
    private final Setting<Boolean> useChinese = sgDisplay.add(new BoolSetting.Builder()
        .name("使用中文")
        .description("开启显示：疾跑，关闭显示：Sprint")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> showIcon = sgDisplay.add(new BoolSetting.Builder()
        .name("显示图标")
        .description("显示 ▶▶ 或 ■ 符号")
        .defaultValue(true)
        .build()
    );

    // --- 3. 颜色设置 ---
    private final Setting<SettingColor> sprintColor = sgColors.add(new ColorSetting.Builder()
        .name("冲刺状态颜色")
        .defaultValue(new SettingColor(0, 255, 0))
        .build()
    );

    private final Setting<SettingColor> idleColor = sgColors.add(new ColorSetting.Builder()
        .name("未冲刺状态颜色")
        .defaultValue(new SettingColor(255, 0, 0))
        .build()
    );

    private final Setting<Boolean> background = sgGeneral.add(new BoolSetting.Builder()
        .name("显示背景")
        .defaultValue(true)
        .build()
    );

    private final Setting<SettingColor> bgColor = sgGeneral.add(new ColorSetting.Builder()
        .name("背景颜色")
        .defaultValue(new SettingColor(0, 0, 0, 150))
        .visible(background::get)
        .build()
    );

    public SprintStatusModule() {
        super(AddonTemplate.CATEGORY, "冲刺显示", "疾跑状态显示 ，当你不知道有没有冲刺的时候用");
    }

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        if (mc.player == null) return;

       
        boolean isSprinting = mc.player.isSprinting();
        
       
        String icon = "";
        if (showIcon.get()) {
            icon = isSprinting ? "▶▶ " : "■ ";
        }

      
        String statusText = "";
        if (useChinese.get()) {
            statusText = isSprinting ? "疾跑: 开启" : "疾跑: 关闭";
        } else {
            statusText = isSprinting ? "Sprint: ON" : "Sprint: OFF";
        }

        String finalText = icon + statusText;
        
       
        SettingColor textColor = isSprinting ? sprintColor.get() : idleColor.get();

    
        TextRenderer renderer = TextRenderer.get();
        double w = renderer.getWidth(finalText) * scale.get();
        double h = renderer.getHeight() * scale.get();
        double x = posX.get();
        double y = posY.get();

    
        if (background.get()) {
            Renderer2D.COLOR.begin();
            Renderer2D.COLOR.quad(x - 4, y - 4, w + 8, h + 8, bgColor.get());
          
            Renderer2D.COLOR.render();
        }

    
        renderer.begin(scale.get(), false, true);
        renderer.render(finalText, x, y, textColor, true);
        renderer.end();
    }
}