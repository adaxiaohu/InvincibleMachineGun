package com.codigohasta.addon.hud;

import com.codigohasta.addon.AddonTemplate;
import com.codigohasta.addon.mixin.AbstractClientPlayerEntityAccessor;
import com.codigohasta.addon.utils.render.GuiRenderUtil;
import meteordevelopment.meteorclient.renderer.Renderer2D;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.combat.KillAura;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.MathHelper;

import java.util.List;

public class TargetHud extends HudElement {

    private static final MinecraftClient mc = MinecraftClient.getInstance();
    public static final HudElementInfo<TargetHud> INFO = new HudElementInfo<>(
        AddonTemplate.HUD_GROUP, "img-target-hud", "高级目标面板(多风格)", TargetHud::new
    );

    // ─── 枚举 ─────────────────────────────────────────────
    public enum VisualMode {
        ThunderHack, NurikZapen, CelkaPasta, Catppuccin, Windows95
    }
    public enum HpDisplay { Health, Percentage }

    // ─── 设置 ─────────────────────────────────────────────
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgElements = settings.createGroup("显示元素");
    private final SettingGroup sgColors = settings.createGroup("颜色");

    private final Setting<VisualMode> mode = sgGeneral.add(new EnumSetting.Builder<VisualMode>()
        .name("视觉模式").defaultValue(VisualMode.ThunderHack).build());
    private final Setting<Double> scale = sgGeneral.add(new DoubleSetting.Builder()
        .name("缩放").defaultValue(1.0).min(0.5).sliderMax(3.0).build());
    private final Setting<HpDisplay> hpDisplay = sgGeneral.add(new EnumSetting.Builder<HpDisplay>()
        .name("血量显示").defaultValue(HpDisplay.Health).build());
    private final Setting<Double> bgAlpha = sgGeneral.add(new DoubleSetting.Builder()
        .name("背景透明度").defaultValue(0.85).min(0).max(1).sliderRange(0, 1).build());

    private final Setting<Boolean> showSkin = sgElements.add(new BoolSetting.Builder()
        .name("头像").defaultValue(true).build());
    private final Setting<Boolean> showName = sgElements.add(new BoolSetting.Builder()
        .name("名字").defaultValue(true).build());
    private final Setting<Boolean> showHealth = sgElements.add(new BoolSetting.Builder()
        .name("血量条").defaultValue(true).build());
    private final Setting<Boolean> showHpText = sgElements.add(new BoolSetting.Builder()
        .name("血量数字").defaultValue(true).build());
    private final Setting<Boolean> showArmor = sgElements.add(new BoolSetting.Builder()
        .name("装备栏").defaultValue(true).build());
    private final Setting<Boolean> showPotions = sgElements.add(new BoolSetting.Builder()
        .name("药水效果").defaultValue(true).build());
    private final Setting<Boolean> showParticles = sgElements.add(new BoolSetting.Builder()
        .name("粒子特效").defaultValue(false).build());
    private final Setting<Boolean> showShadow = sgElements.add(new BoolSetting.Builder()
        .name("阴影").defaultValue(true).build());

    private final Setting<SettingColor> accentColor = sgColors.add(new ColorSetting.Builder()
        .name("强调色").defaultValue(new SettingColor(140, 100, 255)).build());

    // ─── 状态 ─────────────────────────────────────────────
    private LivingEntity target;
    private double animHealth;

    public TargetHud() {
        super(INFO);
    }

    @Override
    public void render(HudRenderer renderer) {
        updateTarget();
        if (target == null) { animHealth = 0; return; }

        float s = scale.get().floatValue();
        double bw = 160 * s, bh = (mode.get() == VisualMode.Windows95) ? 58 * s : 50 * s;
        box.setSize(bw, bh);
        double px = this.x, py = this.y;

        switch (mode.get()) {
            case ThunderHack -> renderTH(renderer, px, py, bw, bh, s);
            case NurikZapen  -> renderNZ(renderer, px, py, bw, bh, s);
            case CelkaPasta  -> renderCP(renderer, px, py, bw, bh, s);
            case Catppuccin  -> renderCT(renderer, px, py, bw, bh, s);
            case Windows95   -> renderWin95(renderer, px, py, bw, bh, s);
        }
    }

