package com.codigohasta.addon.modules;

import com.codigohasta.addon.AddonTemplate;
import com.codigohasta.addon.utils.sakura.color.ColorUtil;
import com.codigohasta.addon.utils.sakura.hud.SakuraTargetResolver;
import com.codigohasta.addon.utils.sakura.render.ImgRenderPipelines;
import com.codigohasta.addon.utils.sakura.render.SakuraEntityPortrait;
import com.codigohasta.addon.utils.sakura.render.SakuraGlitchAnimation;
import com.codigohasta.addon.utils.sakura.render.SakuraSkinRenderer;
import com.codigohasta.addon.utils.sakura.render.SakuraTextRenderer;
import com.codigohasta.addon.utils.sakura.render.Shader2DUtil;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.renderer.MeshBuilder;
import meteordevelopment.meteorclient.renderer.MeshRenderer;
import meteordevelopment.meteorclient.renderer.MeteorRenderPipelines;
import meteordevelopment.meteorclient.renderer.Texture;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.NametagUtils;
import meteordevelopment.meteorclient.utils.render.RenderUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.List;
//这是第一个hud模块，工具类以及本模块注释保留，可供学习参考。
public class SakuraTargetHud extends Module {

    private static final MinecraftClient mc = MinecraftClient.getInstance();

    public enum ColorMode {
        Rainbow,
        Wave,
        Custom
    }

    /** 血量数值的显示方式。 */
    public enum HpDisplay {
        /** 只显示具体血量，如 16.5 */
        Health,
        /** 只显示百分比，如 82% */
        Percentage,
        /** 同时显示，如 16.5/20 */
        Both
    }

    /** 血量条配色方式。 */
    public enum HealthColorMode {
        /** 随血量变化：绿 → 黄 → 红 */
        Gradient,
        /** 使用自定义颜色 */
        Custom,
        /** 双色渐变 + 流动（颜色1 → 颜色2 在血条上滚动） */
        Flow
    }

    /** 贴图（原版为 {@code sakura:textures/particles/...}，命名空间已换成本项目）。 */
    private static final Identifier TARGET_TEX =
        Identifier.of("invincible_machine_gun", "textures/particles/target.png");
    private static final Identifier TARGET1_TEX =
        Identifier.of("invincible_machine_gun", "textures/particles/target1.png");

    // 面板几何。高度留出「名字 / 血量条 / 物品 + 耐久」三行的空间，
    // 物品行固定占位，这样开关显示内容时面板尺寸不会跳动。
    private static final float HUD_WIDTH = 160f;
    private static final float HUD_HEIGHT = 68f;
    private static final float RADIUS = 12f;
    private static final float AVATAR_RADIUS = 8f;
    private static final float AVATAR_SIZE = 40f;
    private static final float PADDING = 5f;

    /** 右侧文字列起点（头像右侧）。 */
    private static final float CONTENT_X = PADDING + AVATAR_SIZE + 8f;
    /** 名称 / 血量数值所在行的基线。 */
    private static final float NAME_Y = 17f;
    /** 名称与右侧血量数值之间至少保留的间距。 */
    private static final float NAME_HP_GAP = 6f;
    /** 血量条纵向位置与高度。 */
    private static final float BAR_Y = 38f;
    private static final float BAR_HEIGHT = 8f;
    /** 物品行顶部（含 16px 图标）。 */
    private static final float ITEM_Y = 49f;
    /** 物品行与同一行右侧距离文字之间至少保留的间距。 */
    private static final float ITEM_DIST_GAP = 6f;
    /** 耐久条距面板底部的留白。 */
    private static final float DURABILITY_RESERVE = 4f;

    /** 没有可用贴图时的头像占位色（固定值，不随背景色变化）。 */
    private static final java.awt.Color PLACEHOLDER_COLOR = new java.awt.Color(70, 70, 70, 255);
    private static final java.awt.Color BAR_BG_COLOR = new java.awt.Color(50, 50, 50, 200);

