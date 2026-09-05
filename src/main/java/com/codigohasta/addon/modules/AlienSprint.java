package com.codigohasta.addon.modules;

import com.codigohasta.addon.AddonTemplate;
import com.codigohasta.addon.utils.alien.AlienMovementUtil;
import com.codigohasta.addon.utils.alien.AlienPlayerUtil;
import com.codigohasta.addon.utils.alien.AlienRotationUtil;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.util.Mth;

public class AlienSprint extends Module {
    public static AlienSprint INSTANCE;

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    public final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("Mode").description("Sprint mode").defaultValue(Mode.Legit).build());
    private final Setting<Boolean> inWaterPause = sgGeneral.add(new BoolSetting.Builder()
        .name("InWaterPause").description("Pause sprinting in water").defaultValue(true).build());
    private final Setting<Boolean> inWebPause = sgGeneral.add(new BoolSetting.Builder()
        .name("InWebPause").description("Pause sprinting in webs").defaultValue(true).build());
    private final Setting<Boolean> sneakingPause = sgGeneral.add(new BoolSetting.Builder()
        .name("SneakingPause").description("Pause sprinting when sneaking").defaultValue(false).build());
    private final Setting<Boolean> blindnessPause = sgGeneral.add(new BoolSetting.Builder()
        .name("BlindnessPause").description("Pause sprinting when blind").defaultValue(false).build());
    private final Setting<Boolean> usingPause = sgGeneral.add(new BoolSetting.Builder()
        .name("UsingPause").description("Pause sprinting when using items").defaultValue(false).build());
    private final Setting<Boolean> lagPause = sgGeneral.add(new BoolSetting.Builder()
        .name("LagPause").description("Pause sprinting after teleport").defaultValue(true).build());

    boolean pause = false;

    public AlienSprint() {
        super(AddonTemplate.CATEGORY, "强制疾跑Alien", "AlienV4的Sprint模块移植，有点问题：rotation模式下跳跃加速方向不对。懂的大神可以修修、暂不能绕过grim。强制保持疾跑状态");
        INSTANCE = this;
    }

    private boolean isSprintPressed() {
        return mc.options.keyUp.isDown() && !mc.options.keyDown.isDown();
    }

    private boolean isBackPressed() {
        return mc.options.keyDown.isDown() && !mc.options.keyUp.isDown();
    }

    private boolean isLeftPressed() {
        return mc.options.keyLeft.isDown() && !mc.options.keyRight.isDown();
    }

    private boolean isRightPressed() {
        return mc.options.keyRight.isDown() && !mc.options.keyLeft.isDown();
    }

    public float getSprintYaw(float yaw) {
        if (isSprintPressed()) {
            if (isLeftPressed()) {
                yaw -= 45.0F;
            } else if (isRightPressed()) {
                yaw += 45.0F;
            }
        } else if (isBackPressed()) {
            yaw += 180.0F;
            if (isLeftPressed()) {
                yaw += 45.0F;
            } else if (isRightPressed()) {
                yaw -= 45.0F;
            }
        } else if (isLeftPressed()) {
            yaw -= 90.0F;
        } else if (isRightPressed()) {
            yaw += 90.0F;
        }

        return Mth.wrapDegrees(yaw);
    }

    @Override
    public String getInfoString() {
        return mode.get().name();
    }

    @Override
    public void onDeactivate() {
        AlienRotationUtil.shouldRotate = false;
        if (mc.player != null) {
            mc.player.yBodyRot = mc.player.getYRot();
            mc.player.yHeadRot = mc.player.getYRot();
        }
    }

    @EventHandler
    public void onPacket(PacketEvent.Receive event) {
        if (lagPause.get() && event.packet instanceof ClientboundPlayerPositionPacket) {
            pause = true;
        }
    }

    @EventHandler
    public void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.level == null) return;
        if (mc.player.getPose().name().equals("GLIDING")) return;

        AlienRotationUtil.shouldRotate = false;

        if (mode.get() == Mode.PressKey) {
            if (!inWater()) {
                mc.options.keySprint.setDown(true);
            }
        } else {
            mc.player.setSprinting(shouldSprint());

            if (mode.get() == Mode.Rotation && AlienMovementUtil.isMoving()) {
                AlienRotationUtil.sprintYaw = getSprintYaw(mc.player.getYRot());
                AlienRotationUtil.shouldRotate = true;
            }
        }
    }

    @EventHandler
    public void onTickPost(TickEvent.Post event) {
        pause = false;
    }

    private boolean inWater() {
        return inWaterPause.get() && mc.player.isInLiquid();
    }

    private boolean shouldSprint() {
        if ((mc.player.getFoodData().getFoodLevel() > 6 || mc.player.isCreative())
            && AlienMovementUtil.isMoving()
            && !pause
            && (!mc.player.isShiftKeyDown() || !sneakingPause.get())
            && (!AlienPlayerUtil.isInWeb(mc.player) || !inWebPause.get())
            && (!mc.player.isUsingItem() || !usingPause.get())
            && !mc.player.isHandsBusy()
            && (!mc.player.hasEffect(MobEffects.BLINDNESS) || !blindnessPause.get())) {

            return switch (mode.get()) {
                case Legit -> mc.options.keyUp.isDown();
                case Rage -> true;
                case Rotation -> true;
                default -> false;
            };
        }
        return false;
    }

    public enum Mode {
        PressKey,
        Legit,
        Rage,
        Rotation
    }
}
