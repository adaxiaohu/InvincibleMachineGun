package com.codigohasta.addon.modules;

import com.codigohasta.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.WireframeEntityRenderer;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.SlimeEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.passive.WanderingTraderEntity;
import net.minecraft.entity.player.PlayerEntity;

public class IMGChams extends Module {
    public static IMGChams INSTANCE;

    // ---- Crystal settings ----
    private final SettingGroup sgCrystal = settings.createGroup("Crystal");

    public final Setting<Boolean> crystalEnabled = sgCrystal.add(new BoolSetting.Builder()
        .name("crystal-enabled")
        .description("Enable custom end crystal rendering.")
        .defaultValue(true)
        .build()
    );

    public final Setting<Boolean> custom = sgCrystal.add(new BoolSetting.Builder()
        .name("custom")
        .description("Use custom colored overlay on crystal model.")
        .defaultValue(false)
        .visible(() -> crystalEnabled.get())
        .build()
    );

    public final Setting<SettingColor> crystalColor = sgCrystal.add(new ColorSetting.Builder()
        .name("crystal-color")
        .description("Color tint for the crystal.")
        .defaultValue(new SettingColor(255, 255, 255, 255))
        .visible(() -> crystalEnabled.get() && custom.get())
        .build()
    );

    public final Setting<Boolean> depth = sgCrystal.add(new BoolSetting.Builder()
        .name("depth")
        .description("Enable depth test for custom crystal.")
        .defaultValue(false)
        .visible(() -> crystalEnabled.get() && custom.get())
        .build()
    );

    public final Setting<Boolean> chamsTexture = sgCrystal.add(new BoolSetting.Builder()
        .name("chams-texture")
        .description("Use texture on crystal model parts.")
        .defaultValue(true)
        .visible(() -> crystalEnabled.get() && custom.get())
        .build()
    );

    public final Setting<Boolean> glint = sgCrystal.add(new BoolSetting.Builder()
        .name("glint")
        .description("Enable glint effect on crystal.")
        .defaultValue(true)
        .visible(crystalEnabled::get)
        .build()
    );

    public final Setting<Boolean> textureEnabled = sgCrystal.add(new BoolSetting.Builder()
        .name("texture")
        .description("Enable crystal texture.")
        .defaultValue(true)
        .visible(crystalEnabled::get)
        .build()
    );

    public final Setting<Boolean> spinSync = sgCrystal.add(new BoolSetting.Builder()
        .name("spin-sync")
        .description("Sync spin with module age.")
        .defaultValue(false)
        .visible(crystalEnabled::get)
        .build()
    );

    public final Setting<Double> scale = sgCrystal.add(new DoubleSetting.Builder()
        .name("scale")
        .description("Crystal scale multiplier (relative to default 2x).")
        .defaultValue(1.0)
        .min(0.0)
        .max(3.0)
        .visible(crystalEnabled::get)
        .build()
    );

    public final Setting<Double> spinSpeed = sgCrystal.add(new DoubleSetting.Builder()
        .name("spin-speed")
        .description("Crystal spin speed.")
        .defaultValue(1.0)
        .min(0.0)
        .max(10.0)
        .visible(crystalEnabled::get)
        .build()
    );

    public final Setting<Double> bounceHeight = sgCrystal.add(new DoubleSetting.Builder()
        .name("bounce-height")
        .description("Crystal bounce height.")
        .defaultValue(1.0)
        .min(0.0)
        .max(3.0)
        .visible(crystalEnabled::get)
        .build()
    );

    public final Setting<Double> bounceSpeed = sgCrystal.add(new DoubleSetting.Builder()
        .name("bounce-speed")
        .description("Crystal bounce speed.")
        .defaultValue(1.0)
        .min(0.0)
        .max(3.0)
        .visible(crystalEnabled::get)
        .build()
    );

    public final Setting<Double> yOffset = sgCrystal.add(new DoubleSetting.Builder()
        .name("y-offset")
        .description("Crystal Y offset.")
        .defaultValue(0.0)
        .min(-1.0)
        .max(1.0)
        .visible(crystalEnabled::get)
        .build()
    );