    /** 护甲槽位（不含主副手，那两个单独开关）。 */
    private static final EquipmentSlot[] ARMOR_SLOTS = {
        EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    // ─── 设置 ─────────────────────────────────────────────

    private final SettingGroup sgHud = settings.getDefaultGroup();
    private final SettingGroup sgShadow = settings.createGroup("阴影");
    private final SettingGroup sgElements = settings.createGroup("显示内容");
    private final SettingGroup sgEsp = settings.createGroup("透视");
    private final SettingGroup sgLock = settings.createGroup("准星锁定");
    private final SettingGroup sgMulti = settings.createGroup("多目标");
    private final SettingGroup sgGlitch = settings.createGroup("故障动画");
    private final SettingGroup sgColors = settings.createGroup("颜色");

    private final Setting<Boolean> hudEnabled = sgHud.add(new BoolSetting.Builder()
        .name("面板").description("显示目标信息面板。").defaultValue(true).build());
    private final Setting<Integer> hudX = sgHud.add(new IntSetting.Builder()
        .name("X坐标").description("面板左上角 X（GUI 缩放后的逻辑坐标）。")
        .defaultValue(595).sliderRange(0, 2000).build());
    private final Setting<Integer> hudY = sgHud.add(new IntSetting.Builder()
        .name("Y坐标").description("面板左上角 Y（GUI 缩放后的逻辑坐标）。")
        .defaultValue(385).sliderRange(0, 2000).build());
    private final Setting<Boolean> hudBlur = sgHud.add(new BoolSetting.Builder()
        .name("模糊").description("面板后方的圆角背景模糊。").defaultValue(true)
        .visible(hudEnabled::get).build());
    private final Setting<Double> hudBlurStrength = sgHud.add(new DoubleSetting.Builder()
        .name("模糊强度").defaultValue(20.0).min(1.0).max(20.0).sliderRange(1.0, 20.0)
        .visible(() -> hudEnabled.get() && hudBlur.get()).build());

    // ── 阴影 ──
    private final Setting<Boolean> shadowEnabled = sgShadow.add(new BoolSetting.Builder()
        .name("阴影")
        .description("在面板边缘绘制向外羽化的投影，让面板浮起来、更有立体感。")
        .defaultValue(true).visible(hudEnabled::get).build());
    private final Setting<SettingColor> shadowColor = sgShadow.add(new ColorSetting.Builder()
        .name("阴影颜色").description("默认中性灰；调低不透明度可让阴影更淡。")
        .defaultValue(new SettingColor(69, 69, 69, 170))
        .visible(() -> hudEnabled.get() && shadowEnabled.get()).build());
    private final Setting<Double> shadowFeather = sgShadow.add(new DoubleSetting.Builder()
        .name("羽化半径").description("阴影从面板边缘向外淡出的距离（逻辑像素）。越大越柔。")
        .defaultValue(10.0).min(1.0).max(40.0).sliderRange(1.0, 40.0)
        .visible(() -> hudEnabled.get() && shadowEnabled.get()).build());
    private final Setting<Double> shadowOffsetX = sgShadow.add(new DoubleSetting.Builder()
        .name("偏移X").description("阴影水平偏移（逻辑像素）。0 = 左右均匀。")
        .defaultValue(0.0).min(-20.0).max(20.0).sliderRange(-20.0, 20.0)
        .visible(() -> hudEnabled.get() && shadowEnabled.get()).build());
    private final Setting<Double> shadowOffsetY = sgShadow.add(new DoubleSetting.Builder()
        .name("偏移Y").description("阴影垂直偏移（逻辑像素，向下为正）。0 = 上下均匀；调大可模拟左上光源的投影。")
        .defaultValue(0.0).min(-20.0).max(20.0).sliderRange(-20.0, 20.0)
        .visible(() -> hudEnabled.get() && shadowEnabled.get()).build());

    // ── 多目标 ──
    private final Setting<Integer> maxTargets = sgMulti.add(new IntSetting.Builder()
        .name("最大目标数")
        .description("同时显示几个目标面板与锁定标记。只有「同时攻击多个目标」的 aura（Meteor KillAura、sfIMG Attack）才可能填满这个数量。")
        .defaultValue(1).min(1).max(8).sliderRange(1, 8).build());
    private final Setting<Double> panelSpacing = sgMulti.add(new DoubleSetting.Builder()
        .name("面板间距").description("多个面板之间垂直方向的间隔（逻辑像素）。")
        .defaultValue(6.0).min(0.0).max(40.0).sliderRange(0.0, 40.0)
        .visible(() -> maxTargets.get() > 1).build());
    private final Setting<Boolean> showDistance = sgMulti.add(new BoolSetting.Builder()
        .name("距离").description("显示自己与目标的距离。").defaultValue(true)
        .visible(hudEnabled::get).build());
    private final Setting<Boolean> marquee = sgMulti.add(new BoolSetting.Builder()
        .name("名称滚动")
        .description("名称过长时横向来回滚动播放，从而看清全名又不占位置。")
        .defaultValue(true).visible(hudEnabled::get).build());
    private final Setting<Double> marqueeSpeed = sgMulti.add(new DoubleSetting.Builder()
        .name("滚动速度").description("每秒滚动的像素数。")
        .defaultValue(28.0).min(5.0).max(120.0).sliderRange(5.0, 120.0)
        .visible(() -> hudEnabled.get() && marquee.get()).build());
    private final Setting<Double> marqueeGap = sgMulti.add(new DoubleSetting.Builder()
        .name("两端停顿")
        .description("滚到两端后各停留多久（秒），让人看清名称的开头与结尾。")
        .defaultValue(0.8).min(0.0).max(3.0).sliderRange(0.0, 3.0)
        .visible(() -> hudEnabled.get() && marquee.get()).build());
    private final Setting<Boolean> hurtParticles = sgMulti.add(new BoolSetting.Builder()
        .name("受伤粒子")
        .description("目标受到伤害时，从头像位置掉出粒子（替代旧的面板闪光）。")
        .defaultValue(true).visible(hudEnabled::get).build());
    private final Setting<SettingColor> hurtParticleColor = sgMulti.add(new ColorSetting.Builder()
        .name("粒子颜色").defaultValue(new SettingColor(255, 255, 255, 220))
        .visible(() -> hudEnabled.get() && hurtParticles.get()).build());
    private final Setting<Integer> hurtParticleCount = sgMulti.add(new IntSetting.Builder()
        .name("粒子数量").description("每次受伤喷出多少个粒子。")
        .defaultValue(10).min(1).max(60).sliderRange(1, 60)
        .visible(() -> hudEnabled.get() && hurtParticles.get()).build());
    private final Setting<Double> hurtParticleLife = sgMulti.add(new DoubleSetting.Builder()
        .name("粒子寿命").description("粒子存活多少秒。")
        .defaultValue(1.403220858895705).min(0.1).max(3.0).sliderRange(0.1, 3.0)
        .visible(() -> hudEnabled.get() && hurtParticles.get()).build());
    private final Setting<Double> hurtParticleSpeed = sgMulti.add(new DoubleSetting.Builder()
        .name("粒子速度").description("粒子飞散的初速度（逻辑像素/秒）。")
        .defaultValue(50.69785276073615).min(5.0).max(300.0).sliderRange(5.0, 300.0)
        .visible(() -> hudEnabled.get() && hurtParticles.get()).build());
    private final Setting<Double> hurtParticleSize = sgMulti.add(new DoubleSetting.Builder()
        .name("粒子大小").description("粒子的边长（逻辑像素）。")
        .defaultValue(3.445552147239261).min(0.5).max(12.0).sliderRange(0.5, 12.0)
        .visible(() -> hudEnabled.get() && hurtParticles.get()).build());
    private final Setting<Double> hurtParticleGravity = sgMulti.add(new DoubleSetting.Builder()
        .name("粒子重力").description("向下的加速度（逻辑像素/秒²）。0 = 悬浮。")
        .defaultValue(53.06748466257662).min(-200.0).max(400.0).sliderRange(-200.0, 400.0)
        .visible(() -> hudEnabled.get() && hurtParticles.get()).build());

    // ── 显示内容开关 ──
    private final Setting<Boolean> showAvatar = sgElements.add(new BoolSetting.Builder()
        .name("头像").description("显示目标头像（玩家用皮肤，生物用实体贴图）。")
        .defaultValue(true).visible(hudEnabled::get).build());
    private final Setting<Boolean> avatarRoundClip = sgElements.add(new BoolSetting.Builder()
        .name("圆角裁剪")
        .description("把玩家头像的四角裁成圆角。关闭时轮廓完全由皮肤贴图的 alpha 决定，"
            + "像直接把整个头颅抠出来。")
        .defaultValue(false).visible(() -> hudEnabled.get() && showAvatar.get()).build());
    private final Setting<Boolean> showName = sgElements.add(new BoolSetting.Builder()
        .name("名称").description("显示目标名称。").defaultValue(true).visible(hudEnabled::get).build());
    private final Setting<Boolean> showHealth = sgElements.add(new BoolSetting.Builder()
        .name("血条").description("显示血量条。").defaultValue(true).visible(hudEnabled::get).build());
    private final Setting<HpDisplay> hpDisplay = sgElements.add(new EnumSetting.Builder<HpDisplay>()
        .name("数值格式").description("血量数字的显示格式。").defaultValue(HpDisplay.Percentage)
        .visible(() -> hudEnabled.get() && showHealth.get()).build());
    private final Setting<Boolean> showHpText = sgElements.add(new BoolSetting.Builder()
        .name("血量数字").description("显示血量具体数值。").defaultValue(true)
        .visible(() -> hudEnabled.get() && showHealth.get()).build());
    private final Setting<Boolean> hpRoll = sgElements.add(new BoolSetting.Builder()
        .name("血量滚动")
        .description("血量数字变化时像里程表一样上下滚动。")
        .defaultValue(true).visible(() -> hudEnabled.get() && showHealth.get() && showHpText.get()).build());
    private final Setting<Boolean> showMainHand = sgElements.add(new BoolSetting.Builder()
        .name("主手物品").description("显示目标主手物品。").defaultValue(true)
        .visible(hudEnabled::get).build());
    private final Setting<Boolean> showOffHand = sgElements.add(new BoolSetting.Builder()
        .name("副手物品").description("显示目标副手物品。").defaultValue(true)
        .visible(hudEnabled::get).build());
    private final Setting<Boolean> showArmor = sgElements.add(new BoolSetting.Builder()
        .name("装备").description("显示目标穿戴的护甲。").defaultValue(true)
        .visible(hudEnabled::get).build());
    private final Setting<Boolean> showItemCount = sgElements.add(new BoolSetting.Builder()
        .name("物品数量").description("显示物品堆叠数量。").defaultValue(true)
        .visible(hudEnabled::get).build());
    private final Setting<Boolean> showDurability = sgElements.add(new BoolSetting.Builder()
        .name("耐久度").description("显示物品耐久条。").defaultValue(true)
        .visible(hudEnabled::get).build());
    private final Setting<Double> itemScale = sgElements.add(new DoubleSetting.Builder()
        .name("物品缩放").description("物品图标的显示大小。").defaultValue(1.0)
        .min(0.5).max(2.0).sliderRange(0.5, 2.0)
        .visible(() -> hudEnabled.get() && (showMainHand.get() || showOffHand.get() || showArmor.get())).build());

    private final Setting<Boolean> espEnabled = sgEsp.add(new BoolSetting.Builder()
        .name("透视").description("在目标身上绘制旋转标记。").defaultValue(true).build());
    private final Setting<ColorMode> colorMode = sgEsp.add(new EnumSetting.Builder<ColorMode>()
        .name("透视模式").defaultValue(ColorMode.Wave).visible(espEnabled::get).build());
    private final Setting<Double> espSize = sgEsp.add(new DoubleSetting.Builder()
        .name("透视大小").defaultValue(1.2).min(0.5).max(3.0).sliderRange(0.5, 3.0)
        .visible(espEnabled::get).build());
    private final Setting<Double> rotationSpeed = sgEsp.add(new DoubleSetting.Builder()
        .name("旋转速度(度/秒)")
        .description("标记每秒转多少度。180 = 2 秒转一圈。")
        .defaultValue(180.0).min(30.0).max(1080.0).sliderRange(30.0, 1080.0)
        .visible(espEnabled::get).build());
    // ── 动态旋转（让标记更灵动）──
    private final Setting<Boolean> dynamicSpin = sgEsp.add(new BoolSetting.Builder()
        .name("动态旋转")
        .description("角速度随条件变化：目标受伤或有 aura 模块锁定时加快，平时变慢。"
            + "并可自动换向（顺时针/逆时针）。")
        .defaultValue(true).visible(espEnabled::get).build());
    private final Setting<Double> dynamicSpinBoost = sgEsp.add(new DoubleSetting.Builder()
        .name("激活加速倍率").description("触发条件时，角速度放大到基础值的多少倍。")
        .defaultValue(6.196691176470586).min(1.0).max(12.0).sliderRange(1.0, 12.0)
        .visible(() -> espEnabled.get() && dynamicSpin.get()).build());
    private final Setting<Double> dynamicSpinMax = sgEsp.add(new DoubleSetting.Builder()
        .name("激活最高转速")
        .description("激活时的角速度上限（度/秒）。基础速度调高后，倍率会把它推得很快，"
            + "这里封顶以免看着发晕。720 = 每秒 2 圈。")
        .defaultValue(1049.117647058823).min(60.0).max(3600.0).sliderRange(60.0, 3600.0)
        .visible(() -> espEnabled.get() && dynamicSpin.get()).build());
    private final Setting<Double> dynamicSpinAccel = sgEsp.add(new DoubleSetting.Builder()
        .name("加速平滑").description("角速度增长的平滑系数（每秒）；越小越「软」。")
        .defaultValue(1.8971428571428584).min(0.2).max(20.0).sliderRange(0.2, 20.0)
        .visible(() -> espEnabled.get() && dynamicSpin.get()).build());
    private final Setting<Double> dynamicSpinDecay = sgEsp.add(new DoubleSetting.Builder()
        .name("减速平滑").description("角速度回落的平滑系数（每秒）；越小越「拖尾」。")
        .defaultValue(1.2).min(0.2).max(20.0).sliderRange(0.2, 20.0)
        .visible(() -> espEnabled.get() && dynamicSpin.get()).build());
    private final Setting<Boolean> dynamicSpinReverse = sgEsp.add(new BoolSetting.Builder()
        .name("自动换向")
        .description("激活结束后有概率反转旋转方向（顺时针/逆时针）。")
        .defaultValue(true).visible(() -> espEnabled.get() && dynamicSpin.get()).build());
    private final Setting<Double> reverseChance = sgEsp.add(new DoubleSetting.Builder()
        .name("换向概率").description("每次「激活→静止」时反转方向的概率。")
        .defaultValue(0.43193277310924405).min(0.0).max(1.0).sliderRange(0.0, 1.0)
        .visible(() -> espEnabled.get() && dynamicSpin.get() && dynamicSpinReverse.get()).build());
    private final Setting<Double> waveSpeed = sgEsp.add(new DoubleSetting.Builder()
        .name("波动速度").defaultValue(6.42352941176471).min(0.5).max(10.0).sliderRange(0.5, 10.0)
        .visible(() -> espEnabled.get() && colorMode.get() == ColorMode.Wave).build());
    private final Setting<Double> espScaleAnim = sgEsp.add(new DoubleSetting.Builder()
        .name("标记跳入回弹")
        .description("透视标记跳入/跳出时的回弹过冲幅度。0 = 无过冲，但仍会从零弹出。")
        .defaultValue(0.38655462184873984).min(0.0).max(1.0).sliderRange(0.0, 1.0)
        .visible(espEnabled::get).build());
    // ── 故障动画 ──
    private final Setting<Boolean> glitchEnabled = sgGlitch.add(new BoolSetting.Builder()
        .name("故障动画")
        .description("面板与透视标记的信号故障风格入场/出场动画（含 RGB 色散）。")
        .defaultValue(true).build());
    private final Setting<Double> glitchEntry = sgGlitch.add(new DoubleSetting.Builder()
        .name("入场时长").description("从出现到稳定锁定的秒数。").defaultValue(0.35)
        .min(0.0).max(2.0).sliderRange(0.0, 2.0)
        .visible(glitchEnabled::get).build());
    private final Setting<Double> glitchExit = sgGlitch.add(new DoubleSetting.Builder()
        .name("出场时长").description("目标消失后溶解掉的秒数。").defaultValue(0.30)
        .min(0.0).max(2.0).sliderRange(0.0, 2.0)
        .visible(glitchEnabled::get).build());
    private final Setting<Double> glitchIdleInterval = sgGlitch.add(new DoubleSetting.Builder()
        .name("待机脉冲间隔")
        .description("目标稳定显示时，每隔多少秒触发一次短促干扰。")
        .defaultValue(2.5).min(0.3).max(10.0).sliderRange(0.3, 10.0)
        .visible(glitchEnabled::get).build());
    private final Setting<Double> glitchIdleDuration = sgGlitch.add(new DoubleSetting.Builder()
        .name("待机脉冲时长").defaultValue(0.7825996533795496).min(0.02).max(1.0).sliderRange(0.02, 1.0)
        .visible(glitchEnabled::get).build());
    private final Setting<Double> glitchRgbSplit = sgGlitch.add(new DoubleSetting.Builder()
        .name("RGB色散").description("红蓝通道分离的像素强度。").defaultValue(12.270363951473149)
        .min(0.0).max(30.0).sliderRange(0.0, 30.0)
        .visible(glitchEnabled::get).build());
    private final Setting<Double> glitchScanline = sgGlitch.add(new DoubleSetting.Builder()
        .name("扫描线").description("扫描线强度。").defaultValue(0.35)
        .min(0.0).max(1.0).sliderRange(0.0, 1.0)
        .visible(glitchEnabled::get).build());
    private final Setting<Double> glitchTear = sgGlitch.add(new DoubleSetting.Builder()
        .name("撕裂").description("水平块状撕裂强度。").defaultValue(0.5)
        .min(0.0).max(1.0).sliderRange(0.0, 1.0)
        .visible(glitchEnabled::get).build());
    private final Setting<Boolean> glitchOnPanel = sgGlitch.add(new BoolSetting.Builder()
        .name("作用于面板").defaultValue(true).visible(glitchEnabled::get).build());
    private final Setting<Double> panelScaleAnim = sgGlitch.add(new DoubleSetting.Builder()
        .name("面板缩放动画")
        .description("入场/出场时面板的缩放幅度。0 = 不缩放。")
        .defaultValue(0.08).min(0.0).max(0.5).sliderRange(0.0, 0.5)
        .visible(glitchEnabled::get).build());

    // ── 准星锁定 ──
    private final Setting<Boolean> lockEnabled = sgLock.add(new BoolSetting.Builder()
        .name("准星锁定")
        .description("没有 aura 目标时，用准星附近的实体作为目标。")
        .defaultValue(true).build());
    private final Setting<Double> lockCone = sgLock.add(new DoubleSetting.Builder()
        .name("捕获角度")
        .description("准星周围多大范围内可以锁定目标（度）。越大越宽松。")
        .defaultValue(12.0).min(1.0).max(60.0).sliderRange(1.0, 60.0)
        .visible(lockEnabled::get).build());
    private final Setting<Double> lockStickiness = sgLock.add(new DoubleSetting.Builder()
        .name("粘滞角度")
        .description("已锁定的目标，准星偏出到这个角度才会脱离。应大于捕获角度。")
        .defaultValue(25.0).min(1.0).max(90.0).sliderRange(1.0, 90.0)
        .visible(lockEnabled::get).build());
    private final Setting<Double> lockRange = sgLock.add(new DoubleSetting.Builder()
        .name("锁定距离").description("最大锁定距离（格）。").defaultValue(30.554675118859)
        .min(2.0).max(128.0).sliderRange(2.0, 128.0)
        .visible(lockEnabled::get).build());
    private final Setting<Double> lockLinger = sgLock.add(new DoubleSetting.Builder()
        .name("停留时间")
        .description("准星移开后，面板与锁定标记继续显示的秒数。")
        .defaultValue(2.0).min(0.0).max(10.0).sliderRange(0.0, 10.0)
        .visible(lockEnabled::get).build());
    private final Setting<Double> lockSwitchMargin = sgLock.add(new DoubleSetting.Builder()
        .name("切换阈值")
        .description("新目标要比当前目标好多少才切换（0~1）。越大越不容易换目标。")
        .defaultValue(0.25).min(0.0).max(1.0).sliderRange(0.0, 1.0)
        .visible(lockEnabled::get).build());
    private final Setting<Boolean> lockPlayersOnly = sgLock.add(new BoolSetting.Builder()
        .name("只锁定玩家").defaultValue(false).visible(lockEnabled::get).build());
    private final Setting<Boolean> lockIgnoreSpectators = sgLock.add(new BoolSetting.Builder()
        .name("忽略旁观者").defaultValue(true).visible(lockEnabled::get).build());
    private final Setting<Boolean> lockRequireVisible = sgLock.add(new BoolSetting.Builder()
        .name("要求可见")
        .description("只锁定没有方块遮挡的目标（会增加开销）。")
        .defaultValue(false).visible(lockEnabled::get).build());

    private final Setting<SettingColor> hudColor = sgColors.add(new ColorSetting.Builder()
        .name("背景颜色").description("面板背景色（不再影响头像）。")
        .defaultValue(new SettingColor(12, 0, 7, 0)).build());
    private final Setting<HealthColorMode> healthColorMode = sgColors.add(new EnumSetting.Builder<HealthColorMode>()
        .name("血条配色").description("血条颜色的取色方式。")
        .defaultValue(HealthColorMode.Flow).build());
    private final Setting<SettingColor> healthColorCustom = sgColors.add(new ColorSetting.Builder()
        .name("血条自定义颜色").description("血条配色选「自定义」时使用。")
        .defaultValue(new SettingColor(255, 183, 217, 255))
        .visible(() -> healthColorMode.get() == HealthColorMode.Custom).build());
    // ── 血条流动（配色选「流动」时生效）──
    private final Setting<SettingColor> healthFlowColor1 = sgColors.add(new ColorSetting.Builder()
        .name("血条流动颜色1").description("流动渐变的起始色。")
        .defaultValue(new SettingColor(255, 183, 217, 255))
        .visible(() -> healthColorMode.get() == HealthColorMode.Flow).build());
    private final Setting<SettingColor> healthFlowColor2 = sgColors.add(new ColorSetting.Builder()
        .name("血条流动颜色2").description("流动渐变的结束色。")
        .defaultValue(new SettingColor(255, 255, 255, 255))
        .visible(() -> healthColorMode.get() == HealthColorMode.Flow).build());
    private final Setting<Double> healthFlowSpeed = sgColors.add(new DoubleSetting.Builder()
        .name("血条流动速度").description("色带每秒滚动多少条。")
        .defaultValue(0.6).min(0.05).max(4.0).sliderRange(0.05, 4.0)
        .visible(() -> healthColorMode.get() == HealthColorMode.Flow).build());
    private final Setting<Double> healthFlowBands = sgColors.add(new DoubleSetting.Builder()
        .name("血条色带数量").description("一条血条里重复几段渐变；越大波纹越密。")
        .defaultValue(2.0).min(1.0).max(8.0).sliderRange(1.0, 8.0)
        .visible(() -> healthColorMode.get() == HealthColorMode.Flow).build());
    private final Setting<SettingColor> espColor1 = sgColors.add(new ColorSetting.Builder()
        .name("透视颜色1").defaultValue(new SettingColor(255, 0, 172, 255))
        .visible(() -> espEnabled.get() && colorMode.get() != ColorMode.Rainbow).build());
    private final Setting<SettingColor> espColor2 = sgColors.add(new ColorSetting.Builder()
        .name("透视颜色2").defaultValue(new SettingColor(255, 255, 255, 255))
        .visible(() -> espEnabled.get() && colorMode.get() != ColorMode.Rainbow).build());

    // ─── 状态 ─────────────────────────────────────────────

    private float rotation = 0f;

    // ─── 动态旋转状态 ─────────────────────────────────────

    /** 当前角速度（度/秒）。动态模式下由条件平滑驱动，否则恒为基础速度。 */
    private float spinVelocity = -180f;
    /** 上一帧「是否处于激活状态」，用于检测「激活 → 静止」的边沿以触发换向。 */
    private boolean spinWasActive;

    /**
     * 推进透视标记的旋转角与角速度。
     *
     * <p><b>静态模式（默认，行为与以前一致）</b>：角速度恒为基础速度，反向旋转。
     *
     * <p><b>动态模式</b>：角速度向「目标速度」平滑逼近 ——
     * 目标速度 = 基础速度 × (激活时 ? 加速倍率 : 1)。
     * 「激活」= 本帧任一槽位受伤中，<b>或</b>任一槽位被 aura 模块锁定
     * （由 {@link SakuraTargetResolver#isAuraLocked} 提供）。
     *
     * <p>加速与减速用<b>不同</b>的平滑系数，所以「受伤瞬间窜快、之后缓慢拖尾回落」，
     * 这正是「灵动」的来源；单一系数只能得到对称的加/减速，观感生硬。
     *
     * <p>换向：在「激活 → 静止」的边沿按概率翻转方向，避免只有单向旋转。
     */
    private void updateRotation() {
        float base = rotationSpeed.get().floatValue();
        float dt = lastFrameDelta;

        if (!dynamicSpin.get()) {
            // 与旧行为一致：恒定角速度反向旋转
            spinVelocity = -base;
            rotation += spinVelocity * dt;
            rotation = wrapDegrees(rotation);
            return;
        }

        // 是否处于激活状态：任一目标受伤中，或被 aura 模块锁定
        boolean active = false;
        for (TargetSlot slot : slots) {
            if (!slot.alive) continue;
            if (slot.hurt > 0.001f) { active = true; break; }
            if (slot.entity != null && SakuraTargetResolver.isAuraLocked(slot.entity)) {
                active = true;
                break;
            }
        }

        // 激活 → 静止 的边沿：按概率换向
        if (spinWasActive && !active && dynamicSpinReverse.get()) {
            if (Math.random() < reverseChance.get()) spinVelocity = -spinVelocity;
        }
        spinWasActive = active;

        // 目标速度：激活时按倍率放大（保持方向），并受「激活最高转速」封顶
        float target = base * (active ? dynamicSpinBoost.get().floatValue() : 1f);
        if (active) {
            float cap = Math.max(base, dynamicSpinMax.get().floatValue());
            target = Math.min(target, cap);
        }
        if (spinVelocity < 0f) target = -target;

        // 平滑逼近：加速与减速用各自的系数
        float rate = active ? dynamicSpinAccel.get().floatValue() : dynamicSpinDecay.get().floatValue();
        float k = Math.min(1f, Math.max(0f, rate * dt));
        // 若方向被换向翻转，先把当前速度对齐到目标方向，避免「先反向再绕回来」
        if ((spinVelocity < 0f) != (target < 0f)) spinVelocity = -spinVelocity;
        spinVelocity += (target - spinVelocity) * k;

        rotation += spinVelocity * dt;
        rotation = wrapDegrees(rotation);
    }

    /**
     * 把角度折回 {@code (-360, 360]}。
     *
     * <p>不能用「加/减一次 360」那种写法：角速度上限到 3600 度/秒时，
     * 一帧（哪怕 dt 只有 1/60 秒）就能转 60 度以上；若遇到卡顿导致 dt 变大
     * （本类上限 0.5 秒），单帧可转过 1800 度 = 5 圈，只减一次 360 根本回不来，
     * 会让 {@code rotation} 无限增长、最终在 float 精度上出问题。
     */
    private static float wrapDegrees(float deg) {
        float d = deg % 360f;
        if (d > 180f) d -= 360f;
        if (d < -180f) d += 360f;
        return d;
    }

    /**
     * 每个目标一个「槽位」，各自持有独立的动画状态。
     *
     * <p><b>为什么不能共用一个 animatedHealth / glitchAnim</b>：多目标时每个面板的
     * 血量插值、入场出场进度、滚动偏移、受伤闪光都必须独立推进；共用会让多个面板
     * 互相干扰（例如 A 目标死亡时 B 的面板一起消失）。
     *
     * <p>槽位按实体 id 复用：同一目标持续存在时保留其动画进度，不会每帧重置。
     */
    private static final class TargetSlot {
        /** 当前绑定的实体。目标消失后仍保留，用来把出场动画播完。 */
        LivingEntity entity;
        /** 本帧目标是否真实存在。false 表示正在播出场动画。 */
        boolean alive;

        /** 故障风格入场/出场动画状态机（每个槽位独立）。 */
        final SakuraGlitchAnimation glitchAnim = new SakuraGlitchAnimation();

        /** 血量插值显示值。 */
        float animatedHealth;
        /** 上一帧血量，用于检测「受伤」事件。 */
        float lastHealth;
        /** 受伤粒子强度 0~1，随时间衰减（用于「刚受伤」判定与旋转加速）。 */
        float hurt;

        /** 从头像喷出的粒子（屏幕空间，坐标为面板内的相对偏移）。 */
        final List<HurtParticle> particles = new ArrayList<>();

        /** 名称滚动偏移（逻辑像素，正数表示已向左滚出的距离）。 */
        float marqueeOffset;
        /** 滚动到两端后的停顿计时（秒）。 */
        float marqueePause;
        /** 滚动方向：+1 向左（偏移增大），-1 向右（偏移减小）。来回滚动用。 */
        float marqueeDir = 1;

        /** 血量数字滚轮用的缓动值；NaN 表示还没初始化。 */
        float rollValue = Float.NaN;
        /** 上一帧是否为百分比格式；用于检测格式切换（单位变化）时重置缓动值。 */
        boolean rollPercent;

        TargetSlot(LivingEntity entity) {
            this.entity = entity;
            this.alive = true;
            this.animatedHealth = entity.getHealth();
            this.lastHealth = entity.getHealth();
        }
    }

    /** 本帧要绘制的槽位，按解析顺序排列（第一个是主目标）。 */
    private final List<TargetSlot> slots = new ArrayList<>();

    /**
     * 受伤粒子（屏幕空间）。
     *
     * <p>坐标存的是<b>相对头像中心的偏移</b>，而不是屏幕绝对坐标：这样面板被拖动、
     * 或目标在多个面板间换位时，粒子会跟着头像走，不会留在原地。
     *
     * <p>用屏幕空间而非 {@code mc.particleManager}：需求是「从头像掉出来」，
     * 而原版粒子在世界里、不在头像上；而且 HUD 是 2D 的，屏幕空间才能精确对齐头像。
     */
    private static final class HurtParticle {
        /** 相对头像中心的偏移（逻辑像素，y 向下）。 */
        float ox, oy;
        /** 速度（逻辑像素/秒）。 */
        float vx, vy;
        /** 剩余寿命（秒）与总寿命（用于算淡出）。 */
        float life, maxLife;
    }

    /** 上一帧的帧间隔（秒），用于受伤闪光的衰减计算。 */
    private float lastFrameDelta = 1f / 60f;

    /** 血量数字滚动到达目标档位的速度（每秒的比例，越大越快）。 */
    private static final float ROLL_EASE = 12f;

    private Texture targetTexture;
    private Texture target1Texture;
    private boolean texturesFailed;
    private boolean worldPipelineChecked;
    private boolean worldPipelineBroken;

    private final org.joml.Vector3d espPos = new org.joml.Vector3d();

    public SakuraTargetHud() {
        super(AddonTemplate.IMG_HUD_CATEGORY, "sakura-target-hud", "目标显示：圆角面板 + 旋转透视标记。");
    }

    @Override
    public void onActivate() {
        rotation = 0f;
        slots.clear();

        // 在这里先确保 GL 侧的着色器/贴图在渲染线程上准备好。
        if (RenderSystem.isOnRenderThread()) releaseTextures();
        else RenderSystem.queueFencedTask(this::releaseTextures);
    }

    @Override
    public void onDeactivate() {
        // 清掉准星锁定状态，免得下次开启时还留着旧目标的停留计时
        SakuraTargetResolver.clearCrosshairLock();
        slots.clear();

        if (RenderSystem.isOnRenderThread()) releaseTextures();
        else RenderSystem.queueFencedTask(this::releaseTextures);
    }

    private void releaseTextures() {
        if (targetTexture != null) {
            targetTexture.close();
            targetTexture = null;
        }
        if (target1Texture != null) {
            target1Texture.close();
            target1Texture = null;
        }
        texturesFailed = false;

        SakuraEntityPortrait.clear();

        // 字形图集纹理也要释放，否则反复开关模块会持续泄漏显存
        SakuraTextRenderer.destroy();

        Shader2DUtil.destroy();
    }

    // ═══════════════════════════════════════════════════════
    //  渲染入口
    // ═══════════════════════════════════════════════════════

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        if (mc.player == null || mc.world == null) return;
        if (!hudEnabled.get()) return;

        // 槽位已由 onRender3D 解析并推进过动画（Render3DEvent 先于 Render2DEvent）。
        // 这里只负责绘制，避免同一帧推进两次动画与停留计时。
        boolean anyVisible = false;
        for (TargetSlot slot : slots) {
            if (slot.glitchAnim.isVisible()) {
                anyVisible = true;
                break;
            }
        }
        if (!anyVisible) {
            slots.clear();
            return;
        }

        // 多个面板纵向排列，各自带独立的入场缩放
        float baseX = hudX.get();
        float baseY = hudY.get();
        float spacing = HUD_HEIGHT + panelSpacing.get().floatValue();

        int drawn = 0;
        for (TargetSlot slot : slots) {
            if (!slot.glitchAnim.isVisible()) continue;
            if (slot.entity == null) continue;

            renderPanel(event, slot, baseX, baseY + drawn * spacing);
            drawn++;
        }

        // 出场动画播完的槽位可以丢掉了
        slots.removeIf(slot -> !slot.glitchAnim.isVisible());
    }