    // ═══════════════════════════════════════════════════════
    //  ThunderHack 风格
    // ═══════════════════════════════════════════════════════
    private void renderTH(HudRenderer r, double px, double py, double bw, double bh, float s) {
        double pad = 6 * s, rad = 6 * s, avSize = 35 * s;
        Color bg = new Color(0, 0, 0, (int)(140 * bgAlpha.get()));

        if (showShadow.get()) GuiRenderUtil.drawShadow(r, px, py, bw, bh, rad, new Color(0, 0, 0, 80));
        GuiRenderUtil.drawRound(r, px, py, bw, bh, rad, bg);

        double textX = px + pad + avSize + pad;
        double infoW = bw - avSize - pad * 3;

        if (showSkin.get()) drawSkin(r, px + pad, py + pad, avSize, avSize, rad, bg);

        if (showName.get()) {
            r.text(truncName(target.getName().getString()), textX, py + pad,
                Color.WHITE, true);
        }

        double barY = py + bh - pad - 8 * s;
        if (showHealth.get()) {
            GuiRenderUtil.drawRound(r, textX, barY, infoW, 8 * s, 3 * s,
                new Color(255, 255, 255, 25));
            animHealth = MathHelper.lerp(0.15, animHealth, target.getHealth());
            double pct = MathHelper.clamp(animHealth / target.getMaxHealth(), 0, 1);
            if (pct > 0.01) {
                Color hpC = pct > 0.5 ? new Color(34, 197, 94) :
                           (pct > 0.2 ? new Color(255, 193, 7) : new Color(239, 68, 68));
                GuiRenderUtil.drawRound(r, textX, barY, infoW * pct, 8 * s, 3 * s, hpC);
            }
        }

        if (showHpText.get()) {
            String hp = hpStr();
            r.text(hp, textX + infoW - r.textWidth(hp), barY - 10 * s, Color.WHITE, true);
        }

        if (showArmor.get() && target instanceof PlayerEntity pe)
            drawArmor(r, textX, barY - 16 * s, s, pe);

        if (showPotions.get() && target instanceof PlayerEntity pe)
            drawPotionText(r, textX, py + pad + 10 * s, pe);
    }

    // ═══════════════════════════════════════════════════════
    //  NurikZapen 风格
    // ═══════════════════════════════════════════════════════
    private void renderNZ(HudRenderer r, double px, double py, double bw, double bh, float s) {
        double rad = 9 * s, avSize = 38 * s;
        Color bg1 = new Color(30, 41, 59, (int)(255 * bgAlpha.get()));
        Color bg2 = new Color(15, 23, 42, (int)(255 * bgAlpha.get()));
        Color ac = accentColor.get();

        if (showShadow.get()) GuiRenderUtil.drawShadow(r, px, py, bw, bh, rad, new Color(99, 102, 241, 40));
        GuiRenderUtil.drawRoundGradient(r, px, py, bw, bh, rad, bg1, bg1, bg2, bg2);

        double avX = px + 4 * s, avY = py + (bh - avSize) / 2;
        if (showSkin.get()) drawSkin(r, avX, avY, avSize, avSize, 6 * s, bg1);

        double textX = avX + avSize + 4 * s;
        double infoW = bw - avSize - 12 * s;

        if (showName.get())
            r.text(truncName(target.getName().getString()), textX, py + 5 * s,
                new Color(205, 214, 244), true);

        if (showArmor.get() && target instanceof PlayerEntity pe)
            drawArmorSmall(r, textX, py + 18 * s, s, pe);

        double barY = py + bh - 12 * s;
        if (showHealth.get()) {
            GuiRenderUtil.drawRound(r, textX, barY, infoW, 6 * s, 3 * s,
                new Color(0, 0, 0, 75));
            animHealth = MathHelper.lerp(0.15, animHealth, target.getHealth());
            double pct = MathHelper.clamp(animHealth / target.getMaxHealth(), 0, 1);
            if (pct > 0.01) GuiRenderUtil.drawRound(r, textX, barY, infoW * pct, 6 * s, 3 * s, ac);
        }

        if (showHpText.get()) {
            String hp = hpStr();
            r.text(hp, px + bw - 4 * s - r.textWidth(hp), py + 5 * s,
                new Color(166, 173, 200), true);
        }
    }

