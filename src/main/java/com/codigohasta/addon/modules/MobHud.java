package com.codigohasta.addon.modules;

import com.codigohasta.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.game.SendMessageEvent;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.network.chat.Component;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;

public class MobHud extends Module {
    // ─── 原版 MobHUD 常量 ───────────────────────────────
    private static final int LEFT_MARGIN = 5;
    private static final int MOB_EXTRA_SHIFT = 3;
    private static final int BOX_PADDING = 4;
    private static final int BOX_HEIGHT = 12;
    private static final int BLACK_FRAME = 0xFF000000;
    private static final float SHADOW_FACTOR = 0.5f;

    // ─── 设置 ────────────────────────────────────────────
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgDisplay = settings.createGroup("内容设置");

    private final Setting<SettingColor> hudColor = sgGeneral.add(new ColorSetting.Builder()
        .name("HUD 颜色")
        .description("HUD 文字与边框颜色")
        .defaultValue(new SettingColor(255, 255, 255))
        .build()
    );

    private final Setting<String> aiName = sgGeneral.add(new StringSetting.Builder()
        .name("AI 名称")
        .description("用于聊天指令的 AI 名称（不区分大小写）")
        .defaultValue("Jarvis")
        .build()
    );

    private final Setting<Boolean> textBoxEnabled = sgGeneral.add(new BoolSetting.Builder()
        .name("文本框")
        .description("在文字周围绘制黑色边框背景（半透明深色背景 + 1px 黑框）")
        .defaultValue(true)
        .build()
    );

    // ─── 显示开关 ────────────────────────────────────────
    private final Setting<Boolean> showWeather = sgDisplay.add(new BoolSetting.Builder()
        .name("天气与温度")
        .defaultValue(true)
        .build()
    );
    private final Setting<Boolean> showEntityInfo = sgDisplay.add(new BoolSetting.Builder()
        .name("注视实体信息")
        .defaultValue(true)
        .build()
    );
    private final Setting<Boolean> showSpeed = sgDisplay.add(new BoolSetting.Builder()
        .name("速度显示")
        .defaultValue(true)
        .build()
    );
    private final Setting<Boolean> showAltitude = sgDisplay.add(new BoolSetting.Builder()
        .name("高度/深度")
        .defaultValue(true)
        .build()
    );
    private final Setting<Boolean> showVitals = sgDisplay.add(new BoolSetting.Builder()
        .name("低血量/氧气警告")
        .defaultValue(true)
        .build()
    );
    private final Setting<Boolean> showNavigation = sgDisplay.add(new BoolSetting.Builder()
        .name("导航")
        .defaultValue(true)
        .build()
    );
    private final Setting<Boolean> showToggleMessage = sgDisplay.add(new BoolSetting.Builder()
        .name("切换反馈消息")
        .description("聊天指令执行后的屏幕提示")
        .defaultValue(true)
        .build()
    );
    private final Setting<Double> entityRange = sgGeneral.add(new DoubleSetting.Builder()
        .name("实体探测范围")
        .description("注视实体的最大探测距离")
        .defaultValue(40.0)
        .min(5.0)
        .sliderMax(100.0)
        .build()
    );

    // ─── 内部状态 ────────────────────────────────────────
    private int tickCounter = 0;
    private double lastX;
    private double lastZ;
    private double blocksPerSecond = 0.0;

    private int targetX = Integer.MIN_VALUE;
    private int targetZ = Integer.MIN_VALUE;

    private String toggleMessage = "";
    private long toggleMessageUntil = 0;
    private boolean pendingDisable = false; // 延迟关闭，让反馈消息显示 3 秒

    public MobHud() {
        super(AddonTemplate.CATEGORY, "MobHUD",
            "智能 HUD — 显示天气、温度、速度、海拔、注视实体信息、导航等。\n" +
            "聊天指令：\"Jarvis enable smart hud\" 启用。");
    }

    // ═════════════════════════════════════════════════════════
    //  事件
    // ═════════════════════════════════════════════════════════

    @EventHandler
    private void onTick(TickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) return;

