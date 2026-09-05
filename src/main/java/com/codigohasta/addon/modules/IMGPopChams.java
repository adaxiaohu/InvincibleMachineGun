package com.codigohasta.addon.modules;

import com.codigohasta.addon.AddonTemplate;
import com.codigohasta.addon.utils.alien.AlienAnimation;
import com.codigohasta.addon.utils.alien.AlienEasing;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.entity.fakeplayer.FakePlayerEntity;
import meteordevelopment.meteorclient.utils.render.WireframeEntityRenderer;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityEvent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;

import java.util.concurrent.CopyOnWriteArrayList;

public class IMGPopChams extends Module {
    public static IMGPopChams INSTANCE;

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<AlienEasing> ease = sgGeneral.add(new EnumSetting.Builder<AlienEasing>()
        .name("ease")
        .description("Easing mode used for the animation.")
        .defaultValue(AlienEasing.CubicInOut)
        .build()
    );

    private final Setting<Boolean> fillEnabled = sgGeneral.add(new BoolSetting.Builder()
        .name("fill")
        .description("Render filled model.")
        .defaultValue(true)
        .build()
    );

    private final Setting<SettingColor> fillColor = sgGeneral.add(new ColorSetting.Builder()
        .name("fill-color")
        .description("Fill color.")
        .defaultValue(new SettingColor(255, 255, 255, 100))
        .build()
    );

    private final Setting<Boolean> lineEnabled = sgGeneral.add(new BoolSetting.Builder()
        .name("line")
        .description("Render the outlind of the model.er line/outline.")
        .defaultValue(true)
        .build()
    );

    private final Setting<SettingColor> lineColor = sgGeneral.add(new ColorSetting.Builder()
        .name("line-color")
        .description("Line color.")
        .defaultValue(new SettingColor(255, 255, 255, 100))
        .build()
    );

    private final Setting<Boolean> alpha = sgGeneral.add(new BoolSetting.Builder()
        .name("alpha")
        .description("淡入淡出alpha值。")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> forceSneak = sgGeneral.add(new BoolSetting.Builder()
        .name("force-sneak")
        .description("Force the ghost to be in sneaking pose.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> noSelf = sgGeneral.add(new BoolSetting.Builder()
        .name("no-self")
        .description("Don't render ghost for the player you are controlling.host for yourself.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> noLimb = sgGeneral.add(new BoolSetting.Builder()
        .name("no-limb")
        .description("Disable limb movement for the ghost. (May not work in 1.21.11)")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> fadeTime = sgGeneral.add(new IntSetting.Builder()
        .name("fade-time")
        .description("淡入淡出时间，单位毫秒。")
        .defaultValue(300)
        .min(0)
        .max(1000)
        .sliderMax(1000)
        .build()
    );

    private final Setting<Double> yOffset = sgGeneral.add(new DoubleSetting.Builder()
        .name("y-offset")
        .description("图腾的y偏移量。")
        .defaultValue(0.0)
        .min(-10.0)
        .max(10.0)
        .build()
    );

    private final Setting<Double> scale = sgGeneral.add(new DoubleSetting.Builder()
        .name("scale")
        .description("图腾的缩放比例。")
        .defaultValue(1.0)
        .min(0.0)
        .max(2.0)
        .build()
    );

    private final Setting<Double> yaw = sgGeneral.add(new DoubleSetting.Builder()
        .name("yaw")
        .description("额外的yaw旋转。")
        .defaultValue(0.0)
        .min(0.0)
        .max(720.0)
        .build()
    );

    private final CopyOnWriteArrayList<GhostPlayer> ghostList = new CopyOnWriteArrayList<>();

    public static void onFakePlayerTotemPop(Player player) {
        if (INSTANCE != null && INSTANCE.isActive()) {
            INSTANCE.ghostList.add(INSTANCE.new GhostPlayer(player));
        }
    }

    public IMGPopChams() {
        super(AddonTemplate.CATEGORY, "R图腾弹出框", "当图腾弹出的时候发动一个渲染框提示你。来自AlienV4的PopChams模块。");
        INSTANCE = this;
    }

    @Override
    public void onDeactivate() {
        ghostList.clear();
    }

    @EventHandler
    private void onReceivePacket(PacketEvent.Receive event) {
        if (!(event.packet instanceof ClientboundEntityEventPacket p)) return;
        if (p.getEventId() != EntityEvent.PROTECTED_FROM_DEATH) return;

        Entity entity = p.getEntity(mc.level);
        if (!(entity instanceof Player player)) return;
        if (noSelf.get() && entity == mc.player) return;

        ghostList.add(new GhostPlayer(player));
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        ghostList.removeIf(ghost -> ghost.render(event));
    }

    private class GhostPlayer extends FakePlayerEntity {
        private final AlienAnimation animation = new AlienAnimation();
        private final double origX, origY, origZ;
        private final float origBodyYaw, origHeadYaw;
        private final int sourceId;

        public GhostPlayer(Player player) {
            super(player, "ghost", 20, false);
            this.origX = player.getX();
            this.origY = player.getY();
            this.origZ = player.getZ();
            this.origBodyYaw = player.yBodyRot;
            this.origHeadYaw = player.yHeadRot;
            this.sourceId = player.getId();

            this.copyPosition(player);
            this.yBodyRot = player.yBodyRot;
            this.yHeadRot = player.yHeadRot;

            if (forceSneak.get()) {
                this.setShiftKeyDown(true);
            }
        }

        public boolean render(Render3DEvent event) {
            double progress = animation.get(1.0, fadeTime.get(), ease.get());
            if (progress >= 1.0) return true;

            // Calculate animated values
            double animScale = 1.0 + (scale.get() - 1.0) * progress;
            double animYaw = yaw.get() * progress;
            double animYOffset = yOffset.get() * progress;
            double animAlpha = alpha.get() ? 1.0 - progress : 1.0;

            // Update entity state for rendering
            this.setPos(origX, origY + animYOffset, origZ);
            this.yBodyRot = origBodyYaw + (float) animYaw;
            this.yHeadRot = origHeadYaw + (float) animYaw;
            this.xOld = this.getX();
            this.yOld = this.getY();
            this.zOld = this.getZ();

            // Create animated colors
            int fillA = (int) (fillColor.get().a * animAlpha);
            int lineA = (int) (lineColor.get().a * animAlpha);
            Color sideC = new Color(fillColor.get().r, fillColor.get().g, fillColor.get().b, Math.max(0, Math.min(255, fillA)));
            Color lineC = new Color(lineColor.get().r, lineColor.get().g, lineColor.get().b, Math.max(0, Math.min(255, lineA)));

            // Choose shape mode based on fill/line settings
            ShapeMode mode;
            if (fillEnabled.get() && lineEnabled.get()) mode = ShapeMode.Both;
            else if (fillEnabled.get()) mode = ShapeMode.Sides;
            else if (lineEnabled.get()) mode = ShapeMode.Lines;
            else mode = ShapeMode.Sides;

            WireframeEntityRenderer.render(event, this, animScale, sideC, lineC, mode);
            return false;
        }
    }
}
