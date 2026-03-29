package com.codigohasta.addon.modules;

import com.codigohasta.addon.AddonTemplate;
import com.codigohasta.addon.mixin.InventoryAccessor;
import com.codigohasta.addon.utils.RotationManager;
import com.codigohasta.addon.utils.player.MoveUtils;
import com.codigohasta.addon.utils.rotation.Priority;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.FireworkRocketEntity;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector2f;

import java.util.HashSet;
import java.util.Set;

public class Testfly extends Module {

    public enum Mode {
        Control, Firework
    }

    public enum SwapMode {
        Silent, InvSwitch
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>().name("Mode").defaultValue(Mode.Firework).build());
    private final Setting<SwapMode> swapMode = sgGeneral.add(new EnumSetting.Builder<SwapMode>().name("SwapMode").defaultValue(SwapMode.InvSwitch).build());
    private final Setting<Boolean> armored = sgGeneral.add(new BoolSetting.Builder().name("Armored").defaultValue(true).build());
    private final Setting<Boolean> visualSpoof = sgGeneral.add(new BoolSetting.Builder().name("VisualSpoof").defaultValue(true).build());
    private final Setting<Boolean> autoStart = sgGeneral.add(new BoolSetting.Builder().name("AutoStart").defaultValue(true).visible(() -> mode.get() == Mode.Firework).build());
    
    private final Setting<Double> horizontalSpeed = sgGeneral.add(new DoubleSetting.Builder().name("HorizontalSpeed").defaultValue(1.35).min(0.1).max(5.0).sliderMax(5.0).visible(() -> mode.get() == Mode.Firework).build());
    private final Setting<Double> verticalSpeed = sgGeneral.add(new DoubleSetting.Builder().name("VerticalSpeed").defaultValue(0.8).min(0.1).max(2.0).sliderMax(2.0).visible(() -> mode.get() == Mode.Firework).build());
    private final Setting<Double> accel = sgGeneral.add(new DoubleSetting.Builder().name("Acceleration").defaultValue(0.35).min(0.05).max(1.0).sliderMax(1.0).visible(() -> mode.get() == Mode.Firework).build());
    private final Setting<Integer> boostDelay = sgGeneral.add(new IntSetting.Builder().name("BoostDelay").defaultValue(9).min(2).max(50).sliderMax(50).visible(() -> mode.get() == Mode.Firework).build());
    private final Setting<Integer> rotationSpeed = sgGeneral.add(new IntSetting.Builder().name("RotationSpeed").defaultValue(10).min(1).max(10).sliderMax(10).visible(() -> mode.get() == Mode.Firework).build());

    private int offGroundTicks;
    private long timer;
    private final Set<Integer> fireworkIds = new HashSet<>();
    private static final int CHEST_ARMOR_MENU_SLOT = 6; // Fabric 原版玩家背包中胸甲槽位固定是 6

    public Testfly() {
        super(AddonTemplate.CATEGORY, "Testfly", "纯净移植版 Grim 甲飞测试模块");
    }

    @Override
    public void onActivate() {
        offGroundTicks = 0;
        timer = 0;
        fireworkIds.clear();
    }

    @Override
    public void onDeactivate() {
        timer = 0;
        fireworkIds.clear();
    }

    public boolean isFirework(FireworkRocketEntity firework) {
        return fireworkIds.contains(firework.getId());
    }

    public boolean shouldVisualSpoof() {
        return isActive() && visualSpoof.get();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        if (mode.get() == Mode.Firework) {
            // 清理无效的烟花 ID
            fireworkIds.removeIf(id -> mc.world.getEntityById(id) == null);

            if (!canGlide()) {
                timer = 0;
                return;
            }

            if (!mc.player.isGliding()) {
                if (!mc.player.isOnGround()) {
                    offGroundTicks++;
                    if (autoStart.get() && !mc.player.isTouchingWater()) {
                        if (armored.get()) {
                            runArmoredFly(); // 这个是甲飞()
                        } else {
                            if (startFallFlying() && hasInput()) {
                                useFirework(true);
                            }
                        }
                    }
                    return;
                } else {
                    offGroundTicks = 0;
                }
            } else {
                // 如果已经处于飞行状态 (类似原版 PlayerTickEvent 阶段)
                applyMotion();

                // 作者原话：我操了家人我想不到更好的办法在第一个tick放烟花的办法了。。
                if (armored.get() && offGroundTicks == 1) {
                    useFirework(true);
                }
                
                if (!armored.get()) {
                    useFirework(false);
                }
            }
        }
    }

