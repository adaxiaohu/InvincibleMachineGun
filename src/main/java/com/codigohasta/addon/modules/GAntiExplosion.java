package com.codigohasta.addon.modules;

import com.codigohasta.addon.AddonTemplate;
import com.codigohasta.addon.mixin.InventoryAccessor;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.meteorclient.utils.entity.DamageUtils;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.RespawnAnchorBlock;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

/**
 * GAntiExplosion - 移植自 LiquidBounce/GCore
 * 原理：通过监听服务器的实体生成/方块更新包，在爆炸前的一游戏刻预测伤害，
 * 发送多段 C04(PlayerMove) 数据包瞬移到安全点，引爆目标后再瞬间发包返回，实现服务端无爆炸伤害。
 */
public class GAntiExplosion extends Module {

    private final SettingGroup sgCrystal = settings.createGroup("Anti Crystal");
    private final SettingGroup sgAnchor = settings.createGroup("Anti Anchor");
    private final SettingGroup sgVClip = settings.createGroup("VClip Exploit Options");

    // ================= [ Crystal Settings ] =================
    private final Setting<Boolean> antiCrystal = sgCrystal.add(new BoolSetting.Builder()
        .name("enable-anti-crystal")
        .description("Prevents end crystal explosion damage via VClip.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> crystalScanBorder = sgCrystal.add(new IntSetting.Builder()
        .name("crystal-scan-border")
        .description("The radius to search for a safe spot.")
        .defaultValue(8)
        .min(1).sliderMax(15)
        .build()
    );

    private final Setting<Double> crystalHitRange = sgCrystal.add(new DoubleSetting.Builder()
        .name("crystal-hit-range")
        .description("Maximum distance you can hit the crystal from the safe spot.")
        .defaultValue(6.0)
        .min(0.1).sliderMax(6.0)
        .build()
    );

    private final Setting<Double> crystalMaxDamage = sgCrystal.add(new DoubleSetting.Builder()
        .name("crystal-max-damage")
        .description("If predicted damage is above this, the exploit will trigger.")
        .defaultValue(6.0)
        .min(0.01).sliderMax(20.0)
        .build()
    );

    // ================= [ Anchor Settings ] =================
    private final Setting<Boolean> antiAnchor = sgAnchor.add(new BoolSetting.Builder()
        .name("enable-anti-anchor")
        .description("Prevents respawn anchor explosion damage via VClip.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> anchorScanBorder = sgAnchor.add(new IntSetting.Builder()
        .name("anchor-scan-border")
        .description("The radius to search for a safe spot.")
        .defaultValue(8)
        .min(1).sliderMax(15)
        .build()
    );

    private final Setting<Double> anchorClickRange = sgAnchor.add(new DoubleSetting.Builder()
        .name("anchor-click-range")
        .description("Maximum distance to click the anchor from the safe spot.")
        .defaultValue(6.0)
        .min(0.1).sliderMax(6.0)
        .build()
    );

    private final Setting<Double> anchorMaxDamage = sgAnchor.add(new DoubleSetting.Builder()
        .name("anchor-max-damage")
        .description("If predicted damage is above this, the exploit will trigger.")
        .defaultValue(6.0)
        .min(0.01).sliderMax(20.0)
        .build()
    );

    // ================= [ VClip Exploit Settings ] =================
    private final Setting<Double> vClipStep = sgVClip.add(new DoubleSetting.Builder()
        .name("vclip-step-distance")
        .description("Distance per spoofed movement packet (bypass anti-cheat).")
        .defaultValue(8.0)
        .min(1.0).sliderMax(10.0)
        .build()
    );

    private final Setting<Integer> packetLimit = sgVClip.add(new IntSetting.Builder()
        .name("max-packets")
        .description("Maximum movement packets allowed per exploit (prevents kicks).")
        .defaultValue(40)
        .min(10).sliderMax(100)
        .build()
    );

    private final Setting<Boolean> returnBack = sgVClip.add(new BoolSetting.Builder()
        .name("return-to-original-pos")
        .description("Teleports you back to your real position after the explosion.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> notifyLimit = sgVClip.add(new BoolSetting.Builder()
        .name("notify-limit-reached")
        .description("Prints a chat message if packet limit is exceeded.")
        .defaultValue(true)
        .build()
    );

    public GAntiExplosion() {
        super(AddonTemplate.CATEGORY, "G-anti-explosion", "试验功能，目前该模块没有任何效果。理想情况是：在爆炸前的一游戏刻预测伤害，tp到别的地方，后再返回，实现服务端无爆炸伤害。看起来就像是免疫了爆炸伤害一样。");
    }

    // ================= [ Core Event Listeners ] =================

    @EventHandler
    private void onReceivePacket(PacketEvent.Receive event) {
        if (mc.player == null || mc.world == null) return;

        if (event.packet instanceof EntitySpawnS2CPacket packet && antiCrystal.get()) {
            if (packet.getEntityType() == EntityType.END_CRYSTAL) {
                // 使用 mc.execute() 将处理推迟到主线程，完美避免与渲染线程冲突 (CME)
                mc.execute(() -> handleCrystalExploit(packet));
            }
        }
        else if (event.packet instanceof BlockUpdateS2CPacket packet && antiAnchor.get()) {
            BlockState state = packet.getState(); 
            if (state.getBlock() == Blocks.RESPAWN_ANCHOR) {
                int charges = state.get(RespawnAnchorBlock.CHARGES);
                // 只在重生锚刚被放置（或者被耗尽）时触发
                // 这意味着这是一个“主动截胡”模块，敌人放锚，你自动瞬移过去帮他点满炸死他
                if (charges == 0) { 
                    mc.execute(() -> handleAnchorExploit(packet));
                }
            }
        }
    }

    // =================[ Crystal Logic ] =================

    private void handleCrystalExploit(EntitySpawnS2CPacket packet) {
        Vec3d crystalPos = new Vec3d(packet.getX(), packet.getY(), packet.getZ());

        // 1. Calculate simulated damage if we stay where we are
        Vec3d currentPos = new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ());
        float currentDamage = calculateDamageAt(currentPos, crystalPos);
        if (currentDamage <= crystalMaxDamage.get()) return;

        // 2. Find safe position
        Vec3d safePos = findSafePosForCrystal(crystalPos);
        if (safePos == null) return;

        // 3. Create dummy entity for attacking (Critical for bypassing 1.21.11 client sync delays)
        EndCrystalEntity dummyCrystal = new EndCrystalEntity(mc.world, crystalPos.x, crystalPos.y, crystalPos.z);
        dummyCrystal.setId(packet.getEntityId()); // Sync the network ID

        // 4. Generate VClip Path (To and From)
        Vec3d originalPos = currentPos;
        List<Vec3d> pathToSafe = buildVClipPath(originalPos, safePos, vClipStep.get());
        List<Vec3d> pathBack = returnBack.get() ? buildVClipPath(safePos, originalPos, vClipStep.get()) : new ArrayList<>();

        int totalPackets = pathToSafe.size() + pathBack.size() + 2;
        if (totalPackets > packetLimit.get()) {
            if (notifyLimit.get()) ChatUtils.warning("§7[GAntiExplosion] TP packet limit exceeded! Aborting.");
            return;
        }

        // 5. Execute The Exploit
        // A. Send movement packets to safe pos
        sendVClipPath(pathToSafe);
        sendPositionPacket(safePos); // Confirm arrival

        // B. Send attack packet (we are virtually at safePos now on the server)
        PlayerInteractEntityC2SPacket attackPacket = PlayerInteractEntityC2SPacket.attack(dummyCrystal, mc.player.isSneaking());
        mc.getNetworkHandler().sendPacket(attackPacket);
        mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));

        // C. Return back
        if (returnBack.get()) {
            sendVClipPath(pathBack);
            sendPositionPacket(originalPos);
        } else {
            // Hard teleport client player to new spot
            mc.player.setPosition(safePos);
        }
        ChatUtils.info("§a[GAntiExplosion] VClip Crystal Exploit Successful!");
    }

