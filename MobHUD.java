package br;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.ClientChatEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod(MobHUD.MODID)
/* loaded from: mobhud-1.0.0-deobf.jar:br/MobHUD.class */
public class MobHUD {
    public static final String MODID = "mobhud";
    private double lastX;
    private double lastZ;
    private static final File CONFIG_FILE = new File("config/smart_hud_config.json");
    private static final int LEFT_MARGIN = 5;
    private static final int MOB_EXTRA_SHIFT = 3;
    private static final int BOX_PADDING = 4;
    private static final int BOX_HEIGHT = 12;
    private static final int BLACK_FRAME = -16777216;
    private static final float SHADOW_FACTOR = 0.5f;
    private static final int BOX_ALPHA = 128;
    private int tickCounter = 0;
    private double blocksPerSecond = 0.0d;
    private boolean smartHudEnabled = false;
    private int hudColor = 16777215;
    private String aiName = "Jarvis";
    private boolean textBoxEnabled = true;
    private int targetX = Integer.MIN_VALUE;
    private int targetZ = Integer.MIN_VALUE;
    private String hudToggleMessage = "";
    private long hudToggleMessageUntil = 0;

    public MobHUD() {
        MinecraftForge.EVENT_BUS.register(this);
        loadConfig();
    }

    private void saveConfig() {
        try {
            CONFIG_FILE.getParentFile().mkdirs();
            JsonObject json = new JsonObject();
            json.addProperty("smartHudEnabled", Boolean.valueOf(this.smartHudEnabled));
            json.addProperty("hudColor", Integer.valueOf(this.hudColor));
            json.addProperty("aiName", this.aiName);
            json.addProperty("textBoxEnabled", Boolean.valueOf(this.textBoxEnabled));
            FileWriter writer = new FileWriter(CONFIG_FILE);
            try {
                new Gson().toJson(json, writer);
                writer.close();
            } finally {
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void loadConfig() {
        if (CONFIG_FILE.exists()) {
            try {
                FileReader reader = new FileReader(CONFIG_FILE);
                try {
                    JsonObject json = (JsonObject) new Gson().fromJson(reader, JsonObject.class);
                    if (json != null) {
                        this.smartHudEnabled = json.has("smartHudEnabled") && json.get("smartHudEnabled").getAsBoolean();
                        this.hudColor = json.has("hudColor") ? json.get("hudColor").getAsInt() : 16777215;
                        this.aiName = json.has("aiName") ? json.get("aiName").getAsString() : "Jarvis";
                        this.textBoxEnabled = json.has("textBoxEnabled") && json.get("textBoxEnabled").getAsBoolean();
                        reader.close();
                        return;
                    }
                    reader.close();
                } finally {
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @SubscribeEvent
    public void onClientChat(ClientChatEvent event) {
        String raw = event.getMessage();
        if (raw == null) {
            return;
        }
        String msg = raw.trim().toLowerCase();
        if (msg.equals(this.aiName.toLowerCase() + " enable smart hud")) {
            this.smartHudEnabled = true;
            this.hudToggleMessage = capitalizeFirst(this.aiName) + ": Smart HUD Enabled Sir";
            this.hudToggleMessageUntil = System.currentTimeMillis() + 3000;
            saveConfig();
            event.setCanceled(true);
            return;
        }
        if (msg.equals(this.aiName.toLowerCase() + " disable smart hud")) {
            this.smartHudEnabled = false;
            this.hudToggleMessage = capitalizeFirst(this.aiName) + ": Smart HUD Disabled Sir";
            this.hudToggleMessageUntil = System.currentTimeMillis() + 3000;
            saveConfig();
            event.setCanceled(true);
            return;
        }
        if (msg.startsWith("ai name ")) {
            String newName = msg.substring("ai name ".length()).trim();
            if (!newName.isEmpty()) {
                this.aiName = newName;
                this.hudToggleMessage = "AI name set to " + capitalizeFirst(this.aiName);
                this.hudToggleMessageUntil = System.currentTimeMillis() + 3000;
                saveConfig();
            } else {
                this.hudToggleMessage = "Invalid AI name";
                this.hudToggleMessageUntil = System.currentTimeMillis() + 3000;
            }
            event.setCanceled(true);
            return;
        }
        if (msg.startsWith("smart hud color ")) {
            String colorName = msg.substring("smart hud color ".length()).trim();
            Integer parsed = parseNamedColor(colorName);
            if (parsed != null) {
                this.hudColor = parsed.intValue();
                this.hudToggleMessage = capitalizeFirst(this.aiName) + ": Smart HUD color set to " + colorName;
                this.hudToggleMessageUntil = System.currentTimeMillis() + 3000;
                saveConfig();
            } else {
                this.hudToggleMessage = capitalizeFirst(this.aiName) + ": Unknown color: " + colorName;
                this.hudToggleMessageUntil = System.currentTimeMillis() + 3000;
            }
            event.setCanceled(true);
            return;
        }
        if (msg.equals(this.aiName.toLowerCase() + " disable text box")) {
            this.textBoxEnabled = false;
            this.hudToggleMessage = capitalizeFirst(this.aiName) + ": Text Box Disabled Sir!";
            this.hudToggleMessageUntil = System.currentTimeMillis() + 3000;
            saveConfig();
            event.setCanceled(true);
            return;
        }
        if (msg.equals(this.aiName.toLowerCase() + " enable text box")) {
            this.textBoxEnabled = true;
            this.hudToggleMessage = capitalizeFirst(this.aiName) + ": Text Box Enabled Sir!";
            this.hudToggleMessageUntil = System.currentTimeMillis() + 3000;
            saveConfig();
            event.setCanceled(true);
            return;
        }
        if (msg.equals(this.aiName.toLowerCase() + " where am i")) {
            LocalPlayer localPlayer = Minecraft.getInstance().player;
            if (localPlayer != null) {
                int x = (int) localPlayer.getX();
                int y = (int) localPlayer.getY();
                int z = (int) localPlayer.getZ();
                Minecraft.getInstance().gui.getChat().addMessage(Component.literal(capitalizeFirst(this.aiName) + ": You are at " + x + ", " + y + ", " + z + " coordinates Sir"));
            }
            event.setCanceled(true);
            return;
        }
        if (msg.startsWith(this.aiName.toLowerCase() + " get me to ")) {
            String coords = msg.substring((this.aiName.toLowerCase() + " get me to ").length()).trim();
            String[] parts = coords.split("\\s+");
            if (parts.length == 2) {
                try {
                    this.targetX = Integer.parseInt(parts[0]);
                    this.targetZ = Integer.parseInt(parts[1]);
                    this.hudToggleMessage = capitalizeFirst(this.aiName) + ": Navigation set to " + this.targetX + ", " + this.targetZ + " Sir";
                    this.hudToggleMessageUntil = System.currentTimeMillis() + 3000;
                    saveConfig();
                } catch (NumberFormatException e) {
                    this.hudToggleMessage = capitalizeFirst(this.aiName) + ": Invalid coordinates Sir";
                    this.hudToggleMessageUntil = System.currentTimeMillis() + 3000;
                }
            } else {
                this.hudToggleMessage = capitalizeFirst(this.aiName) + ": Use format: get me to x z Sir";
                this.hudToggleMessageUntil = System.currentTimeMillis() + 3000;
            }
            event.setCanceled(true);
            return;
        }
        if (msg.equals(this.aiName.toLowerCase() + " cancel trip")) {
            this.targetX = Integer.MIN_VALUE;
            this.targetZ = Integer.MIN_VALUE;
            this.hudToggleMessage = capitalizeFirst(this.aiName) + ": Navigation canceled Sir";
            this.hudToggleMessageUntil = System.currentTimeMillis() + 3000;
            saveConfig();
            event.setCanceled(true);
        }
    }

    private String capitalizeFirst(String str) {
        return (str == null || str.isEmpty()) ? str : str.substring(0, 1).toUpperCase() + str.substring(1);
    }

    /* JADX WARN: Failed to restore switch over string. Please report as a decompilation issue */
    private Integer parseNamedColor(String name) {
        String lowerCase = name.toLowerCase();
        boolean z = -1;
        switch (lowerCase.hashCode()) {
            case -1008851410:
                if (lowerCase.equals("orange")) {
                    z = 11;
                    break;
                }
                break;
            case -976943172:
                if (lowerCase.equals("purple")) {
                    z = 9;
                    break;
                }
                break;
            case -734239628:
                if (lowerCase.equals("yellow")) {
                    z = LEFT_MARGIN;
                    break;
                }
                break;
            case 112785:
                if (lowerCase.equals("red")) {
                    z = 2;
                    break;
                }
                break;
            case 3002044:
                if (lowerCase.equals("aqua")) {
                    z = 6;
                    break;
                }
                break;
            case 3027034:
                if (lowerCase.equals("blue")) {
                    z = MOB_EXTRA_SHIFT;
                    break;
                }
                break;
            case 3068707:
                if (lowerCase.equals("cyan")) {
                    z = 7;
                    break;
                }
                break;
            case 3178592:
                if (lowerCase.equals("gold")) {
                    z = 18;
                    break;
                }
                break;
            case 3181155:
                if (lowerCase.equals("gray")) {
                    z = BOX_HEIGHT;
                    break;
                }
                break;
            case 3181279:
                if (lowerCase.equals("grey")) {
                    z = 13;
                    break;
                }
                break;
            case 3321813:
                if (lowerCase.equals("lime")) {
                    z = 16;
                    break;
                }
                break;
            case 3441014:
                if (lowerCase.equals("pink")) {
                    z = 8;
                    break;
                }
                break;
            case 3555932:
                if (lowerCase.equals("teal")) {
                    z = 17;
                    break;
                }
                break;
            case 93818879:
                if (lowerCase.equals("black")) {
                    z = true;
                    break;
                }
                break;
            case 98619139:
                if (lowerCase.equals("green")) {
                    z = BOX_PADDING;
                    break;
                }
                break;
            case 113101865:
                if (lowerCase.equals("white")) {
                    z = false;
                    break;
                }
                break;
            case 686244985:
                if (lowerCase.equals("lightgray")) {
                    z = 14;
                    break;
                }
                break;
            case 686245109:
                if (lowerCase.equals("lightgrey")) {
                    z = 15;
                    break;
                }
                break;
            case 828922025:
                if (lowerCase.equals("magenta")) {
                    z = 10;
                    break;
                }
                break;
        }
        switch (z) {
            case false:
                return 16777215;
            case true:
                return 0;
            case true:
                return 16733525;
            case MOB_EXTRA_SHIFT /* 3 */:
                return 5592575;
            case BOX_PADDING /* 4 */:
                return 5635925;
            case LEFT_MARGIN /* 5 */:
                return 16777045;
            case true:
            case true:
                return 5636095;
            case true:
                return 16733695;
            case true:
                return 11141375;
            case true:
                return 16711935;
            case true:
                return 16755200;
            case BOX_HEIGHT /* 12 */:
            case true:
                return 11184810;
            case true:
            case true:
                return 13421772;
            case true:
                return 11206400;
            case true:
                return 32896;
            case true:
                return 16766720;
            default:
                if (name.startsWith("#") && name.length() == 7) {
                    try {
                        return Integer.valueOf(Integer.parseInt(name.substring(1), 16));
                    } catch (Exception e) {
                        return null;
                    }
                }
                return null;
        }
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer localPlayer = mc.player;
        if (localPlayer == null || mc.level == null) {
            return;
        }
        this.tickCounter++;
        if (this.tickCounter >= 20) {
            double dx = localPlayer.getX() - this.lastX;
            double dz = localPlayer.getZ() - this.lastZ;
            this.blocksPerSecond = Math.sqrt((dx * dx) + (dz * dz));
            this.lastX = localPlayer.getX();
            this.lastZ = localPlayer.getZ();
            this.tickCounter = 0;
        }
    }

    private void drawTextWithBox(GuiGraphics gui, Font font, String text, int x, int y, int color) {
        if (!this.textBoxEnabled) {
            gui.drawString(font, text, x, y, color);
            return;
        }
        int textWidth = font.width(text);
        int boxWidth = textWidth + 8;
        int boxX = x - BOX_PADDING;
        int boxY = y - 1;
        int r = (int) (((color >> 16) & 255) * SHADOW_FACTOR);
        int g = (int) (((color >> 8) & 255) * SHADOW_FACTOR);
        int b = (int) ((color & 255) * SHADOW_FACTOR);
        int boxColor = Integer.MIN_VALUE | (r << 16) | (g << 8) | b;
        gui.fill(boxX, boxY + 1, boxX + boxWidth, (boxY + BOX_HEIGHT) - 1, boxColor);
        gui.fill(boxX, boxY, boxX + boxWidth, boxY + 1, BLACK_FRAME);
        gui.fill(boxX, (boxY + BOX_HEIGHT) - 1, boxX + boxWidth, boxY + BOX_HEIGHT, BLACK_FRAME);
        gui.fill(boxX, boxY, boxX + 1, boxY + BOX_HEIGHT, BLACK_FRAME);
        gui.fill((boxX + boxWidth) - 1, boxY, boxX + boxWidth, boxY + BOX_HEIGHT, BLACK_FRAME);
        gui.drawString(font, text, x, y, color);
    }

    @SubscribeEvent
    public void onRenderOverlay(RenderGuiOverlayEvent.Post event) {
        String weatherText;
        String direction;
        Minecraft mc = Minecraft.getInstance();
        LivingEntity livingEntity = mc.player;
        if (livingEntity == null) {
            return;
        }
        GuiGraphics gui = event.getGuiGraphics();
        Font font = mc.font;
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();
        if (System.currentTimeMillis() < this.hudToggleMessageUntil && this.hudToggleMessage != null && !this.hudToggleMessage.isEmpty()) {
            int msgX = (screenWidth - font.width(this.hudToggleMessage)) / 2;
            int msgY = screenHeight - 62;
            drawTextWithBox(gui, font, this.hudToggleMessage, msgX, msgY, this.hudColor);
        }
        if (this.smartHudEnabled) {
            int weatherShift = font.width("We");
            if (mc.level.isThundering()) {
                weatherText = "Weather: Thunder";
            } else {
                weatherText = mc.level.isRaining() ? "Weather: Rain" : "Weather: Clear";
            }
            int weatherX = LEFT_MARGIN + weatherShift;
            int weatherY = screenHeight - 15;
            drawTextWithBox(gui, font, weatherText, weatherX, weatherY, this.hudColor);
            int tempC = computeTemperatureC(mc, livingEntity);
            String tempStr = tempC + " °C";
            int tempX = weatherX + font.width(weatherText) + 6;
            drawTextWithBox(gui, font, tempStr, tempX, weatherY, this.hudColor);
            EnderMan lookedAtEntity = getLookedAtEntity(mc, 40.0d);
            if (lookedAtEntity != null) {
                double dist = livingEntity.distanceTo(lookedAtEntity);
                String mobName = lookedAtEntity.getName().getString();
                String mobDist = "Distance: " + ((int) dist) + " Blocks Away";
                int disShift = font.width("Dis");
                int mobX = LEFT_MARGIN + weatherShift + MOB_EXTRA_SHIFT + disShift;
                int baseY = screenHeight / 2;
                drawTextWithBox(gui, font, mobName, mobX, baseY, this.hudColor);
                drawTextWithBox(gui, font, mobDist, mobX, baseY + 10, this.hudColor);
                boolean showDanger = false;
                boolean showPossibleDanger = false;
                if (lookedAtEntity instanceof Mob) {
                    EnderMan enderMan = (Mob) lookedAtEntity;
                    boolean isHostile = enderMan.getType().getCategory() == MobCategory.MONSTER && !(enderMan instanceof NeutralMob);
                    boolean isNeutral = enderMan instanceof NeutralMob;
                    boolean isAngry = enderMan.isAggressive() || enderMan.getTarget() == livingEntity;
                    if (enderMan instanceof EnderMan) {
                        EnderMan enderman = enderMan;
                        isAngry |= enderman.isAngryAt(livingEntity) || enderman.isCreepy();
                    }
                    System.out.println("Mob: " + mobName + ", IsHostile: " + isHostile + ", IsNeutral: " + isNeutral + ", IsAngry: " + isAngry + ", IsAggressive: " + enderMan.isAggressive() + ", Target: " + (enderMan.getTarget() != null ? enderMan.getTarget().getName().getString() : "none") + ", AngerTime: " + String.valueOf(isNeutral ? Integer.valueOf(((NeutralMob) enderMan).getRemainingPersistentAngerTime()) : "N/A"));
                    if (isAngry || enderMan.getLastHurtByMob() == livingEntity || enderMan.getLastHurtMob() == livingEntity) {
                        showDanger = true;
                    } else if (isHostile) {
                        showDanger = true;
                    } else if (isNeutral) {
                        showPossibleDanger = true;
                    }
                }
                if (showDanger) {
                    int pulse = (int) ((System.currentTimeMillis() / 500) % 2);
                    int dangerColor = pulse == 0 ? 16729156 : 16711680;
                    drawTextWithBox(gui, font, "Danger!", mobX, baseY + 20, dangerColor);
                } else if (showPossibleDanger) {
                    int pulse2 = (int) ((System.currentTimeMillis() / 600) % 2);
                    int possibleColor = pulse2 == 0 ? 16755251 : 16737843;
                    drawTextWithBox(gui, font, "Possible Danger!", mobX, baseY + 20, possibleColor);
                }
            }
            String speed = "Speed: " + String.format("%.2f", Double.valueOf(this.blocksPerSecond)) + " b/s";
            int speedX = (screenWidth - font.width(speed)) / 2;
            drawTextWithBox(gui, font, speed, speedX, LEFT_MARGIN, this.hudColor);
            BlockPos pos = livingEntity.blockPosition();
            int groundY = mc.level.getHeight(Heightmap.Types.MOTION_BLOCKING, pos.getX(), pos.getZ());
            int diff = (int) (livingEntity.getY() - groundY);
            if (livingEntity.isUnderWater()) {
                int surfaceY = mc.level.getHeight(Heightmap.Types.WORLD_SURFACE, pos.getX(), pos.getZ());
                int depth = (int) (livingEntity.getEyeY() - surfaceY);
                String depthStr = "Depth: " + (-Math.abs(depth));
                int depthX = (screenWidth - font.width(depthStr)) / 2;
                drawTextWithBox(gui, font, depthStr, depthX, 20, this.hudColor);
            } else if (diff > LEFT_MARGIN) {
                String altitude = "Altitude: " + diff;
                int altX = (screenWidth - font.width(altitude)) / 2;
                drawTextWithBox(gui, font, altitude, altX, 20, this.hudColor);
            }
            if (livingEntity.getHealth() < 10.0f) {
                int pulse3 = (int) ((System.currentTimeMillis() / 500) % 2);
                int lowPulse = pulse3 == 0 ? 16768341 : 16711680;
                int vx = (screenWidth - font.width("!! Vitals Low !!")) / 2;
                int vy = screenHeight - 70;
                drawTextWithBox(gui, font, "!! Vitals Low !!", vx, vy, lowPulse);
            }
            if (livingEntity.getAirSupply() < 120) {
                int pulse4 = (int) ((System.currentTimeMillis() / 500) % 2);
                int oxygenColor = pulse4 == 0 ? 16755251 : 16724787;
                int oxX = (screenWidth - font.width("!! Oxygen !!")) / 2;
                int oxY = screenHeight - 55;
                drawTextWithBox(gui, font, "!! Oxygen !!", oxX, oxY, oxygenColor);
            }
            if (this.targetX != Integer.MIN_VALUE && this.targetZ != Integer.MIN_VALUE) {
                double playerX = livingEntity.getX();
                double playerZ = livingEntity.getZ();
                double dx = this.targetX - playerX;
                double dz = this.targetZ - playerZ;
                double distance = Math.sqrt((dx * dx) + (dz * dz));
                if (distance <= 1.0d) {
                    this.targetX = Integer.MIN_VALUE;
                    this.targetZ = Integer.MIN_VALUE;
                    this.hudToggleMessage = capitalizeFirst(this.aiName) + ": Target reached Sir!";
                    this.hudToggleMessageUntil = System.currentTimeMillis() + 3000;
                    return;
                }
                Vec3 lookVec = livingEntity.getLookAngle();
                double lookAngle = Math.toDegrees(Math.atan2(lookVec.z, lookVec.x));
                double targetAngle = Math.toDegrees(Math.atan2(dz, dx));
                double angleDiff = ((targetAngle - lookAngle) + 360.0d) % 360.0d;
                if (angleDiff < 45.0d || angleDiff >= 315.0d) {
                    direction = "<Forward>";
                } else if (angleDiff < 135.0d || angleDiff >= 225.0d) {
                    direction = (angleDiff < 225.0d || angleDiff >= 315.0d) ? "<Right>" : "<Left>";
                } else {
                    direction = "<Turn Around>";
                }
                int dirX = (screenWidth - font.width(direction)) / 2;
                drawTextWithBox(gui, font, direction, dirX, 35, this.hudColor);
                String distText = "Distance: " + ((int) distance);
                int distX = (screenWidth - font.width(distText)) / 2;
                drawTextWithBox(gui, font, distText, distX, 50, this.hudColor);
            }
        }
    }

    private int computeTemperatureC(Minecraft mc, Player player) {
        Biome biome = (Biome) player.level().getBiome(player.blockPosition()).value();
        float base = biome.getBaseTemperature();
        int temp = Math.round((base - SHADOW_FACTOR) * 20.0f);
        long t = mc.level.getDayTime() % 24000;
        boolean isNight = t >= 13000 && t <= 23000;
        if (isNight) {
            temp -= 2;
        }
        if (mc.level.isThundering()) {
            temp -= 2;
        } else if (mc.level.isRaining()) {
            temp--;
        }
        if (player.getY() > 120.0d) {
            temp -= 3;
        }
        if (player.isUnderWater() && base <= 0.15f) {
            return temp - MOB_EXTRA_SHIFT;
        }
        return temp;
    }

    private Entity getLookedAtEntity(Minecraft mc, double range) {
        if (mc.player == null || mc.level == null) {
            return null;
        }
        Vec3 eyePos = mc.player.getEyePosition(1.0f);
        Vec3 lookVec = mc.player.getLookAngle();
        Vec3 reachVec = eyePos.add(lookVec.x * range, lookVec.y * range, lookVec.z * range);
        AABB box = mc.player.getBoundingBox().expandTowards(lookVec.scale(range)).inflate(1.0d);
        for (Entity e : mc.level.getEntities(mc.player, box)) {
            if (e.isPickable()) {
                AABB aabb = e.getBoundingBox().inflate(0.3d);
                if (aabb.clip(eyePos, reachVec).isPresent()) {
                    return e;
                }
            }
        }
        return null;
    }
}