    private boolean startFallFlying() {
        mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
        mc.player.startGliding(); // 强制本地开启滑翔状态以避免抽搐
        return true;
    }

    private boolean canGlide() {
        if (armored.get() && mode.get() == Mode.Firework && hasElytra()) {
            return true;
        }
        return mc.player.getEquippedStack(EquipmentSlot.CHEST).isOf(Items.ELYTRA);
    }

    private void applyMotion() {
        if (!hasInput()) {
            mc.player.setVelocity(0, 0, 0);
            mc.player.fallDistance = 0.0f;
            return;
        }

        Vec3d moveDir = getMoveDirection();
        Vec3d motion = mc.player.getVelocity();
        boolean up = mc.options.jumpKey.isPressed();
        boolean down = mc.options.sneakKey.isPressed();
        boolean sprinting = mc.player.isSprinting() || mc.options.sprintKey.isPressed();

        double speedMul = sprinting ? 1.35 : 1.0;
        double targetX = moveDir.x * horizontalSpeed.get() * speedMul;
        double targetZ = moveDir.z * horizontalSpeed.get() * speedMul;
        double targetY = up == down ? 0.0 : (up ? verticalSpeed.get() : -verticalSpeed.get());
        
        double factor = Math.max(accel.get(), 0.85);
        double newX = MathHelper.lerp(factor, motion.x, targetX);
        double newY = MathHelper.lerp(factor, motion.y, targetY);
        double newZ = MathHelper.lerp(factor, motion.z, targetZ);

        mc.player.setVelocity(newX, newY, newZ);
        mc.player.fallDistance = 0.0f;
        rotateToMovement(newX, newY, newZ);
    }

    private void rotateToMovement(double x, double y, double z) {
        double horizontal = Math.sqrt(x * x + z * z);
        if (horizontal < 1.0E-5 && Math.abs(y) < 1.0E-5) return;
        float yaw = (float) Math.toDegrees(Math.atan2(z, x)) - 90.0f;
        float pitch = (float) (-Math.toDegrees(Math.atan2(y, Math.max(horizontal, 1.0E-5))));
        
        // 调用你移植的 RotationManager，这里遵从原作者传入 OFF (或根据实际测试改为 ON)
        RotationManager.INSTANCE.setRotations(
            new Vector2f(MathHelper.wrapDegrees(yaw), MathHelper.clamp(pitch, -90.0f, 90.0f)), 
            rotationSpeed.get(), 
            false, 
            Priority.Highest
        );
    }

    private boolean hasInput() {
        return MoveUtils.isMoving() || mc.options.jumpKey.isPressed() || mc.options.sneakKey.isPressed();
    }

    private Vec3d getMoveDirection() {
        float forward = 0.0f;
        float strafe = 0.0f;

        try {
            // 1. 通过反射获取 Input 类中的 movementVector 字段
            java.lang.reflect.Field vectorField = net.minecraft.client.input.Input.class.getDeclaredField("movementVector");
            
            // 2. 强行突破 protected 权限限制
            vectorField.setAccessible(true);
            
            // 3. 拿到 mc.player.input 实例中的 Vector 变量 (通常为 org.joml.Vector2f)
            Object movementVector = vectorField.get(mc.player.input);
            
            // 4. 读取里面的 x 和 y 属性
            java.lang.reflect.Field fieldX = movementVector.getClass().getField("x");
            java.lang.reflect.Field fieldY = movementVector.getClass().getField("y");
            
            forward = fieldY.getFloat(movementVector); // y 对应 forward (前后)
            strafe = fieldX.getFloat(movementVector);  // x 对应 strafe (左右)
            
        } catch (Exception e) {
            e.printStackTrace(); // 如果反射失败，在控制台打印错误
        }

        if (forward == 0.0f && strafe == 0.0f) {
            return Vec3d.ZERO;
        }

        double yawRad = Math.toRadians(mc.player.getYaw());
        double sin = Math.sin(yawRad);
        double cos = Math.cos(yawRad);

        double x = forward * -sin + strafe * cos;
        double z = forward * cos + strafe * sin;

        double length = Math.sqrt(x * x + z * z);
        if (length <= 0.0) return Vec3d.ZERO;
        
        return new Vec3d(x / length, 0.0, z / length);
    }