    // ================= [ Anchor Logic ] =================

    private void handleAnchorExploit(BlockUpdateS2CPacket packet) {
        BlockPos anchorPos = packet.getPos(); 
        Vec3d anchorCenter = anchorPos.toCenterPos();

        // 1. Find Glowstone in inventory
        FindItemResult glowstoneResult = InvUtils.findInHotbar(itemStack -> 
            itemStack.getItem().toString().contains("glowstone")
        );
        if (!glowstoneResult.found()) {
            ChatUtils.warning("§c[GAntiExplosion] Aborted: 快捷栏没有萤石！");
            return;
        }

        // 2. Find a non-glowstone item to ignite the anchor
        FindItemResult igniteItemResult = InvUtils.findInHotbar(itemStack -> 
            !itemStack.getItem().toString().contains("glowstone")
        );
        if (!igniteItemResult.found()) return;

        // 3. Predict damage
        Vec3d currentPos = new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ());
        float currentDamage = calculateDamageAt(currentPos, anchorCenter);
        if (currentDamage <= anchorMaxDamage.get()) return;

        // 4. Find safe pos
        Vec3d safePos = findSafePosForAnchor(anchorPos);
        if (safePos == null) return;

        // 5. Generate Paths
        Vec3d originalPos = currentPos;
        List<Vec3d> pathToSafe = buildVClipPath(originalPos, safePos, vClipStep.get());
        List<Vec3d> pathBack = returnBack.get() ? buildVClipPath(safePos, originalPos, vClipStep.get()) : new ArrayList<>();

