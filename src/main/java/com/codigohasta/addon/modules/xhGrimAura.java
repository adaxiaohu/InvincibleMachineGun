package com.codigohasta.addon.modules;

import com.codigohasta.addon.AddonTemplate;
import com.codigohasta.addon.mixin.InventoryAccessor;
import com.codigohasta.addon.utils.Rotation;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.pathing.PathManagers;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.entity.EntityUtils;
import meteordevelopment.meteorclient.utils.entity.SortPriority;
import meteordevelopment.meteorclient.utils.entity.TargetUtils;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.world.TickRate;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.EndermanEntity;
import net.minecraft.entity.mob.ZombifiedPiglinEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.passive.WolfEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

public class xhGrimAura extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgTargeting = settings.createGroup("目标设置");
    private final SettingGroup sgTiming = settings.createGroup("时机设置");

    // --- General Settings ---
    private final Setting<Weapon> weapon = sgGeneral.add(new EnumSetting.Builder<Weapon>()
            .name("适用武器")
            .description("只有手持选定武器时才进行攻击。")
            .defaultValue(Weapon.全部)
            .build()
    );

    private final Setting<RotationMode> rotation = sgGeneral.add(new EnumSetting.Builder<RotationMode>()
            .name("旋转模式")
            .description("何时看向目标。")
            .defaultValue(RotationMode.总是)
            .build()
    );

    private final Setting<Double> selfPrediction = sgGeneral.add(new DoubleSetting.Builder()
            .name("移动预判")
            .description("根据实体的移动速度预测其位置。")
            .defaultValue(0.5)
            .min(0.0)
            .sliderMax(5.0)
            .build()
    );

    private final Setting<Boolean> autoSwitch = sgGeneral.add(new BoolSetting.Builder()
            .name("自动切换武器")
            .description("攻击时自动切到背包里的合适武器。")
            .defaultValue(false)
            .build()
    );

    private final Setting<Boolean> swapBack = sgGeneral.add(new BoolSetting.Builder()
            .name("自动切回")
            .description("攻击完成后切回原来的槽位。")
            .defaultValue(false)
            .visible(autoSwitch::get)
            .build()
    );

    private final Setting<Boolean> pauseOnCombat = sgGeneral.add(new BoolSetting.Builder()
            .name("战斗时暂停Baritone")
            .description("攻击实体时暂时冻结Baritone寻路。")
            .defaultValue(true)
            .build()
    );

    private final Setting<ShieldMode> shieldMode = sgGeneral.add(new EnumSetting.Builder<ShieldMode>()
            .name("破盾模式")
            .description("当目标举盾时自动使用斧头破盾。")
            .defaultValue(ShieldMode.破盾)
            .visible(() -> autoSwitch.get() && weapon.get() != Weapon.斧)
            .build()
    );

    // --- Targeting Settings ---
    private final Setting<Set<EntityType<?>>> entities = sgTargeting.add(new EntityTypeListSetting.Builder()
            .name("实体列表")
            .description("选择要攻击的实体类型。")
            .onlyAttackable()
            .defaultValue(EntityType.PLAYER)
            .build()
    );

    private final Setting<SortPriority> priority = sgTargeting.add(new EnumSetting.Builder<SortPriority>()
            .name("优先目标")
            .description("当范围内有多个目标时优先打谁。")
            .defaultValue(SortPriority.ClosestAngle)
            .build()
    );

    private final Setting<Integer> maxTargets = sgTargeting.add(new IntSetting.Builder()
            .name("最大目标数")
            .description("同时攻击多少个实体。")
            .defaultValue(1)
            .min(1)
            .sliderRange(1, 5)
            .build()
    );

    private final Setting<Double> range = sgTargeting.add(new DoubleSetting.Builder()
            .name("攻击距离")
            .description("攻击的最大范围。")
            .defaultValue(4.5)
            .min(0)
            .sliderMax(6)
            .build()
    );

    private final Setting<Double> wallsRange = sgTargeting.add(new DoubleSetting.Builder()
            .name("穿墙距离")
            .description("隔着墙壁攻击的最大范围。")
            .defaultValue(3.5)
            .min(0)
            .sliderMax(6)
            .build()
    );

    private final Setting<EntityAge> mobAgeFilter = sgTargeting.add(new EnumSetting.Builder<EntityAge>()
            .name("生物年龄")
            .description("攻击成年、幼年或所有生物。")
            .defaultValue(EntityAge.成年)
            .build()
    );

    private final Setting<Boolean> ignoreNamed = sgTargeting.add(new BoolSetting.Builder()
            .name("忽略命名生物")
            .defaultValue(false)
            .build()
    );

    private final Setting<Boolean> ignorePassive = sgTargeting.add(new BoolSetting.Builder()
            .name("忽略被动生物")
            .description("除非它们攻击你，否则不打中立生物。")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> ignoreTamed = sgTargeting.add(new BoolSetting.Builder()
            .name("忽略已驯服生物")
            .description("不打你自己的宠物。")
            .defaultValue(false)
            .build()
    );

    // --- Timing Settings ---
    private final Setting<Boolean> pauseOnLag = sgTiming.add(new BoolSetting.Builder()
            .name("卡顿时暂停")
            .description("服务器卡顿时暂停攻击防掉线。")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> pauseOnUse = sgTiming.add(new BoolSetting.Builder()
            .name("使用物品时暂停")
            .description("吃东西或挖方块时不攻击。")
            .defaultValue(false)
            .build()
    );

    private final Setting<Boolean> tpsSync = sgTiming.add(new BoolSetting.Builder()
            .name("TPS 同步")
            .description("将攻击延迟与服务器TPS同步。")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> customDelay = sgTiming.add(new BoolSetting.Builder()
            .name("自定义延迟")
            .description("使用自定义的Tick延迟而不是原版冷却。")
            .defaultValue(false)
            .build()
    );

    private final Setting<Integer> hitDelay = sgTiming.add(new IntSetting.Builder()
            .name("攻击间隔 (Tick)")
            .description("攻击频率。")
            .defaultValue(11)
            .min(0)
            .sliderMax(60)
            .visible(customDelay::get)
            .build()
    );

    private final Setting<Integer> switchDelay = sgTiming.add(new IntSetting.Builder()
            .name("切换延迟")
            .description("切快捷栏后等待多少Tick再打。")
            .defaultValue(100)
            .min(0)
            .sliderMax(10)
            .build()
    );

    // --- Internal Variables ---
    private final List<Entity> targets = new ArrayList<>();
    private int switchTimer, hitTimer;
    private boolean wasPathing = false;
    public boolean attacking, swapped;
    public static int previousSlot;
    // yaw/pitch 保留变量，尽管 Utils 会实时计算
    private float yaw, pitch;

    public xhGrimAura() {
        super(AddonTemplate.CATEGORY, "xh-grim-aura", "高度定制的 GrimAC 杀戮光环 (全版本适配)。");
    }

    @Override
    public void onActivate() {
        previousSlot = -1;
        swapped = false;
    }

    @Override
    public void onDeactivate() {
        targets.clear();
        stopAttacking();
    }

    @EventHandler
    private void onTickPre(TickEvent.Pre event) {
        if (targets.isEmpty()) return;
        // 兼容性修复：使用 get(0) 替代 getFirst()
        Entity primary = targets.get(0);
        if (rotation.get() == RotationMode.总是) {
            Rotation.snapAt(primary.getBoundingBox());
        }
    }

    @EventHandler
    private void onTickPost(TickEvent.Post event) {
        if (!mc.player.isAlive() || PlayerUtils.getGameMode() == GameMode.SPECTATOR) {
            stopAttacking();
            return;
        }
        if (pauseOnUse.get() && (mc.interactionManager.isBreakingBlock() || mc.player.isUsingItem())) {
            stopAttacking();
            return;
        }
        if (TickRate.INSTANCE.getTimeSinceLastTick() >= 1f && pauseOnLag.get()) {
            stopAttacking();
            return;
        }
        
        targets.clear();
        TargetUtils.getList(targets, this::entityCheck, priority.get(), maxTargets.get());

        if (targets.isEmpty()) {
            stopAttacking();
            return;
        }

        // 兼容性修复：使用 get(0)
        Entity primary = targets.get(0);
        
        // 自动切换逻辑 (字符串匹配，兼容新旧版本)
        if (autoSwitch.get()) {
            Predicate<ItemStack> predicate = switch (weapon.get()) {
                case 斧 -> stack -> stack.getItem().toString().toLowerCase().contains("_axe");
                case 剑 -> stack -> stack.getItem().toString().toLowerCase().contains("sword");
                case 锤 -> stack -> stack.getItem().toString().toLowerCase().contains("mace");
                case 三叉戟 -> stack -> stack.getItem().toString().toLowerCase().contains("trident");
                case 全部 -> stack -> {
                    String s = stack.getItem().toString().toLowerCase();
                    return s.contains("_axe") || s.contains("sword") || s.contains("mace") || s.contains("trident");
                };
                default -> o -> true;
            };
            
            FindItemResult weaponResult = InvUtils.findInHotbar(predicate);
            
            // 破盾逻辑：找斧头
            if (shouldShieldBreak()) {
                FindItemResult axeResult = InvUtils.findInHotbar(itemStack -> itemStack.getItem().toString().toLowerCase().contains("_axe"));
                if (axeResult.found()) weaponResult = axeResult;
            }
            
            if (!swapped) {
                // 使用 Accessor 获取当前槽位
                previousSlot = ((InventoryAccessor) mc.player.getInventory()).getSelectedSlot();
                swapped = true;
            }
            if (weaponResult.found()) {
                InvUtils.swap(weaponResult.slot(), false);
            }
        }
        
        if (!itemInHand()) {
            stopAttacking();
            return;
        }
        
        attacking = true;
        
        // 移动预判 (兼容写法：使用 getVelocity 或计算差值)
        double velX = mc.player.getX() - mc.player.prevX; // 兼容旧版
        double velY = mc.player.getY() - mc.player.prevY;
        double velZ = mc.player.getZ() - mc.player.prevZ;
        
        double predictX = mc.player.getEyePos().x + velX * selfPrediction.get();
        double predictY = mc.player.getEyePos().y + velY * selfPrediction.get();
        double predictZ = mc.player.getEyePos().z + velZ * selfPrediction.get();
        Vec3d predictedPos = new Vec3d(predictX, predictY, predictZ);

        if (rotation.get() == RotationMode.总是) {
            // 获取计算出的角度以便记录
            float[] rots = Rotation.getRotation(getAttackVec(primary, predictedPos));
            yaw = rots[0];
            pitch = rots[1];
        }

        if (pauseOnCombat.get() && PathManagers.get().isPathing() && !wasPathing) {
            PathManagers.get().pause();
            wasPathing = true;
        }
        
        if (delayCheck()) {
            for (Entity target : targets) {
                attack(target, predictedPos);
            }
        }
        
        // 恢复旋转
        Rotation.snapBack();
    }

    @EventHandler
    private void onSendPacket(PacketEvent.Send event) {
        if (event.packet instanceof UpdateSelectedSlotC2SPacket) {
            switchTimer = switchDelay.get();
        }
    }

    private void stopAttacking() {
        if (!attacking) return;

        attacking = false;
        if (wasPathing) {
            PathManagers.get().resume();
            wasPathing = false;
        }
        if (swapBack.get() && swapped && previousSlot != -1) {
            InvUtils.swap(previousSlot, false);
            swapped = false;
        }
    }

    private boolean shouldShieldBreak() {
        for (Entity target : targets) {
            if (target instanceof PlayerEntity player) {
                // isBlocking 在新旧版本通用
                if (player.isBlocking() && shieldMode.get() == ShieldMode.破盾) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean entityCheck(Entity entity) {
        // getCameraEntity 兼容
        Entity camera = mc.getCameraEntity(); 
        if (entity.equals(mc.player) || entity.equals(camera)) return false;
        if ((entity instanceof LivingEntity livingEntity && livingEntity.isDead()) || !entity.isAlive()) return false;

        Box hitbox = entity.getBoundingBox();
        if (!PlayerUtils.isWithin(
                MathHelper.clamp(mc.player.getX(), hitbox.minX, hitbox.maxX),
                MathHelper.clamp(mc.player.getY(), hitbox.minY, hitbox.maxY),
                MathHelper.clamp(mc.player.getZ(), hitbox.minZ, hitbox.maxZ),
                range.get()
        )) return false;

        if (!entities.get().contains(entity.getType())) return false;
        if (ignoreNamed.get() && entity.hasCustomName()) return false;
        if (!PlayerUtils.canSeeEntity(entity) && !PlayerUtils.isWithin(entity, wallsRange.get())) return false;
        
        if (ignoreTamed.get()) {
            if (entity instanceof TameableEntity tameable
                    && tameable.getOwner() != null
                    && tameable.getOwner().equals(mc.player)
            ) return false;
        }
        
        if (ignorePassive.get()) {
            if (entity instanceof EndermanEntity enderman && !enderman.isAngry()) return false;
            if (entity instanceof ZombifiedPiglinEntity piglin && !piglin.isAttacking()) return false;
            if (entity instanceof WolfEntity wolf && !wolf.isAttacking()) return false;
        }
        
        if (entity instanceof PlayerEntity player) {
            if (player.isCreative()) return false;
            if (!Friends.get().shouldAttack(player)) return false;
            if (shieldMode.get() == ShieldMode.忽略 && player.isBlocking()) return false;
        }
        
        if (entity instanceof AnimalEntity animal) {
            return switch (mobAgeFilter.get()) {
                case 幼年 -> animal.isBaby();
                case 成年 -> !animal.isBaby();
                case 都可以 -> true;
            };
        }
        return true;
    }

    private boolean delayCheck() {
        if (switchTimer > 0) {
            switchTimer--;
            return false;
        }

        float delay = (customDelay.get()) ? hitDelay.get() : 0.5f;
        if (tpsSync.get()) delay /= (TickRate.INSTANCE.getTickRate() / 20);

        if (customDelay.get()) {
            if (hitTimer < delay) {
                hitTimer++;
                return false;
            } else return true;
        } else return mc.player.getAttackCooldownProgress(delay) >= 1;
    }

    private void attack(Entity target, Vec3d predictedPos) {
        if (rotation.get() == RotationMode.攻击时) {
            Rotation.snapAt(getAttackVec(target, predictedPos));
            float[] rots = Rotation.getRotation(getAttackVec(target, predictedPos));
            yaw = rots[0];
            pitch = rots[1];
        }
        
        mc.getNetworkHandler().sendPacket(PlayerInteractEntityC2SPacket.attack(target, mc.player.isSneaking()));
        mc.player.swingHand(Hand.MAIN_HAND);
        hitTimer = 0;
    }

    private Vec3d getAttackVec(Entity entity, Vec3d predictedPos) {
        return getClosestPointToBox(predictedPos, entity.getBoundingBox());
    }

    public Vec3d getClosestPointToBox(Vec3d pos, Box boundingBox) {
        double closestX = Math.max(boundingBox.minX, Math.min(pos.x, boundingBox.maxX));
        double closestY = Math.max(boundingBox.minY, Math.min(pos.y, boundingBox.maxY));
        double closestZ = Math.max(boundingBox.minZ, Math.min(pos.z, boundingBox.maxZ));
        return new Vec3d(closestX, closestY, closestZ);
    }

    private boolean itemInHand() {
        if (shouldShieldBreak()) return mc.player.getMainHandStack().getItem().toString().toLowerCase().contains("_axe");

        return switch (weapon.get()) {
            case 斧 -> mc.player.getMainHandStack().getItem().toString().toLowerCase().contains("_axe");
            case 剑 -> mc.player.getMainHandStack().getItem().toString().toLowerCase().contains("sword");
            case 锤 -> mc.player.getMainHandStack().getItem().toString().toLowerCase().contains("mace");
            case 三叉戟 -> mc.player.getMainHandStack().getItem().toString().toLowerCase().contains("trident");
            case 全部 -> {
                String s = mc.player.getMainHandStack().getItem().toString().toLowerCase();
                yield s.contains("_axe") || s.contains("sword") || s.contains("mace") || s.contains("trident");
            }
            default -> true;
        };
    }

    public Entity getTarget() {
        // 修复：使用 get(0)
        if (!targets.isEmpty()) return targets.get(0);
        return null;
    }

    @Override
    public String getInfoString() {
        if (!targets.isEmpty()) return EntityUtils.getName(getTarget());
        return null;
    }

    public enum Weapon { 剑, 斧, 锤, 三叉戟, 全部, 任意 }
    public enum RotationMode { 总是, 攻击时, 无 }
    public enum ShieldMode { 忽略, 破盾, 无 }
    public enum EntityAge { 幼年, 成年, 都可以 }
}