    /**
     * 同步准星锁定参数并解析本帧目标（支持多目标）。
     *
     * <p>只在 {@code onRender3D} 里调用一次：同一帧内 `Render3DEvent` 先于 `Render2DEvent`
     * 触发，所以 2D 直接复用这个结果即可。若两边各自解析，停留计时会被推进两次
     * （实际停留时长只有配置值的一半），且可能取到不一致的目标。
     *
     * <p><b>槽位复用规则</b>：本帧解析出的目标按顺序与现有槽位配对（同一个实体沿用原槽位，
     * 保留其动画进度）。本帧没出现的目标不会被立刻删除，而是把 {@code alive} 置 false，
     * 让出场动画播完后再由渲染循环清理。
     */
    private void resolveTargetsForFrame(float frameDelta) {
        // 用户可能随时在改设置，每帧同步一次
        SakuraTargetResolver.configureCrosshair(
            lockEnabled.get(),
            lockCone.get().floatValue(),
            lockStickiness.get().floatValue(),
            lockRange.get().floatValue(),
            lockLinger.get().floatValue(),
            lockSwitchMargin.get().floatValue(),
            lockPlayersOnly.get(),
            lockIgnoreSpectators.get(),
            lockRequireVisible.get());

        // 首帧或异常值退化为 1/60 秒，避免停留计时跳变
        float dt = (frameDelta > 0f && frameDelta < 0.5f) ? frameDelta : 1f / 60f;

        List<LivingEntity> resolved = SakuraTargetResolver.resolveAll(dt, maxTargets.get());

        // 槽位重排：按解析顺序重建列表，保证「面板顺序 = 目标优先级」。
        // 本帧出现过的目标沿用原槽位（保留动画进度），未出现的槽位置为出场中并排在后面，
        // 让它的出场动画播完后再由渲染循环清理。
        List<TargetSlot> previous = new ArrayList<>(slots);
        for (TargetSlot slot : previous) slot.alive = false;

        List<TargetSlot> ordered = new ArrayList<>(resolved.size() + previous.size());
        for (LivingEntity entity : resolved) {
            TargetSlot reuse = null;
            for (TargetSlot candidate : previous) {
                if (!candidate.alive && candidate.entity == entity) {
                    reuse = candidate;
                    break;
                }
            }
            if (reuse == null) reuse = new TargetSlot(entity);

            reuse.entity = entity;
            reuse.alive = true;
            ordered.add(reuse);
        }

        // 未出现的目标：保留槽位以播完出场动画
        for (TargetSlot slot : previous) {
            if (!slot.alive) ordered.add(slot);
        }

        slots.clear();
        slots.addAll(ordered);
    }