        int totalPackets = pathToSafe.size() + pathBack.size() + 4; // Extra packets for interaction
        if (totalPackets > packetLimit.get()) {
            if (notifyLimit.get()) ChatUtils.warning("§7[GAntiExplosion] TP packet limit exceeded! Aborting.");
            return;
        }

        // 6. Execute The Exploit
        sendVClipPath(pathToSafe);
        sendPositionPacket(safePos);

        // A. Equip Glowstone and Interact (Charge)
        swapAndInteract(glowstoneResult.slot(), anchorPos);

        // B. Equip Ignite Item and Interact (Explode)
        swapAndInteract(igniteItemResult.slot(), anchorPos);

        // C. Return
        if (returnBack.get()) {
            sendVClipPath(pathBack);
            sendPositionPacket(originalPos);
        } else {
            mc.player.setPosition(safePos);
        }
        ChatUtils.info("§a[GAntiExplosion] VClip Anchor Exploit Successful!");
    }

    // ================= [ Networking & VClip Engine ] =================

    /**
     * Builds a linear path of vectors separated by 'step' distance.
     */
    private List<Vec3d> buildVClipPath(Vec3d start, Vec3d end, double step) {
        List<Vec3d> path = new ArrayList<>();
        double distance = start.distanceTo(end);
        int segments = (int) Math.ceil(distance / step);

        for (int i = 1; i < segments; i++) {
            double percent = (double) i / segments;
            path.add(start.lerp(end, percent));
        }
        return path;
    }

    /**
     * Iterates and sends a list of movement packets.
     */
    private void sendVClipPath(List<Vec3d> path) {
        for (Vec3d pos : path) {
            sendPositionPacket(pos);
        }
    }

    /**
     * Sends a strictly 1.21.11 compliant C04 Movement packet.
     * Rule #4: The 5th parameter `horizontalCollision` must be explicitly provided.
     */
    private void sendPositionPacket(Vec3d pos) {
        // 1.21.11 Constructor: PositionAndOnGround(double x, double y, double z, boolean onGround, boolean horizontalCollision)
        PlayerMoveC2SPacket packet = new PlayerMoveC2SPacket.PositionAndOnGround(
            pos.x, pos.y, pos.z, 
            mc.player.isOnGround(), 
            false // horizontalCollision
        );
        mc.getNetworkHandler().sendPacket(packet);
    }

    /**
     * Swaps to a slot and sends an interaction packet to a block.
     * Utilizes the injected InventoryAccessor for safe 1.21.11 slot manipulation.
     */
    private void swapAndInteract(int slot, BlockPos pos) {
        if (mc.player == null || mc.interactionManager == null) return;
        
        InventoryAccessor accessor = (InventoryAccessor) mc.player.getInventory();
        int prevSlot = accessor.getSelectedSlot();
        
        accessor.setSelectedSlot(slot);
        // 发送原版切槽数据包以同步
        mc.getNetworkHandler().sendPacket(new net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket(slot));

        // 1.21.11 交互修复：必须通过 interactionManager 来自动处理复杂的同步序列(Sequence)
        BlockHitResult hitResult = new BlockHitResult(pos.toCenterPos(), Direction.UP, pos, false);
        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hitResult);
        mc.player.swingHand(Hand.MAIN_HAND);

        // Restore slot
        accessor.setSelectedSlot(prevSlot);
        mc.getNetworkHandler().sendPacket(new net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket(prevSlot));
    }

    // =================[ Simulation & Raycasting Engine ] =================

    /**
     * Calculates explosion damage as if the player were standing at 'simulatedPlayerPos'.
     */
    private float calculateDamageAt(Vec3d simulatedPlayerPos, Vec3d explosionPos) {
        Box originalBox = mc.player.getBoundingBox();
        Vec3d originalPos = new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ());

        try {
            mc.player.setPosition(simulatedPlayerPos);
            mc.player.setBoundingBox(originalBox.offset(simulatedPlayerPos.subtract(originalPos)));

            return DamageUtils.crystalDamage(mc.player, explosionPos);
        } catch (Exception e) {
            return 100f; // 如果测算出现异常（极少情况），视为高危伤害
        } finally {
            mc.player.setPosition(originalPos);
            mc.player.setBoundingBox(originalBox);
        }
    }

    /**
     * Scans a 3D grid around the player to find a safe spot to attack the crystal.
     */
    private Vec3d findSafePosForCrystal(Vec3d crystalPos) {
        int border = crystalScanBorder.get();
        double maxDist = crystalHitRange.get();

        Vec3d bestPos = null;
        float lowestDamage = Float.MAX_VALUE;

        for (int x = -border; x <= border; x++) {
            for (int y = -border; y <= border; y++) {
                for (int z = -border; z <= border; z++) {
                    Vec3d testPos = new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ()).add(x, y, z);

                    if (testPos.distanceTo(crystalPos) > maxDist) continue;
                    
                    // 性能优化与防卡死：过滤掉墙体内的坐标
                    BlockPos testBlockPos = BlockPos.ofFloored(testPos);
                    if (!mc.world.getBlockState(testBlockPos).getCollisionShape(mc.world, testBlockPos).isEmpty()) continue;

                    float dmg = calculateDamageAt(testPos, crystalPos);
                    
                    if (dmg < lowestDamage) {
                        lowestDamage = dmg;
                        bestPos = testPos;
                    }
                }
            }
        }

        // 最终校验：如果找遍了依然没有绝对安全的地方，就中止，防止白送
        if (bestPos != null && lowestDamage > crystalMaxDamage.get()) {
            ChatUtils.warning("§c[GAntiExplosion] 放弃防爆: 晶体爆炸安全点依然受到 " + String.format("%.1f", lowestDamage) + " 伤害。");
            return null;
        }

        return bestPos;
    }

    /**
     * Scans a 3D grid around the player to find a safe spot to interact with the anchor.
     */
    private Vec3d findSafePosForAnchor(BlockPos anchorPos) {
        Vec3d anchorCenter = anchorPos.toCenterPos();
        int border = anchorScanBorder.get();
        double maxDist = anchorClickRange.get();

        Vec3d bestPos = null;
        float lowestDamage = Float.MAX_VALUE;

        for (int x = -border; x <= border; x++) {
            for (int y = -border; y <= border; y++) {
                for (int z = -border; z <= border; z++) {
                    Vec3d testPos = new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ()).add(x, y, z);

                    if (testPos.distanceTo(anchorCenter) > maxDist) continue;

                    // 性能优化与防卡死：过滤掉墙体内的坐标
                    BlockPos testBlockPos = BlockPos.ofFloored(testPos);
                    if (!mc.world.getBlockState(testBlockPos).getCollisionShape(mc.world, testBlockPos).isEmpty()) continue;

                    float dmg = calculateDamageAt(testPos, anchorCenter);
                    
                    if (dmg < lowestDamage) {
                        lowestDamage = dmg;
                        bestPos = testPos;
                    }
                }
            }
        }

        // 最终校验：如果找遍了依然没有绝对安全的地方，就中止
        if (bestPos != null && lowestDamage > anchorMaxDamage.get()) {
            ChatUtils.warning("§c[GAntiExplosion] 放弃防爆: 重生锚爆炸安全点依然受到 " + String.format("%.1f", lowestDamage) + " 伤害。");
            return null;
        }

        return bestPos;
    }
}