    private void useFirework(boolean ignoreDelay) {
        // 原作延时单位是 Tick，我们换算成毫秒 (1 tick = 50ms)
        long currentMs = System.currentTimeMillis();
        boolean delayPassed = (currentMs - timer) >= (boostDelay.get() * 50L);

        if (hasInput() && (ignoreDelay || delayPassed)) {
            FindItemResult rocket = swapMode.get() == SwapMode.Silent ? InvUtils.findInHotbar(Items.FIREWORK_ROCKET) : InvUtils.find(Items.FIREWORK_ROCKET);
            if (!rocket.found()) return;

            // 获取你 Mixin 里面的当前手持快捷栏
            int oldHotbarSlot = ((InventoryAccessor) mc.player.getInventory()).getSelectedSlot();
            boolean swapped = false;

            if (swapMode.get() == SwapMode.Silent) {
                if (rocket.isHotbar()) {
                    InvUtils.swap(rocket.slot(), true);
                    swapped = true;
                }
            } else {
                // 硬切物品 (InvSwitch) : 必须用到 SlotActionType.SWAP 强行把背包的烟花换到当前手里
                mc.interactionManager.clickSlot(mc.player.playerScreenHandler.syncId, toContainerSlot(rocket.slot()), oldHotbarSlot, SlotActionType.SWAP, mc.player);
                swapped = true;
            }

            // 使用物品 (放烟花)
            mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
            timer = currentMs; // 重置冷却
            mc.player.swingHand(Hand.MAIN_HAND);
            updateFireworks();

            // 还原物品
            if (swapped) {
                if (swapMode.get() == SwapMode.Silent) {
                    InvUtils.swapBack();
                } else {
                    mc.interactionManager.clickSlot(mc.player.playerScreenHandler.syncId, toContainerSlot(rocket.slot()), oldHotbarSlot, SlotActionType.SWAP, mc.player);
                }
            }
        }
    }

    private boolean hasElytra() {
        return findElytra() != -1;
    }

    private int findElytra() {
        return InvUtils.find(Items.ELYTRA).slot();
    }

    private int toContainerSlot(int slot) {
        // Fabric/Meteor的找槽位逻辑：如果是快捷栏 (0-8)，它在服务端的 syncId 中对应 36-44
        if (slot < 9) {
            return slot + 36;
        }
        return slot;
    }

    private void runArmoredFly() { // 这个是甲飞()
        if (!mc.player.playerScreenHandler.getCursorStack().isEmpty()) return; // 鼠标上必须为空

        int elytraSlot = findElytra();
        if (elytraSlot < 0) return;

        int elytraContainerSlot = toContainerSlot(elytraSlot);

        swapChestSlotWith(elytraContainerSlot);

        startFallFlying();
        useFirework(false);

        swapChestSlotWith(elytraContainerSlot);
    }

    private void swapChestSlotWith(int containerSlot) {
        int syncId = mc.player.playerScreenHandler.syncId; // 必须使用默认背包的 syncId
        // Fabric 里面的 3次纯正点击换装
        mc.interactionManager.clickSlot(syncId, containerSlot, 0, SlotActionType.PICKUP, mc.player);
        mc.interactionManager.clickSlot(syncId, CHEST_ARMOR_MENU_SLOT, 0, SlotActionType.PICKUP, mc.player);
        mc.interactionManager.clickSlot(syncId, containerSlot, 0, SlotActionType.PICKUP, mc.player);
    }

    private void updateFireworks() {
        for (Entity entity : mc.world.getEntities()) {
            if (entity instanceof FireworkRocketEntity firework && firework.squaredDistanceTo(mc.player) <= 64.0) {
                fireworkIds.add(firework.getId());
            }
        }
    }
}