    /** 目标是否可以继续绘制（存活且未被移除）。 */
    private static boolean isDrawable(LivingEntity entity) {
        return entity != null && entity.isAlive() && !entity.isRemoved();
    }

    /** 把设置项同步进动画控制器，并按帧推进单个槽位。 */
    private void updateAnimation(TargetSlot slot, float deltaTime) {
        if (glitchEnabled.get()) {
            slot.glitchAnim.configure(
                glitchEntry.get().floatValue(),
                glitchExit.get().floatValue(),
                glitchIdleInterval.get().floatValue(),
                glitchIdleDuration.get().floatValue());
        } else {
            // 关闭动画时不走故障逻辑，但入场/出场仍要瞬时完成，避免面板卡住不消失
            slot.glitchAnim.configure(0f, 0f, 999f, 0.02f);
        }
        slot.glitchAnim.update(slot.alive, deltaTime);
    }

    /**
     * 是否需要对面板施加故障后处理。
     *
     * <p>透视标记不走这里：它在世界空间，2D 后处理够不到它。标记只做缩放/淡入的跳入跳出动画。
     */
    private boolean glitchActive(TargetSlot slot) {
        if (!glitchEnabled.get()) return false;
        if (!glitchOnPanel.get()) return false;
        return slot.glitchAnim.glitch() > 0.001f;
    }

    // ═══════════════════════════════════════════════════════
    //  2D 面板
    // ═══════════════════════════════════════════════════════

    /**
     * @param alive 目标当前是否真实存在；false 表示正在播出场动画
     */
    private void renderPanel(Render2DEvent event, TargetSlot slot, float x, float y) {
        LivingEntity target = slot.entity;
        boolean alive = slot.alive;

        if (alive) {
            // 受伤检测：血量比上一帧低就算「真的掉血了」。
            // 用血量差而不是 hurtTime，因为 hurtTime 只在受击后 10 tick 内非 0，
            // 且被击退/格挡等情形也会置位；血量下降才是真的受伤。
            float health = target.getHealth();
            if (health < slot.lastHealth - 0.001f) {
                slot.hurt = 1f;
                // 从头像位置喷出粒子（替代旧的面板闪光）
                if (hurtParticles.get()) spawnHurtParticles(slot);
            }
            slot.lastHealth = health;

            slot.animatedHealth = MathHelper.lerp(0.1f, slot.animatedHealth, health);
        }

        // 血量滚轮的缓动值：每帧只更新一次（在绘制之前），
        // 文本与滚轮都读这一个值，保证两者永远一致。
        updateRollValue(slot);

        // 受伤强度按时间衰减（供旋转加速等使用；粒子有自己的寿命）
        if (slot.hurt > 0f) {
            float decay = Math.max(0.05f, hurtParticleLife.get().floatValue());
            slot.hurt = Math.max(0f, slot.hurt - (float) lastFrameDelta / decay);
        }

        // 粒子推进：位置按速度积分，速度受重力影响
        updateHurtParticles(slot);

        // 入场时的缩放：从略小放大到正常，增强「信号锁定」的观感
        float presence = glitchEnabled.get() ? slot.glitchAnim.presence() : 1f;
        float scale = 1f - (1f - presence) * panelScaleAnim.get().floatValue();
        if (scale < 0.999f && scale > 0.05f) {
            // 以面板中心为原点缩放
            float cx = x + HUD_WIDTH / 2f;
            float cy = y + HUD_HEIGHT / 2f;
            x = cx - HUD_WIDTH * scale / 2f;
            y = cy - HUD_HEIGHT * scale / 2f;
        }

        // 背景模糊先做：它是后处理，需要拿到「面板之前」的干净画面
        if (hudBlur.get() && presence > 0.05f) {
            Shader2DUtil.drawRoundedBlur(x, y, HUD_WIDTH, HUD_HEIGHT, RADIUS,
                toAwt(hudColor.get()), hudBlurStrength.get().floatValue(), 0.9f * presence);
        }

        // 阴影在模糊之后、面板之前画。两个顺序上的理由：
        //   1. 它只画面板外侧（着色器里 discard 内侧），所以不会盖住刚做好的磨砂背景；
        //   2. 若放在模糊之前，模糊那 81 次采样会读到这圈深色，把面板边缘一起拉暗。
        if (shadowEnabled.get() && presence > 0.05f) {
            SettingColor sc = shadowColor.get();
            java.awt.Color shadowBase = new java.awt.Color(sc.r, sc.g, sc.b, sc.a);
            Shader2DUtil.drawRoundedShadow(x, y, HUD_WIDTH, HUD_HEIGHT, RADIUS,
                scaleAlpha(toMeteor(shadowBase), presence),
                shadowFeather.get().floatValue(),
                shadowOffsetX.get().floatValue(),
                shadowOffsetY.get().floatValue());
        }

        // 面板 + 头像 + 文字 + 血条：形状走 SDF 圆角着色器，文字走自建 STB 图集
        try {
            renderHudContent(slot, x, y, presence);
            drawAvatar(x, y, target, presence);
        } catch (Throwable t) {
            MeteorClient.LOG.error("[IMG/Sakura TargetHud] 面板绘制失败，正在关闭模块。", t);
            toggle();
            return;
        }

        // 受伤反馈：从头像位置掉出粒子（替代旧的面板闪光）。
        // 放在形状/文字之后、故障后处理之前，这样它也会被故障效果一起色散。
        if (hurtParticles.get() && !slot.particles.isEmpty()) {
            drawHurtParticles(slot, x, y, presence);
        }

        // 物品用原版渲染，与面板形状/文字分开。
        // 它没有 alpha 参数可控，无法像 SDF 面板那样淡出，所以只在面板基本成型后才画，
        // 出场阶段（presence 下降）直接不画，避免「突然闪没」的突兀感。
        if (alive && presence > 0.5f) {
            drawItems(event, target, x, y);
        }

        // 故障后处理必须放在最后：它拷贝当前帧并按 RGB 色散重绘
        if (glitchActive(slot)) {
            Shader2DUtil.drawGlitch(x, y, HUD_WIDTH, HUD_HEIGHT, RADIUS,
                slot.glitchAnim.glitch(),
                glitchRgbSplit.get().floatValue(),
                glitchScanline.get().floatValue(),
                glitchTear.get().floatValue(),
                slot.glitchAnim.time());
        }
    }

