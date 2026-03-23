package com.codigohasta.addon;

import com.codigohasta.addon.commands.CommandExample;
import com.codigohasta.addon.hud.HudExample;
import com.codigohasta.addon.modules.SilentAura;
import com.codigohasta.addon.modules.AdvancedCriticals;
import com.codigohasta.addon.modules.GrimCriticals;
import com.codigohasta.addon.modules.AutoRespawn;
import com.codigohasta.addon.modules.TotemSmash;
import com.codigohasta.addon.modules.TeleportCriticals;
import com.codigohasta.addon.modules.TotemBypass;
import com.codigohasta.addon.modules.MaceDMGPlus;
import com.codigohasta.addon.modules.TpAura;
import com.codigohasta.addon.modules.ArmorBreaker;
import com.codigohasta.addon.modules.MaceAura;
import com.codigohasta.addon.modules.SimpleTpAura;
import com.codigohasta.addon.modules.AutoMiner;
import com.codigohasta.addon.modules.TntBomber;
import com.codigohasta.addon.modules.AutoServer;
import com.codigohasta.addon.modules.AutoJump;
import com.codigohasta.addon.modules.TargetStrafe;
import com.codigohasta.addon.modules.AnchorGod;
import com.codigohasta.addon.modules.MineESP;
import com.codigohasta.addon.modules.AutoCrystalPlus;
import com.codigohasta.addon.modules.AutoObsidian;
import com.codigohasta.addon.modules.xhEntityList; 
import com.codigohasta.addon.modules.AutoChestAura;
import com.codigohasta.addon.modules.AutoSmithing;
import com.codigohasta.addon.modules.VelocityAlien;
import com.codigohasta.addon.modules.ItemDespawnTimer;
import com.codigohasta.addon.modules.GTest;
import com.codigohasta.addon.modules.xhPacketMinePlus;
import com.codigohasta.addon.modules.TpAnchor;
import com.codigohasta.addon.modules.PearlPhase;
import com.codigohasta.addon.modules.SpearExploit;
import com.codigohasta.addon.modules.CustomFov;
import com.codigohasta.addon.modules.AntiSignPluginGUI;
import com.codigohasta.addon.modules.ElytraFollower;
import com.codigohasta.addon.modules.AttractAura;
import com.codigohasta.addon.modules.FlightAntiKick;
import com.codigohasta.addon.modules.MusicPlayer;
import com.codigohasta.addon.modules.CreativeLavaMountain;
import com.codigohasta.addon.modules.AutoNod;
import com.codigohasta.addon.modules.ChatHighlight;
import com.codigohasta.addon.modules.RTsearch;
import com.codigohasta.addon.modules.PortalESP;
import com.codigohasta.addon.modules.KillFX;
import com.codigohasta.addon.modules.ShieldESP;
import com.codigohasta.addon.modules.AutoDeoxidizer;
import com.codigohasta.addon.modules.MassTpa;
import com.codigohasta.addon.modules.AutoTPAccept;
import com.codigohasta.addon.modules.HitboxESP;
import com.codigohasta.addon.modules.AutoChorus;
import com.codigohasta.addon.modules.FastCrossbow;
import com.codigohasta.addon.modules.CrossbowAura;
import com.codigohasta.addon.modules.SwordGap;
import com.codigohasta.addon.modules.HexChat;
import com.codigohasta.addon.modules.ChatHider;
import com.codigohasta.addon.modules.InfiniteChat;
import com.codigohasta.addon.modules.FeedbackBlocker;   
import com.codigohasta.addon.modules.ChatFilter;
import com.codigohasta.addon.modules.AttackRangeIndicator;
import com.codigohasta.addon.modules.AntiAntiXray;
import com.codigohasta.addon.modules.CommandFlooder;
import com.codigohasta.addon.modules.OreVeinESP;
import com.codigohasta.addon.modules.CustomItemESP;
import com.codigohasta.addon.modules.AuctionDesync;
import com.codigohasta.addon.modules.AutoFirework;
import com.codigohasta.addon.modules.RealLagDupe;
import com.codigohasta.addon.modules.AutoMessage;
import com.codigohasta.addon.modules.CyberFujiOverlay;
import com.codigohasta.addon.modules.MatrixOverlay;
import com.codigohasta.addon.modules.SilentHillOverlay;
import com.codigohasta.addon.modules.SakuraOverlay;
import com.codigohasta.addon.modules.DuskOverlay;
import com.codigohasta.addon.modules.SmartTPAura;
import com.codigohasta.addon.modules.TestTpAura;
import com.codigohasta.addon.modules.MacePathAura;
import com.codigohasta.addon.modules.TeleportNotebot;
import com.codigohasta.addon.modules.MaceBreakerPro;
import com.codigohasta.addon.modules.VanishDetector;
import com.codigohasta.addon.modules.WoodenMan;
import com.codigohasta.addon.modules.ModuleList;
import com.codigohasta.addon.modules.xhGrimAura;
import com.codigohasta.addon.modules.AdvancedFakePlayer;
import com.codigohasta.addon.modules.ChatPrefixCustom;
import com.codigohasta.addon.modules.KnockbackDirection;
import com.codigohasta.addon.modules.AutoKouZi;
import com.codigohasta.addon.modules.CustomArmor;
import com.codigohasta.addon.modules.Follower;





