package com.codigohasta.addon;

import com.codigohasta.addon.commands.CommandExample;
import com.codigohasta.addon.commands.ImgNotifyCommand;
import com.codigohasta.addon.hud.HudExample;
import com.codigohasta.addon.hud.TargetHud;
// 导入所有模块
import com.codigohasta.addon.modules.*;
import com.codigohasta.addon.utils.alien.AlienBreakManager;

import com.mojang.logging.LogUtils;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.addons.GithubRepo;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.commands.Commands;
import meteordevelopment.meteorclient.events.game.GameJoinedEvent;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.systems.hud.Hud;
import meteordevelopment.meteorclient.systems.hud.HudGroup;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import org.slf4j.Logger;

public class AddonTemplate extends MeteorAddon {
    public static final Logger LOG = LogUtils.getLogger();

    private boolean sentWelcome = false;
    private int englishTranslationStartupDelay = 20;
    private boolean englishTranslationStartupChecked;

    public static final Category CATEGORY = new Category("IMG");
    public static final Category SF_CATEGORY = new Category("sfIMG");
    /**
     * HUD 模块专用分类。
     *
     * <p>本项目里「HUD」分两种，别混淆：
     * <ul>
     *     <li>{@link #HUD_GROUP} —— Meteor 原生 {@code HudElement} 的分组
     *         （如 {@code HudExample}、{@code TargetHud}），由 {@code Hud.get().register(...)} 注册</li>
     *     <li>{@link #IMG_HUD_CATEGORY} —— <b>本分类</b>，放继承 {@code Module} 的 HUD 类模块
     *         （如 {@code SakuraTargetHud}），由 {@code Modules.add(...)} 注册</li>
     * </ul>
     * 以后新增的 HUD 模块统一放这里，便于查找。
     */
    public static final Category IMG_HUD_CATEGORY = new Category("IMG-hud");
    public static final HudGroup HUD_GROUP = new HudGroup("IMG");

