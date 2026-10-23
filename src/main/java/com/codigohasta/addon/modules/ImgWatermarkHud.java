package com.codigohasta.addon.modules;

import com.codigohasta.addon.AddonTemplate;
import com.codigohasta.addon.utils.sakura.render.SakuraSkinRenderer;
import com.codigohasta.addon.utils.sakura.render.SakuraTextRenderer;
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
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.util.Identifier;

import java.util.List;

public class ImgWatermarkHud extends Module {

    private static final MinecraftClient mc = MinecraftClient.getInstance();

    private static final float DEFAULT_PANEL_HEIGHT = 13f;
 
    private static final float DEFAULT_PANEL3_GAP = 4f;
    private static final float AVATAR_TO_PANEL = 30f;
    private static final float PANEL1_WIDTH_PAD = 10f;
    private static final float PANEL2_WIDTH_PAD = 6f;
    private static final float PANEL3_WIDTH_PAD = 7f;
    private static final float DEFAULT_TEXT_PADDING = 3f;
    private static final float DEFAULT_PANEL_ROW_STEP = 17f;
    private static final float DEFAULT_CORNER_RADIUS = 3.5f;
    private static final int DEFAULT_HUD_X = 395;
    private static final int DEFAULT_HUD_Y = 418;
    private static final double DEFAULT_AVATAR_SIZE = 30.0;
    private static final double DEFAULT_AVATAR_OFFSET_X = -6.0;
    private static final double DEFAULT_AVATAR_OFFSET_Y = 0.0;
    private static final double DEFAULT_FONT_SIZE = 8.069230769230757;
    private static final double DEFAULT_SHADOW_FEATHER = 4.6292466765140325;
    private static final double DEFAULT_FWD_SPEED = 0.5950000000000021;
    private static final double DEFAULT_BWD_RATIO = 0.7569230769230775;
    private static final double DEFAULT_ANGLE_RANGE = 90.0;
    private static final double DEFAULT_HUD_SCALE = 0.85;
    private static final boolean DEFAULT_SHADOW_ENABLED = false;
    private static final int[] DEFAULT_SHADOW_COLOR = {255, 255, 255, 195};
    private static final int[] DEFAULT_NAME_COLOR_STATIC = {255, 0, 0, 255};
    private static final int[] DEFAULT_NAME_COLOR2 = {255, 42, 128, 255};

    private static final String MOD_ID = "invincible_machine_gun";
    public enum NameColorMode {
        Static,
        Gradient,
        Flow,
        Rainbow
    }

    // ─── 设置 ─────────────────────────────────────────────

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgName = settings.createGroup("名称颜色");

    private final Setting<Boolean> hudEnabled = sgGeneral.add(new BoolSetting.Builder()
        .name("水印").description("显示水印 HUD。").defaultValue(true).build());
    private final Setting<Integer> hudX = sgGeneral.add(new IntSetting.Builder()
        .name("X坐标").description("水印左上角 X（GUI 缩放后的逻辑坐标）。")
        .defaultValue(DEFAULT_HUD_X).sliderRange(0, 2000).build());
    private final Setting<Integer> hudY = sgGeneral.add(new IntSetting.Builder()
        .name("Y坐标").description("水印左上角 Y（GUI 缩放后的逻辑坐标）。")
        .defaultValue(DEFAULT_HUD_Y).sliderRange(0, 2000).build());
    private final Setting<Double> hudScale = sgGeneral.add(new DoubleSetting.Builder()
        .name("缩放").description("整体缩放。").defaultValue(DEFAULT_HUD_SCALE)
        .min(0.5).max(2.0).sliderRange(0.5, 2.0).build());
    private final Setting<Boolean> showAvatar = sgGeneral.add(new BoolSetting.Builder()
        .name("头像").description("显示玩家头像（双层皮肤）。").defaultValue(true).build());
    
    private final Setting<Double> avatarSize = sgGeneral.add(new DoubleSetting.Builder()
        .name("头像大小").description("头像外层边长（逻辑像素）。")
        .defaultValue(DEFAULT_AVATAR_SIZE).min(8.0).max(64.0).sliderRange(8.0, 64.0)
        .visible(() -> false).build());
    
