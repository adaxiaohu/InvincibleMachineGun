package com.codigohasta.addon;

import com.codigohasta.addon.commands.CommandExample;
import com.codigohasta.addon.hud.HudExample;
import com.codigohasta.addon.hud.TargetHud;
// 导入所有模块
import com.codigohasta.addon.modules.*;
import com.mojang.logging.LogUtils;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.addons.GithubRepo;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.commands.Commands;
import meteordevelopment.meteorclient.events.game.GameJoinedEvent;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.systems.hud.Hud;
import meteordevelopment.meteorclient.systems.hud.HudGroup;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import org.slf4j.Logger;

public class AddonTemplate extends MeteorAddon {
    public static final Logger LOG = LogUtils.getLogger();

    private boolean sentWelcome = false;

    public static final Category CATEGORY = new Category("IMG");
    public static final HudGroup HUD_GROUP = new HudGroup("IMG");

    @Override
    public void onInitialize() {
        LOG.info("Initializing IMG Addon");

        // Modules 
        Modules modules = Modules.get();

        modules.add(new AdvancedCriticals());
        modules.add(new AdvancedFakePlayer());
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
        modules.add(new GrimCriticals());
        modules.add(new HexChat());
        modules.add(new HitboxESP());
        modules.add(new InfiniteChat());
        modules.add(new ItemDespawnTimer());
        
        modules.add(new KnockbackDirection());
        modules.add(new MaceAura());
        modules.add(new MaceBreakerPro());
        modules.add(new MaceDMGPlus());
        modules.add(new MassTpa());
        modules.add(new MineESP());
        modules.add(new ModuleList());
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
        modules.add(new TpAnchor());
        modules.add(new TpAura());
        modules.add(new VelocityAlien());
        modules.add(new xhEntityList());
        modules.add(new FillESP());
        modules.add(new ScaffoldPlus());
         modules.add(new LegitNoFall());

        modules.add(new xhPacketMinePlus());
        modules.add(new ElytraFlyPlus());
        modules.add(new adaAttributeSwap());
        modules.add(new adaManualCrystal());
        modules.add(new adaAutoHotbar());
        modules.add(new ODMGear());
        modules.add(new AdaPacketMine());
        modules.add(new SchematicPro());
        
         modules.add(new MacroAnchor()); 
         modules.add(new ElytraFly());
         modules.add(new Follower());
         modules.add(new ArrowDmg());
         modules.add(new Pitcher());
         modules.add(new VillagerTrader());
         modules.add(new IMGWorldStats());
         modules.add(new XTpaura());
         modules.add(new EntityTags());
         modules.add(new TpBowAura());
         modules.add(new TpMachineGun());
         modules.add(new AutoLibrarian());
         modules.add(new SpearKill()); 
         modules.add(new AntiLag());
         modules.add(new SprintStatusModule());
         modules.add(new Backtrack());
         modules.add(new PortalGodMode());
         
         modules.add(new AutoDoubleHand());
         modules.add(new CrystalMacro());
         modules.add(new AutoInvTotem());


        // Commands
        Commands.add(new CommandExample());

        // HUD
        Hud.get().register(HudExample.INFO);
        Hud.get().register(TargetHud.INFO);

        // 注册事件总线以接收 GameJoinEvent
        MeteorClient.EVENT_BUS.subscribe(this);
    }

    @Override
    public void onRegisterCategories() {
        Modules.registerCategory(CATEGORY);
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
        if (sentWelcome) return;

        ChatUtils.forceNextPrefixClass(getClass());
        ChatUtils.sendMsg(createGradientText("成功装载没敌机关枪！b站Ada小虎：本插件开源免费。"));

        sentWelcome = true;
    }

    @EventHandler
    private void onGameLeave(GameLeftEvent event) {
   
        sentWelcome = false;
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
}