    // ═══════════════════════════════════════════════════════
    //  CelkaPasta 风格
    // ═══════════════════════════════════════════════════════
    private void renderCP(HudRenderer r, double px, double py, double bw, double bh, float s) {
        double rad = 0, avSize = 38 * s;
        Color bg = new Color(0, 0, 0, (int)(100 * bgAlpha.get()));

        if (showShadow.get()) GuiRenderUtil.drawShadow(r, px, py, bw, bh, rad, new Color(0, 0, 0, 50));
        GuiRenderUtil.drawRound(r, px, py, bw, bh, rad, bg);

        if (showSkin.get()) drawSkin(r, px + 4 * s, py + (bh - avSize) / 2, avSize, avSize, 0, bg);

        double textX = px + 4 * s + avSize + 4 * s;
        double infoW = bw - avSize - 12 * s;

        if (showName.get())
            r.text(truncName(target.getName().getString()), textX, py + 5 * s,
                new Color(251, 191, 36), true);

        if (showHpText.get()) {
            float hp = target.getHealth();
            String hpStr = String.format("%.1f❤", hp);
            r.text(hpStr, px + bw - 4 * s - r.textWidth(hpStr), py + 5 * s,
                new Color(251, 191, 36), true);
        }

        double barY = py + 18 * s;
        if (showHealth.get()) {
            animHealth = MathHelper.lerp(0.15, animHealth, target.getHealth());
            double pct = MathHelper.clamp(animHealth / target.getMaxHealth(), 0, 1);
            GuiRenderUtil.drawRound(r, textX, barY, infoW, 7 * s, 0, new Color(0, 0, 0, 130));
            if (pct > 0.01) {
                Color hpC = pct > 0.5 ? new Color(34, 197, 94) :
                           (pct > 0.2 ? new Color(251, 191, 36) : new Color(239, 68, 68));
                GuiRenderUtil.drawRound(r, textX, barY, infoW * pct, 7 * s, 0, hpC);
            }
        }

        if (showArmor.get() && target instanceof PlayerEntity pe)
            drawArmor(r, textX, py + 28 * s, s * 0.8f, pe);
    }

    // ═══════════════════════════════════════════════════════
    //  Catppuccin 风格
    // ═══════════════════════════════════════════════════════
    private void renderCT(HudRenderer r, double px, double py, double bw, double bh, float s) {
        double rad = 12 * s, avSize = 38 * s;
        Color crust = new Color(17, 17, 27, (int)(255 * bgAlpha.get()));
        Color base  = new Color(30, 30, 46, (int)(255 * bgAlpha.get()));
        Color text  = new Color(205, 214, 244);
        Color sub   = new Color(166, 173, 200);
        Color green = new Color(166, 227, 161);

        if (showShadow.get()) GuiRenderUtil.drawShadow(r, px, py, bw, bh, rad, new Color(0, 0, 0, 80));
        GuiRenderUtil.drawRoundGradient(r, px, py, bw, bh, rad, crust, base, base, crust);

        double avX = px + 5 * s, avY = py + (bh - avSize) / 2;
        if (showSkin.get()) drawSkin(r, avX, avY, avSize, avSize, 8 * s, crust);

        double textX = avX + avSize + 5 * s;
        double infoW = bw - avSize - 15 * s;

        if (showName.get())
            r.text(truncName(target.getName().getString()), textX, py + 6 * s, text, true);

        if (showArmor.get() && target instanceof PlayerEntity pe)
            drawArmorSmall(r, textX, py + 18 * s, s, pe);

        double barY = py + bh - 14 * s;
        if (showHealth.get()) {
            GuiRenderUtil.drawRound(r, textX, barY, infoW, 6 * s, 3 * s,
                new Color(69, 71, 90, 150));
            animHealth = MathHelper.lerp(0.15, animHealth, target.getHealth());
            double pct = MathHelper.clamp(animHealth / target.getMaxHealth(), 0, 1);
            if (pct > 0.01) GuiRenderUtil.drawRound(r, textX, barY, infoW * pct, 6 * s, 3 * s, green);
        }

        if (showHpText.get()) {
            String hp = String.format("%.1f / %.0f", animHealth, target.getMaxHealth());
            r.text(hp, textX, barY - 10 * s, sub, true);
        }
    }

