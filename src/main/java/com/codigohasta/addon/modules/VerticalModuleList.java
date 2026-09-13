package com.codigohasta.addon.modules;

import com.codigohasta.addon.AddonTemplate;
import com.codigohasta.addon.utils.ScreenVertexTransform;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.renderer.Renderer2D;
import meteordevelopment.meteorclient.renderer.text.TextRenderer;
import meteordevelopment.meteorclient.renderer.text.VanillaTextRenderer;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Vertical module list for IMG / Meteor 1.21.11.
 *
 * <p>Each enabled module occupies one column. CJK text flows top-to-bottom. Latin runs
 * stay as complete words and are rotated clockwise by 90 degrees, so the top of every
 * Latin glyph points to the right instead of stacking letters one per row.
 * Visual effects remain independently configurable so users can keep the HUD restrained
 * or enable high-energy animation combinations.</p>
 *
 * <p>Custom Font support is inherited from Meteor's TextRenderer.get(). The
 * small reflection bridge in beginText() supports both the older 1.21.11
 * snapshot signature begin(scale, scaleOnly, big) and the newer signature
 * begin(graphics, scale, scaleOnly, big).</p>
 */
public class VerticalModuleList extends Module {
    // Keep the settings screen intentionally sectional. Most high-density effect controls live
    // in their own collapsed groups so the normal layout/style controls stay readable.
    private final SettingGroup sgLayout = settings.createGroup("位置与排版");
    private final SettingGroup sgStyle = settings.createGroup("文字与外观");
    private final SettingGroup sgBackground = settings.createGroup("背景");
    private final SettingGroup sgAnimation = settings.createGroup("入场与打字");
    private final SettingGroup sgMotion = settings.createGroup("持续动态");
    private final SettingGroup sgGlitch = settings.createGroup("故障与解码");
    private final SettingGroup sgRobocop = settings.createGroup("机械战警锁定");
    private final SettingGroup sgAdvanced = settings.createGroup("高级持续特效");
    private final SettingGroup sgRandom = settings.createGroup("随机瞬发");
    private final SettingGroup sgBurst = settings.createGroup("扩展瞬发特效");

    // -------------------- Layout --------------------

    private final Setting<Side> side = sgLayout.add(new EnumSetting.Builder<Side>()
        .name("排列侧")
        .description("从屏幕左侧或右侧开始排列模块列。")
        .defaultValue(Side.Right)
        .build()
    );

    private final Setting<VerticalAnchor> verticalAnchor = sgLayout.add(new EnumSetting.Builder<VerticalAnchor>()
        .name("纵向锚点")
        .description("顶部从上往下排；底部从屏幕下方反向往上排，模块头符号也会跟随翻到底部。")
        .defaultValue(VerticalAnchor.Top)
        .build()
    );

    private final Setting<Integer> marginX = sgLayout.add(new IntSetting.Builder()
        .name("X边距")
        .description("模块列表距离屏幕左右边缘的距离。")
        .defaultValue(18)
        .sliderRange(0, 500)
        .build()
    );

    private final Setting<Integer> marginY = sgLayout.add(new IntSetting.Builder()
        .name("Y边距")
        .description("距离所选顶部/底部锚点的垂直边距。")
        .defaultValue(22)
        .sliderRange(0, 1000)
        .build()
    );

    private final Setting<Integer> offsetX = sgLayout.add(new IntSetting.Builder()
        .name("X偏移")
        .description("在基础边距上追加水平偏移，支持正负值。")
        .defaultValue(0)
        .sliderRange(-1000, 1000)
        .build()
    );

    private final Setting<Integer> offsetY = sgLayout.add(new IntSetting.Builder()
        .name("Y偏移")
        .description("在基础Y位置上追加垂直偏移，支持正负值。")
        .defaultValue(0)
        .sliderRange(-1000, 1000)
        .build()
    );

    private final Setting<LatinLayout> latinLayout = sgLayout.add(new EnumSetting.Builder<LatinLayout>()
        .name("英文方向")
        .description("正常模式把完整英文/数字词组顺时针旋转90°：字母顶部朝右、底部朝左；不会拆成一字母一行。")
        .defaultValue(LatinLayout.Normal)
        .build()
    );

    private final Setting<Double> scale = sgLayout.add(new DoubleSetting.Builder()
        .name("字体大小")
        .description("Meteor / Custom Font 的渲染缩放。")
        .defaultValue(1.0)
        .sliderRange(0.45, 2.5)
        .build()
    );

    private final Setting<Double> charSpacing = sgLayout.add(new DoubleSetting.Builder()
        .name("字符间距")
        .description("同一模块列中每个竖排字符之间的额外间距。")
        .defaultValue(1.5)
        .sliderRange(-2.0, 14.0)
        .build()
    );

    private final Setting<Double> columnSpacing = sgLayout.add(new DoubleSetting.Builder()
        .name("列间距")
        .description("相邻模块列之间的距离。")
        .defaultValue(6.0)
        .sliderRange(0.0, 30.0)
        .build()
    );

    private final Setting<Double> fixedColumnWidth = sgLayout.add(new DoubleSetting.Builder()
        .name("固定列宽")
        .description("0 为自动宽度；大于 0 时强制使用指定列宽。")
        .defaultValue(0.0)
        .sliderRange(0.0, 60.0)
        .build()
    );

    private final Setting<Integer> maxColumns = sgLayout.add(new IntSetting.Builder()
        .name("最大列数")
        .description("最多同时显示多少个模块。")
        .defaultValue(24)
        .sliderRange(1, 64)
        .build()
    );

    private final Setting<Integer> columnStaggerMs = sgLayout.add(new IntSetting.Builder()
        .name("列错峰毫秒")
        .description("多个模块同时出现时，每列入场的时间错峰。0 为同步。")
        .defaultValue(24)
        .sliderRange(0, 250)
        .build()
    );

    private final Setting<SortMode> sortMode = sgLayout.add(new EnumSetting.Builder<SortMode>()
        .name("排序")
        .description("模块列的排列方式。")
        .defaultValue(SortMode.Recent)
        .build()
    );