    /**
     * 头像绘制。
     *
     * <p>玩家：按原版内外两层叠加皮肤（见 {@link SakuraSkinRenderer#drawPlayerHeadLayered}），
     * 四角是否裁圆角由「圆角裁剪」开关决定。
     * 生物：取 {@link SakuraEntityPortrait} 解析出的实体贴图，仍走 SDF 圆角着色器裁剪
     * ——不再用背景色遮盖四角（那会让半透明背景下的方角透出来，且调背景色会影响头像观感）。
     */
    private void drawAvatar(float x, float y, LivingEntity target, float presence) {
        float ax = x + PADDING;
        float ay = y + PADDING;

        if (!showAvatar.get()) return;

        boolean drawn = false;

        if (target instanceof AbstractClientPlayerEntity player) {
            Identifier skin = null;
            try {
                skin = player.getSkin().body().texturePath();
            } catch (Throwable ignored) {
                skin = null;
            }
            // 始终双层：开关只决定四角是否裁剪
            float avatarRadius = avatarRoundClip.get() ? AVATAR_RADIUS : 0f;
            drawn = skin != null
                && SakuraSkinRenderer.drawPlayerHeadLayered(skin, ax, ay, AVATAR_SIZE, avatarRadius, presence);
        } else {
            // 生物：UV 从模型里读出来，不同生物的头在贴图上的位置并不相同
            // （双足生物在 (8,8)，蜘蛛等在别处），不能再假定统一位置。
            var portrait = SakuraEntityPortrait.portraitFor(target);
            if (portrait != null) {
                // 脸在贴图上的区域由模型解析得出（大小不一定是 8x8，鱿鱼是 12x16）
                drawn = SakuraSkinRenderer.drawMobHead(portrait.texture(),
                    ax, ay, AVATAR_SIZE, AVATAR_RADIUS, portrait.uv(), presence);
            }
        }

        if (!drawn) {
            // 没有可用贴图时画一个中性占位块（固定色，与背景色无关）
            Shader2DUtil.drawRoundedRect(ax, ay, AVATAR_SIZE, AVATAR_SIZE, AVATAR_RADIUS,
                scaleAlpha(toMeteor(PLACEHOLDER_COLOR), presence), 0f, null);
        }
    }

    // ─── 受伤粒子（屏幕空间，从头像位置喷出）───────────────

    /**
     * 受伤时从头像中心喷出一批粒子。
     *
     * <p>初速度方向是<b>均匀散开的圆周</b>再叠加一点随机抖动，并且整体略微向上偏
     * （减去一个向上的偏置），这样观感是「被打得溅出来」而不是均匀炸开。
     */
    private void spawnHurtParticles(TargetSlot slot) {
        int count = Math.max(1, hurtParticleCount.get());
        float speed = hurtParticleSpeed.get().floatValue();
        float life = Math.max(0.05f, hurtParticleLife.get().floatValue());
        // 头像中心相对面板左上角的偏移
        float cx = PADDING + AVATAR_SIZE / 2f;
        float cy = PADDING + AVATAR_SIZE / 2f;

        for (int i = 0; i < count; i++) {
            HurtParticle p = new HurtParticle();
            double angle = (Math.PI * 2.0) * (i / (double) count) + (Math.random() - 0.5) * 0.6;
            float v = speed * (0.55f + (float) Math.random() * 0.75f);
            p.ox = cx + (float) (Math.random() - 0.5) * AVATAR_SIZE * 0.35f;
            p.oy = cy + (float) (Math.random() - 0.5) * AVATAR_SIZE * 0.35f;
            p.vx = (float) Math.cos(angle) * v;
            // 略向上偏置：粒子先往上溅再被重力拉下来
            p.vy = (float) Math.sin(angle) * v - speed * 0.35f;
            p.maxLife = life * (0.7f + (float) Math.random() * 0.6f);
            p.life = p.maxLife;
            slot.particles.add(p);
        }
    }

    /** 推进粒子：积分位置、施加重力、扣寿命、清理过期。 */
    private void updateHurtParticles(TargetSlot slot) {
        if (slot.particles.isEmpty()) return;
        float dt = lastFrameDelta;
        float gravity = hurtParticleGravity.get().floatValue();

        for (int i = slot.particles.size() - 1; i >= 0; i--) {
            HurtParticle p = slot.particles.get(i);
            p.life -= dt;
            if (p.life <= 0f) {
                slot.particles.remove(i);
                continue;
            }
            p.vy += gravity * dt;
            p.ox += p.vx * dt;
            p.oy += p.vy * dt;
        }
    }

    /** 画粒子：越接近寿命终点越小越淡。 */
    private void drawHurtParticles(TargetSlot slot, float x, float y, float presence) {
        SettingColor pc = hurtParticleColor.get();
        float baseSize = hurtParticleSize.get().floatValue();

        for (HurtParticle p : slot.particles) {
            float t = Math.max(0f, Math.min(1f, p.life / Math.max(0.0001f, p.maxLife)));  // 1 → 0
            float alpha = presence * t;
            if (alpha <= 0.01f) continue;
            float size = Math.max(0.5f, baseSize * (0.45f + 0.55f * t));
            Color c = new Color(pc.r, pc.g, pc.b, Math.max(0, Math.min(255, (int) (pc.a * alpha))));
            Shader2DUtil.drawRoundedRect(x + p.ox - size / 2f, y + p.oy - size / 2f,
                size, size, size / 2f, c, 0f, null);
        }
    }

    /** 距离文字；关闭显示或目标异常时返回 {@code null}。 */
    private String distanceLabel(LivingEntity target) {
        if (!showDistance.get() || target == null || mc.player == null) return null;
        return String.format("%.1fm", mc.player.distanceTo(target));
    }

    /** 面板部分：圆角背景、名字、血量数值与血条。 */
    private void renderHudContent(TargetSlot slot, float x, float y, float presence) {
        LivingEntity target = slot.entity;

        // 面板背景：SDF 圆角着色器（替代 NanoVG 的 nvgRoundedRect）
        Shader2DUtil.drawRoundedRect(x, y, HUD_WIDTH, HUD_HEIGHT, RADIUS,
            scaleAlpha(toMeteor(toAwt(hudColor.get())), presence), 0f, null);

        float textX = x + CONTENT_X;
        float rightEdge = x + HUD_WIDTH - PADDING;

        // 血量数值先算宽度：名字要用它来限定可用宽度，避免长名字压到数值上
        boolean drawHp = showHealth.get() && showHpText.get();
        String hp = drawHp ? hpText(slot) : null;
        float hpWidth = drawHp ? (float) SakuraTextRenderer.getWidth(hp, 11f) : 0f;

        // 距离文本：与物品行同一行，右对齐。物品行已按这个宽度预留右侧空间
        // （见 drawItems），因此两者不会重叠。
        String dist = distanceLabel(target);
        float distWidth = dist != null ? (float) SakuraTextRenderer.getWidth(dist, 9f) : 0f;

        SakuraTextRenderer.begin();

        // ── 名字 ──
        if (showName.get()) {
            float nameMaxWidth = rightEdge - textX - (drawHp ? hpWidth + NAME_HP_GAP : 0f);
            drawName(slot, target.getName().getString(), textX, y + NAME_Y, nameMaxWidth, presence);
        }

        // ── 血量数值（右上角对齐） ──
        if (drawHp) {
            int hpColor = argb(new java.awt.Color(235, 235, 235), presence);
            if (hpRoll.get()) {
                // 停稳（缓动值已精确等于目标档）时整行直绘，避免裁剪削掉字形顶部
                drawRollingNumber(hp, slot.rollValue, isRollSettled(slot),
                    rightEdge - hpWidth, y + NAME_Y, 11f, hpColor);
            } else {
                SakuraTextRenderer.draw(hp, rightEdge - hpWidth, y + NAME_Y, 11f, hpColor);
            }
        }

        // ── 距离（右下角，右对齐） ──
        if (dist != null) {
            SakuraTextRenderer.draw(dist, rightEdge - distWidth, y + ITEM_Y, 9f,
                argb(new java.awt.Color(180, 180, 180), presence));
        }

        // ── 血量条 ──
        if (showHealth.get()) {
            float barX = textX;
            float barWidth = HUD_WIDTH - CONTENT_X - PADDING;
            float barRadius = BAR_HEIGHT / 2f;

            Shader2DUtil.drawRoundedRect(barX, y + BAR_Y, barWidth, BAR_HEIGHT, barRadius,
                scaleAlpha(toMeteor(BAR_BG_COLOR), presence), 0f, null);

            float maxHealth = Math.max(0.01f, target.getMaxHealth());
            float healthPercent = Math.min(1f, Math.max(0f, slot.animatedHealth / maxHealth));
            float healthWidth = barWidth * healthPercent;

            if (healthWidth > 0) {
                if (healthColorMode.get() == HealthColorMode.Flow) {
                    // 流动：用双色渐变着色器。相位随时间循环推进，色带就在血条上滚动。
                    // 注意只对**已填充部分**做渐变，所以宽度传 healthWidth。
                    float phase = (System.currentTimeMillis() % 100000L) / 1000f;
                    float flow = (phase * healthFlowSpeed.get().floatValue()) % 1f;
                    Shader2DUtil.drawRoundedRectGradient(barX, y + BAR_Y, healthWidth, BAR_HEIGHT,
                        barRadius,
                        scaleAlpha(settingToMeteor(healthFlowColor1.get()), presence),
                        scaleAlpha(settingToMeteor(healthFlowColor2.get()), presence),
                        flow, healthFlowBands.get().floatValue());
                } else {
                    Shader2DUtil.drawRoundedRect(barX, y + BAR_Y, healthWidth, BAR_HEIGHT, barRadius,
                        scaleAlpha(toMeteor(healthColor(healthPercent)), presence), 0f, null);
                }
            }

            String healthText = String.format("%.0f", healthPercent * 100);
            SakuraTextRenderer.drawCentered(healthText, barX + barWidth / 2f,
                y + BAR_Y + BAR_HEIGHT / 2f - 4.5f,
                9f, argb(new java.awt.Color(20, 20, 20, 235), presence));
        }

        SakuraTextRenderer.end();
    }

