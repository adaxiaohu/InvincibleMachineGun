package com.codigohasta.addon.modules;

import com.codigohasta.addon.AddonTemplate;
import com.codigohasta.addon.utils.sakura.render.Shader2DUtil;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.MathHelper;

public class CrosshairHud extends Module {

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgColor = settings.createGroup("颜色");
    private final SettingGroup sgMotion = settings.createGroup("动态");
    private final SettingGroup sgEffect = settings.createGroup("效果");

    public enum Style {
        十字,
        十字带点,
        点,
        圆环
    }

    private final Setting<Style> style = sgGeneral.add(new EnumSetting.Builder<Style>()
        .name("样式").description("准星的形状。").defaultValue(Style.十字).build());

    
    private final Setting<Double> gap = sgGeneral.add(new DoubleSetting.Builder()
        .name("间隙").description("中心到每条线起点的距离。")
        .defaultValue(1.98).min(0.0).max(40.0).sliderRange(0.0, 40.0).build());

    
    private final Setting<Double> length = sgGeneral.add(new DoubleSetting.Builder()
        .name("线长").description("每条准星线的长度。")
        .defaultValue(2.27).min(1.0).max(40.0).sliderRange(1.0, 40.0).build());

    
    private final Setting<Double> thickness = sgGeneral.add(new DoubleSetting.Builder()
        .name("线粗").description("准星线的粗细。")
        .defaultValue(0.5).min(0.5).max(10.0).sliderRange(0.5, 10.0).build());

    
    private final Setting<Double> dotSize = sgGeneral.add(new DoubleSetting.Builder()
        .name("中心点大小").description("中心圆点的直径。")
        .defaultValue(2.60).min(1.0).max(24.0).sliderRange(1.0, 24.0)
        .visible(() -> style.get() == Style.点 || style.get() == Style.十字带点).build());

    
    private final Setting<Double> ringSize = sgGeneral.add(new DoubleSetting.Builder()
        .name("圆环直径").description("圆环的外径。")
        .defaultValue(4.0).min(4.0).max(60.0).sliderRange(4.0, 60.0)
        .visible(() -> style.get() == Style.圆环).build());

    // ══════════════ 动态 ══════════════

    
    private final Setting<Boolean> attackBounce = sgMotion.add(new BoolSetting.Builder()
        .name("攻击跳动").description("攻击/命中时准星向外扩张再回弹。")
        .defaultValue(true).build());
    
    private final Setting<Double> bounceAmount = sgMotion.add(new DoubleSetting.Builder()
        .name("跳动幅度").description("扩张的最大额外间隙。")
        .defaultValue(4.95).min(1.0).max(30.0).sliderRange(1.0, 30.0)
        .visible(attackBounce::get).build());
    
    private final Setting<Double> bounceSpeed = sgMotion.add(new DoubleSetting.Builder()
        .name("回弹速度").description("跳动后回到原位有多快。越大越快。")
        .defaultValue(7.85).min(1.0).max(40.0).sliderRange(1.0, 40.0)
        .visible(attackBounce::get).build());

    
    private final Setting<Boolean> viewLag = sgMotion.add(new BoolSetting.Builder()
        .name("视角滞后").description("快速转动视角时准星向反方向偏移，停下后弹回中心。")
        .defaultValue(true).build());
    
    private final Setting<Double> lagStrength = sgMotion.add(new DoubleSetting.Builder()
        .name("滞后强度").description("每度视角变化产生的偏移量。越大越明显。")
        .defaultValue(0.5).min(0.0).max(3.0).sliderRange(0.0, 3.0)
        .visible(viewLag::get).build());
    
    private final Setting<Double> lagReturn = sgMotion.add(new DoubleSetting.Builder()
        .name("回中速度").description("偏移回到中心有多快。越大越快。")
        .defaultValue(16.52).min(1.0).max(40.0).sliderRange(1.0, 40.0)
        .visible(viewLag::get).build());
    