    // ---- ThroughWall settings ----
    private final SettingGroup sgThroughWall = settings.createGroup("ThroughWall");

    public final Setting<Boolean> throughWall = sgThroughWall.add(new BoolSetting.Builder()
        .name("through-wall")
        .description("Show entities through walls (wireframe overlay).")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> twCrystals = sgThroughWall.add(new BoolSetting.Builder()
        .name("crystals")
        .description("Show end crystals through walls.")
        .defaultValue(true)
        .visible(() -> throughWall.get())
        .build()
    );

    private final Setting<Boolean> twPlayers = sgThroughWall.add(new BoolSetting.Builder()
        .name("players")
        .description("Show players through walls.")
        .defaultValue(true)
        .visible(() -> throughWall.get())
        .build()
    );

    private final Setting<Boolean> twMobs = sgThroughWall.add(new BoolSetting.Builder()
        .name("mobs")
        .description("Show mobs through walls.")
        .defaultValue(true)
        .visible(() -> throughWall.get())
        .build()
    );

    private final Setting<Boolean> twAnimals = sgThroughWall.add(new BoolSetting.Builder()
        .name("animals")
        .description("Show animals through walls.")
        .defaultValue(true)
        .visible(() -> throughWall.get())
        .build()
    );

    private final Setting<Boolean> twVillagers = sgThroughWall.add(new BoolSetting.Builder()
        .name("villagers")
        .description("Show villagers through walls.")
        .defaultValue(true)
        .visible(() -> throughWall.get())
        .build()
    );

    private final Setting<Boolean> twSlimes = sgThroughWall.add(new BoolSetting.Builder()
        .name("slimes")
        .description("Show slimes through walls.")
        .defaultValue(true)
        .visible(() -> throughWall.get())
        .build()
    );

    // ---- Hand settings ----
    private final SettingGroup sgHand = settings.createGroup("Hand");

    public final Setting<Boolean> handEnabled = sgHand.add(new BoolSetting.Builder()
        .name("hand-enabled")
        .description("Tint the player's hand and held items.")
        .defaultValue(false)
        .build()
    );

    public final Setting<Boolean> handTexture = sgHand.add(new BoolSetting.Builder()
        .name("hand-texture")
        .description("Show hand texture.")
        .defaultValue(true)
        .visible(handEnabled::get)
        .build()
    );

    public final Setting<SettingColor> handColor = sgHand.add(new ColorSetting.Builder()
        .name("hand-color")
        .description("Color for hand tint.")
        .defaultValue(new SettingColor(198, 135, 254, 150))
        .visible(handEnabled::get)
        .build()
    );

    public int age;

    public IMGChams() {
        super(AddonTemplate.CATEGORY, "R上色渲染者", "自定义水晶的状态，颜色。自定义手部颜色和物品透明度，自定义透视框。来自AlienV4的Chams模块。");
        INSTANCE = this;
    }

    @Override
    public void onActivate() {
        age = 0;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        age++;
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (throughWall.get() && mc.world != null) {
            for (Entity entity : mc.world.getEntities()) {
                if (shouldRenderThroughWall(entity) && entity != mc.player) {
                    WireframeEntityRenderer.render(
                        event,
                        entity,
                        1.0,
                        new SettingColor(255, 255, 255, 30),
                        new SettingColor(255, 255, 255, 127),
                        ShapeMode.Both
                    );
                }
            }
        }
    }

    public boolean customCrystal() {
        return isActive() && crystalEnabled.get();
    }

    private boolean shouldRenderThroughWall(Entity entity) {
        if (!isActive() || !throughWall.get()) return false;

        if (entity instanceof EndCrystalEntity) return twCrystals.get();
        if (entity instanceof SlimeEntity) return twSlimes.get();
        if (entity instanceof PlayerEntity) return twPlayers.get();
        if (entity instanceof VillagerEntity || entity instanceof WanderingTraderEntity) return twVillagers.get();
        if (entity instanceof AnimalEntity) return twAnimals.get();
        if (entity instanceof MobEntity) return twMobs.get();

        return false;
    }
}