    // ═══════════════════════════════════════════════════════
    //  Windows95 风格
    // ═══════════════════════════════════════════════════════
    private void renderWin95(HudRenderer r, double px, double py, double bw, double bh, float s) {
        int b = Math.max(1, (int)(2 * s)); // 3D border width

        // ── Win95 调色板 ──
        Color gray  = new Color(192, 192, 192); // C0C0C0
        Color dk    = new Color(128, 128, 128); // 808080
        Color wh    = new Color(255, 255, 255);
        Color navy  = new Color(0, 0, 128);
        Color black = new Color(0, 0, 0);

        // ── 1) 背景 ──
        r.quad(px, py, bw, bh, gray);

        // ── 2) 3D raised 外边框（上/左 白，下/右 深灰） ──
        r.quad(px, py, bw, b, wh);                     // 上
        r.quad(px, py, b, bh, wh);                     // 左
        r.quad(px, py + bh - b, bw, b, dk);            // 下
        r.quad(px + bw - b, py, b, bh, dk);            // 右

        // ── 3) 标题栏（深蓝 + 白色文字） ──
        double titleH = 14 * s;
        double barY = py + b;
        r.quad(px + b, barY, bw - 2 * b, titleH, navy);

        String name = "";
        if (showName.get() && target != null) {
            name = truncName(target.getName().getString());
            r.text(name, px + b + 4 * s, barY + 2 * s + 6 * s - 11.5, Color.WHITE, false);
        }

        // ── 4) 主体区域 ──
        double bodyY = barY + titleH;
        double bodyH = bh - 2.0 * b - titleH;
        double bodyX = px + b;

        // 皮肤头像（无圆角）— 用 post 确保在 COLOR 背景之上渲染
        double pad = 4 * s;
        double avSize = 28 * s;
        double skX = bodyX + pad;
        double skY = bodyY + (bodyH - avSize) / 2;
        if (showSkin.get()) {
            double fsX = skX, fsY = skY, fs = avSize;
            r.post(() -> drawSkin(r, fsX, fsY, fs, fs, 0, gray));
        }

        // 右侧信息
        double textX = skX + avSize + pad;
        double infoW = bw - 2 * b - pad * 2 - avSize - pad;

        // ── 5) 血量条（sunken groove 效果） ──
        if (showHealth.get()) {
            double groX = textX;
            double groY = bodyY + 3 * s;
            double groW = infoW;
            double groH = 10 * s;

            // sunken 边框：上/左 深灰，下/右 白
            r.quad(groX, groY, groW, b / 2f, dk);
            r.quad(groX, groY, b / 2f, groH, dk);
            r.quad(groX, groY + groH - b / 2f, groW, b / 2f, wh);
            r.quad(groX + groW - b / 2f, groY, b / 2f, groH, wh);

            double inX = groX + b / 2f;
            double inY = groY + b / 2f;
            double inW = groW - b;
            double inH = groH - b;

            // 血量条背景
            r.quad(inX, inY, inW, inH, wh);

            // 血量填充
            animHealth = MathHelper.lerp(0.15, animHealth, target.getHealth());
            double pct = MathHelper.clamp(animHealth / target.getMaxHealth(), 0, 1);
            if (pct > 0.01) {
                Color hpC = pct > 0.5 ? new Color(0, 160, 0) :
                           (pct > 0.2 ? new Color(160, 160, 0) : new Color(200, 0, 0));
                r.quad(inX, inY, inW * pct, inH, hpC);
            }
        }

        // ── 6) 血量数字 ──
        if (showHpText.get()) {
            String hp = hpStr();
            r.text(hp, textX, bodyY + 15 * s, black, false);
        }

        // ── 7) 装备栏 ──
        if (showArmor.get() && target instanceof PlayerEntity pe)
            drawArmorSmall(r, textX+ 30 * s, bodyY + 24 * s, s, pe);

        // ── 8) 药水效果 ──
        if (showPotions.get() && target instanceof PlayerEntity pe)
            drawPotionText(r, textX, bodyY + 2 * s, pe);
    }

    // ─── 辅助方法 ─────────────────────────────────────────

    private void drawSkin(HudRenderer r, double x, double y, double size, double size2, double rad, Color bgColor) {
        if (target instanceof AbstractClientPlayerEntity ace) {
            Identifier skinID = getSkinTexture(ace);
            if (skinID != null) {
                // Head UV in 64x64 skin texture: pixels (8,8) → (16,16)
                double u1 = 8.0  / 64.0, v1 = 8.0  / 64.0;
                double u2 = 16.0 / 64.0, v2 = 16.0 / 64.0;
                // Hat overlay: pixels (40,8) → (48,16) — same V as face
                double hu1 = 40.0 / 64.0;
                double hu2 = 48.0 / 64.0;

                var tex = mc.getTextureManager().getTexture(skinID);
                Renderer2D.TEXTURE.begin();
                // Face layer
                Renderer2D.TEXTURE.texQuad(x, y, size, size2, 0, u1, v1, u2, v2, Color.WHITE);
                // Hat/overlay layer
                Renderer2D.TEXTURE.texQuad(x, y, size, size2, 0, hu1, v1, hu2, v2, Color.WHITE);
                Renderer2D.TEXTURE.render(tex.getGlTextureView(), tex.getSampler());
            }
        } else {
            GuiRenderUtil.drawRound(r, x, y, size, size2, rad, new Color(60, 60, 60, 255));
        }
    }

