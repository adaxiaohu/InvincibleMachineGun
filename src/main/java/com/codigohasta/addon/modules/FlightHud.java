package com.codigohasta.addon.modules;

import com.codigohasta.addon.utils.sakura.render.AbstractPanelHud;
import com.codigohasta.addon.utils.sakura.render.CatProgress;
import com.codigohasta.addon.utils.sakura.render.SakuraTextRenderer;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.MathHelper;

import java.util.Locale;

public class FlightHud extends AbstractPanelHud {

    private final SettingGroup sgFlight = settings.createGroup("飞行");
    private static final float PANEL_W = 120f;
    private static final float PANEL_H = 55f;
    private static final float ARROW_CX = 27.5f;
    private static final float ARROW_CY = 27.5f;
    private static final float ARROW_SIZE = 15f;
    private static final float RING_RADIUS = 17f;
    private static final float RING_FONT_SIZE = 6f;
    private static final Object[][] CARDINAL_RING = {
        {"北", 0f},
        {"东", 90f},
        {"南", 180f},
        {"西", 270f},
    };
    private static final float HEADING_FONT_SIZE = 6.5f;
    private static final float HEADING_Y = 37f;
    private static final String[] CARDINALS = {
        "北", "东北", "东", "东南", "南", "西南", "西", "西北"
    };
    private static final float TEXT_X = 67f;
    private static final float LINE_STEP = 9f;
    private static final float DIVIDER_X = 60f;
    private static final float VALUE_FONT_SIZE = 7.5f;
    private final Setting<meteordevelopment.meteorclient.utils.render.color.SettingColor> arrowColor =
        sgFlight.add(new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
            .name("箭头颜色")
            .description("指南针箭头颜色。")
            .defaultValue(new meteordevelopment.meteorclient.utils.render.color.SettingColor(64, 224, 208, 255))
            .build());
    private final Setting<Boolean> showDivider = sgFlight.add(new BoolSetting.Builder()
        .name("分割线").description("左右两半之间的竖线。").defaultValue(true).build());
    private final Setting<Boolean> showHeading = sgFlight.add(new BoolSetting.Builder()
        .name("实时航向")
        .description("在指南针下方显示当前朝向，例如「东南 135°」。")
        .defaultValue(true).build());
    private final Setting<Integer> decimals = sgFlight.add(new meteordevelopment.meteorclient.settings.IntSetting.Builder()
        .name("小数位数").description("速度与航程保留几位小数。")
        .defaultValue(1).min(0).max(3).sliderRange(0, 3).build());
    private final Setting<Double> animMs = sgFlight.add(new DoubleSetting.Builder()
        .name("动画时长").description("出入场动画的毫秒数。")
        .defaultValue(300.0).min(80.0).max(1200.0).sliderRange(80.0, 1200.0).build());

    private final CatProgress anim = new CatProgress(300f);
    private int cFireworks;
    private int cDurability;
    private int cElytras;
    private double cResKm;
    private double cXzSpeed;
    private double cYSpeed;

    public FlightHud() {
        super("flight-hud", "当你开飞机时候在你屏幕上画个表，显示数据鞘翅飞的时候。。",
            700, 260, 1.0);
    }

    @Override
    protected void onTickUpdate() {
        if (mc.player == null) return;
        if (!mc.player.isGliding()) return;  
        cFireworks = countItem(Items.FIREWORK_ROCKET);
        cElytras = countItem(Items.ELYTRA);
        ItemStack chest = mc.player.getEquippedStack(net.minecraft.entity.EquipmentSlot.CHEST);
        cDurability = (chest.isOf(Items.ELYTRA) && chest.isDamageable())
            ? chest.getMaxDamage() - chest.getDamage() : 0;

        var v = mc.player.getVelocity();
        cXzSpeed = Math.sqrt(v.x * v.x + v.z * v.z) * 20.0;
        cYSpeed = v.y * 20.0;

        double dy = mc.player.getY() - 64.0;
        double secs = (cYSpeed < -0.05) ? (dy / -cYSpeed) : 0.0;
        cResKm = Math.max(0.0, cXzSpeed * secs / 1000.0);
    }

    
    private int countItem(net.minecraft.item.Item item) {
        if (mc.player == null) return 0;
        int total = 0;
        var inv = mc.player.getInventory();
        for (int i = 0; i < inv.size(); i++) {
            ItemStack st = inv.getStack(i);
            if (st.isOf(item)) total += st.getCount();
        }
        ItemStack off = mc.player.getOffHandStack();
        if (off.isOf(item)) total += off.getCount();
        return total;
    }

    
    public float animProgress() {
        return anim.value();
    }