        tickCounter++;
        if (tickCounter >= 20) {
            double dx = player.getX() - lastX;
            double dz = player.getZ() - lastZ;
            blocksPerSecond = Math.sqrt((dx * dx) + (dz * dz));
            lastX = player.getX();
            lastZ = player.getZ();
            tickCounter = 0;
        }
    }

    @EventHandler
    private void onSendMessage(SendMessageEvent event) {
        Minecraft mc = Minecraft.getInstance();
        String raw = event.message;
        if (raw == null) return;

        String msg = raw.trim().toLowerCase();
        String ai = aiName.get().toLowerCase();

        if (msg.equals(ai + " enable smart hud")) {
            if (!isActive()) toggle();
            toggleMessage = capitalize(aiName.get()) + ": Smart HUD Enabled Sir";
            toggleMessageUntil = System.currentTimeMillis() + 3000;
            event.cancel();
            return;
        }
        if (msg.equals(ai + " disable smart hud")) {
            // 不要立即关闭模块 — 让反馈消息显示 3 秒后再关
            pendingDisable = true;
            toggleMessage = capitalize(aiName.get()) + ": Smart HUD Disabled Sir";
            toggleMessageUntil = System.currentTimeMillis() + 3000;
            event.cancel();
            return;
        }
        if (msg.startsWith("ai name ")) {
            String newName = raw.substring("ai name ".length()).trim();
            if (!newName.isEmpty()) {
                aiName.set(newName);
                toggleMessage = "AI name set to " + capitalize(newName);
            } else {
                toggleMessage = "Invalid AI name";
            }
            toggleMessageUntil = System.currentTimeMillis() + 3000;
            event.cancel();
            return;
        }
        if (msg.startsWith("smart hud color ")) {
            String colorName = raw.substring("smart hud color ".length()).trim();
            Integer parsed = parseNamedColor(colorName);
            if (parsed != null) {
                hudColor.set(new SettingColor(parsed));
                toggleMessage = capitalize(aiName.get()) + ": Smart HUD color set to " + colorName;
            } else {
                toggleMessage = capitalize(aiName.get()) + ": Unknown color: " + colorName;
            }
            toggleMessageUntil = System.currentTimeMillis() + 3000;
            event.cancel();
            return;
        }
        if (msg.equals(ai + " disable text box")) {
            textBoxEnabled.set(false);
            toggleMessage = capitalize(aiName.get()) + ": Component AABB Disabled Sir!";
            toggleMessageUntil = System.currentTimeMillis() + 3000;
            event.cancel();
            return;
        }
        if (msg.equals(ai + " enable text box")) {
            textBoxEnabled.set(true);
            toggleMessage = capitalize(aiName.get()) + ": Component AABB Enabled Sir!";
            toggleMessageUntil = System.currentTimeMillis() + 3000;
            event.cancel();
            return;
        }
        if (msg.equals(ai + " where am i")) {
            if (mc.player != null) {
                int x = (int) mc.player.getX();
                int y = (int) mc.player.getY();
                int z = (int) mc.player.getZ();
                mc.gui.getChat().addClientSystemMessage(
                    Component.literal(capitalize(aiName.get()) + ": You are at " + x + ", " + y + ", " + z + " coordinates Sir"));
            }
            event.cancel();
            return;
        }
        if (msg.startsWith(ai + " get me to ")) {
            String coords = msg.substring((ai + " get me to ").length()).trim();
            String[] parts = coords.split("\\s+");
            if (parts.length == 2) {
                try {
                    targetX = Integer.parseInt(parts[0]);
                    targetZ = Integer.parseInt(parts[1]);
                    toggleMessage = capitalize(aiName.get()) + ": Navigation set to " + targetX + ", " + targetZ + " Sir";
                } catch (NumberFormatException e) {
                    toggleMessage = capitalize(aiName.get()) + ": Invalid coordinates Sir";
                }
            } else {
                toggleMessage = capitalize(aiName.get()) + ": Use format: get me to x z Sir";
            }
            toggleMessageUntil = System.currentTimeMillis() + 3000;
            event.cancel();
            return;
        }
        if (msg.equals(ai + " cancel trip")) {
            targetX = Integer.MIN_VALUE;
            targetZ = Integer.MIN_VALUE;
            toggleMessage = capitalize(aiName.get()) + ": Navigation canceled Sir";
            toggleMessageUntil = System.currentTimeMillis() + 3000;
            event.cancel();
        }
    }

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) return;

        Font font = mc.font;
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();

        // 检查延迟关闭（消息显示完毕后再关）
        if (pendingDisable && System.currentTimeMillis() >= toggleMessageUntil) {
            pendingDisable = false;
            if (isActive()) toggle();
        }

        // SettingColor 的 r/g/b/a 是标准 0-255 分量，手动组装 ARGB（drawText/fill 需要的格式）
        SettingColor sc = hudColor.get();
        int argbColor = (sc.a << 24) | (sc.r << 16) | (sc.g << 8) | sc.b;

        // ── 切换反馈消息（居中偏下） ──
        if (showToggleMessage.get() && System.currentTimeMillis() < toggleMessageUntil && !toggleMessage.isEmpty()) {
            int msgX = (screenWidth - font.width(toggleMessage)) / 2;
            int msgY = screenHeight - 62;
            drawTextWithBox(event, font, toggleMessage, msgX, msgY, argbColor);
        }

        // ── 天气 & 温度（右下角） ──
        if (showWeather.get()) {
            String weatherText;
            if (mc.level.isThundering()) {
                weatherText = "Weather: Thunder";
            } else if (mc.level.isRaining()) {
                weatherText = "Weather: Rain";
            } else {
                weatherText = "Weather: Clear";
            }

            int weatherShift = font.width("We");
            int weatherX = LEFT_MARGIN + weatherShift;
            int weatherY = screenHeight - 15;
            drawTextWithBox(event, font, weatherText, weatherX, weatherY, argbColor);

            int tempC = computeTemperatureC(mc, player);
            String tempStr = tempC + " °C";
            int tempX = weatherX + font.width(weatherText) + 6;
            drawTextWithBox(event, font, tempStr, tempX, weatherY, argbColor);
        }

        // ── 注视实体 ──
        if (showEntityInfo.get()) {
            Entity lookedAt = getLookedAtEntity(mc, entityRange.get());
            if (lookedAt != null) {
                double dist = player.distanceTo(lookedAt);
                String mobName = lookedAt.getName().getString();
                String mobDist = "Distance: " + ((int) dist) + " Blocks Away";

                int weatherShift = font.width("We");
                int disShift = font.width("Dis");
                int mobX = LEFT_MARGIN + weatherShift + MOB_EXTRA_SHIFT + disShift;
                int baseY = screenHeight / 2;

                drawTextWithBox(event, font, mobName, mobX, baseY, argbColor);
                drawTextWithBox(event, font, mobDist, mobX, baseY + 10, argbColor);

                boolean showDanger = false;
                boolean showPossibleDanger = false;

                if (lookedAt instanceof Mob mob) {
                    boolean isHostile = mob.getType().getCategory() == MobCategory.MONSTER && !(mob instanceof NeutralMob);
                    boolean isNeutral = mob instanceof NeutralMob;
                    boolean isAngry = mob.isAggressive() || mob.getTarget() == player;

                    if (mob instanceof EnderMan) {
                        isAngry |= ((EnderMan) mob).hasBeenStaredAt();
                    }

                    if (isAngry || mob.getLastAttacker() == player || mob.getLastHurtByMob() == player) {
                        showDanger = true;
                    } else if (isHostile) {
                        showDanger = true;
                    } else if (isNeutral) {
                        showPossibleDanger = true;
                    }
                }

                if (showDanger) {
                    int pulse = (int) ((System.currentTimeMillis() / 500) % 2);
                    int dangerColor = pulse == 0 ? 0xFFFF8C00 : 0xFFFF0000;
                    drawTextWithBox(event, font, "Danger!", mobX, baseY + 20, dangerColor);
                } else if (showPossibleDanger) {
                    int pulse = (int) ((System.currentTimeMillis() / 600) % 2);
                    int possibleColor = pulse == 0 ? 0xFFFFA500 : 0xFFFF7F50;
                    drawTextWithBox(event, font, "Possible Danger!", mobX, baseY + 20, possibleColor);
                }
            }
        }

        // ── 速度（居中顶部） ──
        if (showSpeed.get()) {
            String speed = "Speed: " + String.format("%.2f", blocksPerSecond) + " b/s";
            int speedX = (screenWidth - font.width(speed)) / 2;
            drawTextWithBox(event, font, speed, speedX, LEFT_MARGIN, argbColor);
        }

        // ── 高度 / 深度 ──
        if (showAltitude.get()) {
            BlockPos pos = player.blockPosition();
            int groundY = mc.level.getHeight(Heightmap.Types.MOTION_BLOCKING, pos.getX(), pos.getZ());
            int diff = (int) (player.getY() - groundY);

            if (player.isUnderWater()) {
                int surfaceY = mc.level.getHeight(Heightmap.Types.WORLD_SURFACE, pos.getX(), pos.getZ());
                int depth = (int) (player.getEyeY() - surfaceY);
                String depthStr = "Depth: " + (-Math.abs(depth));
                int depthX = (screenWidth - font.width(depthStr)) / 2;
                drawTextWithBox(event, font, depthStr, depthX, 20, argbColor);
            } else if (diff > LEFT_MARGIN) {
                String altitude = "Altitude: " + diff;
                int altX = (screenWidth - font.width(altitude)) / 2;
                drawTextWithBox(event, font, altitude, altX, 20, argbColor);
            }
        }

        // ── 低血量 & 氧气警告 ──
        if (showVitals.get()) {
            if (player.getHealth() < 10.0f) {
                int pulse = (int) ((System.currentTimeMillis() / 500) % 2);
                int lowPulse = pulse == 0 ? 0xFFFFDD44 : 0xFFFF0000;
                int vx = (screenWidth - font.width("!! Vitals Low !!")) / 2;
                int vy = screenHeight - 70;
                drawTextWithBox(event, font, "!! Vitals Low !!", vx, vy, lowPulse);
            }
            if (player.getAirSupply() < 120) {
                int pulse = (int) ((System.currentTimeMillis() / 500) % 2);
                int oxygenColor = pulse == 0 ? 0xFFFFDD44 : 0xFFFF6600;
                int oxX = (screenWidth - font.width("!! Oxygen !!")) / 2;
                int oxY = screenHeight - 55;
                drawTextWithBox(event, font, "!! Oxygen !!", oxX, oxY, oxygenColor);
            }
        }

        // ── 导航 ──
        if (showNavigation.get() && targetX != Integer.MIN_VALUE && targetZ != Integer.MIN_VALUE) {
            double pX = player.getX();
            double pZ = player.getZ();
            double dx = targetX - pX;
            double dz = targetZ - pZ;
            double distance = Math.sqrt((dx * dx) + (dz * dz));

            if (distance <= 1.0) {
                targetX = Integer.MIN_VALUE;
                targetZ = Integer.MIN_VALUE;
                toggleMessage = capitalize(aiName.get()) + ": Target reached Sir!";
                toggleMessageUntil = System.currentTimeMillis() + 3000;
            } else {
                String direction;
                Vec3 lookVec = player.getViewVector(1.0f);
                double lookAngle = Math.toDegrees(Math.atan2(lookVec.z, lookVec.x));
                double targetAngle = Math.toDegrees(Math.atan2(dz, dx));
                double angleDiff = ((targetAngle - lookAngle) + 360.0) % 360.0;

                if (angleDiff < 45.0 || angleDiff >= 315.0)       direction = "<Forward>";
                else if (angleDiff < 135.0)                       direction = "<Right>";
                else if (angleDiff < 225.0)                       direction = "<Turn Around>";
                else                                              direction = "<Left>";

                int dirX = (screenWidth - font.width(direction)) / 2;
                drawTextWithBox(event, font, direction, dirX, 35, argbColor);
                String distText = "Distance: " + ((int) distance);
                int distX = (screenWidth - font.width(distText)) / 2;
                drawTextWithBox(event, font, distText, distX, 50, argbColor);
            }
        }
    }

    // ═════════════════════════════════════════════════════════
    //  原版 MobHUD 的 drawTextWithBox — 完全一致
    // ═════════════════════════════════════════════════════════

    private void drawTextWithBox(Render2DEvent event, Font font, String text, int x, int y, int color) {
        if (!textBoxEnabled.get()) {
            event.graphics.text(font, text, x, y, color, true);
            return;
        }
        int textWidth = font.width(text);
        int boxWidth = textWidth + 8;
        int boxX = x - BOX_PADDING;
        int boxY = y - 1;
        int r = (int) (((color >> 16) & 255) * SHADOW_FACTOR);
        int g = (int) (((color >> 8) & 255) * SHADOW_FACTOR);
        int b = (int) ((color & 255) * SHADOW_FACTOR);
        int boxColor = 0x80000000 | (r << 16) | (g << 8) | b;
        event.graphics.fill(boxX, boxY + 1, boxX + boxWidth, boxY + BOX_HEIGHT - 1, boxColor);
        event.graphics.fill(boxX, boxY, boxX + boxWidth, boxY + 1, BLACK_FRAME);
        event.graphics.fill(boxX, boxY + BOX_HEIGHT - 1, boxX + boxWidth, boxY + BOX_HEIGHT, BLACK_FRAME);
        event.graphics.fill(boxX, boxY, boxX + 1, boxY + BOX_HEIGHT, BLACK_FRAME);
        event.graphics.fill(boxX + boxWidth - 1, boxY, boxX + boxWidth, boxY + BOX_HEIGHT, BLACK_FRAME);
        event.graphics.text(font, text, x, y, color, true);
    }

    // ═════════════════════════════════════════════════════════
    //  工具方法
    // ═════════════════════════════════════════════════════════

    private String capitalize(String str) {
        return (str == null || str.isEmpty()) ? str : str.substring(0, 1).toUpperCase() + str.substring(1);
    }

    private Integer parseNamedColor(String name) {
        return switch (name.toLowerCase()) {
            case "white" -> 0xFFFFFF; case "black" -> 0;
            case "red" -> 0xFF3535;   case "blue" -> 0x5555FF;
            case "green" -> 0x55FF55; case "yellow" -> 0xFFFF55;
            case "aqua", "cyan" -> 0x55FFFF;
            case "pink" -> 0xFF55FF;  case "purple" -> 0xAA00FF;
            case "magenta" -> 0xFF00FF;
            case "orange" -> 0xFF8800;
            case "gray", "grey" -> 0xAAAAAA;
            case "lightgray", "lightgrey" -> 0xCCCCCC;
            case "lime" -> 0xAAFF00;  case "teal" -> 0x008080;
            case "gold" -> 0xFFD700;
            default -> {
                if (name.startsWith("#") && name.length() == 7) {
                    try { yield Integer.parseInt(name.substring(1), 16); }
                    catch (Exception e) { yield null; }
                }
                yield null;
            }
        };
    }

    private int computeTemperatureC(Minecraft mc, LocalPlayer player) {
        Level world = mc.level;
        if (world == null) return 0;
        Biome biome = world.getBiome(player.blockPosition()).value();
        float base = biome.getBaseTemperature();
        int temp = Math.round((base - 0.5f) * 20.0f);
        long t = world.getOverworldClockTime() % 24000;
        boolean isNight = t >= 13000 && t <= 23000;
        if (isNight) temp -= 2;
        if (world.isThundering()) temp -= 2;
        else if (world.isRaining()) temp--;
        if (player.getY() > 120.0) temp -= 3;
        if (player.isUnderWater() && base <= 0.15f) return temp - 3;
        return temp;
    }

    private Entity getLookedAtEntity(Minecraft mc, double range) {
        if (mc.player == null || mc.level == null) return null;
        Vec3 eyePos = mc.player.getEyePosition();
        Vec3 lookVec = mc.player.getViewVector(1.0f);
        Vec3 reachVec = eyePos.add(lookVec.x * range, lookVec.y * range, lookVec.z * range);
        Entity closest = null;
        double closestDist = Double.MAX_VALUE;
        for (Entity e : mc.level.entitiesForRendering()) {
            if (e == mc.player) continue;
            // 原版 isPickable() 的 Fabric 等效检查：可攻击/交互的实体才纳入检测
            if (!e.isAttackable()) continue;
            AABB aabb = e.getBoundingBox().inflate(0.3);
            if (aabb.clip(eyePos, reachVec).isPresent()) {
                double d = e.distanceToSqr(eyePos);
                if (d < closestDist) {
                    closestDist = d;
                    closest = e;
                }
            }
        }
        return closest;
    }
}