    /**
     * 画名字：放得下就直接画，放不下则按设置滚动播放。
     *
     * <p><b>为什么要滚动</b>：长名字用省略号截断后就看不清全名了。滚动能在
     * 固定宽度内把完整名字展示完，既不占额外位置，也不丢失信息。
     *
     * <p>滚动实现：把整行名字按负偏移绘制（向左滚出的部分为负坐标），
     * 再用 {@link SakuraTextRenderer#drawClipped} 裁到名字区域内。
     * 滚到末尾后暂停一小段，再从 0 重新开始。
     */
    /**
     * 画名字：放得下就直接画，放不下则按设置<b>来回</b>滚动播放。
     *
     * <p><b>为什么是来回而不是循环</b>：循环滚动时，「文字尾部滚出 → 归零重来」这一段
     * 名字区域的右侧是空的（没有任何字），看起来像名称消失了一瞬再从头开始。
     * 改成往返后，文字始终贴着某一端，永远不会出现空窗。
     *
     * <p>两端各停顿一段（用「滚动间隔」设置项控制），且<b>停在文字恰好完整可见的位置</b>：
     * <ul>
     *     <li>左端：偏移 0，名称从第一个字开始显示</li>
     *     <li>右端：偏移 = 文字宽 − 可用宽，名称的最后一个字刚好贴到右边界</li>
     * </ul>
     * 注意右端不是滚「文字宽 + 间隔」——那样末尾会滑出可视区、留下空白，
     * 正是要避免的现象。
     *
     * <p>裁剪用 {@link SakuraTextRenderer#drawClipped}，把整行文字按偏移绘制、
     * 只露出名字区域那一段。
     */
    private void drawName(TargetSlot slot, String name, float x, float y, float maxWidth, float presence) {
        int color = argb(java.awt.Color.WHITE, presence);
        if (name == null || name.isEmpty() || maxWidth <= 0f) return;

        float textWidth = (float) SakuraTextRenderer.getWidth(name, 14f);

        // 放得下：不需要滚动，直接画（并清零滚动状态，避免下次进入时从中途开始）
        if (!marquee.get() || textWidth <= maxWidth) {
            slot.marqueeOffset = 0f;
            slot.marqueePause = 0f;
            slot.marqueeDir = 1;
            SakuraTextRenderer.draw(name, x, y, 14f, color);
            return;
        }

        // 行程上限：滚到这里时最后一个字刚好贴住右边界，不会留下空白
        float travel = textWidth - maxWidth;
        float speed = marqueeSpeed.get().floatValue();

        if (slot.marqueePause > 0f) {
            slot.marqueePause -= lastFrameDelta;
        } else {
            slot.marqueeOffset += slot.marqueeDir * speed * lastFrameDelta;
            if (slot.marqueeOffset >= travel) {
                // 到达右端：停一下，然后反向
                slot.marqueeOffset = travel;
                slot.marqueePause = marqueeGap.get().floatValue();
                slot.marqueeDir = -1;
            } else if (slot.marqueeOffset <= 0f) {
                // 回到左端：停一下，再向右
                slot.marqueeOffset = 0f;
                slot.marqueePause = marqueeGap.get().floatValue();
                slot.marqueeDir = 1;
            }
        }

        // 裁到名字区域，按偏移向左滚动
        SakuraTextRenderer.drawClipped(name, x - slot.marqueeOffset, y, 14f, color,
            x, x + maxWidth);
    }

    /** 把 AWT 颜色 + 面板不透明度转成 ARGB。 */
    private static int argb(java.awt.Color color, float presence) {
        int a = Math.max(0, Math.min(255, (int) (color.getAlpha() * presence)));
        return (a << 24) | (color.getRed() << 16) | (color.getGreen() << 8) | color.getBlue();
    }

    // ─── 血量数字的「里程表」滚动 ──────────────────────────

    /**
     * 逐字符绘制一行文本，其中<b>数字</b>按里程表方式上下滚动，其它字符（{@code . / %}）原样绘制。
     *
     * <p><b>为什么不能「每位都按自身小数部分滚动」</b>：
     * 血量 19.1 时十位的位置是 {@code 19.1/10 = 1.91}，小数部分 0.91 会让它滚到 91%，
     * 显示出来几乎是「2」——读作「29.1」，完全错误。
     *
     * <p><b>正确做法（与真实里程表齿轮一致）</b>：每位只在「下一位即将进位」的那一小段
     * 区间内滚动，窗口宽度随该位的权重缩小：
     * <pre>
     *   place  = 10^该位指数                  （十位 10、个位 1、十分位 0.1）
     *   pos    = value / place                该位的连续位置
     *   minStep= 10^(-小数位数)                最小可显示变化量
     *   w      = min(1, minStep / place)      该位的进位窗口宽度
     *   t      = clamp((frac(pos) - (1 - w)) / w, 0, 1)
     * </pre>
     * 这样每一档的 {@code t} 恒等（各位<b>完全同步</b>滚动），且数值静止时
     * {@code frac(pos)} 落在窗口外、{@code t = 0}，逐位读数干净：
     * 19.1 读出「19.1」，20.0 读出「20.0」；而 19.91→19.99 时各位一起平滑进位。
     *
     * <p><b>为什么用连续血量驱动，而不是已经四舍五入的文字</b>：
     * 文字是 {@code "19.1"} 这种已定型的字符串，用它反推位置，每一位都落在整数上、
     * {@code frac = 0}，{@code t} 恒为 0 —— 动画根本不会播放。
     * 所以传进来的是连续值 {@code animatedHealth}（每帧 lerp 变化）。
     *
     * <p>但直接用连续值也不行：{@code 19.06} 显示为「19.1」，若按 19.06 定位，
     * 最后一位会停在滚动中途、看起来是「19.0/19.1」两字各露一半。因此把连续值
     * <b>量化到显示精度</b>（{@code round(value / minStep) * minStep}），
     * 再减一个极小量让它稳定落在「上一档刚跳到当前档」的位置：
     * 这样静止时 {@code t = 0}（干净显示 19.1），掉血时才连续滚过去。
     *
     * <p>滚动方向：数值减小时 {@code t} 由 1 回到 0，两个字形一起向下走。
     */
    private void drawRollingNumber(String text, double value, boolean settled,
                                   float x, float y, float size, int color) {
        // 已经停稳：整行原样直绘，不逐位裁剪。
        //
        // 停稳时不裁有两个原因：
        //   1) 裁剪会削掉数字 6 顶部超框的那 0.11px（实测 sf=1）；
        //   2) 停稳时本来也没有滚出去的字形需要裁。
        // 而停稳时逐位读数与文本完全一致（离线验证 398474 帧零失配），所以直绘即正确。
        if (settled) {
            SakuraTextRenderer.draw(text, x, y, size, color);
            return;
        }

        // 滚动中：裁剪带高度 = 行高，滚出去的字形被切掉，
        // 因此不会露到上方的名字行或下方的血条上。
        float bandTop = y;
        float bandBottom = y + size;

        // 只滚动血量部分："/20"（最大值）与 "%"（百分号）原样绘制，不参与滚动，
        // 否则最大血量也会跟着一起滚，读数就乱了。
        int cut = text.indexOf('/');
        if (cut < 0) cut = text.indexOf('%');
        String rollPart = cut < 0 ? text : text.substring(0, cut);
        String staticPart = cut < 0 ? "" : text.substring(cut);

        // 小数位数决定「最小可显示变化量」：血量文本固定 1 位小数，百分比是整数
        int dot = rollPart.indexOf('.');
        int decimals = dot < 0 ? 0 : (rollPart.length() - dot - 1);
        double step = Math.pow(10, -Math.min(decimals, 6));

        // 整数部分的位数（小数点前），用于算最高位的指数
        int intDigits = dot < 0 ? rollPart.length() : dot;

        float penX = x;
        for (int i = 0; i < rollPart.length(); i++) {
            char ch = rollPart.charAt(i);

            // 笔位推进用「原文本字符」的宽度，这样整行宽度与文本一致、右对齐不会抖
            float advance = (float) SakuraTextRenderer.getWidth(String.valueOf(ch), size);

            if (ch < '0' || ch > '9') {
                // 小数点：原样画，不参与滚动
                SakuraTextRenderer.drawClippedRect(String.valueOf(ch), penX, y, size, color,
                    penX, penX + advance, bandTop, bandBottom);
                penX += advance;
                continue;
            }

            // 该位的指数：小数点前按位递减；小数点后为 -(从第一个小数位起的序号)
            int exp = (dot < 0 || i < dot) ? intDigits - 1 - i : -(i - dot);
            double place = Math.pow(10, exp);

            double pos = value / place;
            double n = Math.floor(pos);
            double frac = pos - n;
            double w = Math.min(1.0, step / place);
            double t = w <= 0 ? 0 : (frac - (1.0 - w)) / w;
            t = Math.max(0.0, Math.min(1.0, t));

            // t：当前档（旧值）向上移出，下一档（新值）从下方进入。
            // 掉血时数值减小 → t 由 1 回到 0，条带整体向下走，与掉血直觉一致。
            int curDigit = (int) (((n % 10) + 10) % 10);
            int nextDigit = (curDigit + 1) % 10;

            float curY = y - (float) (t * size);
            float nxtY = y + (float) ((1.0 - t) * size);

            String cur = String.valueOf((char) ('0' + curDigit));
            String nxt = String.valueOf((char) ('0' + nextDigit));
            float curW = (float) SakuraTextRenderer.getWidth(cur, size);
            float nxtW = (float) SakuraTextRenderer.getWidth(nxt, size);

            SakuraTextRenderer.drawClippedRect(cur, penX, curY, size, color,
                penX, penX + curW, bandTop, bandBottom);
            SakuraTextRenderer.drawClippedRect(nxt, penX, nxtY, size, color,
                penX, penX + nxtW, bandTop, bandBottom);

            penX += advance;
        }

        // 静态后缀（/20、%）
        if (!staticPart.isEmpty()) {
            SakuraTextRenderer.draw(staticPart, penX, y, size, color);
        }
    }

    // ─── 血量文本与配色 ────────────────────────────────────