import com.mojang.logging.LogUtils;


import meteordevelopment.meteorclient.addons.GithubRepo;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.commands.Commands;
import meteordevelopment.meteorclient.systems.hud.Hud;
import meteordevelopment.meteorclient.systems.hud.HudGroup;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.item.Items;
import org.slf4j.Logger;

public class AddonTemplate extends MeteorAddon {
    public static final Logger LOG = LogUtils.getLogger();
    
    // --- 关键修复：Items.MACE -> Items.MACE.getDefaultStack() ---
    public static final Category CATEGORY = new Category("IMG", Items.MACE.getDefaultStack());
    public static final HudGroup HUD_GROUP = new HudGroup("IMG");

    @Override
    public void onInitialize() {
        LOG.info("Initializing Invincible Machine Gun Addon");

        // Modules
        Modules.get().add(new SilentAura());
        Modules.get().add(new AdvancedCriticals());
        Modules.get().add(new GrimCriticals());
        Modules.get().add(new TotemSmash());
        Modules.get().add(new TeleportCriticals());
        Modules.get().add(new TotemBypass());
        Modules.get().add(new TpAura());
        Modules.get().add(new ArmorBreaker());
        Modules.get().add(new SimpleTpAura());
        Modules.get().add(new MaceAura());
        Modules.get().add(new AutoMiner());
        Modules.get().add(new AutoRespawn());
        Modules.get().add(new TntBomber());
        Modules.get().add(new AutoServer());
        Modules.get().add(new AutoJump());
        Modules.get().add(new TargetStrafe());
        Modules.get().add(new AnchorGod());
        Modules.get().add(new MineESP());
        Modules.get().add(new AutoCrystalPlus());
        Modules.get().add(new AutoObsidian());
        Modules.get().add(new MaceDMGPlus());
        Modules.get().add(new xhEntityList());
        Modules.get().add(new AutoChestAura());
        Modules.get().add(new AutoSmithing());
        Modules.get().add(new VelocityAlien());
        Modules.get().add(new ItemDespawnTimer());
        Modules.get().add(new GTest());
        Modules.get().add(new xhPacketMinePlus());
        Modules.get().add(new TpAnchor());
        Modules.get().add(new PearlPhase());
        Modules.get().add(new SpearExploit());
        Modules.get().add(new CustomFov());
        Modules.get().add(new AntiSignPluginGUI());
        Modules.get().add(new ElytraFollower());
        Modules.get().add(new AttractAura());
        Modules.get().add(new FlightAntiKick());
        Modules.get().add(new MusicPlayer());
        Modules.get().add(new CreativeLavaMountain());
        Modules.get().add(new AutoNod());
        Modules.get().add(new ChatHighlight());
        Modules.get().add(new RTsearch());
        Modules.get().add(new PortalESP());
        Modules.get().add(new KillFX());
        Modules.get().add(new ShieldESP());
        Modules.get().add(new AutoDeoxidizer());
        Modules.get().add(new MassTpa());
        Modules.get().add(new AutoTPAccept());
        Modules.get().add(new HitboxESP());
        Modules.get().add(new AutoChorus());
        Modules.get().add(new FastCrossbow());
        Modules.get().add(new CrossbowAura());
        Modules.get().add(new SwordGap());
        Modules.get().add(new HexChat());
        Modules.get().add(new ChatHider());
        Modules.get().add(new InfiniteChat());  
        Modules.get().add(new FeedbackBlocker());  
        Modules.get().add(new ChatFilter());
        Modules.get().add(new AttackRangeIndicator());
        Modules.get().add(new AntiAntiXray());
        Modules.get().add(new CommandFlooder());
        Modules.get().add(new OreVeinESP());
        Modules.get().add(new CustomItemESP());
        Modules.get().add(new AuctionDesync());
        Modules.get().add(new AutoFirework());
        Modules.get().add(new RealLagDupe());
        Modules.get().add(new AutoMessage());
        Modules.get().add(new CyberFujiOverlay());
        Modules.get().add(new MatrixOverlay());
        Modules.get().add(new SilentHillOverlay());
        Modules.get().add(new SakuraOverlay());
        Modules.get().add(new DuskOverlay());
        Modules.get().add(new SmartTPAura());
        Modules.get().add(new TestTpAura());
        Modules.get().add(new MacePathAura());
        Modules.get().add(new TeleportNotebot());
        Modules.get().add(new MaceBreakerPro());
        Modules.get().add(new VanishDetector());
        Modules.get().add(new WoodenMan());
        Modules.get().add(new ModuleList());
        Modules.get().add(new xhGrimAura());
        Modules.get().add(new AdvancedFakePlayer());
        Modules.get().add(new ChatPrefixCustom());
        Modules.get().add(new KnockbackDirection());
        Modules.get().add(new AutoKouZi());
        Modules.get().add(new CustomArmor());
        Modules.get().add(new Follower());





        // Commands
        Commands.add(new CommandExample());

        // HUD
        Hud.get().register(HudExample.INFO);
    }

    @Override
    public void onRegisterCategories() {
        Modules.registerCategory(CATEGORY);
    }

    @Override
    public String getPackage() {
        return "com.codigohasta.addon";
    }

    @Override
    public GithubRepo getRepo() {
        return new GithubRepo("CodigoHasta", "silent-aura-addon");
    }
}