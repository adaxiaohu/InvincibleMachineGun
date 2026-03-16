package com.codigohasta.addon.modules;

import com.codigohasta.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.renderer.Renderer2D;
import meteordevelopment.meteorclient.renderer.text.TextRenderer;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;

import java.util.*;
import java.util.stream.Collectors;

public class ModuleList extends Module {

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> x = sgGeneral.add(new IntSetting.Builder()
            .name("x轴位置")
            .description("屏幕X轴坐标")
            .defaultValue(1910)
            .sliderRange(0, 2560)
            .build()
    );

    private final Setting<Integer> y = sgGeneral.add(new IntSetting.Builder()
            .name("y轴位置")
            .description("屏幕Y轴坐标")
            .defaultValue(10)
            .sliderRange(0, 1440)
            .build()
    );

    private final Setting<Boolean> additionalInfo = sgGeneral.add(new BoolSetting.Builder()
            .name("显示额外信息")
            .description("显示模块的额外信息（如 [Mode]）")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> onlyBind = sgGeneral.add(new BoolSetting.Builder()
            .name("仅显示绑定")
            .description("只显示绑定了按键的模块")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> shadow = sgGeneral.add(new BoolSetting.Builder()
            .name("字体阴影")
            .defaultValue(true)
            .build()
    );

    private final Setting<SettingColor> moduleColor = sgGeneral.add(new ColorSetting.Builder()
            .name("模块名称颜色")
            .defaultValue(new SettingColor(255, 255, 255))
            .build()
    );

    private final Setting<SettingColor> activeColor = sgGeneral.add(new ColorSetting.Builder()
            .name("开启状态颜色")
            .description("额外信息文字的颜色")
            .defaultValue(new SettingColor(0, 255, 0))
            .build()
    );

    private final Setting<SettingColor> background = sgGeneral.add(new ColorSetting.Builder()
            .name("背景颜色")
            .defaultValue(new SettingColor(0, 0, 0, 80))
            .build()
    );

    private final Setting<SettingColor> tagColor = sgGeneral.add(new ColorSetting.Builder()
            .name("标签条颜色")
            .description("右侧的小竖条颜色")
            .defaultValue(new SettingColor(0, 150, 255))
            .build()
    );

    // 动画参数
    private final double slideSpeed = 0.2;
    private final double fadeSpeed = 0.15;
    private final double ySpeed = 0.3;

    private final Map<Module, ModuleEntry> moduleEntries = new HashMap<>();

    public ModuleList() {
        super(AddonTemplate.CATEGORY, "module-list-plus", "屏幕显示模块列表 (移植增强版)");
    }

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        if (mc.player == null || mc.world == null) return;

        TextRenderer textRenderer = TextRenderer.get();
        boolean useShadow = shadow.get();

        // 1. 确保所有模块都在 map 中
        for (Module m : Modules.get().getAll()) {
            if (!moduleEntries.containsKey(m)) {
                moduleEntries.put(m, new ModuleEntry(m));
            }
        }

        // 2. 获取并排序当前活跃的模块
        List<Module> activeModules = Modules.get().getAll().stream()
                .filter(m -> m != this && m.isActive() && (!onlyBind.get() || m.keybind.isSet()))
                .sorted(Comparator.comparingDouble(m -> -textRenderer.getWidth(getDisplayText(m), useShadow))) 
                .collect(Collectors.toList());

        double drawY = y.get();

        // 3. 渲染活跃模块
        for (Module module : activeModules) {
            ModuleEntry entry = moduleEntries.get(module);
            String fullText = getDisplayText(module);
            
            double width = textRenderer.getWidth(fullText, useShadow);
            double height = textRenderer.getHeight(); 

            double targetX = x.get() - width;
            
            entry.x = lerp(entry.x, targetX, slideSpeed);
            entry.y = lerp(entry.y, drawY, ySpeed);
            entry.fade = lerp(entry.fade, 1.0, fadeSpeed);

            if (entry.fade > 0.01) {
                renderEntry(event, textRenderer, entry, fullText, width, height, useShadow);
            }

            drawY += height + 6; 
        }

        // 4. 处理非活跃模块（滑出动画）
        for (Module m : moduleEntries.keySet()) {
            if (!activeModules.contains(m)) {
                ModuleEntry entry = moduleEntries.get(m);
                
                if (entry.fade <= 0.01 && Math.abs(entry.x - (x.get() + 50)) < 1) {
                    entry.fade = 0;
                    continue;
                }

                entry.fade = lerp(entry.fade, 0.0, fadeSpeed);
                entry.x = lerp(entry.x, x.get() + 50, slideSpeed); 

                if (entry.fade > 0.01) {
                    String fullText = getDisplayText(m);
                    double width = textRenderer.getWidth(fullText, useShadow);
                    double height = textRenderer.getHeight();
                    renderEntry(event, textRenderer, entry, fullText, width, height, useShadow);
                }
            }
        }
    }

    private void renderEntry(Render2DEvent event, TextRenderer textRenderer, ModuleEntry entry, String fullText, double width, double height, boolean useShadow) {
        // 背景框
        Renderer2D.COLOR.begin();
        Renderer2D.COLOR.quad(entry.x - 4, entry.y - 2, width + 8, height + 4,
                new SettingColor(
                        background.get().r,
                        background.get().g,
                        background.get().b,
                        (int) (background.get().a * entry.fade)
                )
        );
        // 右侧标签条
        Renderer2D.COLOR.quad(entry.x + width + 4, entry.y - 2, 3, height + 4,
                new SettingColor(
                        tagColor.get().r,
                        tagColor.get().g,
                        tagColor.get().b,
                        (int) (tagColor.get().a * entry.fade)
                )
        );
        
        // --- 修复点：传入 Matrices 而不是 DrawContext ---
        Renderer2D.COLOR.render(event.drawContext.getMatrices());

        // 文字渲染
        SettingColor mainColor = new SettingColor(moduleColor.get());
        mainColor.a = (int) (mainColor.a * entry.fade);
        
        textRenderer.render(entry.module.title, entry.x, entry.y, mainColor, useShadow);

        String info = getModuleInfo(entry.module);
        if (!info.isEmpty()) {
            double nameWidth = textRenderer.getWidth(entry.module.title, useShadow);
            double spaceWidth = textRenderer.getWidth(" ", useShadow);
            
            SettingColor infoColor = new SettingColor(activeColor.get());
            infoColor.a = (int) (infoColor.a * entry.fade);
            
            textRenderer.render(info, entry.x + nameWidth + spaceWidth, entry.y, infoColor, useShadow);
        }
    }

    private String getDisplayText(Module module) {
        String info = getModuleInfo(module);
        return module.title + (info.isEmpty() ? "" : " " + info);
    }
    
    private String getModuleInfo(Module module) {
        if (!additionalInfo.get()) return "";
        String info = module.getInfoString();
        return info != null ? info : "";
    }

    private double lerp(double start, double end, double step) {
        return start + (end - start) * step;
    }

    private static class ModuleEntry {
        final Module module;
        double x = 0;
        double y = 0;
        double fade = 0;

        ModuleEntry(Module module) {
            this.module = module;
        }
    }
}