    private final Setting<Boolean> onlyBound = sgLayout.add(new BoolSetting.Builder()
        .name("仅显示绑定模块")
        .description("只显示设置了按键绑定的模块。")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> additionalInfo = sgLayout.add(new BoolSetting.Builder()
        .name("显示额外信息")
        .description("把模块的模式等附加信息一起竖排显示。")
        .defaultValue(false)
        .build()
    );

    // -------------------- Style --------------------

    private final Setting<Boolean> shadow = sgStyle.add(new BoolSetting.Builder()
        .name("字体阴影")
        .description("为主文字绘制 Meteor 字体阴影。")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> gradient = sgStyle.add(new BoolSetting.Builder()
        .name("动态渐变")
        .description("在两个自定义颜色之间进行竖向流动渐变。")
        .defaultValue(false)
        .build()
    );

    private final Setting<SettingColor> textColorA = sgStyle.add(new ColorSetting.Builder()
        .name("文字颜色A")
        .defaultValue(new SettingColor(235, 240, 248, 255))
        .build()
    );

    private final Setting<SettingColor> textColorB = sgStyle.add(new ColorSetting.Builder()
        .name("文字颜色B")
        .defaultValue(new SettingColor(115, 185, 255, 255))
        .visible(gradient::get)
        .build()
    );

    private final Setting<Double> gradientSpeed = sgStyle.add(new DoubleSetting.Builder()
        .name("渐变流速")
        .defaultValue(0.22)
        .sliderRange(0.0, 2.0)
        .visible(gradient::get)
        .build()
    );

    private final Setting<Boolean> showSymbol = sgStyle.add(new BoolSetting.Builder()
        .name("模块头符号")
        .description("每一列顶部显示一个符号。")
        .defaultValue(true)
        .build()
    );

    private final Setting<SymbolMode> symbolMode = sgStyle.add(new EnumSetting.Builder<SymbolMode>()
        .name("符号模式")
        .defaultValue(SymbolMode.Cycle)
        .visible(showSymbol::get)
        .build()
    );

    private final Setting<String> fixedSymbol = sgStyle.add(new StringSetting.Builder()
        .name("固定符号")
        .description("固定符号模式使用的字符。")
        .defaultValue("◆")
        .visible(() -> showSymbol.get() && symbolMode.get() == SymbolMode.Fixed)
        .build()
    );

    private final Setting<String> symbolPool = sgStyle.add(new StringSetting.Builder()
        .name("符号池")
        .description("循环符号模式使用的符号。推荐用 | 分隔，可使用多字符符号；Auto 字体会优先用 Minecraft 原生字体提高兼容性。")
        .defaultValue("◆ | ◇ | ✦ | ✧ | ▸ | ◈ | + | > | # | * | [] | <>")
        .visible(() -> showSymbol.get() && symbolMode.get() == SymbolMode.Cycle)
        .build()
    );

    private final Setting<SymbolRendererMode> symbolRendererMode = sgStyle.add(new EnumSetting.Builder<SymbolRendererMode>()
        .name("符号字体")
        .description("Auto 优先使用 Minecraft 原生字体绘制符号，解决 Custom Font 缺少 Unicode 字形的问题。")
        .defaultValue(SymbolRendererMode.Auto)
        .visible(showSymbol::get)
        .build()
    );

    private final Setting<SettingColor> symbolColor = sgStyle.add(new ColorSetting.Builder()
        .name("符号颜色")
        .defaultValue(new SettingColor(90, 190, 255, 255))
        .visible(showSymbol::get)
        .build()
    );

    // -------------------- Background --------------------

    private final Setting<BackgroundMode> backgroundMode = sgBackground.add(new EnumSetting.Builder<BackgroundMode>()
        .name("背景模式")
        .description("无背景、每列独立背景或整个列表统一背景。")
        .defaultValue(BackgroundMode.Column)
        .build()
    );

    private final Setting<SettingColor> backgroundColor = sgBackground.add(new ColorSetting.Builder()
        .name("背景颜色")
        .defaultValue(new SettingColor(7, 10, 16, 105))
        .visible(() -> backgroundMode.get() != BackgroundMode.None)
        .build()
    );

    private final Setting<SettingColor> accentColor = sgBackground.add(new ColorSetting.Builder()
        .name("强调线颜色")
        .defaultValue(new SettingColor(72, 170, 255, 165))
        .visible(() -> backgroundMode.get() != BackgroundMode.None)
        .build()
    );

    private final Setting<Double> paddingX = sgBackground.add(new DoubleSetting.Builder()
        .name("背景横向留白")
        .defaultValue(4.0)
        .sliderRange(0.0, 20.0)
        .visible(() -> backgroundMode.get() != BackgroundMode.None)
        .build()
    );

    private final Setting<Double> paddingY = sgBackground.add(new DoubleSetting.Builder()
        .name("背景纵向留白")
        .defaultValue(4.0)
        .sliderRange(0.0, 20.0)
        .visible(() -> backgroundMode.get() != BackgroundMode.None)
        .build()
    );

    private final Setting<Boolean> accentLine = sgBackground.add(new BoolSetting.Builder()
        .name("锚点强调线")
        .description("顶部排版时画在上沿；底部反向排版时自动翻到下沿。")
        .defaultValue(true)
        .visible(() -> backgroundMode.get() != BackgroundMode.None)
        .build()
    );

    private final Setting<Boolean> backgroundPulse = sgBackground.add(new BoolSetting.Builder()
        .name("背景呼吸")
        .description("背景透明度做非常轻微的呼吸变化。")
        .defaultValue(false)
        .visible(() -> backgroundMode.get() != BackgroundMode.None)
        .build()
    );

    private final Setting<Double> pulseAmount = sgBackground.add(new DoubleSetting.Builder()
        .name("呼吸强度")
        .defaultValue(0.12)
        .sliderRange(0.0, 0.8)
        .visible(() -> backgroundMode.get() != BackgroundMode.None && backgroundPulse.get())
        .build()
    );

    private final Setting<Double> backgroundPulseSpeed = sgBackground.add(new DoubleSetting.Builder()
        .name("呼吸速度")
        .defaultValue(2.2)
        .sliderRange(0.1, 8.0)
        .visible(() -> backgroundMode.get() != BackgroundMode.None && backgroundPulse.get())
        .build()
    );

    // -------------------- Core animation --------------------

    private final Setting<Boolean> animations = sgAnimation.add(new BoolSetting.Builder()
        .name("开关动画")
        .description("总动画开关。关闭后模块直接出现/消失。")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> enterDuration = sgAnimation.add(new DoubleSetting.Builder()
        .name("入场时长")
        .description("模块开启时淡入、归宗等入场动画的基础时长（秒）。")
        .defaultValue(0.48)
        .sliderRange(0.05, 4.0)
        .visible(animations::get)
        .build()
    );

    private final Setting<Double> exitDuration = sgAnimation.add(new DoubleSetting.Builder()
        .name("出场时长")
        .description("模块关闭时淡出、散开等出场动画的基础时长（秒）。")
        .defaultValue(0.34)
        .sliderRange(0.05, 4.0)
        .visible(animations::get)
        .build()
    );

    private final Setting<Boolean> typing = sgAnimation.add(new BoolSetting.Builder()
        .name("打字效果")
        .description("模块开启时逐字出现，关闭时反向逐字消失。")
        .defaultValue(true)
        .visible(animations::get)
        .build()
    );

    private final Setting<Integer> typingEnterInterval = sgAnimation.add(new IntSetting.Builder()
        .name("入场打字间隔")
        .description("模块开启时每个字符出现的间隔毫秒数。数值越小越快。")
        .defaultValue(36)
        .sliderRange(5, 300)
        .visible(() -> animations.get() && typing.get())
        .build()
    );

    private final Setting<Integer> typingExitInterval = sgAnimation.add(new IntSetting.Builder()
        .name("出场退字间隔")
        .description("模块关闭时每个字符反向消失的间隔毫秒数。数值越小越快。")
        .defaultValue(26)
        .sliderRange(5, 300)
        .visible(() -> animations.get() && typing.get())
        .build()
    );

    private final Setting<Boolean> typingCursor = sgAnimation.add(new BoolSetting.Builder()
        .name("打字光标")
        .description("在当前打字位置显示闪烁光标。")
        .defaultValue(false)
        .visible(() -> animations.get() && typing.get())
        .build()
    );

    private final Setting<String> cursorSymbol = sgAnimation.add(new StringSetting.Builder()
        .name("光标符号")
        .defaultValue("▌")
        .visible(() -> animations.get() && typing.get() && typingCursor.get())
        .build()
    );

    // -------------------- Glitch / decrypt --------------------

    private final Setting<Boolean> glitch = sgGlitch.add(new BoolSetting.Builder()
        .name("Glitch故障")
        .description("模块切换时短暂抖动、字符污染并恢复。")
        .defaultValue(true)
        .onChanged(enabled -> {
            if (enabled) previewGlitch();
        })
        .build()
    );

    private final Setting<Boolean> glitchOnTransition = sgGlitch.add(new BoolSetting.Builder()
        .name("切换触发故障")
        .description("模块开启或关闭时对该列触发一次故障。")
        .defaultValue(true)
        .visible(glitch::get)
        .onChanged(enabled -> {
            if (enabled && glitch.get()) previewGlitch();
        })
        .build()
    );

    private final Setting<Double> glitchDuration = sgGlitch.add(new DoubleSetting.Builder()
        .name("故障持续")
        .defaultValue(0.42)
        .sliderRange(0.05, 2.0)
        .visible(glitch::get)
        .build()
    );

    private final Setting<Double> glitchIntensity = sgGlitch.add(new DoubleSetting.Builder()
        .name("故障强度")
        .description("被随机替换/错位字符的比例。")
        .defaultValue(0.24)
        .sliderRange(0.0, 1.0)
        .visible(glitch::get)
        .build()
    );

    private final Setting<Double> glitchJitter = sgGlitch.add(new DoubleSetting.Builder()
        .name("字符抖动")
        .defaultValue(1.25)
        .sliderRange(0.0, 8.0)
        .visible(glitch::get)
        .build()
    );

    private final Setting<Integer> glitchRefreshMs = sgGlitch.add(new IntSetting.Builder()
        .name("故障刷新速度")
        .description("故障字符和抖动重新随机的间隔毫秒数。越小越躁动。")
        .defaultValue(34)
        .sliderRange(12, 180)
        .visible(glitch::get)
        .build()
    );

    private final Setting<String> glitchChars = sgGlitch.add(new StringSetting.Builder()
        .name("故障字符")
        .description("故障/解码时随机使用的字符池。")
        .defaultValue("#@$%&!?/\\<>01ZX+-=")
        .visible(glitch::get)
        .build()
    );

    private final Setting<Boolean> decrypt = sgGlitch.add(new BoolSetting.Builder()
        .name("解码字符")
        .description("真实文字稳定前先显示短暂随机字符，类似 Scramble/Decrypt。")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> rgbSplit = sgGlitch.add(new BoolSetting.Builder()
        .name("RGB分离")
        .description("故障期间绘制红/青错位残像。")
        .defaultValue(true)
        .visible(glitch::get)
        .build()
    );

    private final Setting<Double> rgbDistance = sgGlitch.add(new DoubleSetting.Builder()
        .name("RGB偏移")
        .defaultValue(1.15)
        .sliderRange(0.2, 6.0)
        .visible(() -> glitch.get() && rgbSplit.get())
        .build()
    );

    // -------------------- Motion --------------------

    private final Setting<Boolean> parallax = sgMotion.add(new BoolSetting.Builder()
        .name("视角惯性")
        .description("转动视角时 HUD 轻微延迟偏移，然后阻尼回正。")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> parallaxX = sgMotion.add(new DoubleSetting.Builder()
        .name("视角X强度")
        .defaultValue(0.33)
        .sliderRange(-2.0, 2.0)
        .visible(parallax::get)
        .build()
    );

    private final Setting<Double> parallaxY = sgMotion.add(new DoubleSetting.Builder()
        .name("视角Y强度")
        .defaultValue(0.28)
        .sliderRange(-2.0, 2.0)
        .visible(parallax::get)
        .build()
    );

    private final Setting<Double> parallaxDamping = sgMotion.add(new DoubleSetting.Builder()
        .name("惯性阻尼")
        .description("越大回正越快。")
        .defaultValue(8.0)
        .sliderRange(1.0, 24.0)
        .visible(parallax::get)
        .build()
    );

    private final Setting<Double> maxParallax = sgMotion.add(new DoubleSetting.Builder()
        .name("最大视角偏移")
        .defaultValue(13.0)
        .sliderRange(1.0, 50.0)
        .visible(parallax::get)
        .build()
    );

    private final Setting<Boolean> wave = sgMotion.add(new BoolSetting.Builder()
        .name("列波浪")
        .description("相邻模块列按相位差上下波动。")
        .defaultValue(false)
        .build()
    );

    private final Setting<Double> waveAmplitude = sgMotion.add(new DoubleSetting.Builder()
        .name("波浪幅度")
        .defaultValue(3.0)
        .sliderRange(0.0, 18.0)
        .visible(wave::get)
        .build()
    );

    private final Setting<Double> waveSpeed = sgMotion.add(new DoubleSetting.Builder()
        .name("波浪速度")
        .defaultValue(2.0)
        .sliderRange(0.05, 8.0)
        .visible(wave::get)
        .build()
    );

    private final Setting<Double> wavePhase = sgMotion.add(new DoubleSetting.Builder()
        .name("列相位差")
        .defaultValue(0.68)
        .sliderRange(0.0, 3.14)
        .visible(wave::get)
        .build()
    );

    // -------------------- Advanced --------------------

    private final Setting<Boolean> converge = sgAdvanced.add(new BoolSetting.Builder()
        .name("万剑归宗")
        .description("模块从屏幕不同方向高速归位；关闭时反向散开。")
        .defaultValue(false)
        .build()
    );

    private final Setting<Double> convergeDistance = sgAdvanced.add(new DoubleSetting.Builder()
        .name("归宗距离")
        .defaultValue(135.0)
        .sliderRange(20.0, 600.0)
        .visible(converge::get)
        .build()
    );

    private final Setting<Boolean> echoTrail = sgAdvanced.add(new BoolSetting.Builder()
        .name("Echo残影")
        .description("移动/归宗期间绘制轻量残影拖尾。")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> echoCopies = sgAdvanced.add(new IntSetting.Builder()
        .name("残影层数")
        .defaultValue(2)
        .sliderRange(1, 5)
        .visible(echoTrail::get)
        .build()
    );

    private final Setting<Double> echoDistance = sgAdvanced.add(new DoubleSetting.Builder()
        .name("残影距离")
        .defaultValue(1.7)
        .sliderRange(0.2, 8.0)
        .visible(echoTrail::get)
        .build()
    );

    private final Setting<Boolean> scanLine = sgAdvanced.add(new BoolSetting.Builder()
        .name("扫描光带")
        .description("独立于背景绘制的高亮扫描光带，可联动文字高亮。")
        .defaultValue(false)
        .build()
    );

    private final Setting<ScanMode> scanMode = sgAdvanced.add(new EnumSetting.Builder<ScanMode>()
        .name("扫描模式")
        .defaultValue(ScanMode.SoftBand)
        .visible(scanLine::get)
        .build()
    );

    private final Setting<ScanDirection> scanDirection = sgAdvanced.add(new EnumSetting.Builder<ScanDirection>()
        .name("扫描方向")
        .defaultValue(ScanDirection.PingPong)
        .visible(scanLine::get)
        .build()
    );

    private final Setting<Double> scanSpeed = sgAdvanced.add(new DoubleSetting.Builder()
        .name("扫描速度")
        .defaultValue(0.72)
        .sliderRange(0.03, 5.0)
        .visible(scanLine::get)
        .build()
    );

    private final Setting<Double> scanBandWidth = sgAdvanced.add(new DoubleSetting.Builder()
        .name("光带宽度")
        .description("扫描光带的纵向宽度。")
        .defaultValue(13.0)
        .sliderRange(1.0, 60.0)
        .visible(scanLine::get)
        .build()
    );

    private final Setting<Double> scanIntensity = sgAdvanced.add(new DoubleSetting.Builder()
        .name("光带亮度")
        .defaultValue(0.72)
        .sliderRange(0.05, 1.0)
        .visible(scanLine::get)
        .build()
    );

    private final Setting<SettingColor> scanColor = sgAdvanced.add(new ColorSetting.Builder()
        .name("光带颜色")
        .defaultValue(new SettingColor(95, 210, 255, 220))
        .visible(scanLine::get)
        .build()
    );

    private final Setting<Boolean> scanColumnPhase = sgAdvanced.add(new BoolSetting.Builder()
        .name("列错峰扫描")
        .description("不同列使用轻微相位差，避免所有光带完全齐平。")
        .defaultValue(true)
        .visible(scanLine::get)
        .build()
    );

    private final Setting<Double> scanPhaseStep = sgAdvanced.add(new DoubleSetting.Builder()
        .name("扫描相位差")
        .defaultValue(0.075)
        .sliderRange(0.0, 0.5)
        .visible(() -> scanLine.get() && scanColumnPhase.get())
        .build()
    );

    private final Setting<Boolean> scanTextHighlight = sgAdvanced.add(new BoolSetting.Builder()
        .name("文字扫亮")
        .description("光带经过字符时同步把文字提亮到光带颜色。")
        .defaultValue(true)
        .visible(scanLine::get)
        .build()
    );

    private final Setting<Double> scanTextStrength = sgAdvanced.add(new DoubleSetting.Builder()
        .name("文字扫亮强度")
        .defaultValue(0.65)
        .sliderRange(0.0, 1.0)
        .visible(() -> scanLine.get() && scanTextHighlight.get())
        .build()
    );

    private final Setting<Boolean> neonFlicker = sgAdvanced.add(new BoolSetting.Builder()
        .name("冷启动闪烁")
        .description("模块出现或随机瞬发时做短促霓虹启动闪烁。")
        .defaultValue(false)
        .build()
    );

    private final Setting<Double> flickerStrength = sgAdvanced.add(new DoubleSetting.Builder()
        .name("闪烁强度")
        .defaultValue(0.28)
        .sliderRange(0.0, 0.8)
        .visible(neonFlicker::get)
        .build()
    );

    private final Setting<Double> flickerDuration = sgAdvanced.add(new DoubleSetting.Builder()
        .name("闪烁时长")
        .defaultValue(0.56)
        .sliderRange(0.08, 2.5)
        .visible(neonFlicker::get)
        .build()
    );

    private final Setting<Boolean> robocopLock = sgRobocop.add(new BoolSetting.Builder()
        .name("机械战警锁定")
        .description("对某一列执行目标捕获：收缩锁定框、十字准星、扫描线和 LOCK 状态。")
        .defaultValue(false)
        .build()
    );

    private final Setting<Double> robocopDuration = sgRobocop.add(new DoubleSetting.Builder()
        .name("锁定时长")
        .defaultValue(1.15)
        .sliderRange(0.25, 4.0)
        .visible(robocopLock::get)
        .build()
    );

    private final Setting<Double> robocopTrackPhase = sgRobocop.add(new DoubleSetting.Builder()
        .name("十字追踪阶段")
        .description("锁定动画前半段用于移动十字准星的时间比例，到达目标后才开始锁定框。")
        .defaultValue(0.52)
        .sliderRange(0.20, 0.80)
        .visible(robocopLock::get)
        .build()
    );

    private final Setting<Double> robocopCrossSize = sgRobocop.add(new DoubleSetting.Builder()
        .name("十字准星长度")
        .description("追踪阶段移动十字线的臂长。")
        .defaultValue(18.0)
        .sliderRange(5.0, 60.0)
        .visible(robocopLock::get)
        .build()
    );

    private final Setting<Boolean> robocopTrackTrail = sgRobocop.add(new BoolSetting.Builder()
        .name("追踪轨迹线")
        .description("显示十字准星从起点移动到目标列的细轨迹线。")
        .defaultValue(true)
        .visible(robocopLock::get)
        .build()
    );

    private final Setting<Double> robocopAcquireDistance = sgRobocop.add(new DoubleSetting.Builder()
        .name("锁定收束距离")
        .defaultValue(18.0)
        .sliderRange(2.0, 80.0)
        .visible(robocopLock::get)
        .build()
    );

    private final Setting<SettingColor> robocopColor = sgRobocop.add(new ColorSetting.Builder()
        .name("锁定颜色")
        .defaultValue(new SettingColor(255, 70, 70, 230))
        .visible(robocopLock::get)
        .build()
    );

    private final Setting<Boolean> robocopLabel = sgRobocop.add(new BoolSetting.Builder()
        .name("锁定状态文字")
        .defaultValue(true)
        .visible(robocopLock::get)
        .build()
    );

    private final Setting<String> robocopLockText = sgRobocop.add(new StringSetting.Builder()
        .name("锁定完成文字")
        .defaultValue("LOCK")
        .visible(() -> robocopLock.get() && robocopLabel.get())
        .build()
    );

    private final Setting<Boolean> hologram = sgAdvanced.add(new BoolSetting.Builder()
        .name("全息切片")
        .description("随机切片让局部字符短促横向错层，形成高级全息信号干扰。")
        .defaultValue(false)
        .build()
    );

    private final Setting<Double> hologramDuration = sgAdvanced.add(new DoubleSetting.Builder()
        .name("全息持续")
        .defaultValue(0.52)
        .sliderRange(0.1, 2.0)
        .visible(hologram::get)
        .build()
    );

    private final Setting<Double> hologramOffset = sgAdvanced.add(new DoubleSetting.Builder()
        .name("全息错层")
        .defaultValue(2.2)
        .sliderRange(0.2, 10.0)
        .visible(hologram::get)
        .build()
    );

    private final Setting<Double> hologramSliceChance = sgAdvanced.add(new DoubleSetting.Builder()
        .name("切片密度")
        .defaultValue(0.32)
        .sliderRange(0.02, 1.0)
        .visible(hologram::get)
        .build()
    );

    private final Setting<SettingColor> hologramColor = sgAdvanced.add(new ColorSetting.Builder()
        .name("全息颜色")
        .defaultValue(new SettingColor(90, 235, 255, 150))
        .visible(hologram::get)
        .build()
    );

    private final Setting<Boolean> pulseFrame = sgAdvanced.add(new BoolSetting.Builder()
        .name("脉冲框")
        .description("从列外侧快速收缩的多层矩形脉冲框。")
        .defaultValue(false)
        .build()
    );

    private final Setting<Double> pulseFrameDuration = sgAdvanced.add(new DoubleSetting.Builder()
        .name("脉冲框时长")
        .defaultValue(0.58)
        .sliderRange(0.12, 2.5)
        .visible(pulseFrame::get)
        .build()
    );

    private final Setting<SettingColor> pulseFrameColor = sgAdvanced.add(new ColorSetting.Builder()
        .name("脉冲框颜色")
        .defaultValue(new SettingColor(125, 200, 255, 190))
        .visible(pulseFrame::get)
        .build()
    );

    // -------------------- Random burst scheduler --------------------

    private final Setting<Boolean> randomBursts = sgRandom.add(new BoolSetting.Builder()
        .name("随机瞬发动画")
        .description("在已开启模块中随机挑列，随机播放勾选的瞬发特效。")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> randomMinDelay = sgRandom.add(new DoubleSetting.Builder()
        .name("随机最短间隔")
        .defaultValue(1.8)
        .sliderRange(0.2, 30.0)
        .visible(randomBursts::get)
        .build()
    );

    private final Setting<Double> randomMaxDelay = sgRandom.add(new DoubleSetting.Builder()
        .name("随机最长间隔")
        .defaultValue(4.8)
        .sliderRange(0.3, 60.0)
        .visible(randomBursts::get)
        .build()
    );

    private final Setting<Integer> randomColumns = sgRandom.add(new IntSetting.Builder()
        .name("随机同时列数")
        .description("每次随机事件最多影响多少列。")
        .defaultValue(1)
        .sliderRange(1, 4)
        .visible(randomBursts::get)
        .build()
    );

    private final Setting<Boolean> randomGlitch = sgRandom.add(new BoolSetting.Builder()
        .name("随机故障")
        .defaultValue(true)
        .visible(() -> randomBursts.get() && glitch.get())
        .build()
    );

    private final Setting<Boolean> randomTyping = sgRandom.add(new BoolSetting.Builder()
        .name("随机打字重放")
        .description("把打字效果加入随机瞬发池：随机选中的已开启模块会从空白重新快速打出一次。")
        .defaultValue(true)
        .visible(() -> randomBursts.get() && typing.get())
        .build()
    );

    private final Setting<Boolean> randomRobocop = sgRandom.add(new BoolSetting.Builder()
        .name("随机机械锁定")
        .defaultValue(true)
        .visible(() -> randomBursts.get() && robocopLock.get())
        .build()
    );

    private final Setting<Boolean> randomHologram = sgRandom.add(new BoolSetting.Builder()
        .name("随机全息切片")
        .defaultValue(true)
        .visible(() -> randomBursts.get() && hologram.get())
        .build()
    );

    private final Setting<Boolean> randomPulseFrame = sgRandom.add(new BoolSetting.Builder()
        .name("随机脉冲框")
        .defaultValue(true)
        .visible(() -> randomBursts.get() && pulseFrame.get())
        .build()
    );

    private final Setting<Boolean> randomFlicker = sgRandom.add(new BoolSetting.Builder()
        .name("随机冷启动闪烁")
        .defaultValue(false)
        .visible(() -> randomBursts.get() && neonFlicker.get())
        .build()
    );

    // -------------------- Extended burst library --------------------

    private final Setting<Boolean> extraBursts = sgBurst.add(new BoolSetting.Builder()
        .name("扩展瞬发特效")
        .description("启用轻量2D扩展动画库。不会额外加载后处理Shader。")
        .defaultValue(true)
        .build()
    );

    private final Setting<BurstPreset> burstPreset = sgBurst.add(new EnumSetting.Builder<BurstPreset>()
        .name("特效预设")
        .description("用预设代替二十多个独立开关，保持设置界面简洁；全部模式会开放完整特效池。")
        .defaultValue(BurstPreset.Balanced)
        .visible(extraBursts::get)
        .build()
    );

    private final Setting<String> customBurstPool = sgBurst.add(new StringSetting.Builder()
        .name("自定义特效池")
        .description("仅自定义预设显示。用英文逗号填写特效ID，例如 Glow,Vhs,Electric,RadarSweep；无效名称会自动忽略。")
        .defaultValue("FadeFlash,Pulse,SlideUp,Bounce,Glow,Chromatic,Warp,Electric,Neon,RadarSweep,DataStream")
        .visible(() -> extraBursts.get() && burstPreset.get() == BurstPreset.Custom)
        .build()
    );

    private final Setting<Double> extraBurstDuration = sgBurst.add(new DoubleSetting.Builder()
        .name("扩展特效时长")
        .description("扩展瞬发特效的统一基础时长。")
        .defaultValue(0.72)
        .sliderRange(0.18, 3.0)
        .visible(extraBursts::get)
        .build()
    );

    private final Setting<Double> extraBurstIntensity = sgBurst.add(new DoubleSetting.Builder()
        .name("扩展特效强度")
        .description("统一控制位移、抖动、碎裂、发光等扩展特效幅度。")
        .defaultValue(0.78)
        .sliderRange(0.10, 2.0)
        .visible(extraBursts::get)
        .build()
    );

    private final Setting<SettingColor> extraBurstColorA = sgBurst.add(new ColorSetting.Builder()
        .name("扩展颜色A")
        .defaultValue(new SettingColor(70, 220, 255, 210))
        .visible(extraBursts::get)
        .build()
    );

    private final Setting<SettingColor> extraBurstColorB = sgBurst.add(new ColorSetting.Builder()
        .name("扩展颜色B")
        .defaultValue(new SettingColor(255, 75, 190, 205))
        .visible(extraBursts::get)
        .build()
    );

    // Runtime state.
    private final Map<Module, EntryState> entries = new HashMap<>();
    private long sequence;
    private long lastFrameNanos;
    private long nextRandomBurstNanos;
    private float lastYaw;
    private float lastPitch;
    private boolean cameraInitialized;
    private double parallaxOffsetX;
    private double parallaxOffsetY;
    private int currentScreenWidth;
    private int currentScreenHeight;
    private transient Render2DEvent currentRenderEvent;

    // TextRenderer API bridge cache. Keep one resolved overload per renderer class so using
    // Custom Font text + Minecraft-font symbols does not rescan reflection methods every frame.
    private final Map<Class<?>, Method> cachedBegin3 = new HashMap<>();
    private final Map<Class<?>, Method> cachedBegin4 = new HashMap<>();
    private final Set<Class<?>> resolvedBeginClasses = new HashSet<>();
    private transient Field cachedGraphicsField;
    private transient Class<?> cachedEventClass;
    private boolean textBridgeWarningShown;

    public VerticalModuleList() {
        super(AddonTemplate.CATEGORY, "竖模块列表", "竖向分列显示已开启模块，看到网易云音乐的竖着的歌词后做的。*特效库限时免费。。。");
    }

    @Override
    public void onActivate() {
        entries.clear();
        sequence = 0L;
        lastFrameNanos = System.nanoTime();
        nextRandomBurstNanos = 0L;
        cameraInitialized = false;
        parallaxOffsetX = 0.0;
        parallaxOffsetY = 0.0;

        long now = lastFrameNanos;
        for (Module module : Modules.get().getAll()) {
            if (shouldDisplay(module) && module.isActive()) {
                EntryState state = new EntryState(module);
                state.active = true;
                state.progress = animations.get() ? 0.0 : 1.0;
                state.sequence = ++sequence;
                state.transitionNanos = now + staggerNanos(state.sequence - 1);
                entries.put(module, state);
                triggerTransitionEffects(state, true, state.transitionNanos);
            }
        }
    }

    @Override
    public void onDeactivate() {
        entries.clear();
        nextRandomBurstNanos = 0L;
        cameraInitialized = false;
    }

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        if (mc.player == null || mc.world == null) return;

        long now = System.nanoTime();
        double dt = lastFrameNanos == 0L ? 1.0 / 60.0 : (now - lastFrameNanos) / 1_000_000_000.0;
        lastFrameNanos = now;
        dt = clamp(dt, 0.0, 0.1);

        currentRenderEvent = event;
        currentScreenWidth = event.screenWidth;
        currentScreenHeight = event.screenHeight;

        syncEntries(now);
        updateParallax(dt);
        updateRandomBursts(now);

        TextRenderer renderer = TextRenderer.get();
        if (!beginText(renderer, event, scale.get())) return;

        TextRenderer symbolRenderer = resolveSymbolRenderer(renderer);
        boolean separateSymbolRenderer = symbolRenderer != renderer;
        if (separateSymbolRenderer && !beginText(symbolRenderer, event, scale.get())) {
            symbolRenderer = renderer;
            separateSymbolRenderer = false;
        }

        try {
            List<EntryState> visible = collectVisible();
            if (visible.isEmpty()) return;

            visible.sort(entryComparator());
            if (visible.size() > maxColumns.get()) visible = new ArrayList<>(visible.subList(0, maxColumns.get()));

            List<RenderEntry> layout = buildLayout(renderer, symbolRenderer, visible, event.screenWidth, event.screenHeight, now);
            if (layout.isEmpty()) return;

            drawGeometryEffects(layout, now);
            drawText(renderer, symbolRenderer, layout, now);
        } finally {
            if (separateSymbolRenderer) symbolRenderer.end();
            renderer.end();
        }

        cleanupEntries(now);
    }

    private void syncEntries(long now) {
        Set<Module> seen = new HashSet<>();

        for (Module module : Modules.get().getAll()) {
            if (!shouldDisplay(module)) continue;
            seen.add(module);

            boolean activeNow = module.isActive();
            EntryState state = entries.get(module);

            if (state == null) {
                if (!activeNow) continue;
                state = new EntryState(module);
                state.active = true;
                state.progress = animations.get() ? 0.0 : 1.0;
                state.sequence = ++sequence;
                state.transitionNanos = now;
                entries.put(module, state);
                triggerTransitionEffects(state, true, now);
                continue;
            }

            if (state.active != activeNow) {
                state.active = activeNow;
                state.transitionNanos = now;
                if (activeNow) state.sequence = ++sequence;
                triggerTransitionEffects(state, activeNow, now);
            }
        }

        for (EntryState state : entries.values()) {
            if (!seen.contains(state.module) && state.active) {
                state.active = false;
                state.transitionNanos = now;
                triggerTransitionEffects(state, false, now);
            }
        }

        for (EntryState state : entries.values()) {
            if (!animations.get()) {
                state.progress = state.active ? 1.0 : 0.0;
                continue;
            }

            double duration = Math.max(0.001, state.active ? enterDuration.get() : exitDuration.get());
            if (typing.get()) {
                int chars = Math.max(1, unitCharacterCount(layoutUnits(displayText(state.module))));
                int interval = state.active ? typingEnterInterval.get() : typingExitInterval.get();
                duration = Math.max(duration, chars * Math.max(1, interval) / 1000.0);
            }
            // On removal, keep the column alive for the longest transition burst. Otherwise a
            // 0.3s fade could erase the column while a 0.7s hologram/lock/burst is still playing.
            if (!state.active) {
                long effectEnd = transitionEffectEndNanos(state);
                if (effectEnd > state.transitionNanos) {
                    duration = Math.max(duration, (effectEnd - state.transitionNanos) / 1_000_000_000.0);
                }
            }
            double elapsed = Math.max(0.0, (now - state.transitionNanos) / 1_000_000_000.0);
            double t = clamp(elapsed / duration, 0.0, 1.0);
            state.progress = state.active ? easeOutCubic(t) : 1.0 - easeInCubic(t);
        }
    }

    private void triggerTransitionEffects(EntryState state, boolean entering, long startNanos) {
        // Transition effects are intentionally symmetrical: anything the user enabled should be
        // able to participate when a column is inserted AND when it is removed. Continuous effects
        // (wave / parallax / scan beam / echo / converge) already run from state.progress, while the
        // burst-style effects below need an explicit trigger timestamp.
        if (glitch.get() && glitchOnTransition.get()) triggerGlitch(state, startNanos);
        if (neonFlicker.get()) triggerFlicker(state, startNanos);
        if (robocopLock.get()) triggerRobocop(state, startNanos);
        if (hologram.get()) triggerHologram(state, startNanos);
        if (pulseFrame.get()) triggerPulseFrame(state, startNanos);

        // Typing itself is driven by transitionNanos/progress, so it already runs in both
        // directions. Extended instant effects previously only appeared from the idle random
        // scheduler; now every add/remove transition also selects one effect from the currently
        // configured preset/custom pool. Across transitions every configured burst is eligible.
        long salt = entering ? 0x4F1BBCDCBFA54001L : 0x9E3779B97F4A7C15L;
        long seed = startNanos ^ ((long) state.module.hashCode() * salt) ^ state.sequence;

        // The random-instant selections are also eligible at the exact moment a module enters or
        // leaves the list, not only while the HUD is idle. One of the enabled instant families is
        // picked per transition so the stack remains readable while still exercising the pool.
        if (randomBursts.get()) {
            List<BurstType> instant = new ArrayList<>();
            if (glitch.get() && randomGlitch.get()) instant.add(BurstType.Glitch);
            if (typing.get() && randomTyping.get()) instant.add(BurstType.Typing);
            if (robocopLock.get() && randomRobocop.get()) instant.add(BurstType.Robocop);
            if (hologram.get() && randomHologram.get()) instant.add(BurstType.Hologram);
            if (pulseFrame.get() && randomPulseFrame.get()) instant.add(BurstType.PulseFrame);
            if (neonFlicker.get() && randomFlicker.get()) instant.add(BurstType.Flicker);
            if (!instant.isEmpty()) {
                int index = Math.min(instant.size() - 1, (int) Math.floor(hashUnit(seed ^ 0xD6E8FEB86659FD93L) * instant.size()));
                triggerBurst(state, instant.get(Math.max(0, index)), startNanos);
            }
        }

        // Extended preset/custom effects use a separate slot so an add/remove always gets one of
        // the configured 2D bursts as well; this fixes the old behavior where they only appeared
        // from the idle random scheduler.
        if (extraBursts.get()) {
            List<BurstType> transitionBursts = new ArrayList<>();
            addPresetBurstTypes(transitionBursts, burstPreset.get());
            if (!transitionBursts.isEmpty()) {
                int index = Math.min(transitionBursts.size() - 1,
                    (int) Math.floor(hashUnit(seed ^ 0xA0761D6478BD642FL) * transitionBursts.size()));
                triggerExtraBurst(state, transitionBursts.get(Math.max(0, index)), startNanos);
            }
        }
    }

    private long staggerNanos(long index) {
        return Math.max(0L, index) * Math.max(0, columnStaggerMs.get()) * 1_000_000L;
    }

    private boolean shouldDisplay(Module module) {
        if (module == null || module == this) return false;
        if (onlyBound.get() && !module.keybind.isSet()) return false;
        return true;
    }

    private List<EntryState> collectVisible() {
        List<EntryState> visible = new ArrayList<>();
        for (EntryState state : entries.values()) {
            if (state.active || state.progress > 0.002) visible.add(state);
        }
        return visible;
    }

    private Comparator<EntryState> entryComparator() {
        return switch (sortMode.get()) {
            case Recent -> Comparator.comparingLong((EntryState e) -> e.sequence).reversed();
            case Alphabetical -> Comparator.comparing(e -> e.module.title.toLowerCase(Locale.ROOT));
            case Longest -> Comparator.comparingInt((EntryState e) -> codePointCount(displayText(e.module))).reversed();
        };
    }

    private List<RenderEntry> buildLayout(TextRenderer renderer, TextRenderer symbolRenderer, List<EntryState> states, int screenWidth, int screenHeight, long now) {
        List<RenderEntry> result = new ArrayList<>(states.size());
        double cursorX = side.get() == Side.Right ? screenWidth - marginX.get() : marginX.get();
        cursorX += offsetX.get();

        boolean bottom = verticalAnchor.get() == VerticalAnchor.Bottom;
        double anchorY = (bottom ? screenHeight - marginY.get() : marginY.get()) + offsetY.get() + parallaxOffsetY;
        double time = now / 1_000_000_000.0;

        for (int column = 0; column < states.size(); column++) {
            EntryState state = states.get(column);
            String text = displayText(state.module);
            List<TextUnit> units = layoutUnits(text);
            int totalCharacters = unitCharacterCount(units);
            String symbol = showSymbol.get() ? symbolFor(state.module) : "";

            int visibleChars = visibleCharacterCount(state, totalCharacters, now);
            double symbolHeight = symbol.isEmpty() ? 0.0 : symbolRenderer.getHeight(shadow.get());
            double symbolWidth = symbol.isEmpty() ? 0.0 : symbolRenderer.getWidth(symbol, shadow.get());

            double widest = symbolWidth;
            double textHeight = symbolHeight;
            int blocks = symbol.isEmpty() ? 0 : 1;
            for (TextUnit unit : units) {
                widest = Math.max(widest, unitWidth(renderer, unit, unit.text));
                if (blocks > 0) textHeight += charSpacing.get();
                textHeight += unitHeight(renderer, unit, unit.text);
                blocks++;
            }
            if (textHeight <= 0.0) textHeight = renderer.getHeight(shadow.get());

            double columnWidth = fixedColumnWidth.get() > 0.01 ? fixedColumnWidth.get() : Math.max(1.0, widest);

            double x;
            if (side.get() == Side.Right) {
                cursorX -= columnWidth;
                x = cursorX;
                cursorX -= columnSpacing.get();
            } else {
                x = cursorX;
                cursorX += columnWidth + columnSpacing.get();
            }

            x += parallaxOffsetX;
            double y = bottom ? anchorY - textHeight : anchorY;
            if (wave.get()) y += Math.sin(time * waveSpeed.get() + column * wavePhase.get()) * waveAmplitude.get();

            double progress = state.progress;
            if (converge.get() && animations.get()) {
                double travel = 1.0 - easeOutQuint(progress);
                double angle = scatterAngle(state.module);
                x += Math.cos(angle) * convergeDistance.get() * travel;
                y += Math.sin(angle) * convergeDistance.get() * travel;
            }

            double alpha = clamp(progress, 0.0, 1.0);
            if (isFlickering(state, now)) {
                double flickerP = effectProgress(now, state.flickerStartNanos, state.flickerUntilNanos);
                long bucket = now / 22_000_000L;
                double noise = hashUnit(state.module.hashCode() * 31L + bucket);
                double envelope = Math.sin(Math.PI * clamp(flickerP, 0.0, 1.0));
                alpha *= 1.0 - flickerStrength.get() * noise * envelope;
            }

            result.add(new RenderEntry(state, units, symbol, visibleChars, totalCharacters, x, y, columnWidth, textHeight,
                symbolHeight, alpha, column, bottom));
        }

        return result;
    }

    private void drawGeometryEffects(List<RenderEntry> layout, long now) {
        boolean drawBackground = backgroundMode.get() != BackgroundMode.None;
        boolean drawScan = scanLine.get();
        boolean drawOverlay = pulseFrame.get() || robocopLock.get() || hasActiveExtraBurst(layout, now);
        if (!drawBackground && !drawScan && !drawOverlay) return;

        Renderer2D.COLOR.begin();

        if (drawBackground) drawBackgroundGeometry(layout, now);
        if (drawScan) drawScanGeometry(layout, now);

        for (RenderEntry entry : layout) {
            if (entry.alpha <= 0.002) continue;
            if (pulseFrame.get() && isPulseFraming(entry.state, now)) drawPulseFrame(entry, now);
            if (robocopLock.get() && isRobocopLocking(entry.state, now)) drawRobocopLock(entry, now);
            drawExtraBurstGeometry(entry, now);
        }

        Renderer2D.COLOR.render();
    }

    private void drawBackgroundGeometry(List<RenderEntry> layout, long now) {
        double pulse = 1.0;
        if (backgroundPulse.get()) {
            pulse -= pulseAmount.get() * (0.5 + 0.5 * Math.sin(now / 1_000_000_000.0 * backgroundPulseSpeed.get()));
        }

        if (backgroundMode.get() == BackgroundMode.Panel) {
            double minX = Double.POSITIVE_INFINITY;
            double minY = Double.POSITIVE_INFINITY;
            double maxX = Double.NEGATIVE_INFINITY;
            double maxY = Double.NEGATIVE_INFINITY;
            double maxAlpha = 0.0;

            for (RenderEntry entry : layout) {
                if (entry.alpha <= 0.002) continue;
                minX = Math.min(minX, entry.x - paddingX.get());
                minY = Math.min(minY, entry.y - paddingY.get());
                maxX = Math.max(maxX, entry.x + entry.width + paddingX.get());
                maxY = Math.max(maxY, entry.y + entry.height + paddingY.get());
                maxAlpha = Math.max(maxAlpha, entry.alpha);
            }

            if (minX != Double.POSITIVE_INFINITY) {
                Renderer2D.COLOR.quad(minX, minY, maxX - minX, maxY - minY, withAlpha(backgroundColor.get(), maxAlpha * pulse));
                if (accentLine.get()) {
                    boolean bottom = verticalAnchor.get() == VerticalAnchor.Bottom;
                    double accentY = bottom ? maxY - 1.0 : minY;
                    Renderer2D.COLOR.quad(minX, accentY, maxX - minX, 1.0, withAlpha(accentColor.get(), maxAlpha));
                }
            }
            return;
        }

        for (RenderEntry entry : layout) {
            if (entry.alpha <= 0.002) continue;
            double bx = entry.x - paddingX.get();
            double by = entry.y - paddingY.get();
            double bw = entry.width + paddingX.get() * 2.0;
            double bh = entry.height + paddingY.get() * 2.0;
            Renderer2D.COLOR.quad(bx, by, bw, bh, withAlpha(backgroundColor.get(), entry.alpha * pulse));
            if (accentLine.get()) {
                double accentY = entry.bottom ? by + bh - 1.0 : by;
                Renderer2D.COLOR.quad(bx, accentY, bw, 1.0, withAlpha(accentColor.get(), entry.alpha));
            }
        }
    }

    private void drawScanGeometry(List<RenderEntry> layout, long now) {
        for (RenderEntry entry : layout) {
            if (entry.alpha <= 0.002 || entry.height <= 0.0) continue;
            drawOneScanBeam(entry, now, 0.0);
            if (scanMode.get() == ScanMode.DualBand) drawOneScanBeam(entry, now, 0.5);
        }
    }

    private void drawOneScanBeam(RenderEntry entry, long now, double extraPhase) {
        double centerY = scanBeamY(entry, now, extraPhase);
        double bx = entry.x - paddingX.get();
        double bw = entry.width + paddingX.get() * 2.0;
        double alpha = entry.alpha * scanIntensity.get();

        if (scanMode.get() == ScanMode.Line) {
            Renderer2D.COLOR.quad(bx, centerY, bw, 1.0, withAlpha(scanColor.get(), alpha));
            return;
        }

        double band = Math.max(1.0, scanBandWidth.get());
        int strips = 11;
        double stripH = band / strips;
        for (int i = 0; i < strips; i++) {
            double normalized = ((i + 0.5) / strips) * 2.0 - 1.0;
            double falloff = Math.exp(-normalized * normalized * 3.8);
            double sy = centerY - band / 2.0 + i * stripH;
            Renderer2D.COLOR.quad(bx, sy, bw, stripH + 0.35, withAlpha(scanColor.get(), alpha * falloff * 0.48));
        }
        Renderer2D.COLOR.quad(bx, centerY - 0.5, bw, 1.0, withAlpha(scanColor.get(), alpha));
    }

    private double scanBeamY(RenderEntry entry, long now, double extraPhase) {
        double time = now / 1_000_000_000.0;
        double phase = time * scanSpeed.get() + extraPhase;
        if (scanColumnPhase.get()) phase += entry.columnIndex * scanPhaseStep.get();
        phase = fract(phase);

        double p = switch (scanDirection.get()) {
            case TopToBottom -> phase;
            case BottomToTop -> 1.0 - phase;
            case PingPong -> 0.5 - 0.5 * Math.cos(phase * Math.PI * 2.0);
        };
        return entry.y + entry.height * p;
    }

    private void drawPulseFrame(RenderEntry entry, long now) {
        double p = effectProgress(now, entry.state.pulseStartNanos, entry.state.pulseUntilNanos);
        double fade = 1.0 - p;
        for (int i = 0; i < 3; i++) {
            double expand = (1.0 - easeOutCubic(p)) * (10.0 + i * 7.0);
            double x = entry.x - paddingX.get() - expand;
            double y = entry.y - paddingY.get() - expand;
            double w = entry.width + paddingX.get() * 2.0 + expand * 2.0;
            double h = entry.height + paddingY.get() * 2.0 + expand * 2.0;
            double layerAlpha = entry.alpha * fade * (0.62 / (i + 1));
            Renderer2D.COLOR.boxLines(x, y, w, h, withAlpha(pulseFrameColor.get(), layerAlpha));
        }
    }

    private void drawRobocopLock(RenderEntry entry, long now) {
        double p = effectProgress(now, entry.state.robocopStartNanos, entry.state.robocopUntilNanos);
        double trackEnd = clamp(robocopTrackPhase.get(), 0.20, 0.80);

        double targetX = entry.x + entry.width * 0.5;
        double targetY = entry.y + entry.height * 0.5;
        double seedA = hashUnit(entry.state.robocopSeed * 31L + 17L);
        double seedB = hashUnit(entry.state.robocopSeed * 53L + 29L);
        int edge = Math.floorMod((int) (entry.state.robocopSeed ^ (entry.state.robocopSeed >>> 32)), 4);
        double startX;
        double startY;
        double pad = 18.0;
        switch (edge) {
            case 0 -> { startX = pad + seedA * Math.max(1.0, currentScreenWidth - pad * 2.0); startY = pad; }
            case 1 -> { startX = Math.max(pad, currentScreenWidth - pad); startY = pad + seedB * Math.max(1.0, currentScreenHeight - pad * 2.0); }
            case 2 -> { startX = pad + seedA * Math.max(1.0, currentScreenWidth - pad * 2.0); startY = Math.max(pad, currentScreenHeight - pad); }
            default -> { startX = pad; startY = pad + seedB * Math.max(1.0, currentScreenHeight - pad * 2.0); }
        }

        SettingColor color = withAlpha(robocopColor.get(), entry.alpha);
        if (p < trackEnd) {
            double tp = easeInOutCubic(p / trackEnd);
            double cx = startX + (targetX - startX) * tp;
            double cy = startY + (targetY - startY) * tp;
            double arm = robocopCrossSize.get();
            double gap = 2.5;
            double a = 0.58 + 0.42 * Math.sin(tp * Math.PI * 5.0);
            SettingColor moving = withAlpha(robocopColor.get(), entry.alpha * a);

            Renderer2D.COLOR.quad(cx - arm, cy, Math.max(1.0, arm - gap), 1.0, moving);
            Renderer2D.COLOR.quad(cx + gap, cy, Math.max(1.0, arm - gap), 1.0, moving);
            Renderer2D.COLOR.quad(cx, cy - arm, 1.0, Math.max(1.0, arm - gap), moving);
            Renderer2D.COLOR.quad(cx, cy + gap, 1.0, Math.max(1.0, arm - gap), moving);
            Renderer2D.COLOR.quad(cx - 1.0, cy - 1.0, 3.0, 3.0, withAlpha(robocopColor.get(), entry.alpha));
            if (robocopTrackTrail.get()) {
                Renderer2D.COLOR.line(startX, startY, cx, cy, withAlpha(robocopColor.get(), entry.alpha * 0.22));
                Renderer2D.COLOR.line(cx, cy, targetX, targetY, withAlpha(robocopColor.get(), entry.alpha * 0.12));
            }
            return;
        }

        double lockP = clamp((p - trackEnd) / Math.max(0.001, 1.0 - trackEnd), 0.0, 1.0);
        double acquire = (1.0 - easeOutQuint(lockP)) * robocopAcquireDistance.get();
        double x = entry.x - paddingX.get() - acquire;
        double y = entry.y - paddingY.get() - acquire;
        double w = entry.width + paddingX.get() * 2.0 + acquire * 2.0;
        double h = entry.height + paddingY.get() * 2.0 + acquire * 2.0;
        double lockedPulse = lockP > 0.68 ? 0.72 + 0.28 * Math.sin((lockP - 0.68) * 42.0) : 1.0;
        color = withAlpha(robocopColor.get(), entry.alpha * clamp(lockedPulse, 0.35, 1.0));

        double len = clamp(Math.min(w, h) * 0.22, 4.0, 12.0);
        drawCornerBrackets(x, y, w, h, len, 1.0, color);

        double cx = x + w * 0.5;
        double cy = y + h * 0.5;
        double cross = Math.min(5.0, robocopCrossSize.get() * 0.35);
        Renderer2D.COLOR.quad(cx - cross, cy, Math.max(1.0, cross - 1.0), 1.0, color);
        Renderer2D.COLOR.quad(cx + 1.0, cy, Math.max(1.0, cross - 1.0), 1.0, color);
        Renderer2D.COLOR.quad(cx, cy - cross, 1.0, Math.max(1.0, cross - 1.0), color);
        Renderer2D.COLOR.quad(cx, cy + 1.0, 1.0, Math.max(1.0, cross - 1.0), color);

        double scanY = y + h * clamp(lockP / 0.72, 0.0, 1.0);
        Renderer2D.COLOR.quad(x + 1.0, scanY, Math.max(1.0, w - 2.0), 1.0, withAlpha(robocopColor.get(), entry.alpha * 0.55));
        if (lockP >= 0.72) {
            double inset = 2.0 + (1.0 - lockP) * 4.0;
            Renderer2D.COLOR.boxLines(x + inset, y + inset, Math.max(1.0, w - inset * 2.0), Math.max(1.0, h - inset * 2.0),
                withAlpha(robocopColor.get(), entry.alpha * 0.28));
        }
    }

    private void drawCornerBrackets(double x, double y, double w, double h, double len, double thickness, SettingColor color) {
        Renderer2D.COLOR.quad(x, y, len, thickness, color);
        Renderer2D.COLOR.quad(x, y, thickness, len, color);
        Renderer2D.COLOR.quad(x + w - len, y, len, thickness, color);
        Renderer2D.COLOR.quad(x + w - thickness, y, thickness, len, color);
        Renderer2D.COLOR.quad(x, y + h - thickness, len, thickness, color);
        Renderer2D.COLOR.quad(x, y + h - len, thickness, len, color);
        Renderer2D.COLOR.quad(x + w - len, y + h - thickness, len, thickness, color);
        Renderer2D.COLOR.quad(x + w - thickness, y + h - len, thickness, len, color);
    }

    private void drawText(TextRenderer renderer, TextRenderer symbolRenderer, List<RenderEntry> layout, long now) {
        for (RenderEntry entry : layout) {
            if (entry.alpha <= 0.002) continue;

            // Bottom anchor reverses only the HUD anchoring: the complete column is pinned to the
            // lower edge, but the text itself must NEVER reverse reading direction. Both top and
            // bottom anchors therefore render text from the column's visual top toward its bottom.
            // In bottom mode only the header symbol is moved to the lower edge.
            double cursorY = entry.y;
            int row = 0;

            if (!entry.bottom && !entry.symbol.isEmpty()) {
                drawGlyph(symbolRenderer, entry, entry.symbol, row, cursorY, true, entry.alpha, now, 0.0, false);
                cursorY += entry.symbolHeight + charSpacing.get();
                row++;
            }

            int unitIndex = 0;
            for (TextUnit unit : entry.units) {
                int visibleInUnit = Math.min(unit.length, Math.max(0, entry.visibleCharacters - unit.startIndex));
                if (visibleInUnit <= 0) break;

                String glyph = prefixCodePoints(unit.text, visibleInUnit);
                boolean glitching = isGlitching(entry.state, now);
                boolean decrypting = isDecrypting(entry.state, unit.startIndex + visibleInUnit - 1, now);
                if (glitching || decrypting) glyph = scrambleVisibleText(glyph, entry.state, unit.startIndex, now, glitching, decrypting);
                glyph = applyExtraBurstText(glyph, entry.state, unit.startIndex, now);

                double jitterX = 0.0;
                double jitterY = 0.0;
                long bucket = now / (Math.max(1L, glitchRefreshMs.get()) * 1_000_000L);
                if (glitching && glitchJitter.get() > 0.0) {
                    jitterX += (hashUnit(entry.state.module.hashCode() * 131L + unitIndex * 17L + bucket) - 0.5) * 2.0 * glitchJitter.get();
                    jitterY += (hashUnit(entry.state.module.hashCode() * 197L + unitIndex * 29L + bucket) - 0.5) * 2.0 * glitchJitter.get();
                }

                if (isHologramming(entry.state, now)) {
                    long holoBucket = now / 28_000_000L;
                    double slice = hashUnit(entry.state.module.hashCode() * 887L + unitIndex * 73L + holoBucket);
                    if (slice < hologramSliceChance.get()) {
                        double dir = hashUnit(entry.state.module.hashCode() * 991L + unitIndex * 101L + holoBucket) < 0.5 ? -1.0 : 1.0;
                        jitterX += dir * hologramOffset.get() * (0.45 + slice);
                    }
                }

                double visibleHeight = unitHeight(renderer, unit, glyph);
                double fullHeight = unitHeight(renderer, unit, unit.text);
                double drawY = cursorY;
                drawGlyph(renderer, entry, glyph, row, drawY + jitterY, false, entry.alpha, now, jitterX, unit.rotated);

                if (visibleInUnit < unit.length) {
                    cursorY += visibleHeight;
                    break;
                }

                cursorY += fullHeight + charSpacing.get();
                row++;
                unitIndex++;
            }

            if (typingCursor.get() && typing.get() && animations.get() && entry.state.active
                && entry.visibleCharacters < entry.totalCharacters && ((now / 260_000_000L) & 1L) == 0L) {
                String cursor = nonEmpty(cursorSymbol.get(), "▌");
                drawGlyph(renderer, entry, cursor, row, cursorY, false, entry.alpha, now, 0.0, false);
            }

            if (entry.bottom && !entry.symbol.isEmpty()) {
                // Keep the bottom-anchor visual hierarchy (symbol at the lower edge) without
                // reversing the module name itself. This is the key distinction between
                // "layout grows upward" and "text reads upward".
                double sy = entry.y + entry.height - entry.symbolHeight;
                drawGlyph(symbolRenderer, entry, entry.symbol, row + 1, sy, true, entry.alpha, now, 0.0, false);
            }

            if (robocopLabel.get() && robocopLock.get() && isRobocopLocking(entry.state, now)) {
                double p = effectProgress(now, entry.state.robocopStartNanos, entry.state.robocopUntilNanos);
                double trackEnd = clamp(robocopTrackPhase.get(), 0.20, 0.80);
                String label = p < trackEnd ? "TRACK" : (p < trackEnd + (1.0 - trackEnd) * 0.72 ? "SCAN" : nonEmpty(robocopLockText.get(), "LOCK"));
                double labelWidth = renderer.getWidth(label, false);
                double lx = entry.x + (entry.width - labelWidth) * 0.5;
                double labelHeight = renderer.getHeight(false);
                double ly = entry.bottom ? entry.y + entry.height + 3.0 : entry.y - labelHeight - 3.0;
                ly = clamp(ly, 2.0, Math.max(2.0, currentScreenHeight - labelHeight - 2.0));
                renderer.render(label, lx, ly, withAlpha(robocopColor.get(), entry.alpha), false);
            }
        }
    }

    private void drawGlyph(TextRenderer renderer, RenderEntry entry, String glyph, int row, double y,
                           boolean symbol, double alpha, long now, double extraX, boolean rotateClockwise) {
        double width = rotateClockwise ? renderer.getHeight(shadow.get()) : renderer.getWidth(glyph, shadow.get());
        double x = entry.x + (entry.width - width) * 0.5 + extraX;

        BurstType burst = activeExtraBurst(entry.state, now);
        double burstP = burst == null ? 0.0 : effectProgress(now, entry.state.extraBurstStartNanos, entry.state.extraBurstUntilNanos);
        double envelope = burst == null ? 0.0 : Math.sin(Math.PI * burstP);
        double intensity = extraBurstIntensity.get();
        double alphaMul = 1.0;
        double burstColorMix = 0.0;
        double extraY = 0.0;
        long burstBucket = now / 24_000_000L;

        if (!symbol && burst != null) {
            double rowSeed = hashUnit(entry.state.extraBurstSeed + row * 971L);
            switch (burst) {
                case FadeFlash -> { alphaMul *= 0.55 + 0.45 * (1.0 - envelope); burstColorMix = envelope * 0.85; }
                case Blink -> alphaMul *= ((int) (burstP * 14.0) & 1) == 0 ? 1.0 : 0.18;
                case Scroll -> x += Math.sin(burstP * Math.PI * 2.0) * 11.0 * envelope * intensity;
                case Pulse -> { alphaMul *= 0.72 + 0.28 * Math.sin(burstP * Math.PI * 6.0); burstColorMix = envelope * 0.55; }
                case SlideUp -> extraY -= envelope * 12.0 * intensity;
                case SlideDown -> extraY += envelope * 12.0 * intensity;
                case Bounce -> extraY -= Math.abs(Math.sin(burstP * Math.PI * 3.0)) * envelope * 9.0 * intensity;
                case Drift -> x += Math.sin(burstP * Math.PI * 2.0 + row * 0.8) * envelope * 7.0 * intensity;
                case Shake -> {
                    x += (hashUnit(entry.state.extraBurstSeed + row * 131L + burstBucket) - 0.5) * 7.0 * envelope * intensity;
                    extraY += (hashUnit(entry.state.extraBurstSeed + row * 197L + burstBucket) - 0.5) * 5.0 * envelope * intensity;
                }
                case GradientShift -> burstColorMix = 0.35 + 0.65 * (0.5 + 0.5 * Math.sin(burstP * Math.PI * 4.0 + row * 0.7));
                case Wipe -> {
                    double sweep = 1.0 - Math.abs(2.0 * burstP - 1.0);
                    double rowNorm = entry.units.isEmpty() ? 0.0 : row / (double) Math.max(1, entry.units.size());
                    if (rowNorm > sweep) alphaMul *= 0.10;
                }
                case Stagger -> {
                    double local = clamp((burstP * 1.5) - row * 0.055, 0.0, 1.0);
                    extraY += Math.sin(local * Math.PI) * 6.0 * intensity;
                    alphaMul *= 0.45 + 0.55 * local;
                }
                case Melt -> extraY += (row + 1) * 0.75 * envelope * 5.0 * intensity;
                case Warp -> x += Math.sin(row * 1.7 + burstP * Math.PI * 7.0) * envelope * 5.5 * intensity;
                case Fragment -> {
                    x += (rowSeed - 0.5) * 22.0 * envelope * intensity;
                    extraY += (hashUnit(entry.state.extraBurstSeed + row * 1297L) - 0.5) * 18.0 * envelope * intensity;
                    alphaMul *= 1.0 - envelope * 0.30;
                }
                case MatrixRain -> { extraY += rowSeed * envelope * 13.0 * intensity; burstColorMix = envelope * 0.55; }
                case Electric -> { x += (rowSeed - 0.5) * 3.5 * envelope * intensity; burstColorMix = envelope * 0.75; }
                case Vhs -> {
                    if (rowSeed < 0.48) x += (rowSeed - 0.24) * 24.0 * envelope * intensity;
                    alphaMul *= 0.78 + 0.22 * (((int) (burstP * 20.0) & 1) == 0 ? 1.0 : 0.3);
                }
                case Neon -> { burstColorMix = 0.50 + envelope * 0.50; alphaMul *= 0.82 + 0.18 * Math.sin(burstP * Math.PI * 9.0); }
                case Interference -> x += Math.sin(row * 2.5 + burstP * Math.PI * 14.0) * envelope * 3.2 * intensity;
                case Disintegrate -> {
                    double vanish = hashUnit(entry.state.extraBurstSeed + row * 1777L);
                    if (envelope > vanish) alphaMul *= 0.18;
                    x += (vanish - 0.5) * envelope * 9.0 * intensity;
                    extraY -= envelope * vanish * 7.0 * intensity;
                }
                case RadarSweep, DataStream -> burstColorMix = envelope * 0.38;
                default -> { }
            }
        }

        y += extraY;
        alpha *= clamp(alphaMul, 0.0, 1.0);
        SettingColor color = symbol ? withAlpha(symbolColor.get(), alpha) : glyphColor(entry, row, y, alpha, now);
        if (!symbol && burstColorMix > 0.001) {
            SettingColor target = (row & 1) == 0 ? extraBurstColorA.get() : extraBurstColorB.get();
            color = blendRgbKeepAlpha(color, target, clamp(burstColorMix, 0.0, 1.0));
        }

        double motionX = 0.0;
        double motionY = 0.0;
        if (converge.get() && animations.get()) {
            double travel = 1.0 - easeOutQuint(entry.state.progress);
            double angle = scatterAngle(entry.state.module);
            motionX = Math.cos(angle) * convergeDistance.get() * travel;
            motionY = Math.sin(angle) * convergeDistance.get() * travel;
        }

        if (echoTrail.get()) {
            for (int copy = echoCopies.get(); copy >= 1; copy--) {
                double factor = copy / (double) (echoCopies.get() + 1);
                double dx = motionX * 0.055 * factor - parallaxOffsetX * 0.12 * factor;
                double dy = motionY * 0.055 * factor - parallaxOffsetY * 0.12 * factor;
                if (Math.abs(dx) + Math.abs(dy) < 0.05) {
                    dx = (side.get() == Side.Right ? 1 : -1) * echoDistance.get() * copy;
                    dy = echoDistance.get() * copy * 0.35;
                }
                renderText(renderer, glyph, x - dx, y - dy, withAlpha(color, 0.20 / copy), false, rotateClockwise);
            }
        }

        boolean glitching = isGlitching(entry.state, now);
        boolean burstChromatic = burst == BurstType.Chromatic || burst == BurstType.Vhs;
        if ((glitching && rgbSplit.get()) || burstChromatic) {
            double d = burstChromatic ? 1.5 * intensity * envelope + 0.35 : rgbDistance.get();
            renderText(renderer, glyph, x - d, y, new SettingColor(255, 75, 90, (int) (125 * alpha)), false, rotateClockwise);
            renderText(renderer, glyph, x + d, y, new SettingColor(55, 220, 255, (int) (125 * alpha)), false, rotateClockwise);
        }

        if (!symbol && isHologramming(entry.state, now)) {
            double p = effectProgress(now, entry.state.hologramStartNanos, entry.state.hologramUntilNanos);
            double ghostAlpha = alpha * 0.22 * Math.sin(Math.PI * p);
            renderText(renderer, glyph, x - hologramOffset.get() * 0.45, y, withAlpha(hologramColor.get(), ghostAlpha), false, rotateClockwise);
            renderText(renderer, glyph, x + hologramOffset.get() * 0.30, y, withAlpha(hologramColor.get(), ghostAlpha * 0.70), false, rotateClockwise);
        }

        if (!symbol && burst != null && (burst == BurstType.Glow || burst == BurstType.Neon)) {
            double glowA = alpha * envelope * 0.18;
            double d = 1.2 + 1.4 * intensity;
            for (int i = 1; i <= 2; i++) {
                SettingColor glow = withAlpha(extraBurstColorA.get(), glowA / i);
                renderText(renderer, glyph, x - d * i, y, glow, false, rotateClockwise);
                renderText(renderer, glyph, x + d * i, y, glow, false, rotateClockwise);
                renderText(renderer, glyph, x, y - d * i, glow, false, rotateClockwise);
                renderText(renderer, glyph, x, y + d * i, glow, false, rotateClockwise);
            }
        }

        renderText(renderer, glyph, x, y, color, shadow.get(), rotateClockwise);
    }

    private SettingColor glyphColor(RenderEntry entry, int row, double glyphY, double alpha, long now) {
        SettingColor base;
        if (!gradient.get()) base = withAlpha(textColorA.get(), alpha);
        else {
            double time = now / 1_000_000_000.0 * gradientSpeed.get();
            double t = (row * 0.12 + entry.columnIndex * 0.08 + time) % 2.0;
            if (t > 1.0) t = 2.0 - t;
            base = lerpColor(textColorA.get(), textColorB.get(), t, alpha);
        }

        if (scanLine.get() && scanTextHighlight.get()) {
            double influence = scanInfluence(entry, glyphY, now) * scanTextStrength.get();
            if (influence > 0.001) base = blendRgbKeepAlpha(base, scanColor.get(), influence);
        }
        return base;
    }

    private double scanInfluence(RenderEntry entry, double glyphY, long now) {
        double radius = Math.max(1.0, scanBandWidth.get() * 0.70);
        double d1 = Math.abs(glyphY - scanBeamY(entry, now, 0.0));
        double influence = clamp(1.0 - d1 / radius, 0.0, 1.0);
        if (scanMode.get() == ScanMode.DualBand) {
            double d2 = Math.abs(glyphY - scanBeamY(entry, now, 0.5));
            influence = Math.max(influence, clamp(1.0 - d2 / radius, 0.0, 1.0));
        }
        return influence;
    }

    private int visibleCharacterCount(EntryState state, int total, long now) {
        if (total <= 0) return 0;

        if (isTypingBurst(state, now)) {
            double p = effectProgress(now, state.typingBurstStartNanos, state.typingBurstUntilNanos);
            int animated = Math.max(0, Math.min(total, (int) Math.floor(p * (total + 1))));
            return state.active ? animated : Math.max(0, total - animated);
        }

        if (!animations.get() || !typing.get()) return state.active ? total : (int) Math.ceil(total * state.progress);

        double elapsedMs = Math.max(0.0, (now - state.transitionNanos) / 1_000_000.0);
        int interval = state.active ? typingEnterInterval.get() : typingExitInterval.get();
        int count = (int) Math.floor(elapsedMs / Math.max(1, interval));
        if (state.active) count += 1;
        count = Math.min(total, Math.max(0, count));
        return state.active ? count : Math.max(0, total - count);
    }

    private boolean isGlitching(EntryState state, long now) {
        return glitch.get() && now >= state.glitchStartNanos && now < state.glitchUntilNanos;
    }

    private void previewGlitch() {
        if (!isActive() || entries.isEmpty()) return;

        long now = System.nanoTime();
        for (EntryState state : entries.values()) {
            if (state.active && state.progress > 0.002) triggerGlitch(state, now);
        }
    }

    private boolean isDecrypting(EntryState state, int charIndex, long now) {
        if (!decrypt.get() || !state.active || now < state.transitionNanos) return false;
        double ageMs = (now - state.transitionNanos) / 1_000_000.0;
        double settleMs = (charIndex + 1L) * Math.max(1, typingEnterInterval.get()) + 145.0;
        return ageMs < settleMs;
    }

    private String scrambleVisibleText(String text, EntryState state, int globalStart, long now, boolean glitching, boolean decrypting) {
        if (text == null || text.isEmpty()) return text;
        List<String> pool = codePoints(glitchChars.get());
        if (pool.isEmpty()) pool = List.of("#", "0", "1", "+", "-");

        StringBuilder out = new StringBuilder();
        List<String> chars = codePoints(text);
        long bucket = now / (Math.max(1L, glitchRefreshMs.get()) * 1_000_000L);
        for (int i = 0; i < chars.size(); i++) {
            int global = globalStart + i;
            double probability = glitching ? glitchIntensity.get() : 0.0;
            if (decrypting) {
                double ageMs = Math.max(0.0, (now - state.transitionNanos) / 1_000_000.0);
                double settleMs = (global + 1L) * Math.max(1, typingEnterInterval.get()) + 145.0;
                double remaining = clamp(1.0 - ageMs / Math.max(1.0, settleMs), 0.0, 1.0);
                probability = Math.max(probability, 0.20 + remaining * 0.70);
            }

            long seed = state.module.hashCode() * 1103L + global * 97L + bucket;
            // A short visible name could otherwise randomly replace zero characters for an
            // entire refresh step and make an active glitch look broken. Keep the configured
            // probability for every other character, but guarantee one visible corruption.
            boolean guaranteedVisibleCorruption = glitching && probability > 0.0 && global == 0;
            if (guaranteedVisibleCorruption || hashUnit(seed) < probability) {
                int index = (int) Math.floor(hashUnit(seed * 37L + 17L) * pool.size());
                index = Math.max(0, Math.min(pool.size() - 1, index));
                out.append(pool.get(index));
            } else out.append(chars.get(i));
        }
        return out.toString();
    }

    private String applyExtraBurstText(String text, EntryState state, int globalStart, long now) {
        BurstType burst = activeExtraBurst(state, now);
        if (burst != BurstType.Noise && burst != BurstType.Vhs && burst != BurstType.MatrixRain) return text;
        if (text == null || text.isEmpty()) return text;

        double p = effectProgress(now, state.extraBurstStartNanos, state.extraBurstUntilNanos);
        double envelope = Math.sin(Math.PI * p);
        double probability = switch (burst) {
            case Noise -> 0.58;
            case Vhs -> 0.18;
            case MatrixRain -> 0.26;
            default -> 0.0;
        };
        probability *= envelope * clamp(extraBurstIntensity.get(), 0.1, 2.0);
        List<String> pool = codePoints(glitchChars.get());
        if (pool.isEmpty()) pool = List.of("0", "1", "#", "+", "-");
        long bucket = now / 32_000_000L;
        StringBuilder out = new StringBuilder();
        List<String> chars = codePoints(text);
        for (int i = 0; i < chars.size(); i++) {
            long seed = state.extraBurstSeed + (globalStart + i) * 733L + bucket * 17L;
            if (hashUnit(seed) < probability) {
                int idx = (int) Math.floor(hashUnit(seed * 37L + 11L) * pool.size());
                out.append(pool.get(Math.max(0, Math.min(pool.size() - 1, idx))));
            } else out.append(chars.get(i));
        }
        return out.toString();
    }

    private void drawExtraBurstGeometry(RenderEntry entry, long now) {
        BurstType burst = activeExtraBurst(entry.state, now);
        if (burst == null) return;
        double p = effectProgress(now, entry.state.extraBurstStartNanos, entry.state.extraBurstUntilNanos);
        double env = Math.sin(Math.PI * p);
        double intensity = extraBurstIntensity.get();
        double x = entry.x - paddingX.get();
        double y = entry.y - paddingY.get();
        double w = entry.width + paddingX.get() * 2.0;
        double h = entry.height + paddingY.get() * 2.0;
        SettingColor a = withAlpha(extraBurstColorA.get(), entry.alpha * env);
        SettingColor b = withAlpha(extraBurstColorB.get(), entry.alpha * env);
        long bucket = now / 30_000_000L;

        switch (burst) {
            case Wipe -> {
                double q = p < 0.5 ? p * 2.0 : (1.0 - p) * 2.0;
                double sy = entry.bottom ? y + h * (1.0 - q) : y + h * q;
                Renderer2D.COLOR.quad(x, sy, w, 1.0, withAlpha(extraBurstColorA.get(), entry.alpha * 0.75));
            }
            case MatrixRain -> {
                int drops = 5;
                for (int i = 0; i < drops; i++) {
                    double rx = x + hashUnit(entry.state.extraBurstSeed + i * 311L) * w;
                    double rp = fract(p * (1.2 + i * 0.11) + hashUnit(entry.state.extraBurstSeed + i * 479L));
                    double ry = y - 7.0 + rp * (h + 14.0);
                    double len = 3.0 + hashUnit(entry.state.extraBurstSeed + i * 619L) * 9.0;
                    Renderer2D.COLOR.quad(rx, ry, 1.0, len, withAlpha(extraBurstColorA.get(), entry.alpha * env * (0.28 + i * 0.07)));
                }
            }
            case Electric -> {
                double px = x;
                double py = y + h * 0.5;
                for (int i = 1; i <= 7; i++) {
                    double nx = x + w * i / 7.0;
                    double ny = y + h * 0.5 + (hashUnit(entry.state.extraBurstSeed + i * 911L + bucket) - 0.5) * h * 0.65 * env;
                    Renderer2D.COLOR.line(px, py, nx, ny, (i & 1) == 0 ? a : b);
                    px = nx;
                    py = ny;
                }
            }
            case Vhs -> {
                for (int i = 0; i < 4; i++) {
                    double ry = y + hashUnit(entry.state.extraBurstSeed + i * 1217L + bucket) * h;
                    double rw = w * (0.45 + hashUnit(entry.state.extraBurstSeed + i * 1319L) * 0.55);
                    Renderer2D.COLOR.quad(x, ry, rw, 1.0, withAlpha(i % 2 == 0 ? extraBurstColorA.get() : extraBurstColorB.get(), entry.alpha * env * 0.30));
                }
            }
            case Interference -> {
                for (int i = 0; i < 6; i++) {
                    double ry = y + (i + 0.5) * h / 6.0 + Math.sin(p * Math.PI * 12.0 + i) * 2.0;
                    Renderer2D.COLOR.quad(x - 2.0, ry, w + 4.0, 0.65, withAlpha(extraBurstColorA.get(), entry.alpha * env * (0.12 + i * 0.025)));
                }
            }
            case Disintegrate -> {
                for (int i = 0; i < 10; i++) {
                    double r = hashUnit(entry.state.extraBurstSeed + i * 1597L);
                    double angle = hashUnit(entry.state.extraBurstSeed + i * 1667L) * Math.PI * 2.0;
                    double dist = env * (5.0 + r * 22.0) * intensity;
                    double rx = x + w * hashUnit(entry.state.extraBurstSeed + i * 1699L) + Math.cos(angle) * dist;
                    double ry = y + h * hashUnit(entry.state.extraBurstSeed + i * 1753L) + Math.sin(angle) * dist;
                    double size = 0.7 + r * 1.8;
                    Renderer2D.COLOR.quad(rx, ry, size, size, withAlpha(extraBurstColorA.get(), entry.alpha * env * (0.18 + r * 0.45)));
                }
            }
            case RadarSweep -> {
                double cx = x + w * 0.5;
                double cy = y + h * 0.5;
                double angle = p * Math.PI * 4.0 - Math.PI * 0.5;
                double radius = Math.max(w, h) * 0.62 + 3.0;
                Renderer2D.COLOR.line(cx, cy, cx + Math.cos(angle) * radius, cy + Math.sin(angle) * radius, withAlpha(extraBurstColorA.get(), entry.alpha * env * 0.72));
                Renderer2D.COLOR.boxLines(x, y, w, h, withAlpha(extraBurstColorA.get(), entry.alpha * env * 0.16));
            }
            case DataStream -> {
                int bars = 8;
                for (int i = 0; i < bars; i++) {
                    double bh = 1.0 + hashUnit(entry.state.extraBurstSeed + i * 1877L + bucket) * 5.0 * intensity;
                    double by = y + i * h / bars;
                    double bw = 1.5 + hashUnit(entry.state.extraBurstSeed + i * 1931L) * 4.5;
                    Renderer2D.COLOR.quad(x - bw - 2.0, by, bw, bh, withAlpha(extraBurstColorA.get(), entry.alpha * env * 0.48));
                }
            }
            case Chromatic -> {
                Renderer2D.COLOR.boxLines(x - 1.0, y, w, h, withAlpha(new SettingColor(255, 70, 90, 180), entry.alpha * env * 0.16));
                Renderer2D.COLOR.boxLines(x + 1.0, y, w, h, withAlpha(new SettingColor(55, 220, 255, 180), entry.alpha * env * 0.16));
            }
            default -> { }
        }
    }

    private void updateRandomBursts(long now) {
        if (!randomBursts.get()) {
            nextRandomBurstNanos = 0L;
            return;
        }

        // Do not rebuild the 20+ effect pool every rendered frame. Only resolve the pool when
        // an event is actually due; this keeps the HUD allocation-free during the quiet interval.
        if (nextRandomBurstNanos == 0L) {
            scheduleNextRandomBurst(now);
            return;
        }
        if (now < nextRandomBurstNanos) return;

        List<BurstType> effects = enabledRandomBurstTypes();
        if (effects.isEmpty()) {
            scheduleNextRandomBurst(now);
            return;
        }

        List<EntryState> candidates = new ArrayList<>();
        for (EntryState state : entries.values()) {
            if (state.active && state.progress > 0.55) candidates.add(state);
        }

        long eventSeed = now / 10_000_000L + sequence * 131L;
        int maxCount = Math.min(Math.max(1, randomColumns.get()), candidates.size());
        int count = maxCount <= 0 ? 0 : 1 + (int) Math.floor(hashUnit(eventSeed ^ 0x6A09E667F3BCC909L) * maxCount);
        count = Math.min(count, maxCount);

        for (int i = 0; i < count && !candidates.isEmpty(); i++) {
            int stateIndex = (int) Math.floor(hashUnit(eventSeed + i * 811L) * candidates.size());
            stateIndex = Math.max(0, Math.min(candidates.size() - 1, stateIndex));
            EntryState state = candidates.remove(stateIndex);

            int effectIndex = (int) Math.floor(hashUnit(eventSeed + i * 1237L + state.module.hashCode()) * effects.size());
            effectIndex = Math.max(0, Math.min(effects.size() - 1, effectIndex));
            triggerBurst(state, effects.get(effectIndex), now);
        }

        scheduleNextRandomBurst(now);
    }

    private List<BurstType> enabledRandomBurstTypes() {
        List<BurstType> effects = new ArrayList<>();
        if (glitch.get() && randomGlitch.get()) effects.add(BurstType.Glitch);
        if (typing.get() && randomTyping.get()) effects.add(BurstType.Typing);
        if (robocopLock.get() && randomRobocop.get()) effects.add(BurstType.Robocop);
        if (hologram.get() && randomHologram.get()) effects.add(BurstType.Hologram);
        if (pulseFrame.get() && randomPulseFrame.get()) effects.add(BurstType.PulseFrame);
        if (neonFlicker.get() && randomFlicker.get()) effects.add(BurstType.Flicker);
        if (extraBursts.get()) addPresetBurstTypes(effects, burstPreset.get());
        return effects;
    }

    private void addPresetBurstTypes(List<BurstType> effects, BurstPreset preset) {
        switch (preset) {
            case Minimal -> addUnique(effects, BurstType.FadeFlash, BurstType.Pulse, BurstType.SlideUp, BurstType.Glow, BurstType.Wipe, BurstType.RadarSweep);
            case Balanced -> addUnique(effects, BurstType.FadeFlash, BurstType.Blink, BurstType.Pulse, BurstType.SlideUp, BurstType.Bounce,
                BurstType.Drift, BurstType.GradientShift, BurstType.Glow, BurstType.Wipe, BurstType.Stagger, BurstType.Chromatic,
                BurstType.Warp, BurstType.RadarSweep, BurstType.DataStream);
            case Motion -> addUnique(effects, BurstType.Scroll, BurstType.SlideUp, BurstType.SlideDown, BurstType.Bounce, BurstType.Drift,
                BurstType.Shake, BurstType.Stagger, BurstType.Melt, BurstType.Warp, BurstType.Fragment);
            case Cyber -> addUnique(effects, BurstType.Chromatic, BurstType.Noise, BurstType.MatrixRain, BurstType.Electric, BurstType.Vhs,
                BurstType.Neon, BurstType.Interference, BurstType.Disintegrate, BurstType.Fragment, BurstType.RadarSweep, BurstType.DataStream);
            case Signal -> addUnique(effects, BurstType.FadeFlash, BurstType.Blink, BurstType.Pulse, BurstType.GradientShift, BurstType.Glow,
                BurstType.Wipe, BurstType.Chromatic, BurstType.Neon, BurstType.Interference, BurstType.RadarSweep, BurstType.DataStream);
            case All -> addUnique(effects, BurstType.FadeFlash, BurstType.Blink, BurstType.Scroll, BurstType.Pulse, BurstType.SlideUp, BurstType.SlideDown,
                BurstType.Bounce, BurstType.Drift, BurstType.Shake, BurstType.GradientShift, BurstType.Glow, BurstType.Wipe, BurstType.Stagger,
                BurstType.Chromatic, BurstType.Noise, BurstType.Melt, BurstType.Warp, BurstType.Fragment, BurstType.MatrixRain, BurstType.Electric,
                BurstType.Vhs, BurstType.Neon, BurstType.Interference, BurstType.Disintegrate, BurstType.RadarSweep, BurstType.DataStream);
            case Custom -> {
                String raw = customBurstPool.get();
                if (raw != null) {
                    for (String token : raw.split(",")) {
                        String name = token.trim();
                        if (name.isEmpty()) continue;
                        for (BurstType type : BurstType.values()) {
                            if (type.name().equalsIgnoreCase(name) && type.ordinal() >= BurstType.FadeFlash.ordinal()) {
                                addUnique(effects, type);
                                break;
                            }
                        }
                    }
                }
            }
        }
    }

    private void addUnique(List<BurstType> effects, BurstType... types) {
        for (BurstType type : types) if (!effects.contains(type)) effects.add(type);
    }

    private void triggerBurst(EntryState state, BurstType type, long now) {
        switch (type) {
            case Glitch -> triggerGlitch(state, now);
            case Typing -> triggerTypingBurst(state, now);
            case Robocop -> triggerRobocop(state, now);
            case Hologram -> triggerHologram(state, now);
            case PulseFrame -> triggerPulseFrame(state, now);
            case Flicker -> triggerFlicker(state, now);
            default -> triggerExtraBurst(state, type, now);
        }
    }

    private void scheduleNextRandomBurst(long now) {
        double min = Math.max(0.05, Math.min(randomMinDelay.get(), randomMaxDelay.get()));
        double max = Math.max(min, Math.max(randomMinDelay.get(), randomMaxDelay.get()));
        double unit = hashUnit(now ^ (sequence * 0x9E3779B97F4A7C15L));
        double seconds = min + (max - min) * unit;
        nextRandomBurstNanos = now + (long) (seconds * 1_000_000_000.0);
    }

    private void triggerGlitch(EntryState state, long start) {
        state.glitchStartNanos = start;
        state.glitchUntilNanos = start + (long) (Math.max(0.01, glitchDuration.get()) * 1_000_000_000.0);
    }

    private void triggerRobocop(EntryState state, long start) {
        state.robocopStartNanos = start;
        state.robocopUntilNanos = start + (long) (Math.max(0.05, robocopDuration.get()) * 1_000_000_000.0);
        state.robocopSeed = start ^ ((long) state.module.hashCode() << 32) ^ state.sequence;
    }

    private void triggerTypingBurst(EntryState state, long start) {
        int chars = Math.max(1, codePointCount(displayText(state.module)));
        int interval = state.active ? typingEnterInterval.get() : typingExitInterval.get();
        double seconds = Math.max(0.12, chars * Math.max(1, interval) / 1000.0);
        state.typingBurstStartNanos = start;
        state.typingBurstUntilNanos = start + (long) (seconds * 1_000_000_000.0);
    }

    private void triggerExtraBurst(EntryState state, BurstType type, long start) {
        state.extraBurstType = type;
        state.extraBurstStartNanos = start;
        double duration = Math.max(0.05, extraBurstDuration.get());
        if (type == BurstType.Vhs || type == BurstType.Electric || type == BurstType.Disintegrate) duration *= 1.12;
        state.extraBurstUntilNanos = start + (long) (duration * 1_000_000_000.0);
        state.extraBurstSeed = start ^ ((long) state.module.hashCode() * 0x9E3779B97F4A7C15L) ^ state.sequence;
    }

    private void triggerHologram(EntryState state, long start) {
        state.hologramStartNanos = start;
        state.hologramUntilNanos = start + (long) (Math.max(0.05, hologramDuration.get()) * 1_000_000_000.0);
    }

    private void triggerPulseFrame(EntryState state, long start) {
        state.pulseStartNanos = start;
        state.pulseUntilNanos = start + (long) (Math.max(0.05, pulseFrameDuration.get()) * 1_000_000_000.0);
    }

    private void triggerFlicker(EntryState state, long start) {
        state.flickerStartNanos = start;
        state.flickerUntilNanos = start + (long) (Math.max(0.01, flickerDuration.get()) * 1_000_000_000.0);
    }

    private boolean isRobocopLocking(EntryState state, long now) {
        return now >= state.robocopStartNanos && now < state.robocopUntilNanos;
    }

    private boolean isHologramming(EntryState state, long now) {
        return hologram.get() && now >= state.hologramStartNanos && now < state.hologramUntilNanos;
    }

    private boolean isPulseFraming(EntryState state, long now) {
        return pulseFrame.get() && now >= state.pulseStartNanos && now < state.pulseUntilNanos;
    }

    private boolean isFlickering(EntryState state, long now) {
        return neonFlicker.get() && now >= state.flickerStartNanos && now < state.flickerUntilNanos;
    }

    private boolean isTypingBurst(EntryState state, long now) {
        return now >= state.typingBurstStartNanos && now < state.typingBurstUntilNanos;
    }

    private BurstType activeExtraBurst(EntryState state, long now) {
        if (state.extraBurstType == null || now < state.extraBurstStartNanos || now >= state.extraBurstUntilNanos) return null;
        return state.extraBurstType;
    }

    private boolean hasActiveExtraBurst(List<RenderEntry> layout, long now) {
        for (RenderEntry entry : layout) if (activeExtraBurst(entry.state, now) != null) return true;
        return false;
    }

    private double effectProgress(long now, long start, long end) {
        if (end <= start) return 1.0;
        return clamp((now - start) / (double) (end - start), 0.0, 1.0);
    }

    private String displayText(Module module) {
        if (!additionalInfo.get()) return module.title;
        String info = module.getInfoString();
        return info == null || info.isBlank() ? module.title : module.title + " " + info;
    }

    private TextRenderer resolveSymbolRenderer(TextRenderer mainRenderer) {
        if (symbolRendererMode.get() == SymbolRendererMode.FollowFont) return mainRenderer;
        if (symbolRendererMode.get() == SymbolRendererMode.Minecraft || symbolRendererMode.get() == SymbolRendererMode.Auto) {
            return VanillaTextRenderer.INSTANCE;
        }
        return mainRenderer;
    }

    private String symbolFor(Module module) {
        if (symbolMode.get() == SymbolMode.Fixed) return nonEmpty(fixedSymbol.get(), "◆");

        List<String> symbols = splitSymbolPool(symbolPool.get());
        if (symbols.isEmpty()) return "◆";
        int index = Math.floorMod(module.title.hashCode(), symbols.size());
        return nonEmpty(symbols.get(index), "◆");
    }

    private List<String> splitSymbolPool(String raw) {
        List<String> result = new ArrayList<>();
        if (raw == null || raw.isBlank()) return result;

        String[] parts = raw.contains("|") ? raw.split("\\|") : raw.trim().split("\\s+");
        for (String part : parts) {
            String token = part.trim();
            if (!token.isEmpty()) result.add(token);
        }
        return result;
    }

    private List<TextUnit> layoutUnits(String text) {
        List<TextUnit> result = new ArrayList<>();
        if (text == null || text.isEmpty()) return result;

        List<String> chars = codePoints(text);
        if (latinLayout.get() == LatinLayout.Stacked) {
            int logical = 0;
            for (String c : chars) {
                result.add(new TextUnit(c, logical, 1, false));
                logical++;
            }
            return result;
        }

        StringBuilder run = new StringBuilder();
        int runStart = -1;
        int runLength = 0;
        int logical = 0;
        for (String c : chars) {
            int cp = c.codePointAt(0);
            if (isVerticalScript(cp)) {
                if (runLength > 0) {
                    result.add(new TextUnit(run.toString(), runStart, runLength, true));
                    run.setLength(0);
                    runStart = -1;
                    runLength = 0;
                }
                result.add(new TextUnit(c, logical, 1, false));
                logical++;
            } else {
                if (runLength == 0 && Character.isWhitespace(cp)) continue;
                if (runStart < 0) runStart = logical;
                run.append(c);
                runLength++;
                logical++;
            }
        }
        if (runLength > 0) result.add(new TextUnit(run.toString(), runStart, runLength, true));
        return result;
    }

    private double unitWidth(TextRenderer renderer, TextUnit unit, String visibleText) {
        return unit.rotated ? renderer.getHeight(shadow.get()) : renderer.getWidth(visibleText, shadow.get());
    }

    private double unitHeight(TextRenderer renderer, TextUnit unit, String visibleText) {
        return unit.rotated ? renderer.getWidth(visibleText, shadow.get()) : renderer.getHeight(shadow.get());
    }

    private int unitCharacterCount(List<TextUnit> units) {
        int total = 0;
        for (TextUnit unit : units) total += unit.length;
        return total;
    }

    private boolean isVerticalScript(int cp) {
        Character.UnicodeScript script = Character.UnicodeScript.of(cp);
        return script == Character.UnicodeScript.HAN
            || script == Character.UnicodeScript.HIRAGANA
            || script == Character.UnicodeScript.KATAKANA
            || script == Character.UnicodeScript.HANGUL;
    }

    private List<String> codePoints(String text) {
        List<String> result = new ArrayList<>();
        if (text == null || text.isEmpty()) return result;
        text.codePoints().forEach(cp -> result.add(new String(Character.toChars(cp))));
        return result;
    }

    private int codePointCount(String text) {
        return text == null ? 0 : text.codePointCount(0, text.length());
    }

    private String prefixCodePoints(String text, int count) {
        if (text == null || text.isEmpty() || count <= 0) return "";
        int total = codePointCount(text);
        if (count >= total) return text;
        int end = text.offsetByCodePoints(0, count);
        return text.substring(0, end);
    }

    private String nonEmpty(String value, String fallback) {
        return value == null || value.isEmpty() ? fallback : value;
    }

    private void renderText(TextRenderer renderer, String text, double x, double y, SettingColor color, boolean shadow, boolean rotateClockwise) {
        if (!rotateClockwise || text == null || text.isEmpty()) {
            renderer.render(text, x, y, color, shadow);
            return;
        }

        if (tryRenderRotatedCustom(renderer, text, x, y, color, shadow)) return;
        if (tryRenderRotatedGui(renderer, text, x, y, color, shadow)) return;

        // Last-resort fallback is deliberately a whole horizontal word rather than stacked
        // letters. This path should only be reached on an unknown Meteor snapshot.
        renderer.render(text, x, y, color, shadow);
    }

    private boolean tryRenderRotatedCustom(TextRenderer renderer, String text, double targetX, double targetY, SettingColor color, boolean shadow) {
        if (!renderer.getClass().getName().endsWith("CustomTextRenderer")) return false;

        // CustomTextRenderer bypasses GuiGraphics matrices and emits glyph quads straight into
        // Meteor's MeshBuilder. The MeshBuilder mixin applies this transform only while this
        // render call is active, so the complete Latin run is rotated as one normal horizontal
        // label. No reflection into Meteor's private vertex buffers is required.
        double horizontalHeight = renderer.getHeight(shadow);
        ScreenVertexTransform.beginClockwise90(targetX + horizontalHeight, targetY);
        try {
            renderer.render(text, 0.0, 0.0, color, shadow);
        } finally {
            ScreenVertexTransform.end();
        }
        return true;
    }

    private boolean tryRenderRotatedGui(TextRenderer renderer, String text, double x, double y, SettingColor color, boolean shadow) {
        if (currentRenderEvent == null) return false;
        try {
            Object graphics = eventGraphics(currentRenderEvent);
            if (graphics == null) return false;

            Method poseMethod = null;
            for (String name : new String[] { "pose", "getMatrices" }) {
                try { poseMethod = graphics.getClass().getMethod(name); break; }
                catch (NoSuchMethodException ignored) { }
            }
            if (poseMethod == null) return false;
            Object pose = poseMethod.invoke(graphics);
            if (pose == null) return false;

            Method push = findNoArgMethod(pose.getClass(), "pushMatrix", "push");
            Method pop = findNoArgMethod(pose.getClass(), "popMatrix", "pop");
            Method translate = findNumericMethod(pose.getClass(), "translate", 2);
            Method rotate = findNumericMethod(pose.getClass(), "rotate", 1);
            if (push == null || pop == null || translate == null || rotate == null) return false;

            push.invoke(pose);
            try {
                invokeNumeric(translate, pose, x + renderer.getHeight(shadow), y);
                invokeNumeric(rotate, pose, (float) (Math.PI / 2.0));
                renderer.render(text, 0.0, 0.0, color, shadow);
            } finally {
                pop.invoke(pose);
            }
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private Method findNoArgMethod(Class<?> type, String... names) {
        for (String name : names) {
            try { return type.getMethod(name); } catch (NoSuchMethodException ignored) { }
        }
        return null;
    }

    private Method findNumericMethod(Class<?> type, String name, int parameterCount) {
        for (Method method : type.getMethods()) {
            if (method.getName().equals(name) && method.getParameterCount() == parameterCount) return method;
        }
        return null;
    }

    private void invokeNumeric(Method method, Object target, double... values) throws Exception {
        Class<?>[] types = method.getParameterTypes();
        Object[] args = new Object[values.length];
        for (int i = 0; i < values.length; i++) {
            Class<?> t = types[i];
            if (t == float.class || t == Float.class) args[i] = (float) values[i];
            else if (t == int.class || t == Integer.class) args[i] = (int) Math.round(values[i]);
            else args[i] = values[i];
        }
        method.invoke(target, args);
    }

    private void updateParallax(double dt) {
        if (!parallax.get() || mc.gameRenderer == null) {
            parallaxOffsetX = lerpExp(parallaxOffsetX, 0.0, 12.0, dt);
            parallaxOffsetY = lerpExp(parallaxOffsetY, 0.0, 12.0, dt);
            cameraInitialized = false;
            return;
        }

        float yaw = mc.gameRenderer.getCamera().getYaw();
        float pitch = mc.gameRenderer.getCamera().getPitch();
        if (!cameraInitialized) {
            lastYaw = yaw;
            lastPitch = pitch;
            cameraInitialized = true;
            return;
        }

        double dyaw = wrapDegrees(yaw - lastYaw);
        double dpitch = pitch - lastPitch;
        lastYaw = yaw;
        lastPitch = pitch;

        parallaxOffsetX -= dyaw * parallaxX.get();
        parallaxOffsetY += dpitch * parallaxY.get();

        double max = maxParallax.get();
        parallaxOffsetX = clamp(parallaxOffsetX, -max, max);
        parallaxOffsetY = clamp(parallaxOffsetY, -max, max);

        parallaxOffsetX = lerpExp(parallaxOffsetX, 0.0, parallaxDamping.get(), dt);
        parallaxOffsetY = lerpExp(parallaxOffsetY, 0.0, parallaxDamping.get(), dt);
    }

    private void cleanupEntries(long now) {
        entries.entrySet().removeIf(e -> {
            EntryState state = e.getValue();
            return !state.active && state.progress <= 0.002 && now >= transitionEffectEndNanos(state);
        });
    }

    private long transitionEffectEndNanos(EntryState state) {
        long end = state.transitionNanos;
        end = Math.max(end, state.glitchUntilNanos);
        end = Math.max(end, state.robocopUntilNanos);
        end = Math.max(end, state.typingBurstUntilNanos);
        end = Math.max(end, state.hologramUntilNanos);
        end = Math.max(end, state.pulseUntilNanos);
        end = Math.max(end, state.flickerUntilNanos);
        end = Math.max(end, state.extraBurstUntilNanos);
        return end;
    }

    /**
     * 1.21.11 Meteor snapshots transitioned TextRenderer.begin to carry the GUI graphics object.
     * Resolve the compatible overload once per renderer class; Custom Font and vanilla font can
     * therefore be used side by side without hardcoding an older snapshot signature.
     */
    private boolean beginText(TextRenderer renderer, Render2DEvent event, double textScale) {
        try {
            Class<?> rendererClass = renderer.getClass();
            if (resolvedBeginClasses.add(rendererClass)) {
                for (Method method : rendererClass.getMethods()) {
                    if (!method.getName().equals("begin")) continue;
                    if (method.getParameterCount() == 4) cachedBegin4.put(rendererClass, method);
                    else if (method.getParameterCount() == 3) cachedBegin3.put(rendererClass, method);
                }
            }

            Method begin4 = cachedBegin4.get(rendererClass);
            if (begin4 != null) {
                Object graphics = eventGraphics(event);
                if (graphics == null) throw new IllegalStateException("Render2DEvent has no graphics/drawContext field");
                begin4.invoke(renderer, graphics, textScale, false, true);
                return true;
            }

            Method begin3 = cachedBegin3.get(rendererClass);
            if (begin3 != null) {
                begin3.invoke(renderer, textScale, false, true);
                return true;
            }

            throw new NoSuchMethodException("No compatible TextRenderer.begin method found");
        } catch (Throwable throwable) {
            if (!textBridgeWarningShown) {
                textBridgeWarningShown = true;
                AddonTemplate.LOG.error("[竖模块列表] 无法初始化 1.21.11 TextRenderer 兼容桥", throwable);
            }
            return false;
        }
    }

    private Object eventGraphics(Render2DEvent event) throws IllegalAccessException {
        Class<?> eventClass = event.getClass();
        if (cachedEventClass != eventClass) {
            cachedEventClass = eventClass;
            cachedGraphicsField = null;
            for (String name : new String[] { "graphics", "drawContext" }) {
                try {
                    cachedGraphicsField = eventClass.getField(name);
                    break;
                } catch (NoSuchFieldException ignored) {
                    // Try the other 1.21.11 snapshot name.
                }
            }
        }
        return cachedGraphicsField == null ? null : cachedGraphicsField.get(event);
    }

    private SettingColor withAlpha(SettingColor source, double multiplier) {
        int a = (int) Math.round(source.a * clamp(multiplier, 0.0, 1.0));
        return new SettingColor(source.r, source.g, source.b, a);
    }

    private SettingColor lerpColor(SettingColor a, SettingColor b, double t, double alphaMultiplier) {
        t = clamp(t, 0.0, 1.0);
        int r = (int) Math.round(a.r + (b.r - a.r) * t);
        int g = (int) Math.round(a.g + (b.g - a.g) * t);
        int bl = (int) Math.round(a.b + (b.b - a.b) * t);
        int baseAlpha = (int) Math.round(a.a + (b.a - a.a) * t);
        int outAlpha = (int) Math.round(baseAlpha * clamp(alphaMultiplier, 0.0, 1.0));
        return new SettingColor(r, g, bl, outAlpha);
    }

    private SettingColor blendRgbKeepAlpha(SettingColor a, SettingColor b, double t) {
        t = clamp(t, 0.0, 1.0);
        int r = (int) Math.round(a.r + (b.r - a.r) * t);
        int g = (int) Math.round(a.g + (b.g - a.g) * t);
        int bl = (int) Math.round(a.b + (b.b - a.b) * t);
        return new SettingColor(r, g, bl, a.a);
    }

    private double scatterAngle(Module module) {
        return hashUnit(module.title.hashCode() * 341873128712L) * Math.PI * 2.0;
    }

    private double hashUnit(long value) {
        value ^= (value >>> 33);
        value *= 0xff51afd7ed558ccdL;
        value ^= (value >>> 33);
        value *= 0xc4ceb9fe1a85ec53L;
        value ^= (value >>> 33);
        return (value & 0x1fffffffffffffL) / (double) 0x1fffffffffffffL;
    }

    private double wrapDegrees(double value) {
        value %= 360.0;
        if (value >= 180.0) value -= 360.0;
        if (value < -180.0) value += 360.0;
        return value;
    }

    private double lerpExp(double current, double target, double speed, double dt) {
        double factor = 1.0 - Math.exp(-Math.max(0.0, speed) * Math.max(0.0, dt));
        return current + (target - current) * factor;
    }

    private double easeOutCubic(double t) {
        double x = 1.0 - clamp(t, 0.0, 1.0);
        return 1.0 - x * x * x;
    }

    private double easeInCubic(double t) {
        t = clamp(t, 0.0, 1.0);
        return t * t * t;
    }

    private double easeInOutCubic(double t) {
        t = clamp(t, 0.0, 1.0);
        return t < 0.5 ? 4.0 * t * t * t : 1.0 - Math.pow(-2.0 * t + 2.0, 3.0) / 2.0;
    }

    private double easeOutQuint(double t) {
        double x = 1.0 - clamp(t, 0.0, 1.0);
        return 1.0 - x * x * x * x * x;
    }

    private double fract(double value) {
        return value - Math.floor(value);
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static final class EntryState {
        final Module module;
        boolean active;
        double progress;
        long transitionNanos;
        long sequence;
        long glitchStartNanos;
        long glitchUntilNanos;
        long robocopStartNanos;
        long robocopUntilNanos;
        long robocopSeed;
        long typingBurstStartNanos;
        long typingBurstUntilNanos;
        BurstType extraBurstType;
        long extraBurstStartNanos;
        long extraBurstUntilNanos;
        long extraBurstSeed;
        long hologramStartNanos;
        long hologramUntilNanos;
        long pulseStartNanos;
        long pulseUntilNanos;
        long flickerStartNanos;
        long flickerUntilNanos;

        EntryState(Module module) {
            this.module = module;
        }
    }

    private static final class TextUnit {
        final String text;
        final int startIndex;
        final int length;
        final boolean rotated;

        TextUnit(String text, int startIndex, int length, boolean rotated) {
            this.text = text;
            this.startIndex = startIndex;
            this.length = length;
            this.rotated = rotated;
        }
    }

    private static final class RenderEntry {
        final EntryState state;
        final List<TextUnit> units;
        final String symbol;
        final int visibleCharacters;
        final int totalCharacters;
        final double x;
        final double y;
        final double width;
        final double height;
        final double symbolHeight;
        final double alpha;
        final int columnIndex;
        final boolean bottom;

        RenderEntry(EntryState state, List<TextUnit> units, String symbol, int visibleCharacters, int totalCharacters,
                    double x, double y, double width, double height, double symbolHeight, double alpha, int columnIndex, boolean bottom) {
            this.state = state;
            this.units = units;
            this.symbol = symbol;
            this.visibleCharacters = visibleCharacters;
            this.totalCharacters = totalCharacters;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.symbolHeight = symbolHeight;
            this.alpha = alpha;
            this.columnIndex = columnIndex;
            this.bottom = bottom;
        }
    }

    private enum BurstType {
        Glitch,
        Typing,
        Robocop,
        Hologram,
        PulseFrame,
        Flicker,
        FadeFlash,
        Blink,
        Scroll,
        Pulse,
        SlideUp,
        SlideDown,
        Bounce,
        Drift,
        Shake,
        GradientShift,
        Glow,
        Wipe,
        Stagger,
        Chromatic,
        Noise,
        Melt,
        Warp,
        Fragment,
        MatrixRain,
        Electric,
        Vhs,
        Neon,
        Interference,
        Disintegrate,
        RadarSweep,
        DataStream
    }

    public enum VerticalAnchor {
        Top("顶部向下"),
        Bottom("底部反向向上");

        private final String title;
        VerticalAnchor(String title) { this.title = title; }
        @Override public String toString() { return title; }
    }

    public enum BurstPreset {
        Minimal("极简"),
        Balanced("均衡"),
        Motion("动能"),
        Cyber("赛博故障"),
        Signal("信号HUD"),
        All("全部26种"),
        Custom("自定义");

        private final String title;
        BurstPreset(String title) { this.title = title; }
        @Override public String toString() { return title; }
    }

    public enum Side {
        Left("左侧"),
        Right("右侧");

        private final String title;
        Side(String title) { this.title = title; }
        @Override public String toString() { return title; }
    }

    public enum LatinLayout {
        Normal("整词顺时针90°"),
        Stacked("逐字母竖排");

        private final String title;
        LatinLayout(String title) { this.title = title; }
        @Override public String toString() { return title; }
    }

    public enum SortMode {
        Recent("最近开启优先"),
        Alphabetical("名称排序"),
        Longest("长名称优先");

        private final String title;
        SortMode(String title) { this.title = title; }
        @Override public String toString() { return title; }
    }

    public enum SymbolMode {
        Fixed("固定符号"),
        Cycle("符号池循环");

        private final String title;
        SymbolMode(String title) { this.title = title; }
        @Override public String toString() { return title; }
    }

    public enum SymbolRendererMode {
        Auto("自动兼容"),
        FollowFont("跟随文字字体"),
        Minecraft("Minecraft原生字体");

        private final String title;
        SymbolRendererMode(String title) { this.title = title; }
        @Override public String toString() { return title; }
    }

    public enum BackgroundMode {
        None("无"),
        Column("每列独立"),
        Panel("整体面板");

        private final String title;
        BackgroundMode(String title) { this.title = title; }
        @Override public String toString() { return title; }
    }

    public enum ScanMode {
        Line("细线"),
        SoftBand("柔光带"),
        DualBand("双光带");

        private final String title;
        ScanMode(String title) { this.title = title; }
        @Override public String toString() { return title; }
    }

    public enum ScanDirection {
        TopToBottom("从上到下"),
        BottomToTop("从下到上"),
        PingPong("往返扫描");

        private final String title;
        ScanDirection(String title) { this.title = title; }
        @Override public String toString() { return title; }
    }
}
