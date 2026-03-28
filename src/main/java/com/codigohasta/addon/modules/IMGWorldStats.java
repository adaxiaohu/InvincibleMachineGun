package com.codigohasta.addon.modules;

import com.codigohasta.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.Renderer2D;
import meteordevelopment.meteorclient.renderer.text.TextRenderer;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.network.packet.c2s.play.ClientStatusC2SPacket;
import net.minecraft.registry.Registries;
import net.minecraft.stat.StatHandler;
import net.minecraft.stat.Stats;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class IMGWorldStats extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgStats = settings.createGroup("统计信息");
    private final SettingGroup sgVisual = settings.createGroup("视觉客制化");

    // ================== 基础信息设置 ==================
    private final Setting<Boolean> showTicks = sgGeneral.add(new BoolSetting.Builder().name("游戏刻(日循环)").defaultValue(true).build());
    private final Setting<Boolean> showDays = sgGeneral.add(new BoolSetting.Builder().name("生存天数").defaultValue(true).build());
    private final Setting<Boolean> showBiome = sgGeneral.add(new BoolSetting.Builder().name("当前生物群系").defaultValue(true).build());
    private final Setting<Boolean> showWeather = sgGeneral.add(new BoolSetting.Builder().name("天气状态").defaultValue(true).build());
    private final Setting<Boolean> showCoords = sgGeneral.add(new BoolSetting.Builder().name("双维度坐标").defaultValue(true).build());

    // ================== 统计信息设置 ==================
    private final Setting<Boolean> showPlaytime = sgStats.add(new BoolSetting.Builder().name("总游玩时长").defaultValue(true).build());
    private final Setting<Boolean> showDistance = sgStats.add(new BoolSetting.Builder().name("总移动距离").defaultValue(true).build());
    private final Setting<Boolean> showKills = sgStats.add(new BoolSetting.Builder().name("总击杀数").defaultValue(true).build());
    private final Setting<Boolean> showDeaths = sgStats.add(new BoolSetting.Builder().name("死亡次数").defaultValue(true).build());
    private final Setting<Boolean> showBreaks = sgStats.add(new BoolSetting.Builder().name("挖掘总数").defaultValue(true).build());

    // ================== 视觉客制化 ==================
    public enum Theme { 简约现代风, 简约苹果风, 黑客帝国, 飘雪, 合成器浪潮 }
    private final Setting<Theme> theme = sgVisual.add(new EnumSetting.Builder<Theme>().name("视觉风格").defaultValue(Theme.简约现代风).build());
 // ================== 分离式：文字控制 ==================
    private final Setting<Double> textX = sgVisual.add(new DoubleSetting.Builder().name("文字 X 坐标").defaultValue(15.0).min(0.0).sliderMax(2000.0).build());
    private final Setting<Double> textY = sgVisual.add(new DoubleSetting.Builder().name("文字 Y 坐标").defaultValue(15.0).min(0.0).sliderMax(2000.0).build());
    private final Setting<Double> textScale = sgVisual.add(new DoubleSetting.Builder().name("文字缩放").defaultValue(1.0).min(0.1).sliderMax(3.0).build());
    private final Setting<SettingColor> textColor = sgVisual.add(new ColorSetting.Builder().name("文字颜色").defaultValue(new SettingColor(255, 255, 255)).build());
    private final Setting<Boolean> textShadow = sgVisual.add(new BoolSetting.Builder().name("文字阴影").defaultValue(true).build());

    // ================== 分离式：背景与动画控制 ==================
    private final Setting<Double> bgX = sgVisual.add(new DoubleSetting.Builder().name("背景 X 坐标").defaultValue(10.0).min(0.0).sliderMax(2000.0).build());
    private final Setting<Double> bgY = sgVisual.add(new DoubleSetting.Builder().name("背景 Y 坐标").defaultValue(10.0).min(0.0).sliderMax(2000.0).build());
    private final Setting<Double> bgWidth = sgVisual.add(new DoubleSetting.Builder().name("背景宽度").defaultValue(150.0).min(10.0).sliderMax(1000.0).build());
    private final Setting<Double> bgHeight = sgVisual.add(new DoubleSetting.Builder().name("背景高度").defaultValue(180.0).min(10.0).sliderMax(1000.0).build());

    // ================== 内部状态变量 ==================
    private int syncTimer = 0;
    private int cachedBlocksMined = 0; // 缓存挖掘总数，避免每帧遍历导致的卡顿
    private final List<Particle> particles = new ArrayList<>();
    private final Random random = new Random();

    public IMGWorldStats() {
        super(AddonTemplate.CATEGORY, "世界信息表", "显示当前世界信息与数据统计。查看你玩了多久，之类的，需要手动对齐背景板子");
    }

    @Override
    public void onActivate() {
        requestStats();
        particles.clear();
        for (int i = 0; i < 40; i++) particles.add(new Particle(random.nextDouble() * 200.0, random.nextDouble() * 100.0));
    }

    private void requestStats() {
        if (mc.getNetworkHandler() != null && mc.player != null) {
            mc.getNetworkHandler().sendPacket(new ClientStatusC2SPacket(ClientStatusC2SPacket.Mode.REQUEST_STATS));
            updateCachedBlocksMined();
        }
    }

    // 耗时操作：计算挖掘总数，仅在获取数据包时调用
    private void updateCachedBlocksMined() {
        if (mc.player == null) return;
        StatHandler stats = mc.player.getStatHandler();
        int total = 0;
        for (Block block : Registries.BLOCK) {
            total += stats.getStat(Stats.MINED.getOrCreateStat(block));
        }
        cachedBlocksMined = total;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null) return;

        if (syncTimer++ >= 200) {
            requestStats();
            syncTimer = 0;
        }

        Theme currentTheme = theme.get();
        for (Particle p : particles) {
            p.y += (currentTheme == Theme.黑客帝国 ? 2.5 : 0.8);
            // 将重置高度提高到 1000，防止放大框子时底部没有动画
            if (p.y > 1000.0) p.y = 0.0;
        }
    }

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        if (mc.world == null || mc.player == null) return;

        List<String> lines = buildDisplayLines();
        if (lines.isEmpty()) return;

        // --- 1. 独立渲染背景框与动画 ---
        double bX = bgX.get();
        double bY = bgY.get();
        double bW = bgWidth.get();
        double bH = bgHeight.get();

        Renderer2D.COLOR.begin();
        renderBackground(bX, bY, bW, bH);
        Renderer2D.COLOR.render(); 

        // --- 2. 独立渲染文字 ---
        TextRenderer text = TextRenderer.get();
        double tX = textX.get();
        double tY = textY.get();
        double tS = textScale.get();
        Color c = getThemeTextColor();

        text.begin(tS);
        for (String line : lines) {
            // 文字坐标除以缩放系数，抵消全局矩阵漂移
            text.render(line, tX / tS, tY / tS, c, textShadow.get());
            // Y 轴向下步进
            tY += (text.getHeight() + 2.0) * tS; 
        }
        text.end();
    }

    private List<String> buildDisplayLines() {
        List<String> lines = new ArrayList<>();
        StatHandler stats = mc.player.getStatHandler();

        if (showTicks.get()) {
            long dayTime = mc.world.getTimeOfDay() % 24000L;
            lines.add(String.format("当日刻: %d", dayTime));
        }
        
        if (showDays.get()) {
            long worldDays = mc.world.getTimeOfDay() / 24000L;
            lines.add("生存天数: " + worldDays + " 天");
        }

        // ================== 生物群系中文翻译修复 ==================
        if (showBiome.get()) {
            Identifier biomeId = mc.world.getBiome(mc.player.getBlockPos()).getKey().get().getValue();
            // 巧妙使用 toString().replace 绕开 1.21.11 可能存在的 Record 映射问题
            // 例如把 "minecraft:plains" 转换成 "biome.minecraft.plains"
            String transKey = "biome." + biomeId.toString().replace(":", ".");
            // 调用原版翻译组件获取当前语言文本 (如 "平原")
            String localBiomeName = Text.translatable(transKey).getString();
            lines.add("生物群系: " + localBiomeName);
        }

        if (showWeather.get()) {
            String w = mc.world.isThundering() ? "雷雨 ⛈" : (mc.world.isRaining() ? "下雨 🌧" : "晴朗 ☀");
            lines.add("天气: " + w);
        }

        if (showCoords.get()) {
            double px = mc.player.getX();
            double py = mc.player.getY();
            double pz = mc.player.getZ();
            boolean inNether = mc.world.getRegistryKey().getValue().getPath().contains("nether");
            lines.add(String.format("主界: %.1f, %.1f, %.1f", inNether ? px * 8.0 : px, py, inNether ? pz * 8.0 : pz));
            lines.add(String.format("下界: %.1f, %.1f, %.1f", inNether ? px : px / 8.0, py, inNether ? pz : pz / 8.0));
        }

        if (showPlaytime.get()) {
            int pt = stats.getStat(Stats.CUSTOM.getOrCreateStat(Stats.PLAY_TIME));
            int hrs = pt / 72000;
            int mins = (pt % 72000) / 1200;
            lines.add(String.format("总时长: %dh %dm", hrs, mins));
        }

        if (showDistance.get()) {
            long cm = stats.getStat(Stats.CUSTOM.getOrCreateStat(Stats.WALK_ONE_CM)) 
                    + stats.getStat(Stats.CUSTOM.getOrCreateStat(Stats.SPRINT_ONE_CM))
                    + stats.getStat(Stats.CUSTOM.getOrCreateStat(Stats.FLY_ONE_CM));
            lines.add(String.format("总距离: %.1f km", cm / 100000.0));
        }

        if (showKills.get()) {
            int pk = stats.getStat(Stats.CUSTOM.getOrCreateStat(Stats.PLAYER_KILLS));
            int mk = stats.getStat(Stats.CUSTOM.getOrCreateStat(Stats.MOB_KILLS));
            lines.add("总击杀: 玩家[" + pk + "] / 怪物[" + mk + "]");
        }

        if (showDeaths.get()) {
            lines.add("死亡次数: " + stats.getStat(Stats.CUSTOM.getOrCreateStat(Stats.DEATHS)));
        }

        // ================== 挖掘总数修复 ==================
        if (showBreaks.get()) {
            // 调用之前缓存好的数据
            lines.add("挖掘总数: " + cachedBlocksMined);
        }

        return lines;
    }

    private void renderBackground(double x, double y, double w, double h) {
        Theme t = theme.get();
        if (t == Theme.简约现代风) Renderer2D.COLOR.quad(x, y, w, h, new Color(20, 20, 20, 160));
        else if (t == Theme.简约苹果风) Renderer2D.COLOR.quad(x, y, w, h, new Color(245, 245, 247, 210));
        else if (t == Theme.黑客帝国) {
            Renderer2D.COLOR.quad(x, y, w, h, new Color(0, 15, 0, 230));
            for (Particle p : particles) {
                // 利用 % 取余将粒子严格限制在手动设定的宽高范围内
                double px = p.x % w;
                double py = p.y % h;
                Renderer2D.COLOR.quad(x + px, y + py, 1.5, 5.0, new Color(0, 255, 70, 150));
            }
        } else if (t == Theme.飘雪) {
            Renderer2D.COLOR.quad(x, y, w, h, new Color(25, 30, 45, 180));
            for (Particle p : particles) {
                double px = p.x % w;
                double py = p.y % h;
                Renderer2D.COLOR.quad(x + px, y + py, 2.0, 2.0, new Color(255, 255, 255, 180));
            }
        } else if (t == Theme.合成器浪潮) {
            Renderer2D.COLOR.quad(x, y, w, h, new Color(45, 0, 70, 200), new Color(0, 180, 255, 200), new Color(0, 180, 255, 200), new Color(45, 0, 70, 200));
        }
    }

    private Color getThemeTextColor() {
        Theme t = theme.get();
        if (t == Theme.简约苹果风) return new Color(25, 25, 25);
        if (t == Theme.黑客帝国) return new Color(0, 255, 0);
        if (t == Theme.合成器浪潮) return new Color(255, 50, 200);
        return textColor.get();
    }

    private static class Particle {
        double x, y;
        Particle(double x, double y) { this.x = x; this.y = y; }
    }
}