    /**
     * 按**实际像素宽度**截断文本并补省略号。
     *
     * <p>不能用「字符数」判断：字体是比例字体，且中文/英文/符号宽度差很多，
     * 按字符数截断仍会溢出。这里用 {@link SakuraTextRenderer#getWidth} 实测，
     * 从尾部逐个删字符直到放得下，保证一定不越界。
     *
     * @param maxWidth 可用宽度；{@code <= 0} 时返回空串
     */
    private static String fitText(String text, float size, float maxWidth) {
        if (text == null || text.isEmpty() || maxWidth <= 0f) return "";
        if (SakuraTextRenderer.getWidth(text, size) <= maxWidth) return text;

        final String ellipsis = "...";
        float ellipsisWidth = (float) SakuraTextRenderer.getWidth(ellipsis, size);
        if (ellipsisWidth > maxWidth) return "";

        // 从尾部逐个删，直到「正文 + 省略号」放得下
        for (int end = text.length() - 1; end > 0; end--) {
            String candidate = text.substring(0, end) + ellipsis;
            if (SakuraTextRenderer.getWidth(candidate, size) <= maxWidth) return candidate;
        }
        return ellipsis;
    }

    /**
     * 每帧更新血量滚轮的缓动值；**文本与滚轮共用它**。
     *
     * <p><b>为什么必须共用一个值</b>（这里踩过坑，写下来防回归）：
     * 文本是 {@code "19.1"} 这种四舍五入后的字符串，若用它反推滚轮位置，
     * 每一位都落在整数上、进位窗口外，{@code t} 恒为 0 —— 动画根本不会播放。
     * 反过来，若直接用原始血量（任意小数）驱动，静止时滚轮会停在半格上
     * （例如血量 19.06 显示「19.1」，滚轮却停在 19.0/19.1 之间），读数发虚。
     * 所以先把值缓动到<b>显示精度</b>（{@code round(v/step)*step}），
     * 再用它同时生成文本和滚轮位置：静止时精确等于某一档，
     * 变化时连续地从旧档走到新档，两端都对。
     *
     * <p>只在渲染前调用一次；重复调用会让缓动在一帧里走两步。
     */
    private void updateRollValue(TargetSlot slot) {
        float target = rollTarget(slot);
        float step = rollStep(slot);

        // 关闭「血量滚动」时不做缓动：直接等于目标档位。
        // 否则文字读的是这个缓动值，关掉开关后数字仍会慢半拍地追上去。
        // （量化到档位不影响显示：hpText 本来就按 %.1f / %.0f 格式化。）
        if (!hpRoll.get()) {
            slot.rollValue = target;
            slot.rollPercent = hpDisplay.get() == HpDisplay.Percentage;
            return;
        }

        // 显示格式切换（血量 ⇄ 百分比）时单位变了：血量 19.1 与百分数 95 是同一状态的
        // 两种写法，直接缓动会从「19%」一路滚到「95%」，短暂显示无意义的数据。
        // 所以切换格式时直接吸附到新档位。
        boolean percentNow = hpDisplay.get() == HpDisplay.Percentage;
        if (slot.rollPercent != percentNow) {
            slot.rollPercent = percentNow;
            slot.rollValue = target;
            return;
        }

        if (Float.isNaN(slot.rollValue)) {
            slot.rollValue = target;
            return;
        }
        if (slot.rollValue == target) return;

        slot.rollValue += (target - slot.rollValue) * Math.min(1f, ROLL_EASE * lastFrameDelta);
        // 足够接近就吸附到档位，否则会一直悬在半格上、数字看起来是虚的
        if (Math.abs(slot.rollValue - target) < step * 1e-3f) slot.rollValue = target;
    }

    /**
     * 滚动的目标值 —— 必须落在**显示单位**上，且量化到显示精度。
     *
     * <p>两点都容易写错，各踩过一次：
     * <ul>
     *   <li><b>单位</b>：百分比模式的显示值是 0~100 的百分数；若直接送血量（0~20），
     *       满血 20/20 会被格式化成「20%」而不是「100%」。</li>
     *   <li><b>量化</b>：必须 {@code round(v/step)*step}。若用连续的原始血量，
     *       静止时滚轮会停在半格上（例如血量 19.06 显示「19.1」，
     *       滚轮却停在 19.0/19.1 之间），数字看起来是糊的。</li>
     * </ul>
     */
    private float rollTarget(TargetSlot slot) {
        float step = rollStep(slot);
        float display = slot.animatedHealth;
        if (hpDisplay.get() == HpDisplay.Percentage) {
            float max = Math.max(0.01f, slot.entity != null ? slot.entity.getMaxHealth() : 20f);
            display = Math.max(0f, Math.min(100f, slot.animatedHealth / max * 100f));
        }
        return Math.round(display / step) * step;
    }

    /** 当前显示格式的数值步长：血量是一位小数（0.1），百分比是整数（1）。 */
    private float rollStep(TargetSlot slot) {
        return hpDisplay.get() == HpDisplay.Percentage ? 1f : 0.1f;
    }

    /** 缓动值是否已停在目标档位上（停稳时整行直绘、不裁剪）。 */
    private boolean isRollSettled(TargetSlot slot) {
        return slot.rollValue == rollTarget(slot);
    }

    /**
     * 血量文本。读 {@link #updateRollValue} 算好的缓动值，
     * 保证「显示的数字」与「滚轮停的位置」永远一致。
     */
    private String hpText(TargetSlot slot) {
        float max = Math.max(0.01f, slot.entity != null ? slot.entity.getMaxHealth() : 20f);
        float v = Float.isNaN(slot.rollValue) ? rollTarget(slot) : slot.rollValue;
        if (hpDisplay.get() == HpDisplay.Percentage) {
            return String.format("%.0f%%", Math.max(0f, Math.min(100f, v)));
        }
        if (hpDisplay.get() == HpDisplay.Both) {
            return String.format("%.1f/%.0f", v, max);
        }
        return String.format("%.1f", v);
    }

    private java.awt.Color healthColor(float percent) {
        if (healthColorMode.get() == HealthColorMode.Gradient) {
            // 绿 → 黄 → 红
            if (percent > 0.5f) {
                float t = (1f - percent) * 2f;
                return new java.awt.Color(
                    (int) (34 + (255 - 34) * t), (int) (197 + (193 - 197) * t), (int) (94 + (7 - 94) * t), 235);
            }
            float t = percent * 2f;
            return new java.awt.Color(
                (int) (255 + (239 - 255) * t), (int) (193 + (68 - 193) * t), (int) (7 + (68 - 7) * t), 235);
        }
        return toAwt(healthColorCustom.get());
    }

    // ─── 物品与药水（在 NanoVG 帧之外用原版物品渲染） ──────

    /**
     * 绘制主手 / 副手 / 护甲，以及数量与耐久。
     *
     * <p>物品图标必须用原版物品渲染（{@code RenderUtils.drawItem}），不能走 NanoVG。
     * 这里用 {@code disableGuiScale=false}：{@code Render2DEvent} 在 unscaledProjection 下派发，
     * 该参数会按 GUI 缩放换算，从而与 NanoVG 的逻辑坐标对齐。
     */
    private void drawItems(Render2DEvent event, LivingEntity target, float x, float y) {
        List<ItemStack> stacks = new ArrayList<>();
        if (showMainHand.get()) stacks.add(target.getMainHandStack());
        if (showOffHand.get()) stacks.add(target.getOffHandStack());
        if (showArmor.get()) {
            for (EquipmentSlot slot : ARMOR_SLOTS) {
                ItemStack stack = target.getEquippedStack(slot);
                if (!stack.isEmpty()) stacks.add(stack);
            }
        }

        // 过滤空物品（主副手为空时跳过，护甲已在上面跳过）
        stacks.removeIf(ItemStack::isEmpty);
        if (stacks.isEmpty()) return;

        // 物品行从**面板左边缘**起画（位于头像下方那一行，不会遮到头像），
        // 并把右侧让给距离文字。
        //
        // 原先从 CONTENT_X（头像右侧）起画：护甲齐全时共 6 件
        // （主手 + 副手 + 头盔/胸甲/护腿/靴子）= 6*16 + 5*1 = 101px，
        // 从 x+53 起几乎铺满整行，最后 1~2 件正好压在右对齐的距离文字上
        // ——代码里「物品行右侧是空的」那个前提，护甲齐全时不成立。
        // 左移后可用宽度从 102px 增到 150px，默认缩放（1.0）下可留出 22px 以上空隙。
        String dist = distanceLabel(target);
        float reservedRight = dist != null
            ? (float) SakuraTextRenderer.getWidth(dist, 9f) + ITEM_DIST_GAP
            : 0f;

        float rowLeft = x + PADDING;
        float available = (x + HUD_WIDTH - PADDING - reservedRight) - rowLeft;

        float gap = 1f;
        float itemSize = 16f * itemScale.get().floatValue();
        float totalWidth = stacks.size() * itemSize + (stacks.size() - 1) * gap;

        // 缩放调大时（itemScale > ~1.2）整行可能放不下：此时按可用宽度等比缩小图标，
        // 保证永远不盖住距离文字。下限取设置的最小值 0.5，避免缩到看不清。
        // 默认 1.0 不会触发这段，图标大小与设置一致。
        if (available > 0f && totalWidth > available) {
            float fitScale = Math.max(0.5f,
                (available - (stacks.size() - 1) * gap) / (stacks.size() * 16f));
            itemSize = 16f * Math.min(itemScale.get().floatValue(), fitScale);
            totalWidth = stacks.size() * itemSize + (stacks.size() - 1) * gap;
        }

        float startX = rowLeft;
        float startY = y + ITEM_Y;
        float drawScale = itemSize / 16f;

        for (int i = 0; i < stacks.size(); i++) {
            ItemStack stack = stacks.get(i);
            float ix = startX + i * (itemSize + gap);
            RenderUtils.drawItem(event.drawContext, stack, (int) ix, (int) startY,
                drawScale, showItemCount.get(), null, false);

            if (showDurability.get()) {
                drawDurabilityBar(event, stack, ix, startY, itemSize);
            }
        }
    }

    /**
     * 耐久条：画在物品格子下方，颜色取自原版 {@code ItemStack.getItemBarColor()}。
     *
     * <p>用 {@code DrawContext.fill} 而不是 {@code Renderer2D}：{@code Render2DEvent} 期间
     * {@code DrawContext} 与 NanoVG 同处逻辑（GUI 缩放）坐标，而 {@code Renderer2D} 走的是
     * unscaledProjection 的物理像素，混用会导致坐标错位。
     */
    private void drawDurabilityBar(Render2DEvent event, ItemStack stack, float x, float y, float itemSize) {
        if (!stack.isItemBarVisible()) return;

        int step = stack.getItemBarStep();
        int max = 13;
        if (step <= 0) return;

        int color = stack.getItemBarColor();
        // ItemStack.getItemBarColor 返回 RGB，这里补上不透明度
        int barColor = 0xFF000000 | (color & 0xFFFFFF);

        float barY = y + itemSize + 1f;

        event.drawContext.fill((int) x, (int) barY, (int) (x + itemSize), (int) (barY + 2f), 0xC0000000);
        event.drawContext.fill((int) x, (int) barY,
            (int) (x + itemSize * (step / (float) max)), (int) (barY + 2f), barColor);
    }

    // ═══════════════════════════════════════════════════════
    //  透视标记
    // ═══════════════════════════════════════════════════════

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (mc.player == null || mc.world == null) return;

        // 本帧唯一的目标解析点：Render3DEvent 先于 Render2DEvent 触发，
        // 2D 侧直接复用结果，从而让准星停留计时每帧只推进一次。
        float frameDelta = (float) event.frameTime;
        lastFrameDelta = (frameDelta > 0f && frameDelta < 0.5f) ? frameDelta : 1f / 60f;

