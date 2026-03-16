package com.codigohasta.addon.modules;

import net.minecraft.util.math.Vec3d;

import com.codigohasta.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.mixininterface.IPlayerInteractEntityC2SPacket;
import meteordevelopment.meteorclient.mixininterface.IPlayerMoveC2SPacket;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.item.Items;

import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;

import java.lang.reflect.Field;

public class AdvancedCriticals extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    // --- Alien 的原版设置 ---
    private final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("mode")
        .description("Alien 的发包模式。")
        .defaultValue(Mode.Vanilla)
        .build()
    );

    private final Setting<Boolean> noCrystal = sgGeneral.add(new BoolSetting.Builder()
        .name("no-crystal")
        .description("Swap模式下不攻击水晶。")
        .defaultValue(true)
        .visible(() -> mode.get() == Mode.Swap)
        .build()
    );

    private final Setting<Boolean> inventorySwap = sgGeneral.add(new BoolSetting.Builder()
        .name("inventory-swap")
        .description("从背包中查找重锤（不仅是快捷栏）。")
        .defaultValue(false)
        .visible(() -> mode.get() == Mode.Swap)
        .build()
    );

    private final Setting<Boolean> onlyGround = sgGeneral.add(new BoolSetting.Builder()
        .name("only-ground")
        .description("仅在地面或飞行时触发。")
        .defaultValue(true)
        .visible(() -> mode.get() == Mode.Vanilla)
        .build()
    );

    private final Setting<Double> height = sgGeneral.add(new DoubleSetting.Builder()
        .name("height")
        .description("伪造高度。Alien 默认 25，最高 2000。")
        .defaultValue(25.0)
        .min(0.0)
        .max(2000.0)
        .visible(() -> mode.get() == Mode.Vanilla)
        .build()
    );

    // 内部变量
    private boolean ignore = false;
    private PlayerInteractEntityC2SPacket lastPacket = null;
    private static Field onGroundField; // 反射字段缓存

    public AdvancedCriticals() {
        super(AddonTemplate.CATEGORY, "重锤暴击", "重锤特效手动版，需要自己拿重锤点");
        
        // 初始化反射字段 (onGround)
        try {
            // 尝试查找名为 onGround 的字段 (Yarn 映射)
            for (Field field : PlayerMoveC2SPacket.class.getDeclaredFields()) {
                if (field.getType() == boolean.class && (field.getName().equals("onGround") || field.getName().equals("field_12951"))) {
                    field.setAccessible(true);
                    onGroundField = field;
                    break;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPacketSend(PacketEvent.Send event) {
        if (mc.player == null || mc.world == null) return;

        // --- Alien Vanilla Mode (核心暴力逻辑) ---
        if (mode.get() == Mode.Vanilla) {
            if (event.packet instanceof PlayerInteractEntityC2SPacket packet) {
                if (!(mc.player.getMainHandStack().getItem().toString().contains("mace"))) return;
                if (!isAttack(packet)) return;

                Entity entity = getEntity(packet);
                if (entity instanceof EndCrystalEntity) return;

                if (onlyGround.get() && !mc.player.isOnGround() && !mc.player.getAbilities().flying) return;
                if (mc.player.isInLava() || mc.player.isSubmergedInWater()) return;
                if (entity == null) return;

                // Alien 的 4+1+1 暴力发包循环
                for (int i = 0; i < 4; i++) {
                    this.sendFakeY(0.0);
                }
                this.sendFakeY(this.height.get());
                this.sendFakeY(0.0);
            }
        } 
        // --- Alien NCP Mode (反射修复版) ---
        else if (mode.get() == Mode.NCP) {
            if (event.packet instanceof PlayerMoveC2SPacket) {
                // 使用反射修改 onGround = false
                if (onGroundField != null) {
                    try {
                        onGroundField.setBoolean(event.packet, false);
                    } catch (IllegalAccessException e) {
                        e.printStackTrace();
                    }
                }
            }
        } 
        // --- Alien Swap Mode (自动切刀逻辑) ---
        else if (mode.get() == Mode.Swap) {
            if (event.isCancelled()) return;

            int slot = this.getMaceSlot();
            if (slot == -1) return;
            if (this.ignore) return;

            if (event.packet instanceof PlayerInteractEntityC2SPacket packet && isAttack(packet)) {
                if (this.noCrystal.get() && getEntity(packet) instanceof EndCrystalEntity) {
                    return;
                }

                this.lastPacket = packet;
                this.ignore = true;
                this.doSpoof();
                this.ignore = false;
                event.cancel();
            }
        }
    }

    // Alien 的 doSpoof 逻辑
    private void doSpoof() {
        if (this.lastPacket != null) {
            int slot = this.getMaceSlot();
            if (slot != -1) {
                int old = ((com.codigohasta.addon.mixin.InventoryAccessor) mc.player.getInventory()).getSelectedSlot();
                this.doSwap(slot);
                mc.getNetworkHandler().sendPacket(this.lastPacket);
                if (this.inventorySwap.get()) {
                    this.doSwap(slot);
                } else {
                    this.doSwap(old);
                }
            }
        }
    }

    private void doSwap(int slot) {
        if (this.inventorySwap.get()) {
            InvUtils.swap(slot, true);
        } else {
            InvUtils.swap(slot, false);
        }
    }

    private int getMaceSlot() {
        FindItemResult result;
        if (this.inventorySwap.get()) {
            result = InvUtils.find(itemStack -> itemStack.getItem() == Items.MACE);
        } else {
            result = InvUtils.findInHotbar(Items.MACE);
        }
        return result.found() ? result.slot() : -1;
    }

    // Alien 的 sendFakeY 逻辑 (1.21.4 构造器适配)
    private void sendFakeY(double offset) {
        PlayerMoveC2SPacket packet = new PlayerMoveC2SPacket.PositionAndOnGround(
            mc.player.getX(), 
            mc.player.getY() + offset, 
            mc.player.getZ(), 
            false, 
            mc.player.horizontalCollision
        );
        ((IPlayerMoveC2SPacket) packet).meteor$setTag(1337);
        mc.getNetworkHandler().sendPacket(packet);
    }

    // 获取实体 (使用 Meteor Mixin)
    private Entity getEntity(PlayerInteractEntityC2SPacket packet) {
        return ((IPlayerInteractEntityC2SPacket) packet).meteor$getEntity();
    }

    // 判断是否为攻击包 (解决 1.21.4 枚举可见性问题)
    private boolean isAttack(PlayerInteractEntityC2SPacket packet) {
        IPlayerInteractEntityC2SPacket accessor = (IPlayerInteractEntityC2SPacket) packet;
        return String.valueOf(accessor.meteor$getType()).equals("ATTACK");
    }

    public enum Mode {
        Vanilla,
        NCP,
        Swap
    }
}