    @Override
    public void onInitialize() {
        LOG.info("Initializing IMG Addon");

        com.codigohasta.addon.utils.slimefunhelper.SlimefunRuntime.initialize();
        //注意了，sfimg部分 的模块并非在此注册。决定sfimg模块是否添加要在\addon\Utils\slimefunhelper\SlimefunRuntime.java中确定！
        // Modules 
        Modules modules = Modules.get();

        modules.add(new AdvancedCriticals());
        // modules.add(new AdvancedFakePlayer());
        modules.add(new AntiAntiXray());
        modules.add(new AttackRangeIndicator());
        modules.add(new AttractAura());
        modules.add(new AutoChestAura());
        modules.add(new AutoChorus());
        modules.add(new AutoDeoxidizer());
        modules.add(new AutoFirework());
        modules.add(new AutoJump());
        modules.add(new AutoKouZi());
        modules.add(new AutoMessage());
        modules.add(new AutoNod());
        modules.add(new AutoRespawn());
        modules.add(new AutoServer());
        modules.add(new AutoSmithing());
        modules.add(new AutoTPAccept());
        modules.add(new ChatFilter());
        modules.add(new ChatHider());
        modules.add(new ChatHighlight());
        modules.add(new ChatPrefixCustom());
        modules.add(new CrossbowAura());
        modules.add(new CustomFov());
        modules.add(new CustomItemESP());
        modules.add(new ElytraFollower());
        modules.add(new FastCrossbow());
        modules.add(new FeedbackBlocker());
        modules.add(new FlightAntiKick());
        // modules.add(new GrimCriticals());
        modules.add(new HexChat());
        modules.add(new HitboxESP());
        modules.add(new InfiniteChat());
        modules.add(new ItemDespawnTimer());
        
        modules.add(new KnockbackDirection());
        // modules.add(new MaceAura());
        modules.add(new MaceBreakerPro());
        modules.add(new MaceDMGPlus());
        modules.add(new MassTpa());
        modules.add(new MineESP());
        modules.add(new ModuleList());
        modules.add(new VerticalModuleList());
        modules.add(new MusicPlayer());
        modules.add(new OreVeinESP());
        modules.add(new PearlPhase());
        modules.add(new PortalESP());
        modules.add(new RTsearch());
        modules.add(new ShieldESP());
        modules.add(new SpearExploit());
        modules.add(new SwordGap());
        modules.add(new TargetStrafe());
        modules.add(new TntBomber());
        // modules.add(new TpAnchor());
        modules.add(new TpAura());
        // modules.add(new VelocityAlien());
        modules.add(new xhEntityList());
        modules.add(new FillESP());
        modules.add(new ScaffoldPlus());
         modules.add(new LegitNoFall());
        modules.add(new NoFallimg());

        modules.add(new xhPacketMinePlus());
        modules.add(new ElytraFlyPlus());
        modules.add(new adaAttributeSwap());
        modules.add(new adaManualCrystal());
        modules.add(new adaAutoHotbar());
        modules.add(new ODMGear());
        modules.add(new AdaPacketMine());
        modules.add(new AlienV4PacketMine());
        modules.add(new SchematicPro());
        
         modules.add(new MacroAnchor()); 
        // modules.add(new ElytraFly());
         modules.add(new Follower());
         modules.add(new ArrowDmg());
         modules.add(new Pitcher());
         modules.add(new VillagerTrader());
         modules.add(new IMGWorldStats());
         modules.add(new XTpaura());
         modules.add(new XCarry());
         modules.add(new EntityTags());
         // modules.add(new TpBowAura());
         // modules.add(new TpMachineGun());
         modules.add(new AutoLibrarian());
         modules.add(new SpearKill()); 
        // modules.add(new AntiLag());
        modules.add(new SprintStatusModule());
         // modules.add(new Backtrack());
         modules.add(new PortalGodMode());
        
         
         modules.add(new AutoDoubleHand());
         modules.add(new CrystalMacro());
         modules.add(new AutoInvTotem());
         modules.add(new KillFX());
        modules.add(new AlienSprint());
        modules.add(new RoundRotation());
         modules.add(new CameraClip());
         modules.add(new MotionCamera());
         modules.add(new Panic());
         modules.add(new Trajectories());
         modules.add(new IMGChams());
         modules.add(new IMGPopChams());
         modules.add(new IMGTips());
         modules.add(new IMGTotemParticle());
         modules.add(new IMGFakePlayer());
         modules.add(new GlobalSetting());
        // modules.add(new PlaceRender());
         modules.add(new AutoTorch());
         modules.add(new AutoRefreshTrade());
        // modules.add(new AutoCrystal());
        // modules.add(new AutoAnchor());
        // modules.add(new PistonCrystal());
        // modules.add(new ScaffoldPlusLeaves());
        //  modules.add(new LegitNoFallLeaves());
        // modules.add(new PrinterLeaves());
        // modules.add(new PacketMinePlus());
        // modules.add(new AutoCity());
        // modules.add(new FireworkElytraFly());
         modules.add(new Stuck());
         modules.add(new Ambience());
         modules.add(new CyberFujiShader());
         modules.add(new BlueHourShader());
         modules.add(new DuskShader());
         modules.add(new MatrixShader());
         modules.add(new SakuraShader());
         modules.add(new SilentHillShader());
         modules.add(new TerminatorHudShader());
         modules.add(new Freeze());
        // modules.add(new BMWSprint());
         modules.add(new AutoVault());
         modules.add(new ScreenActions());
         modules.add(new MobHud());
         modules.add(new LavaESP());
       //  modules.add(new CustomFishingBot());
         modules.add(new FurnaceUnclogger());
         modules.add(new EnglishUITranslation());

         modules.add(new SakuraTargetHud());
         modules.add(new ImgWatermarkHud());
         modules.add(new PlayerListHud());
         modules.add(new FpsHud());
         modules.add(new PingHud());
         modules.add(new CoordHud());
         modules.add(new ArmorHud());
         modules.add(new InventoryHud());
         modules.add(new CooldownsHud());
         modules.add(new PotionsHud());
         modules.add(new ChestCounterHud());
        modules.add(new NotificationsWidget());
        modules.add(new TpsHud());
        modules.add(new SpeedHud());
        modules.add(new Notifier());
        modules.add(new PvpResourcesHud());
        modules.add(new FlightHud());
        modules.add(new KeybindsHud());
        modules.add(new CrosshairHud());

        modules.add(new StandGui());

         modules.add(new BreakESP());
         new AlienBreakManager();
         modules.add(new TpAnchorAura());
         modules.add(new GAntiLag());
         modules.add(new GLightTpAnchorAura());
         modules.add(new GcoreGTrap());
         modules.add(new GcoreWebAura());
         modules.add(new GcoreTpCrystalAura());
         modules.add(new GAntiExplosion());
         modules.add(new GSuperVClip());
         modules.add(new GTpbot());
         modules.add(new GTpaura());
         modules.add(new TPServerEquivalent());
         modules.add(new GcoreTpDiagnostic());

        // English UI is enabled by default for every Minecraft language except
        // Simplified Chinese (zh_cn) and Traditional Chinese (zh_tw).
        enableEnglishTranslationForCurrentLanguage();
        EnglishUITranslation translation = modules.get(EnglishUITranslation.class);
        LOG.info("IMG English Translation registered; language={}, active={}", currentLanguageCode(), translation != null && translation.isActive());

        // Commands
        Commands.add(new CommandExample());
        Commands.add(new ImgNotifyCommand());

        // HUD
        Hud.get().register(HudExample.INFO);
        Hud.get().register(TargetHud.INFO);

        // 注册事件总线以接收 GameJoinEvent
        MeteorClient.EVENT_BUS.subscribe(this);
        // 移动包预算计数器（2026-10-11）
        MeteorClient.EVENT_BUS.subscribe(com.codigohasta.addon.utils.gcore.GcoreMoveBudget.INSTANCE);

        //  IMG 目标位置预判2026-10-13
        MeteorClient.EVENT_BUS.subscribe(com.codigohasta.addon.utils.img.ImgPositionPredict.INSTANCE);

        //  每帧推进 Sakura 2D 层的帧号性能修复2026-10-07
        MeteorClient.EVENT_BUS.subscribe(new com.codigohasta.addon.utils.sakura.render.SakuraFrameTicker());
    }

