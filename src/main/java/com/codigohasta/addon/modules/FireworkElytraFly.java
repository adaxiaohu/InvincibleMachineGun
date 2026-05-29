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
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.network.PendingUpdateManager;
import net.minecraft.client.network.SequencedPacketCreator;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.projectile.FireworkRocketEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.CloseHandledScreenC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInputC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.PlayerInput;

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
        mc.options.jumpKey.setPressed(false);
        clearInputTicks = 0;
        forceJumpInput = false;
        if (pressSneak.get()) {
            mc.options.sneakKey.setPressed(true);
        }
        if (releaseSneak.get()) {
            long delay = releaseDelay.get();
            java.util.Timer timer = new java.util.Timer();
            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    mc.execute(() -> {
                        mc.options.sneakKey.setPressed(false);
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
        if (mc.currentScreen instanceof ChatScreen) {
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
        ((IVec3d) mc.player.getVelocity()).setY(f);
    }
    private void setX(double f) {
        ((IVec3d) mc.player.getVelocity()).setX(f);
    }
    private void setZ(double f) {
        ((IVec3d) mc.player.getVelocity()).setZ(f);
    }
    @Override
    public String getInfoString() {
        if (mc.player == null || mc.world == null) return null;
        int fireworks = 0;
        if (inventorySwap.get()) {
            for (int i = 0; i < 45; ++i) {
                ItemStack stack = mc.player.getInventory().getStack(i);
                if (stack.getItem() == Items.FIREWORK_ROCKET) fireworks = fireworks + stack.getCount();
            }
        } else {
            for (int i = 0; i < 9; ++i) {
                ItemStack stack = mc.player.getInventory().getStack(i);
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
        if (mc.currentScreen != null && deBug.get()) info("screen" + mc.currentScreen.getTitle() + " " + mc.currentScreen.getClass().getSimpleName() + " " + mc.currentScreen.getClass().getSuperclass().getSimpleName() + " " + mc.currentScreen.getTitle());
        if (mc.currentScreen != null && mc.currentScreen instanceof HandledScreen<?> && !(mc.currentScreen instanceof InventoryScreen || mc.currentScreen instanceof CreativeInventoryScreen)) return;
        int elytra = InventoryUtil.findItemInventorySlot(Items.ELYTRA);
        packetDelayInt++;
        if (mode.get() == Mode.GrimDurability && elytra != -1 && packetDelayInt == packetDealy.get().intValue()) {
            clearInputTicks = 2;
        }
        yaw = getSprintYaw(mc.player.getYaw());
        pitch = getPitch(mc.player.getPitch());
        if (deBug.get()) info("Yaw: " + yaw + " Pitch: " + pitch);
        if (mode.get() == Mode.GrimDurability) {
            if (GlobalSetting.INSTANCE.moveFix.get()) {
                Rotation.snapAt(yaw, pitch);
            } else {
                mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.Full(mc.player.getX(), mc.player.getY(), mc.player.getZ(), yaw, pitch, mc.player.isOnGround(), mc.player.horizontalCollision));
            }
        }
        boolean hasFirework = false;
        if (checkFirework.get()) {
            for (Entity entity : mc.world.getEntities()) {
                if (entity instanceof FireworkRocketEntity firework) {
                    if (firework.getOwner() == mc.player) {
                        hasFirework = true;
                    }
                }
            }
        }
        isUsingFirework = hasFirework;
        ItemStack chestStack = mc.player.getEquippedStack(EquipmentSlot.CHEST);
        boolean wearingElytra = chestStack.getItem() == Items.ELYTRA && chestStack.getDamage() < chestStack.getMaxDamage() - 1;
        if (mode.get() == Mode.GrimDurability) {
            if (elytra != -1 && packetDelayInt > packetDealy.get()) {
                mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, elytra, 0, SlotActionType.PICKUP, mc.player);
                mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, 6, 0, SlotActionType.PICKUP, mc.player);
                mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, elytra, 0, SlotActionType.PICKUP, mc.player);
                if (!mc.player.isOnGround()) {
                    sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
                    mc.player.startGliding();
                }
                if (!hasFirework && fireWorkMode.get() == FireWorkMode.Auto) {
                    offFirework();
                } else if (fireWorkMode.get() == FireWorkMode.Delay && wantToMove()){
                    if (!checkFirework.get() || !isUsingFirework){
                        offFirework();
                    }
                }
                mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, elytra, 0, SlotActionType.PICKUP, mc.player);
                mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, 6, 0, SlotActionType.PICKUP, mc.player);
                mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, elytra, 0, SlotActionType.PICKUP, mc.player);
                forceJumpInput = true;
                packetDelayInt = 0;
            }
        } else {
            if (wearingElytra && !mc.player.isGliding() && !mc.player.isOnGround()) {
                sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
                mc.player.startGliding();
            }
            if (mode.get() == Mode.Legit && wearingElytra && mc.player.isGliding() && !mc.player.isOnGround() && unbreaking.get() && swapTimer.passedMs(fakeDelay.get())) {
                mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, 6, 0, SlotActionType.PICKUP, mc.player);
                mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, 6, 0, SlotActionType.PICKUP, mc.player);
                sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
                mc.player.startGliding();
                swapTimer.reset();
            }
            if (wearingElytra && mc.player.isGliding()) {
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
        if (mc.player.getMainHandStack().getItem() == Items.FIREWORK_ROCKET) {
            sendSequencedPacket(id -> new PlayerInteractItemC2SPacket(Hand.MAIN_HAND, id, yaw, pitch));
            fireworkTimer.reset();
        } else if (mc.player.getOffHandStack().getItem() == Items.FIREWORK_ROCKET) {
            sendSequencedPacket(id -> new PlayerInteractItemC2SPacket(Hand.OFF_HAND, id, yaw, pitch));
            fireworkTimer.reset();
        } else if (inventorySwap.get() && (firework = InventoryUtil.findItemInventorySlot(Items.FIREWORK_ROCKET)) != -1) {
            InventoryUtil.inventorySwap(firework, ((InventoryAccessor)mc.player.getInventory()).getSelectedSlot());
            sendSequencedPacket(id -> new PlayerInteractItemC2SPacket(Hand.MAIN_HAND, id, yaw, pitch));
            InventoryUtil.inventorySwap(firework, ((InventoryAccessor)mc.player.getInventory()).getSelectedSlot());
            sendPacket(new CloseHandledScreenC2SPacket(mc.player.currentScreenHandler.syncId));
            fireworkTimer.reset();
        } else if ((firework = InventoryUtil.findItem(Items.FIREWORK_ROCKET)) != -1) {
            int old = ((InventoryAccessor)mc.player.getInventory()).getSelectedSlot();
            InventoryUtil.switchToSlot(firework);
            sendSequencedPacket(id -> new PlayerInteractItemC2SPacket(Hand.MAIN_HAND, id, yaw, pitch));
            InventoryUtil.switchToSlot(old);
            fireworkTimer.reset();
        }
    }
    public void sendSequencedPacket(SequencedPacketCreator packetCreator) {
        if (mc.getNetworkHandler() == null || mc.world == null) return;
        try (PendingUpdateManager pendingUpdateManager = mc.world.getPendingUpdateManager().incrementSequence()) {
            int i = pendingUpdateManager.getSequence();
            mc.getNetworkHandler().sendPacket(packetCreator.predict(i));
        }
    }
    private boolean wantToMove() {
        return mc.options.forwardKey.isPressed() || mc.options.backKey.isPressed() || mc.options.leftKey.isPressed() || mc.options.rightKey.isPressed() || mc.options.jumpKey.isPressed() || mc.options.sneakKey.isPressed();
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
        return mc.player.input.playerInput.forward() || mc.player.input.playerInput.backward() || mc.player.input.playerInput.left() || mc.player.input.playerInput.right();
    }
    public float getSprintYaw(float yaw) {
        if (mc.options.forwardKey.isPressed() && !mc.options.backKey.isPressed()) {
            if (mc.options.leftKey.isPressed() && !mc.options.rightKey.isPressed()) {
                yaw -= 45f;
            } else if (mc.options.rightKey.isPressed() && !mc.options.leftKey.isPressed()) {
                yaw += 45f;
            }
        } else if (mc.options.backKey.isPressed() && !mc.options.forwardKey.isPressed()) {
            yaw += 180f;
            if (mc.options.leftKey.isPressed() && !mc.options.rightKey.isPressed()) {
                yaw += 45f;
            } else if (mc.options.rightKey.isPressed() && !mc.options.leftKey.isPressed()) {
                yaw -= 45f;
            }
        } else if (mc.options.leftKey.isPressed() && !mc.options.rightKey.isPressed()) {
            yaw -= 90f;
        } else if (mc.options.rightKey.isPressed() && !mc.options.leftKey.isPressed()) {
            yaw += 90f;
        }
        return yaw;
    }
    private float getPitch(float pitch) {
        if (!(mc.currentScreen instanceof ChatScreen)) {
            boolean pressingWASD = mc.options.forwardKey.isPressed() || mc.options.backKey.isPressed() || mc.options.leftKey.isPressed() || mc.options.rightKey.isPressed();
            if (mc.options.sneakKey.isPressed() && mc.options.jumpKey.isPressed()) {
                pitch = -3;
            } else if (mc.options.jumpKey.isPressed()) {
                if (pressingWASD) {
                    pitch = -45;
                } else {
                    pitch = -90;
                }
            } else if (mc.options.sneakKey.isPressed()) {
                if (pressingWASD) {
                    pitch = 45;
                } else {
                    pitch = 90;
                }
            }
            if (pressingWASD && !mc.options.sneakKey.isPressed() && !mc.options.jumpKey.isPressed()) {
                pitch = -1.9f;
            }
        }
        return pitch;
    }
    public boolean isPhased() {
        return mc.world.canCollide(mc.player,mc.player.getBoundingBox());
    }
}