    @Override
    protected void render(float x, float y, float scale) {
        boolean gliding = mc.player != null && mc.player.isGliding();
        anim.animateTo(gliding ? 1f : 0f);
        anim.update(frameDelta());

        float a = anim.value();
        if (a <= 0.01f) return;
        float ease;
        float alpha;
        if (a < 1f) {
            ease = CatProgress.outElastic(a, 1.0f);
            alpha = Math.min(1f, a * 3f);
        } else {
            ease = 1f;
            alpha = 1f;
        }
        ease = Math.max(0f, ease);

    
        float w = PANEL_W * scale * Math.min(1.15f, ease);
        float h = PANEL_H * scale * Math.min(1.15f, ease);
        float cx = x + PANEL_W * scale / 2f;
        float cy = y + PANEL_H * scale / 2f;
        float px = cx - w / 2f;
        float py = cy - h / 2f;

        queuePanel(px, py, w, h, alpha);

        float k = Math.min(1f, ease);


        if (showDivider.get()) {
            queueShape(px + DIVIDER_X * scale * k, py + 6f * scale * k,
                Math.max(0.05f, 0.5f * scale * k), (PANEL_H - 12f) * scale * k,
                0f, new meteordevelopment.meteorclient.utils.render.color.Color(255, 255, 255, 90), alpha);
        }

        drawCompass(px, py, scale, k, alpha);
        float hdg = headingDeg();
        for (Object[] d : CARDINAL_RING) {
            String name = (String) d[0];
            float worldDeg = (float) d[1];

            double ang = Math.toRadians(worldDeg - hdg);
            float cxp = px + (ARROW_CX + RING_RADIUS * (float) Math.sin(ang)) * scale * k;
            float cyp = py + (ARROW_CY - RING_RADIUS * (float) Math.cos(ang)) * scale * k;
            float tw = (float) SakuraTextRenderer.getWidth(name, RING_FONT_SIZE);
            queueText(name,
                cxp - tw * scale * k / 2f,
                cyp - RING_FONT_SIZE * scale * k / 2f,
                alpha, RING_FONT_SIZE);
        }

        if (showHeading.get()) {
            String heading = headingLabel();
            float headingW = (float) SakuraTextRenderer.getWidth(heading, HEADING_FONT_SIZE);
            float headingX = px + (ARROW_CX - headingW / 2f) * scale * k;
            float headingY = py + HEADING_Y * scale * k;
            queueText(heading, headingX, headingY, alpha, HEADING_FONT_SIZE);
        }

        int dec = decimals.get();
        String f1 = String.format(Locale.ROOT, "Fireworks: %d", cFireworks);
        String f2 = String.format(Locale.ROOT, "Durability: %d", cDurability);
        String f3 = String.format(Locale.ROOT, "Res (km): %." + dec + "f", cResKm);
        String f4 = String.format(Locale.ROOT, "Elytras: %d", cElytras);
        String f5 = String.format(Locale.ROOT, "XZ Speed: %." + dec + "f", cXzSpeed);
        String f6 = String.format(Locale.ROOT, "Y Speed: %." + dec + "f", cYSpeed);

        String[] lines = {f1, f2, f3, f4, f5, f6};
        for (int i = 0; i < lines.length; i++) {
            queueText(lines[i],
                px + TEXT_X * scale * k,
                py + (i * LINE_STEP) * scale * k,
                alpha, VALUE_FONT_SIZE);
        }
    }

    
    private float headingDeg() {
        float yaw = mc.player != null ? mc.player.getYaw() : 0f;
        double rad = Math.toRadians(yaw);
        double east = -Math.sin(rad);    
        double north = -Math.cos(rad);   
        double deg = Math.toDegrees(Math.atan2(east, north));
        return (float) ((deg % 360.0 + 360.0) % 360.0);
    }

    
    private String headingLabel() {
        float deg = headingDeg();
        int idx = (int) Math.floor((deg + 22.5) / 45.0) % 8;
        int rounded = Math.round(deg) % 360;
        return CARDINALS[idx] + " " + rounded + "°";
    }

    
    private void drawCompass(float px, float py, float scale, float k, float alpha) {
        float acx = px + ARROW_CX * scale * k;
        float acy = py + ARROW_CY * scale * k;
        float arrowPx = ARROW_SIZE * scale * k;

        
        
        
        
        
        
        
        
        
        
        
        float arrowAngle = 57.3f;

        
        
        
        var ac = arrowColor.get();
        var tint = new meteordevelopment.meteorclient.utils.render.color.Color(
            ac.r, ac.g, ac.b, ac.a);
        
        
        
        queueIcon("nav", acx - arrowPx / 2f, acy - arrowPx / 2f, arrowPx, alpha,
            arrowAngle, tint, ARROW_SIZE * scale);
    }
}
