package com.codigohasta.addon.modules;

import com.codigohasta.addon.AddonTemplate;

import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.systems.friends.Friends;
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
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.MaceItem;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Arrays;
import java.util.stream.Collectors;
import java.util.Set;

public class ArmorBreaker extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgSpoof = settings.createGroup("Spoofing");
    private final SettingGroup sgTargeting = settings.createGroup("Targeting");
    private final SettingGroup sgWhitelist = settings.createGroup("Whitelist");
    private final SettingGroup sgRender = settings.createGroup("Render");

    // --- General ---
    private final Setting<Boolean> autoSwitch = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-switch")
        .description("自动切换到重锤")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> onlyOnGround = sgGeneral.add(new BoolSetting.Builder()
        .name("only-on-ground")
        .description("仅在地面时触发（安全保护）")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> autoToggle = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-toggle")
        .description("攻击一次后自动关闭")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
        .name("range")
        .description("攻击范围")
        .defaultValue(4.5)
        .build()
    );

    // --- Spoofing ---
    private final Setting<Double> height = sgSpoof.add(new DoubleSetting.Builder()
        .name("spoof-height")
        .description("伪造高度。150格足以碎掉全套下界合金。")
        .defaultValue(150.0) 
        .min(20.0)
        .sliderMax(300.0)
        .max(2000.0)
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

    private final Setting<Boolean> throughWalls = sgTargeting.add(new BoolSetting.Builder()
        .name("through-walls")
        .description("穿墙攻击 (无视视线检测)")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> ignoreNamed = sgTargeting.add(new BoolSetting.Builder()
        .name("ignore-named")
        .description("不攻击被命名的生物。")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> ignoreTamed = sgTargeting.add(new BoolSetting.Builder()
        .name("ignore-tamed")
        .description("不攻击被驯服的生物 (宠物)。")
        .defaultValue(true)
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
        .description("渲染当前目标")
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

    private int originalSlot = -1;
    private final List<Entity> targets = new ArrayList<>();
    private Entity currentTarget; 

    // --- 新增：状态机和攻击序列变量 ---
    private enum State {
        IDLE,
        ASCENDING,
        DESCENDING,
        ATTACKING,
        LANDING // 新增：安全着陆状态
    }
    private State currentState = State.IDLE;
    private Vec3d startPos;
    private double currentSpoofY;
    private double targetSpoofY;

    public ArmorBreaker() {
        super(AddonTemplate.CATEGORY, "armor-breaker", "一击碎甲");
    }

    @Override
    public void onActivate() {
        // 激活时重置所有状态
        currentState = State.IDLE;
        startPos = null;
        currentSpoofY = 0;
        targetSpoofY = 0;
        originalSlot = -1;
        targets.clear();
        currentTarget = null;
    }

    @Override
    public void onDeactivate() {
        currentState = State.IDLE; // 关闭时重置状态
        currentTarget = null;
        if (originalSlot != -1 && autoSwitch.get() && mc.player != null) {
            mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(originalSlot));
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        // 如果正在执行攻击序列，则由状态机接管
        if (currentState != State.IDLE) {
            handleAttackSequence();
            return;
        }

        // 1. 安全检查
        if (onlyOnGround.get() && !mc.player.isOnGround()) {
            currentTarget = null;
            return;
        }

        // 2. 查找目标
        targets.clear();
        TargetUtils.getList(targets, this::targetCheck, SortPriority.ClosestAngle, 1);
        
        if (targets.isEmpty()) {
            currentTarget = null;
            return;
        }
        
        currentTarget = targets.get(0);

        // 3. 切换武器
        if (!checkAndSwapWeapon()) return;

        // 4. 初始化并开始攻击序列
        startArmorBreak(currentTarget);
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

    private boolean checkAndSwapWeapon() {
        if (mc.player.getMainHandStack().getItem() instanceof MaceItem) return true;

        if (autoSwitch.get()) {
            FindItemResult mace = InvUtils.find(itemStack -> itemStack.getItem() instanceof MaceItem, 0, 8);
            if (mace.found()) {
                originalSlot = mc.player.getInventory().selectedSlot;
                InvUtils.swap(mace.slot(), false);
                return true;
            }
        }
        return false;
    }

    private void startArmorBreak(Entity target) {
        if (currentState != State.IDLE) return;

        info("开始对 " + EntityUtils.getName(target) + " 执行碎甲攻击...");
        startPos = mc.player.getPos();
        currentSpoofY = startPos.y;
        targetSpoofY = startPos.y + height.get();

        // 冻结客户端移动
        mc.player.setVelocity(0, 0, 0);

        // 发送地面基准包，并切换到上升状态
        sendPacket(startPos.x, startPos.y, startPos.z, true);
        currentState = State.ASCENDING;
    }

    private void handleAttackSequence() {
        // 如果目标丢失或死亡，则中止序列
        if (currentTarget == null || !currentTarget.isAlive()) {
            error("目标丢失，攻击中止。");
            resetAndToggle();
            return;
        }

        // 定义安全的每tick移动步长
        double maxStep = 9.0;

        switch (currentState) {
            case ASCENDING -> {
                currentSpoofY = Math.min(currentSpoofY + maxStep, targetSpoofY);
                sendPacket(startPos.x, currentSpoofY, startPos.z, false);

                if (currentSpoofY >= targetSpoofY) {
                    currentState = State.DESCENDING; // 上升完成，切换到下降
                }
            }
            case DESCENDING -> {
                double hitY = startPos.y + 1.5; // 攻击锚点
                currentSpoofY = Math.max(currentSpoofY - maxStep, hitY);
                sendPacket(startPos.x, currentSpoofY, startPos.z, false);

                if (currentSpoofY <= hitY) {
                    currentState = State.ATTACKING; // 下降完成，准备攻击
                }
            }
            case ATTACKING -> {
                // 1. 强制锚点与旋转
                double yaw = Rotations.getYaw(currentTarget);
                double pitch = Rotations.getPitch(currentTarget);
                mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.LookAndOnGround((float) yaw, (float) pitch, false, mc.player.horizontalCollision));

                // 2. 攻击
                mc.getNetworkHandler().sendPacket(PlayerInteractEntityC2SPacket.attack(currentTarget, mc.player.isSneaking()));
                mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));

                // 3. 切换到着陆状态，在下一个tick执行着陆
                currentState = State.LANDING;
            }
            case LANDING -> {
                // 在新的tick中执行安全着陆
                sendPacket(startPos.x, startPos.y, startPos.z, true); // 确认落地
                info("已对 " + EntityUtils.getName(currentTarget) + " 执行碎甲攻击。"); // 反馈
                resetAndToggle(); // 结束并重置
            }
        }
    }

    private void resetAndToggle() {
        currentState = State.IDLE;
        startPos = null; // 清理起始位置
        if (autoToggle.get()) {
            if (originalSlot != -1 && autoSwitch.get()) {
                InvUtils.swap(originalSlot, false);
                originalSlot = -1;
            }
            toggle();
        }
    }

    private void sendPacket(double x, double y, double z, boolean onGround) {
        mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
            x, y, z, onGround, mc.player.horizontalCollision
        ));
    }

    private boolean targetCheck(Entity entity) {
        if (!(entity instanceof LivingEntity) || !entity.isAlive()) return false;
        if (entity.equals(mc.player)) return false;
        if (mc.player.distanceTo(entity) > range.get()) return false;

        // 增加视线检查
        if (!throughWalls.get() && !mc.player.canSee(entity)) {
            return false;
        }

        // 玩家检查
        if (entity instanceof PlayerEntity p) {
            if (!players.get()) return false;
            if (p.isCreative()) return false;

            // 优先检查好友系统
            if (!Friends.get().shouldAttack(p)) return false;

            // 白名单/黑名单逻辑
            String name = p.getGameProfile().getName();
            // 修复：分割后去除每个名字前后的空格，防止匹配失败
            List<String> players = Arrays.stream(playerList.get().split(","))
                                         .map(String::trim)
                                         .collect(Collectors.toList());
            switch (listMode.get()) {
                case Whitelist -> {
                    return players.contains(name);
                }
                case Blacklist -> {
                    return !players.contains(name);
                }
            }
            return true; // Off 模式或检查通过
        }
        
        // 其他实体检查 (非玩家)
        if (ignoreNamed.get() && entity.hasCustomName()) {
            return false;
        }

        // 宠物检查
        if (ignoreTamed.get() && entity instanceof TameableEntity && ((TameableEntity) entity).isTamed()) {
            return false;
        }

        return entities.get().contains(entity.getType());
    }
}