    private final Setting<Double> avatarOffsetX = sgGeneral.add(new DoubleSetting.Builder()
        .name("头像X偏移").description("头像相对水印锚点的水平偏移（逻辑像素），用于对齐排版。")
        .defaultValue(DEFAULT_AVATAR_OFFSET_X).min(-60.0).max(60.0).sliderRange(-60.0, 60.0)
        .visible(() -> false).build());
    private final Setting<Double> avatarOffsetY = sgGeneral.add(new DoubleSetting.Builder()
        .name("头像Y偏移").description("头像相对水印锚点的垂直偏移（逻辑像素），用于对齐排版。")
        .defaultValue(DEFAULT_AVATAR_OFFSET_Y).min(-60.0).max(60.0).sliderRange(-60.0, 60.0)
        .visible(() -> false).build());
    private final Setting<Boolean> showPanel1 = sgGeneral.add(new BoolSetting.Builder()
        .name("插件名面板").description("显示「插件名称 + 版本号」面板。").defaultValue(true).build());
    private final Setting<Boolean> showPanel2 = sgGeneral.add(new BoolSetting.Builder()
        .name("玩家名面板").description("显示玩家名称面板。").defaultValue(true).build());
    private final Setting<Boolean> showPanel3 = sgGeneral.add(new BoolSetting.Builder()
        .name("服务器面板").description("显示当前服务器地址面板。").defaultValue(true).build());

    // ── 面板几何（已调好 → 隐藏；见上方「已隐藏」说明）──
    private final Setting<Double> panelHeight = sgGeneral.add(new DoubleSetting.Builder()
        .name("面板高度").description("三个子面板的高度。")
        .defaultValue(DEFAULT_PANEL_HEIGHT).min(6.0).max(40.0).sliderRange(6.0, 40.0)
        .visible(() -> false).build());
    private final Setting<Double> panelRadius = sgGeneral.add(new DoubleSetting.Builder()
        .name("面板圆角").description("圆角半径；等于高度一半时是胶囊形（会太圆）。")
        .defaultValue((double) DEFAULT_CORNER_RADIUS).min(0.0).max(20.0).sliderRange(0.0, 20.0).build());
    private final Setting<Double> fontSize = sgGeneral.add(new DoubleSetting.Builder()
        .name("字号").description("面板里的文字大小。")
        .defaultValue((double) DEFAULT_FONT_SIZE).min(5.0).max(20.0).sliderRange(5.0, 20.0).build());
    private final Setting<Double> textPadding = sgGeneral.add(new DoubleSetting.Builder()
        .name("文字左内边距").description("文字距面板左缘的距离。")
        .defaultValue(DEFAULT_TEXT_PADDING).min(0.0).max(20.0).sliderRange(0.0, 20.0)
        .visible(() -> false).build());
    private final Setting<Double> rowStep = sgGeneral.add(new DoubleSetting.Builder()
        .name("面板纵向间距").description("面板1 与面板2 的垂直步进（含面板高度）。")
        .defaultValue(DEFAULT_PANEL_ROW_STEP).min(4.0).max(60.0).sliderRange(4.0, 60.0)
        .visible(() -> false).build());
    private final Setting<Double> panelGapX = sgGeneral.add(new DoubleSetting.Builder()
        .name("面板横向间距").description("面板2 与面板3 之间的水平间隙。")
        .defaultValue(DEFAULT_PANEL3_GAP).min(0.0).max(40.0).sliderRange(0.0, 40.0)
        .visible(() -> false).build());

    private final Setting<SettingColor> panelColor = sgGeneral.add(new ColorSetting.Builder()
        .name("面板颜色").description("三个子面板的背景色。")
        .defaultValue(new SettingColor(0, 0, 0, 120)).build());
    private final Setting<Boolean> panelBlur = sgGeneral.add(new BoolSetting.Builder()
        .name("背景模糊").description("面板后方的圆角背景模糊。").defaultValue(true).build());
    private final Setting<Double> blurStrength = sgGeneral.add(new DoubleSetting.Builder()
        .name("模糊强度").description("越大越糊。").defaultValue(8.0).min(1.0).max(20.0)
        .sliderRange(1.0, 20.0).visible(panelBlur::get).build());