        resolveTargetsForFrame(frameDelta);

        // 每个槽位独立推进自己的入场/出场动画
        for (TargetSlot slot : slots) {
            updateAnimation(slot, lastFrameDelta);
        }

        if (!espEnabled.get()) return;

        // 只为「本帧真实存在」的目标画锁定标记；出场中的槽位用自身 presence 收尾
        boolean anyVisible = false;
        for (TargetSlot slot : slots) {
            if (slot.glitchAnim.isVisible() && slot.entity != null) {
                anyVisible = true;
                break;
            }
        }
        if (!anyVisible) return;

        if (!ensureTextures()) return;

        updateRotation();

        for (TargetSlot slot : slots) {
            if (!slot.glitchAnim.isVisible()) continue;
            LivingEntity target = slot.entity;
            if (!isDrawable(target)) continue;
            drawEspFor(event, slot, target);
        }
    }

    /** 为单个目标绘制旋转锁定标记。 */
    private void drawEspFor(Render3DEvent event, TargetSlot slot, LivingEntity target) {
        // 插值出目标中心（绝对世界坐标），与原版一致：ey 再加身高的一半
        double tx = MathHelper.lerp(event.tickDelta, target.lastRenderX, target.getX());
        double ty = MathHelper.lerp(event.tickDelta, target.lastRenderY, target.getY()) + target.getHeight() * 0.5;
        double tz = MathHelper.lerp(event.tickDelta, target.lastRenderZ, target.getZ());

        float half = espSize.get().floatValue() * 0.5f;

        // 入场/出场动画：标记由小放大 + 淡入（带轻微回弹，像「跳」进来）
        float presence = glitchEnabled.get() ? slot.glitchAnim.presence() : 1f;
        if (presence < 0.999f) {
            float jump = slot.glitchAnim.jumpScale(espScaleAnim.get().floatValue());
            half *= Math.max(0f, jump);
        }

        // 公告板四元数：先抵消相机偏航/俯仰（→ 正对相机），再绕视线自转。
        // 乘序与 Sakura 原版一致：Ry(-yaw) * Rx(pitch) * Rz(rotation)。
        var camera = mc.gameRenderer.getCamera();
        var billboard = new org.joml.Quaternionf()
            .rotateY(org.joml.Math.toRadians(-camera.getYaw()))
            .rotateX(org.joml.Math.toRadians(camera.getPitch()))
            .rotateZ(org.joml.Math.toRadians(rotation));

        boolean rainbow = colorMode.get() == ColorMode.Rainbow;
        Color c1 = toMeteor(getColorForProgress(0f));
        Color c2 = toMeteor(getColorForProgress(0.25f));
        Color c3 = toMeteor(getColorForProgress(0.5f));
        Color c4 = toMeteor(getColorForProgress(0.75f));

        if (rainbow) {
            // 原版 Rainbow 模式顶点色为纯白，颜色来自贴图本身
            c1 = c2 = c3 = c4 = Color.WHITE;
        }

        // 入场/出场淡入淡出：把 presence 乘进顶点色 alpha
        if (presence < 0.999f) {
            c1 = scaleAlpha(c1, presence);
            c2 = scaleAlpha(c2, presence);
            c3 = scaleAlpha(c3, presence);
            c4 = scaleAlpha(c4, presence);
        }

        Texture texture = rainbow ? target1Texture : targetTexture;

        // 优先使用世界空间贴图管线：几何体直接落在目标身上，最贴近原版效果。
        // 该管线由引擎懒编译，编译失败时引擎返回 ShaderProgram.INVALID（不会抛异常），
        // 因此这里主动预编译一次来判断可用性，不可用就回退到屏幕空间方案。
        if (!worldPipelineChecked) {
            worldPipelineChecked = true;
            if (!ImgRenderPipelines.isWorldTexturedAvailable()) {
                worldPipelineBroken = true;
                warning("世界空间透视管线无法编译（着色器缺失或驱动不支持），已回退到屏幕空间绘制。");
            }
        }

        if (!worldPipelineBroken) {
            try {
                renderEspWorld(event, tx, ty, tz, half, billboard, texture, c1, c2, c3, c4);
                return;
            } catch (Throwable t) {
                worldPipelineBroken = true;
                warning("世界空间透视绘制失败，已回退到屏幕空间绘制：%s", t.getMessage());
                // 本帧不再尝试回退：若异常发生在 MeshRenderer.end() 内部，
                // MeshRenderer 的单例状态会停留在「未结束」，同一帧再调 begin() 会抛
                // IllegalStateException 并连累其它模块。下一帧 worldPipelineBroken 已为 true，
                // 会直接走屏幕空间方案。
                return;
            }
        }

        renderEspScreenSpace(target, half, texture, c1, c2, c3, c4);
    }

    /** 方案 A：自定义 WORLD_TEXTURED 管线，几何体位于世界空间。 */
    private void renderEspWorld(Render3DEvent event,
                                double tx, double ty, double tz, float half,
                                org.joml.Quaternionf billboard, Texture texture,
                                Color c1, Color c2, Color c3, Color c4) {
        drawEspQuad(event, tx, ty, tz, half, billboard, texture, c1, c2, c3, c4);
    }

    /** 绘制透视标记四边形。 */
    private void drawEspQuad(Render3DEvent event,
                             double tx, double ty, double tz, float half,
                             org.joml.Quaternionf billboard, Texture texture,
                             Color c1, Color c2, Color c3, Color c4) {
        var pipeline = ImgRenderPipelines.WORLD_TEXTURED;

        // MeshBuilder 会剥离相机 X/Z、MeshRenderer 会补回 Y，两者合起来把「绝对世界坐标」
        // 转成相机相对坐标。因此这里直接传绝对坐标（目标中心 + 旋转后的局部偏移）。
        var mesh = new MeshBuilder(pipeline);
        mesh.begin();
        // 必须先生成缓冲区容量，否则 vec3/vec2/color 会写入 null 指针导致 JVM 崩溃
        mesh.ensureQuadCapacity();
        mesh.quad(
            quadVertex(mesh, billboard, tx, ty, tz, -half, half).vec2(0f, 0f).color(c1).next(),
            quadVertex(mesh, billboard, tx, ty, tz, -half, -half).vec2(0f, 1f).color(c2).next(),
            quadVertex(mesh, billboard, tx, ty, tz, half, -half).vec2(1f, 1f).color(c3).next(),
            quadVertex(mesh, billboard, tx, ty, tz, half, half).vec2(1f, 0f).color(c4).next()
        );
        mesh.end();

        MeshRenderer.begin()
            .attachments(mc.getFramebuffer())
            .pipeline(pipeline)
            .mesh(mesh, event.matrices)
            .sampler("u_Texture", texture.getGlTextureView(), texture.getSampler())
            .end();
    }

    /**
     * 方案 B（回退）：把目标投影到屏幕，再用 Meteor 现成的 UI_TEXTURED 管线绘制。
     * 位置正确且不依赖自定义管线，但贴图尺寸随距离缩放的行为由 NametagUtils 决定。
     */
    private void renderEspScreenSpace(LivingEntity target, float half, Texture texture,
                                      Color c1, Color c2, Color c3, Color c4) {
        NametagUtils.to2D(espPos, espSize.get(), true, true);
        NametagUtils.begin(espPos);
        try {
            double rad = Math.toRadians(rotation);
            double cos = Math.cos(rad);
            double sin = Math.sin(rad);
            double h = half * 0.5;

            var mesh = new MeshBuilder(MeteorRenderPipelines.UI_TEXTURED);
            mesh.begin();
            // 同上：写顶点前必须分配容量
            mesh.ensureQuadCapacity();
            mesh.quad(
                mesh.vec2(-h * cos + h * sin, -h * sin - h * cos).vec2(0f, 0f).color(c1).next(),
                mesh.vec2(-h * cos - h * sin, -h * sin + h * cos).vec2(0f, 1f).color(c2).next(),
                mesh.vec2(h * cos - h * sin, h * sin + h * cos).vec2(1f, 1f).color(c3).next(),
                mesh.vec2(h * cos + h * sin, h * sin - h * cos).vec2(1f, 0f).color(c4).next()
            );
            mesh.end();

            MeshRenderer.begin()
                .attachments(mc.getFramebuffer())
                .pipeline(MeteorRenderPipelines.UI_TEXTURED)
                .mesh(mesh)
                .sampler("u_Texture", texture.getGlTextureView(), texture.getSampler())
                .end();
        } finally {
            NametagUtils.end();
        }
    }

    /** 把公告板局部角点旋转后平移到目标中心，写成绝对世界坐标。 */
    private static MeshBuilder quadVertex(MeshBuilder mesh, org.joml.Quaternionf billboard,
                                          double tx, double ty, double tz, float lx, float ly) {
        var offset = new org.joml.Vector3f(lx, ly, 0f).rotate(billboard);
        return mesh.vec3(tx + offset.x, ty + offset.y, tz + offset.z);
    }

    private boolean ensureTextures() {
        if (texturesFailed) return false;
        if (targetTexture != null && target1Texture != null) return true;
        if (!RenderSystem.isOnRenderThread()) return false;

        targetTexture = Texture.readResource("/assets/invincible_machine_gun/textures/particles/target.png",
            false, FilterMode.LINEAR);
        target1Texture = Texture.readResource("/assets/invincible_machine_gun/textures/particles/target1.png",
            false, FilterMode.LINEAR);

        if (targetTexture == null || target1Texture == null) {
            texturesFailed = true;
            error("透视贴图加载失败，已停止绘制透视标记。");
            releaseTextures();
            return false;
        }
        return true;
    }

    /** 与原版一致：按进度（0~1）取顶点颜色。 */
    private java.awt.Color getColorForProgress(float progress) {
        switch (colorMode.get()) {
            case Rainbow -> {
                float hue = (progress + System.currentTimeMillis() / 5000f) % 1f;
                return java.awt.Color.getHSBColor(hue, 0.8f, 1f);
            }
            case Wave -> {
                float wave = (float) Math.sin((progress * Math.PI * 2)
                    + (System.currentTimeMillis() / 1000f * waveSpeed.get()));
                wave = (wave + 1f) / 2f;
                return ColorUtil.interpolateColor(toAwt(espColor1.get()), toAwt(espColor2.get()), wave);
            }
            case Custom -> {
                return ColorUtil.interpolateColor(toAwt(espColor1.get()), toAwt(espColor2.get()), progress);
            }
        }
        return java.awt.Color.WHITE;
    }

    // ─── 颜色转换 ─────────────────────────────────────────

    private static java.awt.Color toAwt(SettingColor color) {
        return new java.awt.Color(color.r, color.g, color.b, color.a);
    }

    private static Color toMeteor(java.awt.Color color) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha());
    }

    /** {@link SettingColor} → Meteor {@link Color}（设置项直接转，不经 AWT）。 */
    private static Color settingToMeteor(SettingColor color) {
        return new Color(color.r, color.g, color.b, color.a);
    }

    /** 按比例缩放 alpha（用于入场/出场淡入淡出）。 */
    private static Color scaleAlpha(Color color, float factor) {
        return new Color(color.r, color.g, color.b,
            Math.max(0, Math.min(255, (int) (color.a * factor))));
    }
}
