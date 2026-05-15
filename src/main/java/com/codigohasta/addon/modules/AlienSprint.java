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
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.util.math.MathHelper;

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
        return mc.options.forwardKey.isPressed() && !mc.options.backKey.isPressed();
    }

    private boolean isBackPressed() {
        return mc.options.backKey.isPressed() && !mc.options.forwardKey.isPressed();
    }

    private boolean isLeftPressed() {
        return mc.options.leftKey.isPressed() && !mc.options.rightKey.isPressed();
    }

    private boolean isRightPressed() {
        return mc.options.rightKey.isPressed() && !mc.options.leftKey.isPressed();
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

        return MathHelper.wrapDegrees(yaw);
    }

    @Override
    public String getInfoString() {
        return mode.get().name();
    }

    @Override
    public void onDeactivate() {
        AlienRotationUtil.shouldRotate = false;
        if (mc.player != null) {
            mc.player.bodyYaw = mc.player.getYaw();
            mc.player.headYaw = mc.player.getYaw();
        }
    }

    @EventHandler
    public void onPacket(PacketEvent.Receive event) {
        if (lagPause.get() && event.packet instanceof PlayerPositionLookS2CPacket) {
            pause = true;
        }
    }

    @EventHandler
    public void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;
        if (mc.player.getPose().name().equals("FALL_FLYING")) return;

        AlienRotationUtil.shouldRotate = false;

        if (mode.get() == Mode.PressKey) {
            if (!inWater()) {
                mc.options.sprintKey.setPressed(true);
            }
        } else {
            mc.player.setSprinting(shouldSprint());

            if (mode.get() == Mode.Rotation && AlienMovementUtil.isMoving()) {
                AlienRotationUtil.sprintYaw = getSprintYaw(mc.player.getYaw());
                AlienRotationUtil.shouldRotate = true;
            }
        }
    }

    @EventHandler
    public void onTickPost(TickEvent.Post event) {
        pause = false;
    }

    private boolean inWater() {
        return inWaterPause.get() && mc.player.isInFluid();
    }

    private boolean shouldSprint() {
        if ((mc.player.getHungerManager().getFoodLevel() > 6 || mc.player.isCreative())
            && AlienMovementUtil.isMoving()
            && !pause
            && (!mc.player.isSneaking() || !sneakingPause.get())
            && (!AlienPlayerUtil.isInWeb(mc.player) || !inWebPause.get())
            && (!mc.player.isUsingItem() || !usingPause.get())
            && !mc.player.isRiding()
            && (!mc.player.hasStatusEffect(StatusEffects.BLINDNESS) || !blindnessPause.get())) {

            return switch (mode.get()) {
                case Legit -> mc.options.forwardKey.isPressed();
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