    // ── 阴影 ──
    private final Setting<Boolean> shadowEnabled = sgGeneral.add(new BoolSetting.Builder()
        .name("阴影").description("在面板边缘绘制向外羽化的投影。")
        .defaultValue(DEFAULT_SHADOW_ENABLED).build());
    private final Setting<SettingColor> shadowColor = sgGeneral.add(new ColorSetting.Builder()
        .name("阴影颜色")
        .defaultValue(new SettingColor(DEFAULT_SHADOW_COLOR[0], DEFAULT_SHADOW_COLOR[1],
            DEFAULT_SHADOW_COLOR[2], DEFAULT_SHADOW_COLOR[3]))
        .visible(shadowEnabled::get).build());
    private final Setting<Double> shadowFeather = sgGeneral.add(new DoubleSetting.Builder()
        .name("阴影羽化").defaultValue((double) DEFAULT_SHADOW_FEATHER)
        .min(1.0).max(40.0).sliderRange(1.0, 40.0)
        .visible(shadowEnabled::get).build());
    private final Setting<Double> shadowOffsetX = sgGeneral.add(new DoubleSetting.Builder()
        .name("阴影X偏移").defaultValue(0.0).min(-20.0).max(20.0).sliderRange(-20.0, 20.0)
        .visible(shadowEnabled::get).build());
    private final Setting<Double> shadowOffsetY = sgGeneral.add(new DoubleSetting.Builder()
        .name("阴影Y偏移").defaultValue(0.0).min(-20.0).max(20.0).sliderRange(-20.0, 20.0)
        .visible(shadowEnabled::get).build());

    // ── 名称颜色（只作用于面板1 的插件名称）──
    private final Setting<NameColorMode> nameColorMode = sgName.add(new EnumSetting.Builder<NameColorMode>()
        .name("颜色模式").description("插件名称的着色方式。")
        .defaultValue(NameColorMode.Flow).build());
    
    private final Setting<SettingColor> nameColorStatic = sgName.add(new ColorSetting.Builder()
        .name("单色颜色").description("「单色」模式下插件名称的颜色。只影响单色模式。")
        .defaultValue(new SettingColor(DEFAULT_NAME_COLOR_STATIC[0], DEFAULT_NAME_COLOR_STATIC[1],
            DEFAULT_NAME_COLOR_STATIC[2], DEFAULT_NAME_COLOR_STATIC[3]))
        .visible(() -> nameColorMode.get() == NameColorMode.Static).build());
    private final Setting<SettingColor> nameColor1 = sgName.add(new ColorSetting.Builder()
        .name("颜色1").description("「渐变」/「流动」模式的起始色。")
        .defaultValue(new SettingColor(255, 255, 255, 255))
        .visible(() -> nameColorMode.get() == NameColorMode.Gradient
            || nameColorMode.get() == NameColorMode.Flow).build());
    private final Setting<SettingColor> nameColor2 = sgName.add(new ColorSetting.Builder()
        .name("颜色2").description("渐变/流动模式的结束色。")
        .defaultValue(new SettingColor(DEFAULT_NAME_COLOR2[0], DEFAULT_NAME_COLOR2[1],
            DEFAULT_NAME_COLOR2[2], DEFAULT_NAME_COLOR2[3]))
        .visible(() -> nameColorMode.get() == NameColorMode.Gradient
            || nameColorMode.get() == NameColorMode.Flow).build());
    private final Setting<Double> rainbowSaturation = sgName.add(new DoubleSetting.Builder()
        .name("彩虹饱和度").defaultValue(0.8).min(0.0).max(1.0).sliderRange(0.0, 1.0)
        .visible(() -> nameColorMode.get() == NameColorMode.Rainbow).build());