    private final Setting<Double> lagMax = sgMotion.add(new DoubleSetting.Builder()
        .name("最大偏移").description("滞后偏移的上限，防止准星飞出屏幕。")
        .defaultValue(28.0).min(2.0).max(120.0).sliderRange(2.0, 120.0)
        .visible(viewLag::get).build());

    // ══════════════ 颜色 ══════════════

    
    private final Setting<SettingColor> colorInner = sgColor.add(new ColorSetting.Builder()
        .name("内圈颜色").description("靠近中心的颜色。")
        .defaultValue(new SettingColor(255, 255, 255, 255)).build());
    
    private final Setting<SettingColor> colorOuter = sgColor.add(new ColorSetting.Builder()
        .name("外圈颜色").description("线末端的颜色（与内圈形成渐变）。")
        .defaultValue(new SettingColor(255, 48, 175, 235)).build());
    
    private final Setting<Boolean> gradient = sgColor.add(new BoolSetting.Builder()
        .name("渐变").description("从内圈颜色渐变到外圈颜色。关掉则只用内圈颜色。")
        .defaultValue(false).build());

    // ══════════════ 效果 ══════════════

    
    private final Setting<Boolean> rounded = sgEffect.add(new BoolSetting.Builder()
        .name("圆角").description("线段两端做圆角")
        .defaultValue(false).build());
    
    private final Setting<Boolean> glow = sgEffect.add(new BoolSetting.Builder()
        .name("发光").description("准星四周加一层柔和光晕（shadow 着色器）。")
        .defaultValue(false).build());
    
    private final Setting<Double> glowSize = sgEffect.add(new DoubleSetting.Builder()
        .name("发光大小").description("光晕的羽化半径。")
        .defaultValue(6.0).min(1.0).max(24.0).sliderRange(1.0, 24.0)
        .visible(glow::get).build());
    
    private final Setting<Integer> glowAlpha = sgEffect.add(new IntSetting.Builder()
        .name("发光强度").description("光晕的不透明度（0~255）。")
        .defaultValue(255).min(10).max(255).sliderRange(10, 255)
        .visible(glow::get).build());
    
    private final Setting<Boolean> flow = sgEffect.add(new BoolSetting.Builder()
        .name("流动渐变").description("颜色沿准星流动（用渐变着色器）。")
        .defaultValue(false).build());
    
    private final Setting<Double> flowSpeed = sgEffect.add(new DoubleSetting.Builder()
        .name("流动速度").description("色带流动的快慢。")
        .defaultValue(0.154).min(0.05).max(4.0).sliderRange(0.05, 4.0)
        .visible(flow::get).build());
    
    private final Setting<Boolean> glitch = sgEffect.add(new BoolSetting.Builder()
        .name("故障效果").description("受击/攻击瞬间出现 RGB 错位与扫描线。")
        .defaultValue(false).build());
    
    private final Setting<Double> glitchTime = sgEffect.add(new DoubleSetting.Builder()
        .name("故障时长").description("每次触发后持续多久（秒）。")
        .defaultValue(0.22).min(0.05).max(2.0).sliderRange(0.05, 2.0)
        .visible(glitch::get).build());

    // ══════════════ 运行时状态 ══════════════

    
    private float bounceValue;
    
    private float lagX, lagY;
    
    private float lastYaw, lastPitch;
    
    private boolean haveLastAngle;
    
    private float glitchLeft;
    
    private float flowPhase;

    public CrosshairHud() {
        super(AddonTemplate.IMG_HUD_CATEGORY, "crosshair-hud",
            "把你屏幕上的准星替换成特异的准心。有些功能坏的，懒得做哈哈。");
    }

