package com.codigohasta.addon.modules;

import com.codigohasta.addon.AddonTemplate;
import com.codigohasta.addon.utils.Timer;
import com.codigohasta.addon.mixin.IVec3d;
import com.codigohasta.addon.utils.leaveshack.InventoryUtil;
import com.codigohasta.addon.utils.leaveshack.Rotation;
import com.codigohasta.addon.utils.leaveshack.events.ElytraUpdateEvent;
import com.codigohasta.addon.utils.leaveshack.events.TravelEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.multiplayer.prediction.BlockStatePredictionHandler;
import net.minecraft.client.multiplayer.prediction.PredictiveAction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Input;

import java.util.TimerTask;

import static com.codigohasta.addon.utils.leaveshack.Rotation.*;
import com.codigohasta.addon.mixin.InventoryAccessor;

public class FireworkElytraFly extends Module {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    public final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
            .name("Mode")
            .description("运行模式(Legit合法，GrimDurability甲飞)")
            .defaultValue(Mode.Legit)
            .build()
    );
    public final Setting<FireWorkMode> fireWorkMode = sgGeneral.add(new EnumSetting.Builder<FireWorkMode>()
            .name("FireWorkMode")
            .description("烟花使用模式(Delay延迟放，Auto自动放)")
            .defaultValue(FireWorkMode.Delay)
            .build()
    );
    private final Setting<Double> packetDealy = sgGeneral.add(new DoubleSetting.Builder()
            .name("PacketDelay")
            .description("发包延迟tick数")
            .defaultValue(3)
            .sliderMax(100)
            .build()
    );
    public final Setting<Boolean> unbreaking = sgGeneral.add(new BoolSetting.Builder()
            .name("Unbreaking")
            .description("无限耐久")
            .description("")
            .defaultValue(true)
            .build()
    );
    private final Setting<Double> fakeDelay = sgGeneral.add(new DoubleSetting.Builder()
            .name("FakeDelay")
            .description("无限耐久操作延迟")
            .defaultValue(800)
            .sliderMax(1000)
            .build()
    );
    public final Setting<Boolean> stand = sgGeneral.add(new BoolSetting.Builder()
            .name("Stand")
            .description("站飞")
            .description("")
            .defaultValue(true)
            .build()
    );
    public final Setting<Boolean> releaseSneak = sgGeneral.add(new BoolSetting.Builder()
            .name("ReleaseSneak")
            .description("自动shift")
            .description("")
            .defaultValue(true)
            .build()
    );
    public final Setting<Boolean> pressSneak = sgGeneral.add(new BoolSetting.Builder()
            .name("PressSneak")
            .description("自动shift")
            .description("")
            .defaultValue(true)
            .build()
    );
    public final Setting<Integer> releaseDelay = sgGeneral.add(new IntSetting.Builder()
            .name("ReleaseDelay")
            .description("shift延迟")
            .defaultValue(100)
            .sliderMax(1000)
            .build()
    );
    private final Setting<Double> delay = sgGeneral.add(new DoubleSetting.Builder()
            .name("FireWorkDelay")
            .description("烟花操作延迟")
            .defaultValue(1000)
            .visible(() -> fireWorkMode.get() == FireWorkMode.Delay)
            .sliderMax(3000)
            .build()
    );
    private final Setting<Boolean> checkFirework = sgGeneral.add(new BoolSetting.Builder()
            .name("CheckFireWork")
            .description("自动检查烟花")
            .defaultValue(true)
            .build()
    );
    public final Setting<Boolean> inventorySwap = sgGeneral.add(new BoolSetting.Builder()
            .name("InventorySwap")
            .description("背包鬼手")
            .defaultValue(true)
            .build()
    );
    public final Setting<Boolean> control = sgGeneral.add(new BoolSetting.Builder()
            .name("Control")
            .description("甲飞控制")
            .defaultValue(true)
            .build()
    );
    private final Setting<Double> fallSpeed = sgGeneral.add(new DoubleSetting.Builder()
            .name("FallSpeed")
            .description("下落速度")
            .defaultValue(0.02)
            .sliderRange(0.0, 3.0)
            .build()
    );
    private final Setting<Boolean> deBug = sgGeneral.add(new BoolSetting.Builder()
            .name("DeBug")
            .description("dev查bug的，没iq不要开")
            .defaultValue(false)
            .build()
    );
    public static FireworkElytraFly INSTANCE;
    public FireworkElytraFly() {
        super(AddonTemplate.CATEGORY, "L鞘翅飞行", "来自leaveshack的烟花鞘翅飞行。历经一个通宵最终能在grim用了。飞的不快，有更好的选择。");
        INSTANCE = this;
    }
    public float yaw = rotationYaw;
    public float pitch = rotationPitch;
    public boolean isUsingFirework = false;
    private final Timer fireworkTimer = new Timer();
    private final Timer swapTimer = new Timer();
    public boolean isFallFlying = false;
    public int packetDelayInt = 0;
    public int clearInputTicks = 0;
    public boolean forceJumpInput = false;
    @Override
    public void onActivate() {
        clearInputTicks = 0;
        forceJumpInput = false;
        fireworkTimer.setMs(99999);
        packetDelayInt = 0;
        swapTimer.setMs(99999);
    }
    @Override
    public void onDeactivate() {
        mc.options.keyJump.setDown(false);
        clearInputTicks = 0;
        forceJumpInput = false;
        if (pressSneak.get()) {
            mc.options.keyShift.setDown(true);
        }
        if (releaseSneak.get()) {
            long delay = releaseDelay.get();
            java.util.Timer timer = new java.util.Timer();
            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    mc.execute(() -> {
                        mc.options.keyShift.setDown(false);
                    });
                }
            }, delay);
        }
    }
    @EventHandler
    public void onTravel(TravelEvent event) {
        if (!isFallFlying) return;
        if (mode.get() == Mode.Legit) return;
        if (!control.get()) return;
        if (mc.screen instanceof ChatScreen) {
            setY(fallSpeed.get());
            return;
        }
        if (!wantToMove()) {
            setX(0);
            setZ(0);
            setY(fallSpeed.get());
        }
    }
    private void setY(double f) {
        ((IVec3d) mc.player.getDeltaMovement()).setY(f);
    }
    private void setX(double f) {
        ((IVec3d) mc.player.getDeltaMovement()).setX(f);
    }
    private void setZ(double f) {
        ((IVec3d) mc.player.getDeltaMovement()).setZ(f);
    }
    @Override
    public String getInfoString() {
        if (mc.player == null || mc.level == null) return null;
        int fireworks = 0;
        if (inventorySwap.get()) {
            for (int i = 0; i < 45; ++i) {
                ItemStack stack = mc.player.getInventory().getItem(i);
                if (stack.getItem() == Items.FIREWORK_ROCKET) fireworks = fireworks + stack.getCount();
            }
        } else {
            for (int i = 0; i < 9; ++i) {
                ItemStack stack = mc.player.getInventory().getItem(i);
                if (stack.getItem() == Items.FIREWORK_ROCKET) fireworks = fireworks + stack.getCount();
            }
        }
        return "搂f[F:" + fireworks + "]";
    }
    @EventHandler
    public void onElytraUpdate(ElytraUpdateEvent event) {
        if (stand.get()) event.cancel();
    }
    @EventHandler
    public void onTick(TickEvent.Pre event){
        forceJumpInput = false;
        if (mc.screen != null && deBug.get()) info("screen" + mc.screen.getTitle() + " " + mc.screen.getClass().getSimpleName() + " " + mc.screen.getClass().getSuperclass().getSimpleName() + " " + mc.screen.getTitle());
        if (mc.screen != null && mc.screen instanceof AbstractContainerScreen<?> && !(mc.screen instanceof InventoryScreen || mc.screen instanceof CreativeModeInventoryScreen)) return;
        int elytra = InventoryUtil.findItemInventorySlot(Items.ELYTRA);
        packetDelayInt++;
        if (mode.get() == Mode.GrimDurability && elytra != -1 && packetDelayInt == packetDealy.get().intValue()) {
            clearInputTicks = 2;
        }
        yaw = getSprintYaw(mc.player.getYRot());
        pitch = getPitch(mc.player.getXRot());
        if (deBug.get()) info("Yaw: " + yaw + " Pitch: " + pitch);
        if (mode.get() == Mode.GrimDurability) {
            if (GlobalSetting.INSTANCE.moveFix.get()) {
                Rotation.snapAt(yaw, pitch);
            } else {
                mc.getConnection().send(new ServerboundMovePlayerPacket.PosRot(mc.player.getX(), mc.player.getY(), mc.player.getZ(), yaw, pitch, mc.player.onGround(), mc.player.horizontalCollision));
            }
        }
        boolean hasFirework = false;
        if (checkFirework.get()) {
            for (Entity entity : mc.level.entitiesForRendering()) {
                if (entity instanceof FireworkRocketEntity firework) {
                    if (firework.getOwner() == mc.player) {
                        hasFirework = true;
                    }
                }
            }
        }
        isUsingFirework = hasFirework;
        ItemStack chestStack = mc.player.getItemBySlot(EquipmentSlot.CHEST);
        boolean wearingElytra = chestStack.getItem() == Items.ELYTRA && chestStack.getDamageValue() < chestStack.getMaxDamage() - 1;
        if (mode.get() == Mode.GrimDurability) {
            if (elytra != -1 && packetDelayInt > packetDealy.get()) {
                mc.gameMode.handleContainerInput(mc.player.containerMenu.containerId, elytra, 0, ContainerInput.PICKUP, mc.player);
                mc.gameMode.handleContainerInput(mc.player.containerMenu.containerId, 6, 0, ContainerInput.PICKUP, mc.player);
                mc.gameMode.handleContainerInput(mc.player.containerMenu.containerId, elytra, 0, ContainerInput.PICKUP, mc.player);
                if (!mc.player.onGround()) {
                    sendPacket(new ServerboundPlayerCommandPacket(mc.player, ServerboundPlayerCommandPacket.Action.START_FALL_FLYING));
                    mc.player.startFallFlying();
                }
                if (!hasFirework && fireWorkMode.get() == FireWorkMode.Auto) {
                    offFirework();
                } else if (fireWorkMode.get() == FireWorkMode.Delay && wantToMove()){
                    if (!checkFirework.get() || !isUsingFirework){
                        offFirework();
                    }
                }
                mc.gameMode.handleContainerInput(mc.player.containerMenu.containerId, elytra, 0, ContainerInput.PICKUP, mc.player);
                mc.gameMode.handleContainerInput(mc.player.containerMenu.containerId, 6, 0, ContainerInput.PICKUP, mc.player);
                mc.gameMode.handleContainerInput(mc.player.containerMenu.containerId, elytra, 0, ContainerInput.PICKUP, mc.player);
                forceJumpInput = true;
                packetDelayInt = 0;
            }
        } else {
            if (wearingElytra && !mc.player.isFallFlying() && !mc.player.onGround()) {
                sendPacket(new ServerboundPlayerCommandPacket(mc.player, ServerboundPlayerCommandPacket.Action.START_FALL_FLYING));
                mc.player.startFallFlying();
            }
            if (mode.get() == Mode.Legit && wearingElytra && mc.player.isFallFlying() && !mc.player.onGround() && unbreaking.get() && swapTimer.passedMs(fakeDelay.get())) {
                mc.gameMode.handleContainerInput(mc.player.containerMenu.containerId, 6, 0, ContainerInput.PICKUP, mc.player);
                mc.gameMode.handleContainerInput(mc.player.containerMenu.containerId, 6, 0, ContainerInput.PICKUP, mc.player);
                sendPacket(new ServerboundPlayerCommandPacket(mc.player, ServerboundPlayerCommandPacket.Action.START_FALL_FLYING));
                mc.player.startFallFlying();
                swapTimer.reset();
            }
            if (wearingElytra && mc.player.isFallFlying()) {
                if (!hasFirework && fireWorkMode.get() == FireWorkMode.Auto) {
                    offFirework();
                } else if (fireWorkMode.get() == FireWorkMode.Delay && wantToMove()){
                    if (!checkFirework.get() || !isUsingFirework){
                        offFirework();
                    }
                }
            }
        }
    }

    @EventHandler
    public void onTickPost(TickEvent.Post event) {
        if (clearInputTicks > 0) clearInputTicks--;
    }

    public void offFirework() {
        if (!fireworkTimer.passedMs(delay.get()) && fireWorkMode.get() == FireWorkMode.Delay) return;
        int firework;
        if (mc.player.getMainHandItem().getItem() == Items.FIREWORK_ROCKET) {
            sendSequencedPacket(id -> new ServerboundUseItemPacket(InteractionHand.MAIN_HAND, id, yaw, pitch));
            fireworkTimer.reset();
        } else if (mc.player.getOffhandItem().getItem() == Items.FIREWORK_ROCKET) {
            sendSequencedPacket(id -> new ServerboundUseItemPacket(InteractionHand.OFF_HAND, id, yaw, pitch));
            fireworkTimer.reset();
        } else if (inventorySwap.get() && (firework = InventoryUtil.findItemInventorySlot(Items.FIREWORK_ROCKET)) != -1) {
            InventoryUtil.inventorySwap(firework, ((InventoryAccessor)mc.player.getInventory()).getSelectedSlot());
            sendSequencedPacket(id -> new ServerboundUseItemPacket(InteractionHand.MAIN_HAND, id, yaw, pitch));
            InventoryUtil.inventorySwap(firework, ((InventoryAccessor)mc.player.getInventory()).getSelectedSlot());
            sendPacket(new ServerboundContainerClosePacket(mc.player.containerMenu.containerId));
            fireworkTimer.reset();
        } else if ((firework = InventoryUtil.findItem(Items.FIREWORK_ROCKET)) != -1) {
            int old = ((InventoryAccessor)mc.player.getInventory()).getSelectedSlot();
            InventoryUtil.switchToSlot(firework);
            sendSequencedPacket(id -> new ServerboundUseItemPacket(InteractionHand.MAIN_HAND, id, yaw, pitch));
            InventoryUtil.switchToSlot(old);
            fireworkTimer.reset();
        }
    }
    public void sendSequencedPacket(PredictiveAction packetCreator) {
        if (mc.getConnection() == null || mc.level == null) return;
        try (BlockStatePredictionHandler pendingUpdateManager = mc.level.getBlockStatePredictionHandler().startPredicting()) {
            int i = pendingUpdateManager.currentSequence();
            mc.getConnection().send(packetCreator.predict(i));
        }
    }
    private boolean wantToMove() {
        return mc.options.keyUp.isDown() || mc.options.keyDown.isDown() || mc.options.keyLeft.isDown() || mc.options.keyRight.isDown() || mc.options.keyJump.isDown() || mc.options.keyShift.isDown();
    }
    public enum Mode {
        Legit,
        GrimDurability
    }
    public enum FireWorkMode {
        Auto,
        Delay,
        None
    }
    public boolean isMoving() {
        if (mc.player == null) return false;
        return mc.player.input.keyPresses.forward() || mc.player.input.keyPresses.backward() || mc.player.input.keyPresses.left() || mc.player.input.keyPresses.right();
    }
    public float getSprintYaw(float yaw) {
        if (mc.options.keyUp.isDown() && !mc.options.keyDown.isDown()) {
            if (mc.options.keyLeft.isDown() && !mc.options.keyRight.isDown()) {
                yaw -= 45f;
            } else if (mc.options.keyRight.isDown() && !mc.options.keyLeft.isDown()) {
                yaw += 45f;
            }
        } else if (mc.options.keyDown.isDown() && !mc.options.keyUp.isDown()) {
            yaw += 180f;
            if (mc.options.keyLeft.isDown() && !mc.options.keyRight.isDown()) {
                yaw += 45f;
            } else if (mc.options.keyRight.isDown() && !mc.options.keyLeft.isDown()) {
                yaw -= 45f;
            }
        } else if (mc.options.keyLeft.isDown() && !mc.options.keyRight.isDown()) {
            yaw -= 90f;
        } else if (mc.options.keyRight.isDown() && !mc.options.keyLeft.isDown()) {
            yaw += 90f;
        }
        return yaw;
    }
    private float getPitch(float pitch) {
        if (!(mc.screen instanceof ChatScreen)) {
            boolean pressingWASD = mc.options.keyUp.isDown() || mc.options.keyDown.isDown() || mc.options.keyLeft.isDown() || mc.options.keyRight.isDown();
            if (mc.options.keyShift.isDown() && mc.options.keyJump.isDown()) {
                pitch = -3;
            } else if (mc.options.keyJump.isDown()) {
                if (pressingWASD) {
                    pitch = -45;
                } else {
                    pitch = -90;
                }
            } else if (mc.options.keyShift.isDown()) {
                if (pressingWASD) {
                    pitch = 45;
                } else {
                    pitch = 90;
                }
            }
            if (pressingWASD && !mc.options.keyShift.isDown() && !mc.options.keyJump.isDown()) {
                pitch = -1.9f;
            }
        }
        return pitch;
    }
    public boolean isPhased() {
        return mc.level.noCollision(mc.player,mc.player.getBoundingBox());
    }
}
