package com.codigohasta.addon.utils.alien;

import com.codigohasta.addon.mixin.InventoryAccessor;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.AirBlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.s2c.play.BlockBreakingProgressS2CPacket;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class AlienBreakManager {
    private static final MinecraftClient mc = MinecraftClient.getInstance();

    public final ConcurrentHashMap<Integer, BreakData> breakMap = new ConcurrentHashMap<>();
    public final ConcurrentHashMap<Integer, BreakData> doubleMap = new ConcurrentHashMap<>();

    public static AlienBreakManager INSTANCE;

    public double damageMultiplier = 1.0;
    public boolean detectDouble = true;
    public double minTimeout = 0.5;
    public double doubleMineTimeout = 2.0;
    public double breakTimeout = 2.0;

    public AlienBreakManager() {
        INSTANCE = this;
        MeteorClient.EVENT_BUS.subscribe(this);
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        breakMap.clear();
        doubleMap.clear();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.world == null || mc.player == null) return;

        if (detectDouble) {
            for (int i : doubleMap.keySet()) {
                BreakData breakData = doubleMap.get(i);
                if (breakData == null || breakData.getEntity() == null
                    || mc.world.isAir(breakData.pos)
                    || breakData.timer.passedMs(
                    Math.max(minTimeout * 1000.0, breakData.breakTime * doubleMineTimeout))) {
                    doubleMap.remove(i);
                }
            }
        }

        for (BreakData breakData : breakMap.values()) {
            breakData.breakTime = Math.max(getBreakTime(breakData.pos, false), 50.0);
            if (unbreakable(breakData.pos)) {
                breakData.fade.setLength(0L);
                breakData.complete = false;
                breakData.failed = true;
            } else if (mc.world.isAir(breakData.pos)) {
                breakData.fade.setLength(0L);
                breakData.complete = true;
                breakData.failed = false;
            } else if (!breakData.complete && breakData.timer.passedMs(breakData.breakTime * breakTimeout)) {
                breakData.fade.setLength(0L);
                breakData.failed = true;
            } else {
                breakData.fade.setLength((long) breakData.breakTime);
            }
        }
    }

    @EventHandler
    private void onPacket(PacketEvent.Receive event) {
        if (mc.world == null || mc.player == null) return;

        if (event.packet instanceof BlockBreakingProgressS2CPacket packet) {
            if (packet.getPos() == null) return;

            BreakData breakData = new BreakData(packet.getPos(), packet.getEntityId(), false);
            if (breakData.getEntity() == null) return;

            if (MathHelper.sqrt((float) breakData.getEntity().getEyePos()
                .squaredDistanceTo(packet.getPos().toCenterPos())) > 8.0F) return;

            if (detectDouble && packet.getProgress() != 255) {
                if (packet.getProgress() != 0) {
                    BreakData doublePos = doubleMap.get(packet.getEntityId());
                    if (doublePos != null) {
                        doublePos.pos = packet.getPos();
                        doublePos.timer.reset();
                    } else if (!unbreakable(packet.getPos())) {
                        doubleMap.put(packet.getEntityId(), new BreakData(packet.getPos(), packet.getEntityId(), true));
                    }
                    return;
                }

                BreakData doublePos = doubleMap.get(packet.getEntityId());
                if (doublePos != null && doublePos.pos.equals(packet.getPos()) && !doublePos.timer.passedS(150.0)) {
                    return;
                }
            }

            BreakData current = breakMap.get(packet.getEntityId());
            if (current != null && !current.failed && current.pos.equals(packet.getPos())) {
                return;
            }

            breakMap.put(packet.getEntityId(), breakData);

            if (detectDouble && !doubleMap.containsKey(packet.getEntityId()) && !unbreakable(packet.getPos())) {
                doubleMap.put(packet.getEntityId(), new BreakData(packet.getPos(), packet.getEntityId(), true));
            }
        }
    }

    public boolean isMining(BlockPos pos) {
        return isMining(pos, true);
    }

    public boolean isMining(BlockPos pos, boolean self) {
        for (BreakData breakData : breakMap.values()) {
            if (breakData.getEntity() != null
                && !breakData.failed
                && breakData.pos.equals(pos)) {
                return true;
            }
        }
        return false;
    }

    public static boolean unbreakable(BlockPos pos) {
        BlockState state = mc.world.getBlockState(pos);
        return state.getHardness(mc.world, pos) < 0;
    }

    public static double getBreakTime(BlockPos pos, boolean extraBreak) {
        int slot = getTool(pos);
        if (slot == -1) {
            slot = ((InventoryAccessor) mc.player.getInventory()).getSelectedSlot();
        }
        return getBreakTime(pos, slot, extraBreak ? 1.0 : (INSTANCE != null ? INSTANCE.damageMultiplier : 1.0));
    }

    public static int getTool(BlockPos pos) {
        AtomicInteger slot = new AtomicInteger(-1);
        float currentFastest = 1.0F;
        RegistryEntry<Enchantment> effEntry = mc.world.getRegistryManager()
            .getOrThrow(RegistryKeys.ENCHANTMENT)
            .getOrThrow(Enchantments.EFFICIENCY);
        for (Map.Entry<Integer, ItemStack> entry : AlienInventoryUtil.getInventoryAndHotbarSlots().entrySet()) {
            if (!(entry.getValue().getItem() instanceof AirBlockItem)) {
                float digSpeed = EnchantmentHelper.getLevel(effEntry, entry.getValue());
                float destroySpeed = entry.getValue().getMiningSpeedMultiplier(mc.world.getBlockState(pos));
                if (digSpeed + destroySpeed > currentFastest) {
                    currentFastest = digSpeed + destroySpeed;
                    slot.set(entry.getKey());
                }
            }
        }
        return slot.get();
    }

    public static double getBreakTime(BlockPos pos, int slot, double damage) {
        return 1.0F / getBlockStrength(pos, mc.player.getInventory().getStack(slot)) / 20.0F * 1000.0F * damage;
    }

    public static float getBlockStrength(BlockPos position, ItemStack itemStack) {
        BlockState state = mc.world.getBlockState(position);
        float hardness = state.getHardness(mc.world, position);
        if (hardness < 0.0F) return 0.0F;
        float i = state.isToolRequired() && !itemStack.isSuitableFor(state) ? 100.0F : 30.0F;
        return getDigSpeed(state, itemStack) / hardness / i;
    }

    public static float getDigSpeed(BlockState state, ItemStack itemStack) {
        float digSpeed = getDestroySpeed(state, itemStack);
        if (digSpeed > 1.0F) {
            RegistryEntry<Enchantment> effEntry = mc.world.getRegistryManager()
                .getOrThrow(RegistryKeys.ENCHANTMENT)
                .getOrThrow(Enchantments.EFFICIENCY);
            int efficiencyModifier = EnchantmentHelper.getLevel(effEntry, itemStack);
            if (efficiencyModifier > 0 && !itemStack.isEmpty()) {
                digSpeed += (float) (StrictMath.pow(efficiencyModifier, 2.0) + 1.0);
            }
        }
        return digSpeed < 0.0F ? 0.0F : digSpeed;
    }

    public static float getDestroySpeed(BlockState state, ItemStack itemStack) {
        float destroySpeed = 1.0F;
        if (itemStack != null && !itemStack.isEmpty()) {
            destroySpeed *= itemStack.getMiningSpeedMultiplier(state);
        }
        return destroySpeed;
    }

    public static class BreakData {
        public BlockPos pos;
        private final int entityId;
        public final AlienFadeUtils fade;
        public final AlienTimer timer;
        public double breakTime;
        public boolean failed = false;
        public boolean complete = false;

        public BreakData(BlockPos pos, int entityId, boolean extraBreak) {
            this.pos = pos;
            this.entityId = entityId;
            this.breakTime = Math.max(getBreakTime(pos, extraBreak), 50.0);
            this.fade = new AlienFadeUtils((long) this.breakTime);
            this.timer = new AlienTimer();
        }

        public Entity getEntity() {
            if (mc.world == null) return null;
            Entity entity = mc.world.getEntityById(this.entityId);
            return entity instanceof PlayerEntity ? entity : null;
        }
    }
}
