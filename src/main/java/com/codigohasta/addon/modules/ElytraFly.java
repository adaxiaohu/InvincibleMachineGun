package com.codigohasta.addon.modules;

import com.codigohasta.addon.AddonTemplate;
import com.codigohasta.addon.mixin.InventoryAccessor;
import com.codigohasta.addon.mixin.LivingEntityAccessor;
import com.codigohasta.addon.utils.alien.*;
import meteordevelopment.meteorclient.events.entity.player.PlayerMoveEvent;
import meteordevelopment.meteorclient.events.entity.player.SendMovementPacketsEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.mixininterface.IVec3;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.misc.Keybind;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.InteractionHand;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public class ElytraFly extends Module {
    public static ElytraFly INSTANCE;

    public enum Mode {
        Control, Boost, Bounce, Freeze, None, Rotation, Pitch
    }

    // === Setting Groups ===
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgControl = settings.createGroup("Control");
    private final SettingGroup sgPitch = settings.createGroup("Pitch");
    private final SettingGroup sgBounce = settings.createGroup("Bounce");
    private final SettingGroup sgFirework = settings.createGroup("Firework");

    // === General Settings ===
    public final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("mode").description("Flight mode.").defaultValue(Mode.Control).build());
    public final Setting<Boolean> infiniteDura = sgGeneral.add(new BoolSetting.Builder()
        .name("infinite-dura").description("Prevents elytra from taking damage.").defaultValue(false).build());
    public final Setting<Boolean> packet = sgGeneral.add(new BoolSetting.Builder()
        .name("packet-mode").description("Uses packets to fly without equipping elytra (Chestplate fly).").defaultValue(false).build());
    private final Setting<Integer> packetDelay = sgGeneral.add(new IntSetting.Builder()
        .name("packet-delay").description("Delay for packet mode ticks.").defaultValue(0).min(0).max(20).visible(packet::get).build());
    private final Setting<Boolean> setFlag = sgGeneral.add(new BoolSetting.Builder()
        .name("set-flag").description("Forces client-side flight flag.").defaultValue(false).visible(() -> mode.get() != Mode.Bounce).build());
    private final Setting<Boolean> autoStop = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-stop").description("Stops flying on unloaded chunks.").defaultValue(true).build());
    private final Setting<Boolean> instantFly = sgGeneral.add(new BoolSetting.Builder()
        .name("instant-fly").description("Starts flying automatically when falling.").defaultValue(true).visible(() -> mode.get() != Mode.Bounce).build());
    private final Setting<Double> timeout = sgGeneral.add(new DoubleSetting.Builder()
        .name("timeout").description("Instant fly timeout.").defaultValue(0.0).min(0.1).max(1.0).visible(() -> mode.get() != Mode.Bounce).build());
    public final Setting<Boolean> releaseSneak = sgGeneral.add(new BoolSetting.Builder()
        .name("release-sneak").description("Releases shift when module disables.").defaultValue(false).build());

    // === Control Mode Settings ===
    public final Setting<Double> upPitch = sgControl.add(new DoubleSetting.Builder()
        .name("up-pitch").description("Pitch angle when going up (rotation-based).").defaultValue(0.0).min(0.0).max(90.0)
        .visible(() -> mode.get() == Mode.Control).build());
    public final Setting<Double> upFactor = sgControl.add(new DoubleSetting.Builder()
        .name("up-factor").description("Upward velocity factor.").defaultValue(1.0).min(0.0).max(10.0)
        .visible(() -> mode.get() == Mode.Control).build());
    public final Setting<Double> fallSpeed = sgControl.add(new DoubleSetting.Builder()
        .name("fall-speed").description("Downward velocity factor.").defaultValue(1.0).min(0.0).max(10.0)
        .visible(() -> mode.get() == Mode.Control).build());
    public final Setting<Double> speed = sgControl.add(new DoubleSetting.Builder()
        .name("speed").description("Horizontal flight speed.").defaultValue(1.0).min(0.1).max(10.0)
        .visible(() -> mode.get() == Mode.Control).build());
    public final Setting<Boolean> speedLimit = sgControl.add(new BoolSetting.Builder()
        .name("speed-limit").description("Limits maximum speed.").defaultValue(true)
        .visible(() -> mode.get() == Mode.Control).build());
    public final Setting<Double> maxSpeed = sgControl.add(new DoubleSetting.Builder()
        .name("max-speed").description("Maximum speed cap.").defaultValue(2.5).min(0.1).max(10.0)
        .visible(() -> speedLimit.get() && mode.get() == Mode.Control).build());
    public final Setting<Boolean> noDrag = sgControl.add(new BoolSetting.Builder()
        .name("no-drag").description("Disables velocity drag.").defaultValue(false)
        .visible(() -> mode.get() == Mode.Control).build());
    private final Setting<Double> sneakDownSpeed = sgControl.add(new DoubleSetting.Builder()
        .name("down-speed").description("Sneak downward speed.").defaultValue(1.0).min(0.1).max(10.0)
        .visible(() -> mode.get() == Mode.Control).build());
    private final Setting<Double> boost = sgControl.add(new DoubleSetting.Builder()
        .name("boost").description("Boost mode strength.").defaultValue(1.0).min(0.1).max(4.0)
        .visible(() -> mode.get() == Mode.Boost).build());
    private final Setting<Boolean> freeze = sgControl.add(new BoolSetting.Builder()
        .name("freeze").description("Freeze in place when not moving (Rotation mode).").defaultValue(false)
        .visible(() -> mode.get() == Mode.Rotation).build());
    private final Setting<Boolean> motionStop = sgControl.add(new BoolSetting.Builder()
        .name("motion-stop").description("Stop all motion when not pressing keys (Rotation mode).").defaultValue(false)
        .visible(() -> mode.get() == Mode.Rotation).build());

    // === Pitch (Grim Bypass) Settings ===
    private final Setting<Double> infiniteMaxSpeed = sgPitch.add(new DoubleSetting.Builder()
        .name("infinite-max-speed").description("Max speed for pitch oscillation.").defaultValue(150.0).min(50.0).max(170.0)
        .visible(() -> mode.get() == Mode.Pitch).build());
    private final Setting<Double> infiniteMinSpeed = sgPitch.add(new DoubleSetting.Builder()
        .name("infinite-min-speed").description("Min speed for pitch oscillation.").defaultValue(25.0).min(10.0).max(70.0)
        .visible(() -> mode.get() == Mode.Pitch).build());
    private final Setting<Double> infiniteMaxHeight = sgPitch.add(new DoubleSetting.Builder()
        .name("infinite-max-height").description("Max Y level for pitch oscillation.").defaultValue(200.0).min(-50.0).max(360.0)
        .visible(() -> mode.get() == Mode.Pitch).build());

    // === Bounce Mode Settings ===
    public final Setting<Boolean> autoJump = sgBounce.add(new BoolSetting.Builder()
        .name("auto-jump").description("Automatically holds jump for Bounce mode.").defaultValue(true)
        .visible(() -> mode.get() == Mode.Bounce).build());
    private final Setting<Boolean> sprint = sgBounce.add(new BoolSetting.Builder()
        .name("sprint").description("Sprint in Bounce mode.").defaultValue(true)
        .visible(() -> mode.get() == Mode.Bounce).build());
    private final Setting<Double> bouncePitch = sgBounce.add(new DoubleSetting.Builder()
        .name("pitch").description("Pitch for Bounce mode.").defaultValue(88.0).min(-90.0).max(90.0).sliderMax(90.0)
        .visible(() -> mode.get() == Mode.Bounce).build());

    // === Firework Settings ===
    public final Setting<Boolean> firework = sgFirework.add(new BoolSetting.Builder()
        .name("firework").description("Auto uses fireworks.").defaultValue(false).build());
    public final Setting<Keybind> fireWorkBind = sgFirework.add(new KeybindSetting.Builder()
        .name("firework-bind").description("Manual firework keybind.").defaultValue(Keybind.none()).action(this::manualFirework).build());
    public final Setting<Boolean> packetInteract = sgFirework.add(new BoolSetting.Builder()
        .name("packet-interact").description("Use packets for firework interaction.").defaultValue(true).visible(firework::get).build());
    public final Setting<Boolean> inventorySwap = sgFirework.add(new BoolSetting.Builder()
        .name("inventory-swap").description("Pulls firework from inventory silently.").defaultValue(true).visible(firework::get).build());
    public final Setting<Boolean> onlyOne = sgFirework.add(new BoolSetting.Builder()
        .name("only-one").description("Limits to one rocket entity at a time.").defaultValue(true).visible(firework::get).build());
    private final Setting<Boolean> usingPause = sgFirework.add(new BoolSetting.Builder()
        .name("using-pause").description("Pauses firework while using items.").defaultValue(true).visible(firework::get).build());
    private final Setting<Boolean> checkSpeed = sgFirework.add(new BoolSetting.Builder()
        .name("check-speed").description("Only use firework when speed is below min-speed.").defaultValue(false)
        .visible(() -> mode.get() != Mode.Bounce).build());
    public final Setting<Double> minSpeed = sgFirework.add(new DoubleSetting.Builder()
        .name("min-speed").description("Minimum speed threshold for firework use.").defaultValue(70.0).min(0.1).max(200.0).sliderMax(200.0)
        .visible(() -> mode.get() != Mode.Bounce).build());
    private final Setting<Integer> delay = sgFirework.add(new IntSetting.Builder()
        .name("delay").description("Delay between firework uses (ms).").defaultValue(1000).min(0).max(20000)
        .visible(() -> mode.get() != Mode.Bounce).build());

    // === State Variables ===
    private final AlienTimer fireworkTimer = new AlienTimer();
    private final AlienTimer instantFlyTimer = new AlienTimer();
    private boolean hasElytra = false;
    private float yaw = 0.0f;
    private float rotationPitch = 0.0f;
    private boolean flying = false;
    private int packetDelayInt = 0;
    private boolean down;
    private float lastInfinitePitch;
    private float infinitePitch;
    private boolean prev;
    private float prePitch;

    public ElytraFly() {
        super(AddonTemplate.CATEGORY, "鞘翅飞行V4", "移植自AlienV4的完整鞘翅飞行，7种模式 + 烟花辅助。grim发包有点问题，效果不好");
        INSTANCE = this;
    }

    @Override
    public void onActivate() {
        if (mc.player == null) return;
        hasElytra = false;
        yaw = mc.player.getYRot();
        rotationPitch = mc.player.getXRot();
    }

    @Override
    public void onDeactivate() {
        if (mc.player != null && releaseSneak.get()) {
            mc.options.keyShift.setDown(false);
        }
    }

    @Override
    public String getInfoString() {
        return mode.get().name();
    }

    // ========================================
    // Firework System
    // ========================================

    private void manualFirework() {
        if (mc.player == null) return;
        if ((!mc.player.isUsingItem() || !usingPause.get()) && isFallFlying()
            && fireworkTimer.passed(delay.get())) {
            off();
            fireworkTimer.reset();
        }
    }

    public void off() {
        if (mc.player == null) return;
        if (!inventorySwap.get() || AlienEntityUtil.inInventory()) {
            if (onlyOne.get()) {
                for (Entity entity : mc.level.entitiesForRendering()) {
                    if (entity instanceof FireworkRocketEntity fw && fw.getOwner() == mc.player) {
                        return;
                    }
                }
            }
            fireworkTimer.reset();
            useFireworkItem();
        }
    }

    private void useFireworkItem() {
        if (mc.player.getMainHandItem().getItem() == Items.FIREWORK_ROCKET) {
            interactFirework();
        } else {
            int invSlot = AlienInventoryUtil.findItemInventorySlot(Items.FIREWORK_ROCKET);
            int hotbarSlot = AlienInventoryUtil.findItem(Items.FIREWORK_ROCKET);

            if (inventorySwap.get() && invSlot != -1) {
                int selectedSlot = ((InventoryAccessor) mc.player.getInventory()).getSelectedSlot();
                AlienInventoryUtil.inventorySwap(invSlot, selectedSlot);
                interactFirework();
                AlienInventoryUtil.inventorySwap(invSlot, selectedSlot);
                AlienEntityUtil.syncInventory();
            } else if (hotbarSlot != -1) {
                int old = ((InventoryAccessor) mc.player.getInventory()).getSelectedSlot();
                AlienInventoryUtil.switchToSlot(hotbarSlot);
                interactFirework();
                AlienInventoryUtil.switchToSlot(old);
            }
        }
    }

    private void interactFirework() {
        if (packetInteract.get()) {
            mc.getConnection().send(
                new ServerboundUseItemPacket(InteractionHand.MAIN_HAND, 0, mc.player.getYRot(), mc.player.getXRot()));
        } else {
            mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
        }
    }

    // ========================================
    // Static Helpers
    // ========================================

    public static boolean recastElytra(LocalPlayer player) {
        if (checkConditions(player) && ignoreGround(player)) {
            player.connection.send(
                new ServerboundPlayerCommandPacket(player, ServerboundPlayerCommandPacket.Action.START_FALL_FLYING));
            if (INSTANCE != null && INSTANCE.setFlag.get()) {
                INSTANCE.mc.player.startFallFlying();
            }
            return true;
        }
        return false;
    }

    public static boolean checkConditions(LocalPlayer player) {
        ItemStack stack = player.getItemBySlot(EquipmentSlot.CHEST);
        return !player.getAbilities().flying && !player.isPassenger() && !player.onClimbable()
            && stack.is(Items.ELYTRA) && (stack.getMaxDamage() - stack.getDamageValue() > 1);
    }

    private static boolean ignoreGround(LocalPlayer player) {
        if (!player.isInWater() && !player.hasEffect(MobEffects.LEVITATION)) {
            ItemStack stack = player.getItemBySlot(EquipmentSlot.CHEST);
            if (stack.is(Items.ELYTRA) && (stack.getMaxDamage() - stack.getDamageValue() > 1)) {
                player.startFallFlying();
                return true;
            }
        }
        return false;
    }

    private void boostFunc() {
        if (hasElytra && isFallFlying()) {
            float yaw = (float) Math.toRadians(mc.player.getYRot());
            if (mc.options.keyUp.isDown()) {
                mc.player.push(
                    -Mth.sin(yaw) * boost.get().floatValue() / 10.0f,
                    0.0,
                    Mth.cos(yaw) * boost.get().floatValue() / 10.0f);
            }
        }
    }

    // ========================================
    // Rotation Handler (SendMovementPacketsEvent.Pre)
    // ========================================

    @EventHandler(priority = -9999)
    private void onUpdateRotation(SendMovementPacketsEvent.Pre event) {
        if (mc.player == null || !isFallFlying()) return;

        if (mode.get() == Mode.Rotation) {
            if (AlienMovementUtil.isMoving()) {
                rotationPitch = mc.options.keyJump.isDown() ? -45.0f
                    : mc.options.keyShift.isDown() ? 45.0f : -1.9f;
            } else {
                rotationPitch = mc.options.keyJump.isDown() ? -89.0f
                    : mc.options.keyShift.isDown() ? 89.0f : rotationPitch;
                if (motionStop.get()) setY(0.0);
            }

            if (AlienMovementUtil.isMoving()) {
                yaw = getSprintYaw(mc.player.getYRot());
            } else if (motionStop.get()) {
                setX(0.0);
                setZ(0.0);
            }

            mc.player.setYRot(yaw);
            mc.player.setXRot(rotationPitch);

        } else if (mode.get() == Mode.Pitch && isFallFlying()) {
            mc.player.setXRot(infinitePitch);

        } else if (mode.get() == Mode.Bounce && isFallFlying()) {
            mc.player.setXRot(bouncePitch.get().floatValue());
        }
    }

    // ========================================
    // Tick Handler
    // ========================================

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null) return;

        getInfinitePitch();
        flying = false;

        if (packet.get()) {
            hasElytra = AlienInventoryUtil.findItem(Items.ELYTRA) != -1
                || AlienInventoryUtil.findItemInventorySlot(Items.ELYTRA) != -1;
        } else {
            hasElytra = false;
            ItemStack chestStack = mc.player.getItemBySlot(EquipmentSlot.CHEST);
            hasElytra = chestStack.is(Items.ELYTRA);

            if (infiniteDura.get() && !mc.player.onGround() && hasElytra) {
                flying = true;
                clickDurabilitySlot();
                mc.getConnection().send(
                    new ServerboundPlayerCommandPacket(mc.player, ServerboundPlayerCommandPacket.Action.START_FALL_FLYING));
                if (setFlag.get()) mc.player.startFallFlying();
            }

            if (mode.get() == Mode.Bounce) {
                ((LivingEntityAccessor) mc.player).setJumpingCooldown(0);
                return;
            }
        }

        double speedVal = calcSpeed();
        if (mode.get() == Mode.Boost) boostFunc();

        if (packet.get()) {
            handlePacketFly(speedVal);
        } else {
            handleNormalFly(speedVal);
        }
    }

    private double calcSpeed() {
        double dx = mc.player.getX() - mc.player.xOld;
        double dy = mc.player.getY() - mc.player.yOld;
        double dz = mc.player.getZ() - mc.player.zOld;
        double dist = Math.sqrt(dx * dx + dz * dz + dy * dy) / 1000.0;
        return dist / 1.388888888888889E-5;
    }

    private void clickDurabilitySlot() {
        int syncId = mc.player.containerMenu.containerId;
        mc.gameMode.handleContainerInput(syncId, 6, 0, ContainerInput.PICKUP, mc.player);
        mc.gameMode.handleContainerInput(syncId, 6, 0, ContainerInput.PICKUP, mc.player);
    }

    private void handlePacketFly(double speedVal) {
        if (mc.player.onGround()) return;

        packetDelayInt++;
        if (packetDelayInt > packetDelay.get()) {
            int syncId = mc.player.containerMenu.containerId;
            int elytra = AlienInventoryUtil.findItem(Items.ELYTRA);

            if (elytra != -1) {
                mc.gameMode.handleContainerInput(syncId, 6, elytra, ContainerInput.SWAP, mc.player);
                mc.getConnection().send(
                    new ServerboundPlayerCommandPacket(mc.player, ServerboundPlayerCommandPacket.Action.START_FALL_FLYING));
                mc.player.startFallFlying();
                mc.gameMode.handleContainerInput(syncId, 6, elytra, ContainerInput.SWAP, mc.player);
                packetDelayInt = 0;
            } else {
                int invElytra = AlienInventoryUtil.findItemInventorySlot(Items.ELYTRA);
                if (invElytra != -1) {
                    mc.gameMode.handleContainerInput(syncId, invElytra, 0, ContainerInput.PICKUP, mc.player);
                    mc.gameMode.handleContainerInput(syncId, 6, 0, ContainerInput.PICKUP, mc.player);
                    mc.getConnection().send(
                        new ServerboundPlayerCommandPacket(mc.player, ServerboundPlayerCommandPacket.Action.START_FALL_FLYING));
                    mc.player.startFallFlying();
                    mc.gameMode.handleContainerInput(syncId, 6, 0, ContainerInput.PICKUP, mc.player);
                    mc.gameMode.handleContainerInput(syncId, invElytra, 0, ContainerInput.PICKUP, mc.player);
                    packetDelayInt = 0;
                }
            }
        }

        // 烟花和自动起飞: 在 clickSlot 序列之外执行, 避免库存状态被破坏
        if (!mc.player.onGround() && isFallFlying()) {
            boolean moving = AlienMovementUtil.isMoving()
                || (mode.get() == Mode.Rotation && mc.options.keyJump.isDown());
            boolean speedOk = !checkSpeed.get() || speedVal <= minSpeed.get();
            boolean notUsing = !mc.player.isUsingItem() || !usingPause.get();

            if (speedOk && firework.get() && fireworkTimer.passed(delay.get())
                && moving && notUsing) {
                off();
                fireworkTimer.reset();
            }
        }
    }

    private void handleNormalFly(double speedVal) {
        tryAutoFirework(speedVal);

        if (!isFallFlying() && hasElytra) {
            fireworkTimer.setMs(99999999L);
            if (!mc.player.onGround() && instantFly.get()
                && mc.player.getDeltaMovement().y < 0.0 && !infiniteDura.get()) {
                if (!instantFlyTimer.passed((long) (1000.0 * timeout.get()))) return;
                instantFlyTimer.reset();
                mc.getConnection().send(
                    new ServerboundPlayerCommandPacket(mc.player, ServerboundPlayerCommandPacket.Action.START_FALL_FLYING));
                if (setFlag.get()) mc.player.startFallFlying();
            }
        }
    }

    private void tryAutoFirework(double speedVal) {
        boolean speedOk = !checkSpeed.get() || speedVal <= minSpeed.get();
        boolean moving = AlienMovementUtil.isMoving()
            || (mode.get() == Mode.Rotation && mc.options.keyJump.isDown());
        boolean notUsing = !mc.player.isUsingItem() || !usingPause.get();

        if (speedOk && firework.get() && fireworkTimer.passed(delay.get())
            && moving && notUsing && isFallFlying()) {
            off();
            fireworkTimer.reset();
        }
    }

    // ========================================
    // Post Tick (Bounce Mode)
    // ========================================

    @EventHandler
    private void onTickPost(TickEvent.Post event) {
        if (mc.player == null) return;

        if (mode.get() == Mode.Bounce && hasElytra && !packet.get()) {
            if (autoJump.get()) mc.options.keyJump.setDown(true);

            if (checkConditions(mc.player)) {
                if (!isFallFlying()) {
                    mc.getConnection().send(
                        new ServerboundPlayerCommandPacket(mc.player, ServerboundPlayerCommandPacket.Action.START_FALL_FLYING));
                }
                if (!sprint.get()) {
                    mc.player.setSprinting(isFallFlying() && mc.player.onGround());
                }
            } else if (sprint.get()) {
                mc.player.setSprinting(true);
            }
        }
    }

    // ========================================
    // Player Move Handler
    // ========================================

    @EventHandler
    private void onPlayerMove(PlayerMoveEvent event) {
        if (mc.player == null) return;

        // Auto-stop on unloaded chunks
        if (autoStop.get() && isFallFlying()) {
            int chunkX = (int) (mc.player.getX() / 16.0);
            int chunkZ = (int) (mc.player.getZ() / 16.0);
            if (!mc.level.getChunkSource().hasChunk(chunkX, chunkZ)) {
                ((IVec3) event.movement).meteor$set(0.0, 0.0, 0.0);
                return;
            }
        }

        if (hasElytra && isFallFlying()) {
            boolean moving = AlienMovementUtil.isMoving();
            boolean jumpPressed = mc.options.keyJump.isDown();
            boolean sneakPressed = mc.options.keyShift.isDown();

            // Freeze / Rotation freeze
            if ((mode.get() == Mode.Freeze
                || (mode.get() == Mode.Rotation && freeze.get()))
                && !moving && !jumpPressed && !sneakPressed) {
                ((IVec3) event.movement).meteor$set(0.0, 0.0, 0.0);
                return;
            }

            // Control mode movement
            if (mode.get() == Mode.Control) {
                handleControlMove(event);
            }
        }
    }

    private void handleControlMove(PlayerMoveEvent event) {
        if (firework.get()) {
            // Simple velocity-based control (firework mode)
            if (mc.options.keyShift.isDown() && mc.options.keyJump.isDown()) {
                setY(0.0);
            } else if (mc.options.keyShift.isDown()) {
                setY(-sneakDownSpeed.get());
            } else if (mc.options.keyJump.isDown()) {
                setY(upFactor.get());
            } else {
                setY(-3.0E-11 * fallSpeed.get());
            }

            double[] dir = AlienMovementUtil.directionSpeed(speed.get());
            setX(dir[0]);
            setZ(dir[1]);
        } else {
            // Rotation-based control
            Vec3 lookVec = getRotationVec(1.0f);
            double lookDist = Math.sqrt(lookVec.x * lookVec.x + lookVec.z * lookVec.z);
            double motionDist = Math.sqrt(getX() * getX() + getZ() * getZ());

            if (mc.options.keyShift.isDown()) {
                setY(-sneakDownSpeed.get());
            } else if (!mc.options.keyJump.isDown()) {
                setY(-3.0E-11 * fallSpeed.get());
            }

            if (mc.options.keyJump.isDown()) {
                if (motionDist > upFactor.get() / 10.0) {
                    double rawUpSpeed = motionDist * 0.01325;
                    setY(getY() + rawUpSpeed * 3.2);
                    setX(getX() - lookVec.x * rawUpSpeed / lookDist);
                    setZ(getZ() - lookVec.z * rawUpSpeed / lookDist);
                } else {
                    double[] dir = AlienMovementUtil.directionSpeed(speed.get());
                    setX(dir[0]);
                    setZ(dir[1]);
                }
            }

            if (lookDist > 0.0) {
                setX(getX() + (lookVec.x / lookDist * motionDist - getX()) * 0.1);
                setZ(getZ() + (lookVec.z / lookDist * motionDist - getZ()) * 0.1);
            }

            if (!mc.options.keyJump.isDown()) {
                double[] dir = AlienMovementUtil.directionSpeed(speed.get());
                setX(dir[0]);
                setZ(dir[1]);
            }

            if (!noDrag.get()) {
                setY(getY() * 0.99);
                setX(getX() * 0.98);
                setZ(getZ() * 0.99);
            }

            double finalDist = Math.sqrt(getX() * getX() + getZ() * getZ());
            if (speedLimit.get() && finalDist > maxSpeed.get()) {
                setX(getX() * maxSpeed.get() / finalDist);
                setZ(getZ() * maxSpeed.get() / finalDist);
            }

            ((IVec3) event.movement).meteor$set(getX(), getY(), getZ());
        }
    }

    // ========================================
    // Packet Handlers (Bounce Mode)
    // ========================================

    @EventHandler
    private void onPacketSend(PacketEvent.Send event) {
        if (mc.player == null) return;
        if (mode.get() == Mode.Bounce && hasElytra && !packet.get()
            && event.packet instanceof ServerboundPlayerCommandPacket pkt
            && pkt.getAction() == ServerboundPlayerCommandPacket.Action.START_FALL_FLYING
            && !sprint.get()) {
            mc.player.setSprinting(true);
        }
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (mc.player == null) return;
        if (mode.get() == Mode.Bounce && hasElytra && !packet.get()
            && event.packet instanceof ClientboundPlayerPositionPacket) {
            mc.player.stopFallFlying();
        }
    }

    // ========================================
    // Utility Methods
    // ========================================

    private double getX() { return AlienMovementUtil.getMotionX(); }
    private void setX(double v) { AlienMovementUtil.setMotionX(v); }
    private double getY() { return AlienMovementUtil.getMotionY(); }
    private void setY(double v) { AlienMovementUtil.setMotionY(v); }
    private double getZ() { return AlienMovementUtil.getMotionZ(); }
    private void setZ(double v) { AlienMovementUtil.setMotionZ(v); }

    private void getInfinitePitch() {
        lastInfinitePitch = infinitePitch;
        double speedVal = Math.hypot(mc.player.getX() - mc.player.xOld,
            mc.player.getZ() - mc.player.zOld);
        if (mc.player.getY() < infiniteMaxHeight.get()) {
            if (speedVal * 72.0 < infiniteMinSpeed.get() && !down) down = true;
            if (speedVal * 72.0 > infiniteMaxSpeed.get() && down) down = false;
        } else {
            down = true;
        }
        infinitePitch += down ? 3.0f : -3.0f;
        infinitePitch = AlienMathUtil.clamp(infinitePitch, -40.0f, 40.0f);
    }

    public boolean isFallFlying() {
        return mc.player.isFallFlying()
            || (packet.get() && hasElytra && !mc.player.onGround())
            || flying;
    }

    private Vec3 getRotationVector(float pitch, float yaw) {
        float f = pitch * (float) (Math.PI / 180.0);
        float g = -yaw * (float) (Math.PI / 180.0);
        float h = Mth.cos(g);
        float i = Mth.sin(g);
        float j = Mth.cos(f);
        float k = Mth.sin(f);
        return new Vec3(i * j, -k, h * j);
    }

    public Vec3 getRotationVec(float tickDelta) {
        return getRotationVector(-upPitch.get().floatValue(), mc.player.getYRot());
    }

    private float getSprintYaw(float yaw) {
        if (mc.options.keyUp.isDown() && !mc.options.keyDown.isDown()) {
            if (mc.options.keyLeft.isDown() && !mc.options.keyRight.isDown()) yaw -= 45.0f;
            else if (mc.options.keyRight.isDown() && !mc.options.keyLeft.isDown()) yaw += 45.0f;
        } else if (mc.options.keyDown.isDown() && !mc.options.keyUp.isDown()) {
            yaw += 180.0f;
            if (mc.options.keyLeft.isDown() && !mc.options.keyRight.isDown()) yaw += 45.0f;
            else if (mc.options.keyRight.isDown() && !mc.options.keyLeft.isDown()) yaw -= 45.0f;
        } else if (mc.options.keyLeft.isDown() && !mc.options.keyRight.isDown()) {
            yaw -= 90.0f;
        } else if (mc.options.keyRight.isDown() && !mc.options.keyLeft.isDown()) {
            yaw += 90.0f;
        }
        return Mth.wrapDegrees(yaw);
    }
}
