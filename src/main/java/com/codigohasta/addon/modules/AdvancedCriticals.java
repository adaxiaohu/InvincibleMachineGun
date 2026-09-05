package com.codigohasta.addon.modules;

import net.minecraft.world.phys.Vec3;

import com.codigohasta.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.mixininterface.IServerboundMovePlayerPacket;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.item.Items;

import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;

import java.lang.reflect.Field;
import net.minecraft.network.protocol.game.ServerboundAttackPacket;

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
    private ServerboundAttackPacket lastPacket = null;
    private static Field onGroundField; // 反射字段缓存

    public AdvancedCriticals() {
        super(AddonTemplate.CATEGORY, "重锤暴击", "重锤特效手动版，需要自己拿重锤点");
        
        // 初始化反射字段 (onGround)
        try {
            // 尝试查找名为 onGround 的字段 (Yarn 映射)
            for (Field field : ServerboundMovePlayerPacket.class.getDeclaredFields()) {
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
        if (mc.player == null || mc.level == null) return;

        // --- Alien Vanilla Mode (核心暴力逻辑) ---
        if (mode.get() == Mode.Vanilla) {
            if (event.packet instanceof ServerboundAttackPacket packet) {
                if (!(mc.player.getMainHandItem().getItem().toString().contains("mace"))) return;

                Entity entity = getEntity(packet);
                if (entity instanceof EndCrystal) return;

                if (onlyGround.get() && !mc.player.onGround() && !mc.player.getAbilities().flying) return;
                if (mc.player.isInLava() || mc.player.isUnderWater()) return;
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
            if (event.packet instanceof ServerboundMovePlayerPacket) {
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

            if (event.packet instanceof ServerboundAttackPacket packet) {
                if (this.noCrystal.get() && getEntity(packet) instanceof EndCrystal) {
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
                mc.getConnection().send(this.lastPacket);
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
        ServerboundMovePlayerPacket packet = new ServerboundMovePlayerPacket.Pos(
            mc.player.getX(), 
            mc.player.getY() + offset, 
            mc.player.getZ(), 
            false, 
            mc.player.horizontalCollision
        );
        ((IServerboundMovePlayerPacket) packet).meteor$setTag(1337);
        mc.getConnection().send(packet);
    }

    // 26.1.2: 攻击包只带实体 id，实体要回世界里查
    private Entity getEntity(ServerboundAttackPacket packet) {
        return mc.level == null ? null : mc.level.getEntity(packet.entityId());
    }

    public enum Mode {
        Vanilla,
        NCP,
        Swap
    }
}