    @Override
    public void onActivate() {
        bounceValue = 0f;
        lagX = lagY = 0f;
        haveLastAngle = false;
        glitchLeft = 0f;
        flowPhase = 0f;
    }

    
    @EventHandler
    private void onAttack(meteordevelopment.meteorclient.events.entity.player.AttackEntityEvent event) {
        if (attackBounce.get()) bounceValue = 1f;
        if (glitch.get()) glitchLeft = glitchTime.get().floatValue();
    }

    
    @EventHandler
    private void onRender2D(Render2DEvent event) {
        if (!isActive()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return;
        if (mc.options != null && mc.options.hudHidden) return;

        float dt = (float) event.frameTime;
        if (dt <= 0f || dt > 0.5f) dt = 1f / 60f;

        advance(dt, mc);
        draw(mc);
    }

    
    private void advance(float dt, MinecraftClient mc) {
        if (bounceValue > 0.001f) {
            bounceValue *= (float) Math.exp(-bounceSpeed.get() * dt);
            if (bounceValue < 0.001f) bounceValue = 0f;
        }

        // ── 视角滞后 ──
        if (viewLag.get()) {
            float yaw = mc.player.getYaw();
            float pitch = mc.player.getPitch();
            if (haveLastAngle) {
                float dYaw = MathHelper.wrapDegrees(yaw - lastYaw);
                float dPitch = pitch - lastPitch;
                float s = lagStrength.get().floatValue();
                lagX -= dYaw * s;
                lagY -= dPitch * s;
            }
            lastYaw = yaw;
            lastPitch = pitch;
            haveLastAngle = true;

            // 回中
            float k = (float) Math.exp(-lagReturn.get() * dt);
            lagX *= k;
            lagY *= k;

            // 钳制
            float lim = lagMax.get().floatValue();
            lagX = MathHelper.clamp(lagX, -lim, lim);
            lagY = MathHelper.clamp(lagY, -lim, lim);
        } else {
            lagX = lagY = 0f;
            haveLastAngle = false;
        }

        // ── 故障倒计时 ──
        if (glitchLeft > 0f) {
            glitchLeft = Math.max(0f, glitchLeft - dt);
        }

        // ── 流动相位 ──
        if (flow.get()) {
            flowPhase = (flowPhase + dt * flowSpeed.get().floatValue()) % 1f;
        }
    }

    
    private void draw(MinecraftClient mc) {
        float cx = mc.getWindow().getScaledWidth() / 2f + lagX;
        float cy = mc.getWindow().getScaledHeight() / 2f + lagY;

        float g = gap.get().floatValue()
            + (attackBounce.get() ? bounceValue * bounceAmount.get().floatValue() : 0f);
        float len = length.get().floatValue();
        float th = thickness.get().floatValue();

        SettingColor ci = colorInner.get();
        SettingColor co = gradient.get() ? colorOuter.get() : colorInner.get();
        Style st = style.get();
        float half = Math.max(th, Math.max(g + len, ringSize.get().floatValue() / 2f + th)) + 4f;
        float boxX = cx - half, boxY = cy - half;
        float boxW = half * 2f, boxH = half * 2f;

        if (st == Style.圆环) {
            drawRing(cx, cy, th, ci, co);
        } else {
            if (st != Style.点) {
                drawCrossLines(cx, cy, g, len, th, ci, co);
            }
            if (st == Style.点 || st == Style.十字带点) {
                drawDot(cx, cy, ci);
            }
        }
        if (glitch.get() && glitchLeft > 0f) {
            float k = glitchLeft / Math.max(0.01f, glitchTime.get().floatValue());  // 1 -> 0
            Shader2DUtil.drawGlitch(boxX, boxY, boxW, boxH, 0f,
                k, 3f, 0.35f, 0.5f, (float) (System.nanoTime() / 1.0e9));
        }
    }

    
    private void drawCrossLines(float cx, float cy, float g, float len, float th,
                                SettingColor ci, SettingColor co) {
        bar(cx, cy - g, 0f, -1f, th, len, ci, co);   // 上：内端在下、向外朝上
        bar(cx, cy + g, 0f, +1f, th, len, ci, co);   // 下
        bar(cx - g, cy, -1f, 0f, th, len, ci, co);   // 左
        bar(cx + g, cy, +1f, 0f, th, len, ci, co);   // 右
    }

    
    private void bar(float sx, float sy, float dx, float dy, float th, float len,
                     SettingColor ci, SettingColor co) {
        float r = rounded.get() ? th / 2f : 0f;
        boolean vertical = (dx == 0f);

        float bx, by, bw, bh;
        if (vertical) {
            bx = sx - th / 2f;
            by = dy > 0f ? sy : sy - len;
            bw = th;
            bh = len;
        } else {
            bx = dx > 0f ? sx : sx - len;
            by = sy - th / 2f;
            bw = len;
            bh = th;
        }

        if (!flow.get() && !gradient.get()) {
            Shader2DUtil.drawRoundedRect(bx, by, bw, bh, r, ci, 0f, null);
            return;
        }

        final int SEG = 6;
        float seg = len / SEG;
        float segLen = seg + 0.5f;       
        for (int i = 0; i < SEG; i++) {
            float t = (i + 0.5f) / SEG;  
            float tt = t;
            if (flow.get()) {
                // 三角波，无缝循环
                float s = t + flowPhase;
                float f = s - (float) Math.floor(s);
                tt = 1f - Math.abs(2f * f - 1f);
            }
            Color col = lerp(ci, co, tt);
            float p = seg * i;
            if (vertical) {
                float yy = dy > 0f ? (sy + p) : (sy - p - seg);
                Shader2DUtil.drawRoundedRect(sx - th / 2f, yy, th, segLen, r, col, 0f, null);
            } else {
                float xx = dx > 0f ? (sx + p) : (sx - p - seg);
                Shader2DUtil.drawRoundedRect(xx, sy - th / 2f, segLen, th, r, col, 0f, null);
            }
        }
    }

    
    private void drawRing(float cx, float cy, float th, SettingColor ci, SettingColor co) {
        float d = ringSize.get().floatValue();
        float x = cx - d / 2f, y = cy - d / 2f;

        if (glow.get()) {
            glowBox(x, y, d, d, d / 2f, ci);
        }

        if (flow.get()) {
            Shader2DUtil.drawRoundedRectGradient(x, y, d, d, d / 2f, ci, co, flowPhase, 1f);
        } else {

            Color fill = new Color(0, 0, 0, 0);
            Shader2DUtil.drawRoundedRect(x, y, d, d, d / 2f, fill, th, ci);
        }
    }

    
    private void drawDot(float cx, float cy, SettingColor ci) {
        float d = Math.max(1f, dotSize.get().floatValue());
        float x = cx - d / 2f, y = cy - d / 2f;

        if (glow.get()) {
            glowBox(x, y, d, d, d / 2f, ci);
        }
        if (flow.get()) {
            Shader2DUtil.drawRoundedRectGradient(x, y, d, d, d / 2f, ci, colorOuter.get(), flowPhase, 1f);
        } else {
            Shader2DUtil.drawRoundedRect(x, y, d, d, d / 2f, ci, 0f, null);
        }
    }

    
    private void glowBox(float x, float y, float w, float h, float radius, SettingColor base) {
        Color halo = new Color(base.r, base.g, base.b, glowAlpha.get());
        Shader2DUtil.drawRoundedShadow(x, y, w, h, radius, halo,
            glowSize.get().floatValue(), 0f, 0f);
    }

    
    private static Color lerp(SettingColor a, SettingColor b, float t) {
        return new Color(
            Math.round(a.r + (b.r - a.r) * t),
            Math.round(a.g + (b.g - a.g) * t),
            Math.round(a.b + (b.b - a.b) * t),
            Math.round(a.a + (b.a - a.a) * t));
    }
}