    @Override
    public void onRegisterCategories() {
        Modules.registerCategory(CATEGORY);
        Modules.registerCategory(SF_CATEGORY);
        Modules.registerCategory(IMG_HUD_CATEGORY);
    }

    @Override
    public String getPackage() {
        // 修正：必须匹配你的实际包名，否则 mixin 无法加载
        return "com.codigohasta.addon";
    }

    @Override
    public GithubRepo getRepo() {
        return new GithubRepo("adaxiaohu", "InvincibleMachineGun");
    }

    @EventHandler
    private void onGameJoin(GameJoinedEvent event) {
        enableEnglishTranslationForCurrentLanguage();
        if (sentWelcome) return;

        ChatUtils.forceNextPrefixClass(getClass());
        ChatUtils.sendMsg(createGradientText("成功装载没敌机关枪！b站Ada小虎：本插件开源免费。"));

        sentWelcome = true;
    }

    @EventHandler
    private void onGameLeave(GameLeftEvent event) {
   
        sentWelcome = false;
    }

    // Meteor restores module profiles after addon initialization. Delay the default
    // decision until client ticks have started so an existing profile cannot undo it.
    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (englishTranslationStartupChecked) return;
        if (englishTranslationStartupDelay-- > 0) return;

        englishTranslationStartupChecked = true;
        enableEnglishTranslationForCurrentLanguage();

        EnglishUITranslation translation = Modules.get().get(EnglishUITranslation.class);
        LOG.info("IMG English Translation startup check; language={}, auto-enable={}, active={}",
            currentLanguageCode(),
            translation != null && translation.autoEnableForNonChinese.get(),
            translation != null && translation.isActive());
    }

    private Text createGradientText(String text) {
        Color startColor = new Color(0, 255, 255); // 青色
        Color endColor = new Color(255, 0, 255);   // 品红色
        MutableText result = Text.empty();
        for (int i = 0; i < text.length(); i++) {
            float f = (float) i / (float) text.length();
            int r = (int) (startColor.r + (endColor.r - startColor.r) * f);
            int g = (int) (startColor.g + (endColor.g - startColor.g) * f);
            int b = (int) (startColor.b + (endColor.b - startColor.b) * f);
            Color stepColor = new Color(r, g, b, 255);
            result.append(Text.literal(String.valueOf(text.charAt(i))).setStyle(Style.EMPTY.withColor(TextColor.fromRgb(stepColor.getPacked()))));
        }
        return result;
    }

    private boolean isNonChineseLanguage() {
        String code = currentLanguageCode();
        if (code == null || code.isBlank()) return true;
        code = code.toLowerCase(java.util.Locale.ROOT);
        return !code.equals("zh_cn") && !code.equals("zh_tw");
    }

    private String currentLanguageCode() {
        return MinecraftClient.getInstance().getLanguageManager().getLanguage();
    }

    private void enableEnglishTranslationForCurrentLanguage() {
        EnglishUITranslation translation = Modules.get().get(EnglishUITranslation.class);
        if (translation == null || !translation.autoEnableForNonChinese.get() || !isNonChineseLanguage()) return;

        if (!translation.isActive()) {
            translation.enable();
            LOG.info("IMG English Translation enabled for language={}", currentLanguageCode());
        } else {
            translation.refreshTranslation();
            LOG.info("IMG English Translation refreshed for language={}", currentLanguageCode());
        }
    }
}