    // ── 流动算法（参数与「流动算法演示文件.html」一一对应）──
    private final Setting<Boolean> waveCycle = sgName.add(new BoolSetting.Builder()
        .name("海浪流动")
        .description("周期流动：正向 sin 加速→逆向倒流→静止变向，像海浪涌上来又退回去。")
        .defaultValue(true).visible(() -> nameColorMode.get() == NameColorMode.Flow).build());
    private final Setting<Double> flowAngle = sgName.add(new DoubleSetting.Builder()
        .name("流动角度")
        .description("色带方向（度）。0 = 从左到右，90 = 从上到下。")
        .defaultValue(0.0).min(-180.0).max(180.0).sliderRange(-180.0, 180.0)
        .visible(() -> nameColorMode.get() == NameColorMode.Flow).build());
    private final Setting<Double> fwdSpeed = sgName.add(new DoubleSetting.Builder()
        .name("正向峰值速度").description("对应演示文件的「正向峰值速度」。")
        .defaultValue((double) DEFAULT_FWD_SPEED).min(0.1).max(10.0).sliderRange(0.1, 10.0)
        .visible(() -> flowSettingsVisible()).build());
    private final Setting<Double> bwdRatio = sgName.add(new DoubleSetting.Builder()
        .name("逆向速度倍率").description("对应演示文件的「逆向速度倍率」。")
        .defaultValue((double) DEFAULT_BWD_RATIO).min(0.0).max(1.5).sliderRange(0.0, 1.5)
        .visible(() -> flowSettingsVisible()).build());
    private final Setting<Double> durForward = sgName.add(new DoubleSetting.Builder()
        .name("正向时长").description("正向流动持续多少秒。")
        .defaultValue(5.0).min(0.5).max(8.0).sliderRange(0.5, 8.0)
        .visible(() -> flowSettingsVisible()).build());
    private final Setting<Double> durBackward = sgName.add(new DoubleSetting.Builder()
        .name("逆向时长").description("逆向回流持续多少秒。")
        .defaultValue(5.0).min(0.5).max(8.0).sliderRange(0.5, 8.0)
        .visible(() -> flowSettingsVisible()).build());
    private final Setting<Double> durTurn = sgName.add(new DoubleSetting.Builder()
        .name("变向时长").description("色带静止、方向平滑旋转用多少秒。")
        .defaultValue(0.7).min(0.1).max(3.0).sliderRange(0.1, 3.0)
        .visible(() -> flowSettingsVisible()).build());
    private final Setting<Double> angleRange = sgName.add(new DoubleSetting.Builder()
        .name("角度范围").description("每次换向时随机到的最大角度（度）。")
        .defaultValue((double) DEFAULT_ANGLE_RANGE).min(10.0).max(90.0).sliderRange(10.0, 90.0)
        .visible(() -> flowSettingsVisible()).build());
    private final Setting<Double> bandScale = sgName.add(new DoubleSetting.Builder()
        .name("色带宽度").description("一个完整色带的宽度（相对文字宽度的比例）。")
        .defaultValue(0.74).min(0.30).max(1.50).sliderRange(0.30, 1.50)
        .visible(() -> flowSettingsVisible()).build());
    private final Setting<Boolean> randomAngle = sgName.add(new BoolSetting.Builder()
        .name("随机换向").description("每次变向时随机挑一个新角度；关闭则回到「流动角度」。")
        .defaultValue(true).visible(() -> flowSettingsVisible()).build());

    // ─── 流动状态（对应演示文件的 flowPhase / phase / angle）──

    
    private enum FlowStage { Forward, Backward, Turn }

    private FlowStage flowStage = FlowStage.Forward;
    private float flowStageTimer;
    private double flowPhase;         
    private double flowAngleStart;    
    private double flowAngleTarget;  
    private double flowAngleCurrent;   
    private float lastFrameDelta = 1f / 60f;
    private boolean flowInitialized;

    
    private static final float BASE_PX = 240f;

    
    private boolean flowSettingsVisible() {
        return nameColorMode.get() == NameColorMode.Flow && waveCycle.get();
    }

    
    private String cachedVersion;

    public ImgWatermarkHud() {
        super(AddonTemplate.IMG_HUD_CATEGORY, "img-watermark",
            "没敌机关枪的水印hud组件。");
    }

    // ─── 渲染 ─────────────────────────────────────────────

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        if (!hudEnabled.get()) return;
        if (mc.player == null) return;
        float dt = (float) event.frameTime;
        lastFrameDelta = (dt > 0f && dt < 0.5f) ? dt : 1f / 60f;
        updateFlow(lastFrameDelta);

