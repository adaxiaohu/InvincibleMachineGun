package com.codigohasta.addon.modules;

import com.codigohasta.addon.AddonTemplate;

import com.codigohasta.addon.utils.KeyUtils;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

import java.util.HashSet;
import java.util.Set;

public class CrystalMacro extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> activateKey = sgGeneral.add(new IntSetting.Builder()
        .name("activate-key")
        .description("Key that does the crystalling.")
        .defaultValue(1)
        .min(-1)
        .max(400)
        .build()
    );

    // 依然保留 Double 设置，但底层将按毫秒精准计算，不再丢失小数
    private final Setting<Double> placeDelay = sgGeneral.add(new DoubleSetting.Builder()
        .name("place-delay")
        .description("The delay in ticks between placing crystals.")
        .defaultValue(0.3)
        .min(0.0)
        .max(20.0)
        .sliderMax(20.0)
        .build()
    );

    private final Setting<Double> breakDelay = sgGeneral.add(new DoubleSetting.Builder()
        .name("break-delay")
        .description("The delay in ticks between breaking crystals.")
        .defaultValue(0.03)
        .min(0.0)
        .max(20.0)
        .sliderMax(20.0)
        .build()
    );

    private final Setting<Boolean> stopOnKill = sgGeneral.add(new BoolSetting.Builder()
        .name("stop-on-kill")
        .description("Pauses the macro when a nearby player dies, then resumes after 5 seconds.")
        .defaultValue(true)
        .build()
    );

    // 【高精度毫秒计时器】取代原有的 Tick Counter
    private long lastPlaceTime = 0;
    private long lastBreakTime = 0;

    private final Set<PlayerEntity> deadPlayers = new HashSet<>();
    private boolean paused = false;
    private long resumeTime = 0;

    public CrystalMacro() {
        super(AddonTemplate.CATEGORY, "Legit自动水晶", "快速放置与点爆水晶");
    }

    @Override
    public void onActivate() {
        lastPlaceTime = 0;
        lastBreakTime = 0;
        deadPlayers.clear();
        paused = false;
        resumeTime = 0;
    }

    @Override
    public void onDeactivate() {
        deadPlayers.clear();
        paused = false;
        resumeTime = 0;
    }

    @EventHandler
    private void onTick(final TickEvent.Pre event) {
        if (mc.currentScreen != null) return;

        if (paused && System.currentTimeMillis() >= resumeTime) {
            paused = false;
            if (mc.player != null) {
                mc.player.sendMessage(net.minecraft.text.Text.literal(
                    "§7[§bLegitCrystalMacro§7] §aResumed after stop-on-kill"
                ), false);
            }
        }

        if (paused) return;
        if (!isKeyActive()) return;
        if (mc.player.isUsingItem()) return;

        // 【1.21.11 准则】基于字符串检查持有物品是否为水晶
        String mainHandName = mc.player.getMainHandStack().getItem().toString().toLowerCase();
        if (!mainHandName.contains("end_crystal")) return;

        if (stopOnKill.get() && checkForDeadPlayers()) {
            paused = true;
            resumeTime = System.currentTimeMillis() + 5000;
            if (mc.player != null) {
                mc.player.sendMessage(net.minecraft.text.Text.literal(
                    "§7[§bLegitCrystalMacro§7] §cPaused due to player death (will resume in 5s)"
                ), false);
            }
            return;
        }

        handleInteraction();
    }

    private boolean isKeyActive() {
        int d = activateKey.get();
        return d == -1 || KeyUtils.isKeyPressed(d);
    }

    private void handleInteraction() {
        HitResult crosshairTarget = mc.crosshairTarget;
        if (crosshairTarget instanceof BlockHitResult blockHit) {
            handleBlockInteraction(blockHit);
        } else if (crosshairTarget instanceof EntityHitResult entityHit) {
            handleEntityInteraction(entityHit);
        }
    }

    private void handleBlockInteraction(BlockHitResult blockHitResult) {
        if (blockHitResult.getType() != HitResult.Type.BLOCK) return;

        // 【毫秒级计时器计算】将 double ticks 转换为具体的毫秒 (1 tick = 50ms)
        long currentTime = System.currentTimeMillis();
        long placeDelayMs = (long) (placeDelay.get() * 50.0);
        if (currentTime - lastPlaceTime < placeDelayMs) return;

        BlockPos blockPos = blockHitResult.getBlockPos();
        
        // 【1.21.11 准则】通过名称判定方块
        String targetBlockName = mc.world.getBlockState(blockPos).getBlock().toString().toLowerCase();
        boolean isObsidianOrBedrock = targetBlockName.contains("obsidian") || targetBlockName.contains("bedrock");

        if (isObsidianOrBedrock && isValidCrystalPlacement(blockPos)) {
            // 调用原生接口放置水晶
            mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, blockHitResult);
            mc.player.swingHand(Hand.MAIN_HAND);
            
            // 记录放置时间
            lastPlaceTime = currentTime;
        }
    }

    private void handleEntityInteraction(EntityHitResult entityHitResult) {
        long currentTime = System.currentTimeMillis();
        long breakDelayMs = (long) (breakDelay.get() * 50.0);
        if (currentTime - lastBreakTime < breakDelayMs) return;

        Entity entity = entityHitResult.getEntity();
        String entityTypeStr = entity.getType().toString().toLowerCase();

        // 【1.21.11 准则】安全检查实体类型
        if (!entityTypeStr.contains("end_crystal") && !entityTypeStr.contains("slime")) return;

        // 发送攻击与挥手数据包
        mc.interactionManager.attackEntity(mc.player, entity);
        mc.player.swingHand(Hand.MAIN_HAND);
        
        // 【GrimAC 核心绕过】客户端预测移除 (Predictive Entity Discard)
        // 这个方法直接在你的客户端强行抹除水晶实体。
        // 这样在接下来的几十毫秒内，你的准星不会再次扫到它，彻底杜绝了“鞭尸”重发包现象，GrimAC 将判定你为 100% 合法的高手操作。
        entity.discard(); 
        
        // 记录破坏时间
        lastBreakTime = currentTime;
    }

    private boolean isValidCrystalPlacement(BlockPos blockPos) {
        BlockPos up = blockPos.up();
        if (!mc.world.isAir(up)) return false;

        int x = up.getX(), y = up.getY(), z = up.getZ();
        // 如果上面被 predictive discard 的水晶被移除了，这里判断就会立刻通过，实现无缝连点！
        return mc.world.getOtherEntities(null, new Box(x, y, z, x + 1.0, y + 2.0, z + 1.0)).isEmpty();
    }

    private boolean checkForDeadPlayers() {
        if (mc.world == null) return false;

        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player == mc.player) continue;

            // 遵守 1.21.11 Record 调用规则
            String name = player.getGameProfile().name();

            if (player.isDead() || player.getHealth() <= 0) {
                if (!deadPlayers.contains(player)) {
                    deadPlayers.add(player);
                    return true;
                }
            }
        }

        deadPlayers.removeIf(p -> !p.isDead() && p.getHealth() > 0);
        return false;
    }
}