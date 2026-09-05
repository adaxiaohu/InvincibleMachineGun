package com.codigohasta.addon.modules;

import com.codigohasta.addon.AddonTemplate;
import com.codigohasta.addon.utils.alien.AlienFriendManager;
import com.codigohasta.addon.utils.alien.AlienInventoryUtil;
import com.codigohasta.addon.utils.alien.AlienPopManager;
import com.codigohasta.addon.utils.alien.AlienTimer;
import meteordevelopment.meteorclient.events.entity.EntityAddedEvent;
import meteordevelopment.meteorclient.events.entity.EntityRemovedEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.EntityEvent;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.sounds.SoundSource;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

public class IMGTips extends Module {
    public static IMGTips INSTANCE;

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgAppearance = settings.createGroup("外观");

    // ==================== General ====================

    private final Setting<Boolean> visualRange = sgGeneral.add(new BoolSetting.Builder()
        .name("visual-range")
        .description("Notify when players enter/leave your visual range.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> friends = sgGeneral.add(new BoolSetting.Builder()
        .name("friends")
        .description("Also notify when friends enter/leave your visual range.")
        .defaultValue(false)
        .visible(visualRange::get)
        .build()
    );

    private final Setting<Boolean> popCounter = sgGeneral.add(new BoolSetting.Builder()
        .name("pop-counter")
        .description("Reports how many totems a player popped when they die.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> deathCoords = sgGeneral.add(new BoolSetting.Builder()
        .name("death-coords")
        .description("Records your death coordinates in chat.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> serverLag = sgGeneral.add(new BoolSetting.Builder()
        .name("server-lag")
        .description("Shows server not responding warning on screen.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> lagBack = sgGeneral.add(new BoolSetting.Builder()
        .name("lag-back")
        .description("Shows lagback countdown on screen.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> potion = sgGeneral.add(new BoolSetting.Builder()
        .name("potion")
        .description("Shows potion effect durations on screen.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> resistanceLevelCheck = sgGeneral.add(new BoolSetting.Builder()
        .name("resistance-level-check")
        .description("Only show resistance when amplifier > 0.")
        .defaultValue(true)
        .visible(potion::get)
        .build()
    );

    private final Setting<Boolean> chinese = sgGeneral.add(new BoolSetting.Builder()
        .name("chinese")
        .description("Use Chinese language for all tip messages.")
        .defaultValue(true)
        .build()
    );

    // ==================== Appearance ====================

    private final Setting<Double> textScale = sgAppearance.add(new DoubleSetting.Builder()
        .name("text-scale")
        .description("Scale of HUD warning and potion text.")
        .defaultValue(1.5)
        .min(0.5)
        .max(5.0)
        .sliderMax(5.0)
        .build()
    );

    private final Setting<SettingColor> warningColor = sgAppearance.add(new ColorSetting.Builder()
        .name("warning-color")
        .description("Color of the server lag / lagback warning text.")
        .defaultValue(new SettingColor(190, 0, 0))
        .build()
    );

    private final Setting<SettingColor> potionBaseColor = sgAppearance.add(new ColorSetting.Builder()
        .name("potion-base-color")
        .description("Base color for potion display (individual colors still apply).")
        .defaultValue(new SettingColor(255, 255, 255))
        .build()
    );

    private final Setting<Boolean> potionShadow = sgAppearance.add(new BoolSetting.Builder()
        .name("potion-shadow")
        .description("Draw shadow behind potion display text.")
        .defaultValue(true)
        .visible(potion::get)
        .build()
    );

    // ==================== Position ====================

    private final Setting<Integer> warningX = sgAppearance.add(new IntSetting.Builder()
        .name("warning-x")
        .description("X position for warning text (-1 = auto center).")
        .defaultValue(-1)
        .min(-1)
        .sliderMax(1920)
        .build()
    );

    private final Setting<Integer> warningY = sgAppearance.add(new IntSetting.Builder()
        .name("warning-y")
        .description("Y position for warning text.")
        .defaultValue(19)
        .min(0)
        .sliderMax(1080)
        .build()
    );

    private final Setting<Integer> potionX = sgAppearance.add(new IntSetting.Builder()
        .name("potion-x")
        .description("X position for potion display (-1 = auto center).")
        .defaultValue(-1)
        .min(-1)
        .sliderMax(1920)
        .build()
    );

    private final Setting<Integer> potionY = sgAppearance.add(new IntSetting.Builder()
        .name("potion-y")
        .description("Y position for potion text (-1 = default center+9).")
        .defaultValue(-1)
        .min(-1)
        .sliderMax(1080)
        .build()
    );

    private final Setting<Integer> yOffset = sgAppearance.add(new IntSetting.Builder()
        .name("y-offset")
        .description("Fine-tune potion Y offset (applied on top of potion-y).")
        .defaultValue(0)
        .min(-200)
        .max(200)
        .sliderMax(200)
        .visible(potion::get)
        .build()
    );

    // ==================== Internal State ====================

    private final DecimalFormat df = new DecimalFormat("0.0");
    private final AlienTimer lagTimer = new AlienTimer();
    private final AlienTimer lagBackTimer = new AlienTimer();
    private final AlienPopManager popManager = new AlienPopManager();
    private final AlienFriendManager friendManager = new AlienFriendManager();
    private final List<Player> deadPlayers = new ArrayList<>();
    int turtles = 0;

    public IMGTips() {
        super(AddonTemplate.CATEGORY, "提示", "提示信息，包含视距提醒、图腾计数、死亡坐标、服务器延迟检测和药水效果显示。来自AlienV4的Tips模块。");
        INSTANCE = this;
    }

    @Override
    public void onActivate() {
        lagTimer.reset();
        lagBackTimer.reset();
        deadPlayers.clear();
    }

    // ==================== Visual Range ====================

    @EventHandler
    private void onEntityAdded(EntityAddedEvent event) {
        if (!visualRange.get()) return;
        if (!(event.entity instanceof Player)) return;
        if (event.entity.getDisplayName() == null || event.entity == mc.player) return;

        String playerName = event.entity.getDisplayName().getString();
        boolean isFriend = friendManager.isFriend(playerName);
        if (!isFriend || friends.get()) {
            String msg = chinese.get()
                ? playerName + " 进入你的视距范围"
                : playerName + " entered your visual range.";
            ChatUtils.sendMsg(event.entity.getId() + 777, ChatFormatting.GRAY, msg);
            if (mc.level != null) {
                mc.level.playSound(mc.player, mc.player.blockPosition(),
                    SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 100.0F, 1.9F);
            }
        }
    }

    @EventHandler
    private void onEntityRemoved(EntityRemovedEvent event) {
        if (!visualRange.get()) return;
        if (!(event.entity instanceof Player)) return;
        if (event.entity.getDisplayName() == null || event.entity == mc.player) return;

        String playerName = event.entity.getDisplayName().getString();
        boolean isFriend = friendManager.isFriend(playerName);
        if (!isFriend || friends.get()) {
            String msg = chinese.get()
                ? playerName + " 离开你的视距范围"
                : playerName + " left your visual range.";
            ChatUtils.sendMsg(event.entity.getId() + 777, ChatFormatting.GRAY, msg);
            if (mc.level != null) {
                mc.level.playSound(mc.player, mc.player.blockPosition(),
                    SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 100.0F, 1.9F);
            }
        }
    }

    // ==================== Tick ====================

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.level == null) return;

        if (potion.get()) {
            turtles = AlienInventoryUtil.getPotionCount(MobEffects.RESISTANCE.value());
        }

        if (popCounter.get()) {
            for (Player player : mc.level.players()) {
                if (player == null) continue;
                if (player.isDeadOrDying() || player.getHealth() <= 0.0f) {
                    if (!deadPlayers.contains(player)) {
                        deadPlayers.add(player);
                        onPlayerDeath(player);
                    }
                } else {
                    deadPlayers.remove(player);
                }
            }
        }
    }

    // ==================== Packet ====================

    @EventHandler
    private void onPacket(PacketEvent.Receive event) {
        lagTimer.reset();

        if (event.packet instanceof ClientboundPlayerPositionPacket) {
            lagBackTimer.reset();
        }

        if (popCounter.get() && event.packet instanceof ClientboundEntityEventPacket packet) {
            if (packet.getEventId() == EntityEvent.PROTECTED_FROM_DEATH
                && packet.getEntity(mc.level) instanceof Player player) {
                popManager.onTotemPop(player.getName().getString());
                onTotemPop(player);
            }
        }
    }

    // ==================== 2D Rendering ====================
    //
    // Two paths to cover all scenarios:
    //   Render2DEvent  — fires from InGameHud TAIL when the world HUD is drawn
    //                     (no screen, or non-pausing screen — but behind the GUI)
    //   Screen render  — Screen @TAIL mixin renders ON TOP of any open GUI
    //
    // We use Render2DEvent only when no screen is open, and the Screen mixin
    // when a screen IS open.  This guarantees text is always visible on top.

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        if (mc.screen != null) return; // Screen mixin handles this
        renderText(event.graphics, event.screenWidth, event.screenHeight);
    }

    /** Called from MixinScreenOverlay — renders on top of any GUI. */
    public static void renderOnScreen(GuiGraphicsExtractor context) {
        IMGTips module = Modules.get().get(IMGTips.class);
        if (module == null || !module.isActive()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen == null) return;
        module.renderText(context, context.guiWidth(), context.guiHeight());
    }

    private void renderText(GuiGraphicsExtractor context, int screenWidth, int screenHeight) {
        double scale = textScale.get();

        try {
            // ---- Server Lag ----
            if (serverLag.get() && lagTimer.passedS(1.4)) {
                String line = (chinese.get() ? "服务器无响应" : "Server not responding")
                    + " (" + df.format(lagTimer.getMs() / 1000.0) + "s)";
                double y = warningY.get();
                drawString(context, line, warningX.get(), y, warningColor.get(), scale, screenWidth);

                // ---- Lagback (stacked below server lag) ----
                if (lagBack.get() && !lagBackTimer.passedS(1.5)) {
                    y += getTextHeight(scale) + 2;
                    drawLagback(context, y, scale, screenWidth);
                }
            } else if (lagBack.get() && !lagBackTimer.passedS(1.5)) {
                drawLagback(context, warningY.get(), scale, screenWidth);
            }

            // ---- Potion ----
            if (potion.get() && mc.player != null) {
                StringBuilder sb = buildPotionString();
                if (!sb.isEmpty()) {
                    String str = sb.toString();
                    double x;
                    if (potionX.get() >= 0) {
                        x = potionX.get();
                    } else {
                        x = (screenWidth / 2.0) - (mc.font.width(str) * scale / 2.0);
                    }
                    double py = potionY.get().intValue();
                    if (py < 0) {
                        py = screenHeight / 2.0 + 9;
                    }
                    py -= yOffset.get();
                    drawString(context, str, x, py, potionBaseColor.get(), scale, screenWidth,
                        potionShadow.get(), false);
                }
            }
        } catch (Exception ignored) { }
    }

    private void drawLagback(GuiGraphicsExtractor context, double y, double scale, int screenWidth) {
        String label = chinese.get() ? "回弹" : "Lagback";
        String line = label + " (" + df.format((1500L - lagBackTimer.getMs()) / 1000.0) + "s)";
        drawString(context, line, warningX.get(), y, warningColor.get(), scale, screenWidth);
    }

    /** Draw scaled text with shadow, auto-centered when settingX < 0. */
    private void drawString(GuiGraphicsExtractor context, String text, double settingX, double settingY,
                            SettingColor color, double scale, int screenWidth) {
        drawString(context, text, settingX, settingY, color, scale, screenWidth, true, true);
    }

    private void drawString(GuiGraphicsExtractor context, String text, double settingX, double settingY,
                            SettingColor color, double scale, int screenWidth,
                            boolean shadow, boolean centerWhenAuto) {
        double x;
        if (settingX >= 0) {
            x = settingX;
        } else if (centerWhenAuto) {
            float textWidth = mc.font.width(text);
            x = (screenWidth / 2.0) - (textWidth * scale / 2.0);
        } else {
            x = 0;
        }

        var matrices = context.pose();
        matrices.pushMatrix();
        matrices.translate((float) x, (float) settingY);
        matrices.scale((float) scale, (float) scale);
        context.text(mc.font, text, 0, 0, color.getPacked(), shadow);
        matrices.popMatrix();
    }

    /** Height of one line of text at the current scale (including shadow offset). */
    private double getTextHeight(double scale) {
        return (mc.font.lineHeight + 1) * scale;
    }

    // ==================== Potion string builder ====================

    private StringBuilder buildPotionString() {
        StringBuilder sb = new StringBuilder();

        if (turtles > 0) {
            sb.append("§e").append(turtles);
        }

        if (mc.player.hasEffect(MobEffects.RESISTANCE)
            && (!resistanceLevelCheck.get() || mc.player.getEffect(MobEffects.RESISTANCE).getAmplifier() > 0)) {
            if (!sb.isEmpty()) sb.append(" ");
            sb.append("§9").append(mc.player.getEffect(MobEffects.RESISTANCE).getDuration() / 20 + 1);
        }

        if (mc.player.hasEffect(MobEffects.STRENGTH)) {
            if (!sb.isEmpty()) sb.append(" ");
            sb.append("§4").append(mc.player.getEffect(MobEffects.STRENGTH).getDuration() / 20 + 1);
        }

        if (mc.player.hasEffect(MobEffects.SPEED)) {
            if (!sb.isEmpty()) sb.append(" ");
            sb.append("§b").append(mc.player.getEffect(MobEffects.SPEED).getDuration() / 20 + 1);
        }

        return sb;
    }

    // ==================== Death ====================

    private void onPlayerDeath(Player player) {
        String name = player.getName().getString();
        int popCount = popManager.getPop(name);
        boolean cn = chinese.get();

        MutableComponent msg;
        if (player.equals(mc.player)) {
            if (popCount > 0) {
                msg = Component.literal(cn ? "你在弹出 " : "You died after popping ").withStyle(ChatFormatting.GREEN);
                msg.append(Component.literal(String.valueOf(popCount)).withStyle(ChatFormatting.WHITE));
                msg.append(Component.literal(cn ? " 个图腾后死亡。" : (popCount == 1 ? " totem." : " totems.")).withStyle(ChatFormatting.GREEN));
            } else {
                msg = Component.literal(cn ? "你死了。" : "You died.").withStyle(ChatFormatting.RESET);
            }
        } else {
            if (popCount > 0) {
                msg = Component.literal(name).withStyle(ChatFormatting.WHITE);
                msg.append(Component.literal(cn ? " 在弹出 " : " died after popping ").withStyle(ChatFormatting.GREEN));
                msg.append(Component.literal(String.valueOf(popCount)).withStyle(ChatFormatting.WHITE));
                msg.append(Component.literal(cn ? " 个图腾后死亡。" : (popCount == 1 ? " totem." : " totems.")).withStyle(ChatFormatting.GREEN));
            } else {
                msg = Component.literal(name).withStyle(ChatFormatting.WHITE);
                msg.append(Component.literal(cn ? " 死了。" : " died.").withStyle(ChatFormatting.RESET));
            }
        }
        info(msg);

        if (deathCoords.get() && player == mc.player) {
            info(Component.literal(cn ? "你死于 " + player.getBlockX() + ", " + player.getBlockY() + ", " + player.getBlockZ()
                    : "You died at " + player.getBlockX() + ", " + player.getBlockY() + ", " + player.getBlockZ())
                .withStyle(ChatFormatting.DARK_RED));
        }

        popManager.onDeath(name);
    }

    // ==================== Totem pop ====================

    /** Called by IMGFakePlayer when its client-side fake player pops a totem. */
    public static void onFakePlayerTotemPop(String playerName, Player player) {
        IMGTips module = Modules.get().get(IMGTips.class);
        if (module != null && module.isActive() && module.popCounter.get()) {
            module.popManager.onTotemPop(playerName);
            module.onTotemPop(player);
        }
    }

    private void onTotemPop(Player player) {
        String name = player.getName().getString();
        int popCount = popManager.getPop(name);
        boolean cn = chinese.get();

        MutableComponent msg;
        if (player.equals(mc.player)) {
            msg = Component.literal(cn ? "你弹出了 " : "You popped ").withStyle(ChatFormatting.LIGHT_PURPLE);
            msg.append(Component.literal(String.valueOf(popCount)).withStyle(ChatFormatting.WHITE));
            msg.append(Component.literal(cn ? " 个图腾。" : (popCount == 1 ? " totem." : " totems.")).withStyle(ChatFormatting.LIGHT_PURPLE));
        } else {
            msg = Component.literal(name).withStyle(ChatFormatting.WHITE);
            msg.append(Component.literal(cn ? " 弹出了 " : " has popped ").withStyle(ChatFormatting.RED));
            msg.append(Component.literal(String.valueOf(popCount)).withStyle(ChatFormatting.WHITE));
            msg.append(Component.literal(cn ? " 个图腾。" : (popCount == 1 ? " totems." : " totems.")).withStyle(ChatFormatting.RED));
        }
        info(msg);
    }
}