        float s = hudScale.get().floatValue();
        float x = hudX.get();
        float y = hudY.get();
        String version = currentVersion();
        String nameText = "Invincible Machine Gun";
        String versionText = "v" + version;
        String playerName = mc.player.getName().getString();
        String serverText = serverAddress();

        float scale = s;
        float panelH = panelHeight.get().floatValue() * scale;
        float fontPx = fontSize.get().floatValue() * scale;
        float radius = Math.max(0f, Math.min(panelRadius.get().floatValue() * scale, panelH / 2f));

        float nameW = (float) SakuraTextRenderer.getWidth(nameText, fontPx);
        float versionW = (float) SakuraTextRenderer.getWidth(versionText, fontPx);
        float playerW = (float) SakuraTextRenderer.getWidth(playerName, fontPx);
        float serverW = (float) SakuraTextRenderer.getWidth(serverText, fontPx);

        float pad = textPadding.get().floatValue() * scale;
        float spacing = 3f * scale; 
        float p1W = nameW + spacing + versionW + PANEL1_WIDTH_PAD * scale;
        float p2W = playerW + PANEL2_WIDTH_PAD * scale;
        float p3W = serverW + PANEL3_WIDTH_PAD * scale;

        float avatarPx = avatarSize.get().floatValue() * scale;
        float avatarX = x + (1f + avatarOffsetX.get().floatValue()) * scale;
        float avatarY = y + (-1f + avatarOffsetY.get().floatValue()) * scale;
        float panelX = x + AVATAR_TO_PANEL * scale;
        float panel1Y = y;
        float panel2Y = y + rowStep.get().floatValue() * scale;
        float panel3X = panelX + p2W + panelGapX.get().floatValue() * scale;
        float panel3Y = panel2Y;

        if (panelBlur.get()) {
            float bs = blurStrength.get().floatValue();
            java.awt.Color pc = toAwt(panelColor.get());
            if (showPanel1.get()) {
                Shader2DUtil.drawRoundedBlur(panelX, panel1Y, p1W, panelH, radius, pc, bs, 0.9f);
            }
            if (showPanel2.get()) {
                Shader2DUtil.drawRoundedBlur(panelX, panel2Y, p2W, panelH, radius, pc, bs, 0.9f);
            }
            if (showPanel3.get()) {
                Shader2DUtil.drawRoundedBlur(panel3X, panel3Y, p3W, panelH, radius, pc, bs, 0.9f);
            }
        }

        // ── 阴影：模糊之后、面板之前（阴影只画面板外侧，顺序理由见 SakuraTargetHud）──
        if (shadowEnabled.get()) {
            SettingColor sc = shadowColor.get();
            Color shadow = new Color(sc.r, sc.g, sc.b, sc.a);
            float feather = shadowFeather.get().floatValue();
            float offX = shadowOffsetX.get().floatValue();
            float offY = shadowOffsetY.get().floatValue();
            if (showPanel1.get()) {
                Shader2DUtil.drawRoundedShadow(panelX, panel1Y, p1W, panelH, radius,
                    shadow, feather, offX, offY);
            }
            if (showPanel2.get()) {
                Shader2DUtil.drawRoundedShadow(panelX, panel2Y, p2W, panelH, radius,
                    shadow, feather, offX, offY);
            }
            if (showPanel3.get()) {
                Shader2DUtil.drawRoundedShadow(panel3X, panel3Y, p3W, panelH, radius,
                    shadow, feather, offX, offY);
            }
        }

        Color bg = toMeteor(panelColor.get());
        if (showPanel1.get()) {
            Shader2DUtil.drawRoundedRect(panelX, panel1Y, p1W, panelH, radius, bg, 0f, null);
        }
        if (showPanel2.get()) {
            Shader2DUtil.drawRoundedRect(panelX, panel2Y, p2W, panelH, radius, bg, 0f, null);
        }
        if (showPanel3.get()) {
            Shader2DUtil.drawRoundedRect(panel3X, panel3Y, p3W, panelH, radius, bg, 0f, null);
        }