    private Identifier getSkinTexture(AbstractClientPlayerEntity player) {
        try {
            PlayerListEntry entry = ((AbstractClientPlayerEntityAccessor) player).invokeGetPlayerListEntry();
            if (entry != null) {
                return entry.getSkinTextures().body().texturePath();
            }
        } catch (Exception ignored) {}
        return DefaultSkinHelper.getSkinTextures(player.getUuid()).body().texturePath();
    }

    private void drawArmor(HudRenderer r, double x, double y, float s, PlayerEntity pe) {
        ItemStack[] items = {
            pe.getMainHandStack(),
            pe.getEquippedStack(net.minecraft.entity.EquipmentSlot.HEAD),
            pe.getEquippedStack(net.minecraft.entity.EquipmentSlot.CHEST),
            pe.getEquippedStack(net.minecraft.entity.EquipmentSlot.LEGS),
            pe.getEquippedStack(net.minecraft.entity.EquipmentSlot.FEET),
            pe.getOffHandStack()
        };
        int ix = (int) x;
        for (ItemStack stack : items) {
            if (stack.isEmpty()) { ix += (int)(12 * s); continue; }
            r.item(stack, ix, (int) y, 1f, true);
            ix += (int)(16 * s);
        }
    }

    private void drawArmorSmall(HudRenderer r, double x, double y, float s, PlayerEntity pe) {
        ItemStack[] items = {
            pe.getMainHandStack(),
            pe.getEquippedStack(net.minecraft.entity.EquipmentSlot.HEAD),
            pe.getEquippedStack(net.minecraft.entity.EquipmentSlot.CHEST),
            pe.getEquippedStack(net.minecraft.entity.EquipmentSlot.LEGS),
            pe.getEquippedStack(net.minecraft.entity.EquipmentSlot.FEET),
            pe.getOffHandStack()
        };
        int ix = (int) x;
        for (ItemStack stack : items) {
            if (stack.isEmpty()) { ix += (int)(10 * s); continue; }
            r.item(stack, ix, (int) y, 0.75f, true);
            ix += (int)(13 * s);
        }
    }

    private void drawPotionText(HudRenderer r, double x, double y, PlayerEntity pe) {
        StringBuilder sb = new StringBuilder();
        for (StatusEffectInstance effect : pe.getStatusEffects()) {
            var type = effect.getEffectType().value();
            if (type == StatusEffects.STRENGTH) sb.append("Str").append(effect.getAmplifier()+1).append(" ");
            else if (type == StatusEffects.SPEED) sb.append("Spd").append(effect.getAmplifier()+1).append(" ");
            else if (type == StatusEffects.REGENERATION) sb.append("Reg ");
            else if (type == StatusEffects.RESISTANCE) sb.append("Res").append(effect.getAmplifier()+1).append(" ");
            else if (type == StatusEffects.WEAKNESS) sb.append("Weak ");
            else if (type == StatusEffects.SLOWNESS) sb.append("Slow").append(effect.getAmplifier()+1).append(" ");
        }
        if (!sb.isEmpty())
            r.text(sb.toString().trim(), x, y, new Color(141, 141, 141), true);
    }

    // ─── 工具 ─────────────────────────────────────────────

    private String hpStr() {
        return hpDisplay.get() == HpDisplay.Health
            ? String.format("%.1f", animHealth)
            : (int)(animHealth / target.getMaxHealth() * 100) + "%";
    }

    private String truncName(String name) {
        return name.length() > 14 ? name.substring(0, 14) + "…" : name;
    }

    private void updateTarget() {
        KillAura aura = Modules.get().get(KillAura.class);
        if (aura != null && aura.isActive() && aura.getTarget() instanceof LivingEntity le) {
            target = le; return;
        }
        if (mc.crosshairTarget instanceof EntityHitResult hit && hit.getEntity() instanceof LivingEntity le) {
            target = le; return;
        }
        target = null;
    }
}
