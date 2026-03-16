package com.codigohasta.addon.modules;

import com.codigohasta.addon.AddonTemplate;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.entity.EntityUtils;
import meteordevelopment.meteorclient.utils.entity.SortPriority;
import meteordevelopment.meteorclient.utils.entity.TargetUtils;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.AxeItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SwordItem;
import net.minecraft.item.TridentItem;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.entity.EntityPose;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class SimpleTpAura extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgTargeting = settings.createGroup("Targeting");
    private final SettingGroup sgWhitelist = settings.createGroup("Whitelist");
    private final SettingGroup sgCriticals = settings.createGroup("Criticals");
    private final SettingGroup sgTiming = settings.createGroup("Timing");
    private final SettingGroup sgRender = settings.createGroup("Render");

    // --- General ---
    private final Setting<Weapon> weapon = sgGeneral.add(new EnumSetting.Builder<Weapon>()
        .name("weapon")
        .description("指定触发攻击的武器类型。")
        .defaultValue(Weapon.Sword)
        .build()
    );

    private final Setting<Boolean> autoSwitch = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-switch")
        .description("攻击时自动切换到指定武器。")
        .defaultValue(true)
        .visible(() -> weapon.get() != Weapon.Any)
        .build()
    );

    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
        .name("range")
        .description("最大传送攻击距离。")
        .defaultValue(30.0)
        .min(0)
        .sliderMax(100)
        .build()
    );

    private final Setting<Double> steps = sgGeneral.add(new DoubleSetting.Builder()
        .name("packet-steps")
        .description("每个传送包移动的步长（越小越稳，但包越多）。建议 5-8。")
        .defaultValue(6.0)
        .min(1.0)
        .sliderMax(20.0)
        .build()
    );

    private final Setting<Boolean> collisionCheck = sgGeneral.add(new BoolSetting.Builder()
        .name("collision-check")
        .description("检查路径上是否有方块阻挡。如果开启且有阻挡，则不攻击。这是防止卡在墙里的关键。")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> swingHand = sgGeneral.add(new BoolSetting.Builder()
        .name("swing-hand")
        .description("显示挥手动画。")
        .defaultValue(true)
        .build()
    );

    // --- Criticals ---
    private final Setting<Boolean> useCriticals = sgCriticals.add(new BoolSetting.Builder()
        .name("use-criticals")
        .description("在目标位置进行微小跳跃以触发暴击（飞行时会自动禁用以防摔伤）。")
        .defaultValue(true)
        .build()
    );

    // --- Timing ---
    private final Setting<Boolean> smartDelay = sgTiming.add(new BoolSetting.Builder()
        .name("smart-delay")
        .description("智能冷却：根据手中武器的攻速属性计算最佳间隔。")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> attackDelay = sgTiming.add(new IntSetting.Builder()
        .name("attack-delay")
        .description("手动攻击延迟 (刻)。")
        .defaultValue(10)
        .min(0)
        .sliderRange(0, 40)
        .visible(() -> !smartDelay.get())
        .build()
    );

    // --- Targeting ---
    private final Setting<Boolean> players = sgTargeting.add(new BoolSetting.Builder()
        .name("players")
        .description("攻击玩家")
        .defaultValue(true)
        .build()
    );

    private final Setting<Set<EntityType<?>>> entities = sgTargeting.add(new EntityTypeListSetting.Builder()
        .name("entities")
        .description("选择要攻击的具体生物种类")
        .onlyAttackable()
        .defaultValue(EntityType.PLAYER)
        .build()
    );

    private final Setting<SortPriority> priority = sgTargeting.add(new EnumSetting.Builder<SortPriority>()
        .name("priority")
        .description("攻击目标的优先级。")
        .defaultValue(SortPriority.LowestDistance)
        .build()
    );

    private final Setting<Boolean> ignoreNamed = sgTargeting.add(new BoolSetting.Builder()
        .name("ignore-named")
        .description("不攻击被命名的生物。")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> throughWalls = sgTargeting.add(new BoolSetting.Builder()
        .name("through-walls")
        .description("允许锁定墙后的目标（配合 Collision Check 关闭时可强制穿墙，但容易卡住）。")
        .defaultValue(false)
        .build()
    );

    // --- Whitelist/Blacklist ---
    public enum ListMode {
        Whitelist,
        Blacklist,
        Off
    }

    private final Setting<ListMode> listMode = sgWhitelist.add(new EnumSetting.Builder<ListMode>()
        .name("mode")
        .description("白名单/黑名单模式。")
        .defaultValue(ListMode.Off)
        .build()
    );

    private final Setting<String> playerList = sgWhitelist.add(new StringSetting.Builder()
        .name("player-list")
        .description("玩家列表，用英文逗号(,)分隔。")
        .defaultValue("")
        .visible(() -> listMode.get() != ListMode.Off)
        .build()
    );

    // --- Render ---
    private final Setting<Boolean> render = sgRender.add(new BoolSetting.Builder()
        .name("render")
        .description("渲染当前攻击目标。")
        .defaultValue(true)
        .build()
    );

    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode")
        .description("渲染模式")
        .defaultValue(ShapeMode.Lines)
        .visible(render::get)
        .build()
    );

    private final Setting<SettingColor> sideColor = sgRender.add(new ColorSetting.Builder()
        .name("side-color")
        .description("填充颜色")
        .defaultValue(new SettingColor(255, 0, 0, 75))
        .visible(render::get)
        .build()
    );

    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder()
        .name("line-color")
        .description("线框颜色")
        .defaultValue(new SettingColor(255, 0, 0, 255))
        .visible(render::get)
        .build()
    );

    private int timer;
    private int originalSlot = -1;
    private final List<Entity> targets = new ArrayList<>();
    private Entity currentTarget;

    public SimpleTpAura() {
        super(AddonTemplate.CATEGORY, "神仙飞刀-tp-aura", "修复版：防止飞行摔伤与卡墙。");
    }

    @Override
    public void onActivate() {
        timer = 0;
        originalSlot = -1;
        targets.clear();
        currentTarget = null;
    }

    @Override
    public void onDeactivate() {
        currentTarget = null;
        if (originalSlot != -1 && autoSwitch.get() && mc.player != null) {
            InvUtils.swap(originalSlot, false);
            originalSlot = -1;
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        if (timer > 0) {
            timer--;
            return;
        }

        // 查找目标
        targets.clear();
        TargetUtils.getList(targets, this::entityCheck, priority.get(), 1);

        if (targets.isEmpty()) {
            currentTarget = null;
            if (originalSlot != -1 && autoSwitch.get()) {
                InvUtils.swap(originalSlot, false);
                originalSlot = -1;
            }
            return;
        }

        currentTarget = targets.get(0);

        if (weapon.get() != Weapon.Any) {
            if (autoSwitch.get()) {
                if (!switchWeapon()) return;
            } else if (!checkHand()) {
                return;
            }
        }

        // 尝试攻击
        if (performInstantAttack(currentTarget)) {
            // 只有成功发送包才重置冷却
            if (smartDelay.get()) {
                double attackSpeed = mc.player.getAttributeValue(EntityAttributes.ATTACK_SPEED);
                timer = (int) Math.ceil(20.0 / attackSpeed);
            } else {
                timer = attackDelay.get();
            }
        }
    }

    private boolean performInstantAttack(Entity target) {
        Vec3d startPos = mc.player.getPos();
        Vec3d targetPos = target.getPos(); 
        
        // 关键修复1: 碰撞检测
        if (collisionCheck.get()) {
            HitResult result = mc.world.raycast(new RaycastContext(
                startPos.add(0, 0.5, 0),
                targetPos.add(0, 0.5, 0),
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                mc.player
            ));
            
            if (result.getType() == HitResult.Type.BLOCK) {
                return false; 
            }
        }

        List<Vec3d> path = generatePath(startPos, targetPos);
        
        // 关键修复2: 飞行状态检测 (修改了这里)
        // 使用 getPose() == EntityPose.FALL_FLYING 替代 isFallFlying() 以兼容不同 mappings
        boolean isElytraFlying = mc.player.getPose().name().equals("FALL_FLYING");
        boolean isFlying = mc.player.getAbilities().flying || isElytraFlying;
        
        boolean onGroundState = !isFlying && mc.player.isOnGround();

        // 1. 发送去程包
        for (Vec3d pos : path) {
            sendPacket(pos.x, pos.y, pos.z, onGroundState);
        }

        // 2. 旋转包
        double yaw = Rotations.getYaw(target);
        double pitch = Rotations.getPitch(target);
        mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(
            (float) yaw, (float) pitch, onGroundState, mc.player.horizontalCollision
        ));

        // 3. 攻击前暴击逻辑 (仅在非飞行且在地面时触发)
        if (useCriticals.get() && !isFlying && onGroundState && !mc.player.isTouchingWater() && !mc.player.isInLava()) {
            double x = targetPos.x;
            double y = targetPos.y;
            double z = targetPos.z;
            sendPacket(x, y + 0.0625, z, false); // 小跳
            sendPacket(x, y, z, false);          // 下落
        }

        // 4. 攻击
        mc.getNetworkHandler().sendPacket(PlayerInteractEntityC2SPacket.attack(target, mc.player.isSneaking()));
        if (swingHand.get()) {
            mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
        }

        // 5. 发送回程包
        for (int i = path.size() - 1; i >= 0; i--) {
            Vec3d pos = path.get(i);
            sendPacket(pos.x, pos.y, pos.z, onGroundState);
        }

        // 6. 关键修复3: 强制同步回起点
        sendPacket(startPos.x, startPos.y, startPos.z, onGroundState);
        
        return true;
    }

    private List<Vec3d> generatePath(Vec3d start, Vec3d end) {
        List<Vec3d> path = new ArrayList<>();
        double distance = start.distanceTo(end);
        double stepSize = steps.get();
        
        int count = (int) Math.ceil(distance / stepSize);
        if (count == 0) count = 1;

        for (int i = 1; i <= count; i++) {
            double progress = (double) i / count;
            path.add(start.lerp(end, progress));
        }
        return path;
    }

    private void sendPacket(double x, double y, double z, boolean onGround) {
        mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
            x, y, z, onGround, mc.player.horizontalCollision
        ));
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (!render.get() || currentTarget == null) return;

        event.renderer.box(
            currentTarget.getBoundingBox(),
            sideColor.get(),
            lineColor.get(),
            shapeMode.get(),
            0
        );
    }

    // --- Helper Methods ---

    private boolean checkHand() {
        ItemStack stack = mc.player.getMainHandStack();
        return switch (weapon.get()) {
            case Sword -> stack.getItem() instanceof SwordItem;
            case Axe -> stack.getItem() instanceof AxeItem;
            case Trident -> stack.getItem() instanceof TridentItem;
            case All -> stack.getItem() instanceof SwordItem || stack.getItem() instanceof AxeItem || stack.getItem() instanceof TridentItem;
            case Any -> true;
        };
    }

    private boolean switchWeapon() {
        if (checkHand()) {
            if (originalSlot == -1) originalSlot = mc.player.getInventory().selectedSlot;
            return true;
        }

        Predicate<ItemStack> predicate = switch (weapon.get()) {
            case Sword -> stack -> stack.getItem() instanceof SwordItem;
            case Axe -> stack -> stack.getItem() instanceof AxeItem;
            case Trident -> stack -> stack.getItem() instanceof TridentItem;
            case All -> stack -> stack.getItem() instanceof SwordItem || stack.getItem() instanceof AxeItem || stack.getItem() instanceof TridentItem;
            default -> stack -> true;
        };

        FindItemResult item = InvUtils.find(predicate, 0, 8);
        if (item.found()) {
            if (originalSlot == -1) originalSlot = mc.player.getInventory().selectedSlot;
            InvUtils.swap(item.slot(), false);
            return true;
        }
        return false;
    }

    private boolean entityCheck(Entity entity) {
        if (!(entity instanceof LivingEntity) || !entity.isAlive()) return false;
        if (entity.equals(mc.player)) return false;

        if (mc.player.distanceTo(entity) > range.get()) return false;
        
        // 如果关闭了穿墙且开启了 collisionCheck，这里先做个简单的视线检查优化性能
        if (!throughWalls.get() && !mc.player.canSee(entity)) return false;

        if (entity instanceof PlayerEntity p) {
            if (!players.get()) return false;
            if (p.isCreative()) return false;
            if (!Friends.get().shouldAttack(p)) return false;

            String name = p.getGameProfile().getName();
            List<String> playerNames = Arrays.stream(playerList.get().split(","))
                .filter(s -> !s.isBlank())
                .map(String::trim)
                .collect(Collectors.toList());

            if (listMode.get() == ListMode.Whitelist && !playerNames.contains(name)) return false;
            if (listMode.get() == ListMode.Blacklist && playerNames.contains(name)) return false;
        }

        if (ignoreNamed.get() && entity.hasCustomName()) return false;
        if (entity instanceof TameableEntity && ((TameableEntity) entity).isTamed()) return false;

        return entities.get().contains(entity.getType());
    }

    @Override
    public String getInfoString() {
        if (currentTarget != null) {
            return EntityUtils.getName(currentTarget);
        }
        return null;
    }

    public enum Weapon {
        Sword,
        Axe,
        Trident,
        All,
        Any
    }
}