        // ── 头像（双层皮肤，复用现成方法）──
        if (showAvatar.get() && mc.player instanceof AbstractClientPlayerEntity player) {
            Identifier skin = null;
            try {
                skin = player.getSkin().body().texturePath();
            } catch (Throwable ignored) {
                skin = null;
            }
            boolean drawn = skin != null
                // radius=0 表示不裁剪：轮廓由贴图 alpha 决定，外层透明处透出内层，
                // 效果就是把整个头抠出来
                && SakuraSkinRenderer.drawPlayerHeadLayered(skin, avatarX, avatarY, avatarPx, 0f, 1f);
            if (!drawn) {
                Shader2DUtil.drawRoundedRect(avatarX, avatarY, avatarPx, avatarPx, 2f * scale,
                    new Color(70, 70, 70, 255), 0f, null);
            }
        }

        SakuraTextRenderer.begin();
        try {
            float offsetY = Math.max(0f, (panelH - fontPx) / 2f);
            float textY1 = panel1Y + offsetY;
            float textY23 = panel2Y + offsetY;

            if (showPanel1.get()) {
                float tx = panelX + pad;
                int white = argb(255, 255, 255, 255);
                switch (nameColorMode.get()) {
                    case Static -> SakuraTextRenderer.draw(nameText, tx, textY1, fontPx,
                        argbOf(nameColorStatic.get()));
                    case Gradient -> SakuraTextRenderer.drawGradient(nameText, tx, textY1, fontPx,
                        argbOf(nameColor1.get()), argbOf(nameColor2.get()));
                    case Flow -> drawFlowName(nameText, tx, textY1, fontPx, nameW);
                    case Rainbow -> {
                        float sat = rainbowSaturation.get().floatValue();
                        float lit = (System.currentTimeMillis() % 100000L) / 1000f * 0.25f;
                        SakuraTextRenderer.drawShaded(nameText, tx, textY1, fontPx,
                            (lx, ly) -> {
                                float hue = (lx / Math.max(1f, nameW) + lit) % 1f;
                                return hsbArgb(hue, sat, 1f, 255);
                            });
                    }
                }
                SakuraTextRenderer.draw(versionText, tx + nameW + spacing, textY1, fontPx, white);
            }
            if (showPanel2.get()) {
                SakuraTextRenderer.draw(playerName, panelX + pad, textY23, fontPx,
                    argb(255, 255, 255, 255));
            }
            if (showPanel3.get()) {
                SakuraTextRenderer.draw(serverText, panel3X + pad, textY23, fontPx,
                    argb(255, 255, 255, 255));
            }
        } finally {
            SakuraTextRenderer.end();
        }
    }

    
    private void drawFlowName(String text, float tx, float ty, float size, float textWidth) {
        int c1 = argbOf(nameColor1.get());
        int c2 = argbOf(nameColor2.get());
        float band = Math.max(20f, textWidth * bandScale.get().floatValue());
        double angle = waveCycle.get() ? flowAngleCurrent : Math.toRadians(flowAngle.get());
        double phase = waveCycle.get()
            ? flowPhase
            : (System.currentTimeMillis() % 100000L) / 1000.0 * BASE_PX * 0.5;

        float cos = (float) Math.cos(angle);
        float sin = (float) Math.sin(angle);

        SakuraTextRenderer.drawShaded(text, tx, ty, size, (lx, ly) -> {
            double proj = lx * cos + ly * sin + phase;
            double u = proj / band;
            double frac = u - Math.floor(u);
            float t = (float) (1.0 - Math.abs(2.0 * frac - 1.0));
            return lerpArgb(c1, c2, t);
        });
    }

    // ─── 内容 ─────────────────────────────────────────────

    
    private String currentVersion() {
        if (cachedVersion != null) return cachedVersion;
        String v = "?";
        try {
            List<net.fabricmc.loader.api.ModContainer> found =
                FabricLoader.getInstance().getModContainer(MOD_ID).map(List::of).orElse(List.of());
            if (!found.isEmpty()) {
                v = found.get(0).getMetadata().getVersion().getFriendlyString();
            }
        } catch (Throwable ignored) {
        }
        cachedVersion = v;
        return v;
    }

    
    private String serverAddress() {
        if (mc.isInSingleplayer()) return "单人游戏";
        if (mc.getCurrentServerEntry() != null) return mc.getCurrentServerEntry().address;
        return "单人游戏";
    }

    // ─── 流动状态机（对应演示文件 html 的 frame()）──────────

    
    private void updateFlow(float dt) {
        if (!flowInitialized) {
            flowInitialized = true;
            flowAngleCurrent = Math.toRadians(flowAngle.get());
        }

        // 不使用海浪周期：色带恒速单向走，角度直接跟随设置
        if (!waveCycle.get()) {
            flowPhase += dt * BASE_PX * 0.5;
            flowAngleCurrent = Math.toRadians(flowAngle.get());
            return;
        }

        flowStageTimer += dt;

        switch (flowStage) {
            case Forward -> {
                if (flowStageTimer >= durForward.get()) {
                    flowStage = FlowStage.Backward;
                    flowStageTimer = 0f;
                }
            }
            case Backward -> {
                if (flowStageTimer >= durBackward.get()) {
                    flowStage = FlowStage.Turn;
                    flowStageTimer = 0f;
                    flowAngleStart = flowAngleCurrent;
                    if (randomAngle.get()) {
                        double maxRad = Math.toRadians(angleRange.get());
                        flowAngleTarget = (Math.random() * 2 - 1) * maxRad;
                    } else {
                        flowAngleTarget = Math.toRadians(flowAngle.get());
                    }
                }
            }
            case Turn -> {
                if (flowStageTimer >= durTurn.get()) {
                    flowStage = FlowStage.Forward;
                    flowStageTimer = 0f;
                    flowAngleCurrent = flowAngleTarget;
                }
            }
        }

        // 速度（对应演示文件的「速度」段）
        double speed;
        switch (flowStage) {
            case Forward -> {
                double p = durForward.get() > 0 ? flowStageTimer / durForward.get() : 1.0;
                speed = BASE_PX * fwdSpeed.get() * Math.sin(Math.min(1.0, p) * Math.PI);
            }
            case Backward -> {
                double p = durBackward.get() > 0 ? flowStageTimer / durBackward.get() : 1.0;
                speed = -BASE_PX * fwdSpeed.get() * bwdRatio.get() * Math.sin(Math.min(1.0, p) * Math.PI);
            }
            default -> speed = 0.0;
        }
        flowPhase += dt * speed;
        if (flowStage == FlowStage.Turn) {
            double p = durTurn.get() > 0 ? Math.min(1.0, flowStageTimer / durTurn.get()) : 1.0;
            double s = p * p * (3 - 2 * p);
            flowAngleCurrent = flowAngleStart + (flowAngleTarget - flowAngleStart) * s;
        }
    }

    // ─── 颜色工具 ─────────────────────────────────────────

    
    private static int hsbArgb(float h, float s, float b, int a) {
        int rgb = java.awt.Color.HSBtoRGB(h, s, b);
        return argb((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, a);
    }

    
    private static int lerpArgb(int c1, int c2, float t) {
        int a1 = (c1 >>> 24), r1 = (c1 >> 16) & 0xFF, g1 = (c1 >> 8) & 0xFF, b1 = c1 & 0xFF;
        int a2 = (c2 >>> 24), r2 = (c2 >> 16) & 0xFF, g2 = (c2 >> 8) & 0xFF, b2 = c2 & 0xFF;
        int a = Math.round(a1 + (a2 - a1) * t);
        int r = Math.round(r1 + (r2 - r1) * t);
        int g = Math.round(g1 + (g2 - g1) * t);
        int b = Math.round(b1 + (b2 - b1) * t);
        return argb(r, g, b, a);
    }

    private static int argbOf(SettingColor c) {
        return argb(c.r, c.g, c.b, c.a);
    }

    private static int argb(int r, int g, int b, int a) {
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static java.awt.Color toAwt(SettingColor c) {
        return new java.awt.Color(c.r, c.g, c.b, c.a);
    }

    private static Color toMeteor(SettingColor c) {
        return new Color(c.r, c.g, c.b, c.a);
    }
}
