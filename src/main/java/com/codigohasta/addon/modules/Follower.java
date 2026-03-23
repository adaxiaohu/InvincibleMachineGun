package com.codigohasta.addon.modules;

import com.codigohasta.addon.AddonTemplate;
import com.codigohasta.addon.utils.CamUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent.Post;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.entity.EntityUtils;
import meteordevelopment.meteorclient.utils.entity.SortPriority;
import meteordevelopment.meteorclient.utils.entity.Target;
import meteordevelopment.meteorclient.utils.entity.TargetUtils;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;

public class Follower extends Module {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    private final SettingGroup sgTargets = this.settings.createGroup("目标选择");
    private final SettingGroup sgRender = this.settings.createGroup("渲染设置");

    private final List<Entity> targets = new ArrayList<>();

    // --- 实体列表 ---
    private final Setting<Set<EntityType<?>>> entities = sgTargets.add(new EntityTypeListSetting.Builder()
        .name("目标实体")
        .description("选择哪些种类的生物会被视为目标")
        .defaultValue(Set.of(EntityType.PLAYER))
        .build()
    );

    // --- 游戏模式  ---
    private final Setting<Boolean> attackSurvival = sgTargets.add(new BoolSetting.Builder()
        .name("攻击生存模式")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> attackCreative = sgTargets.add(new BoolSetting.Builder()
        .name("攻击创造模式")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> attackAdventure = sgTargets.add(new BoolSetting.Builder()
        .name("攻击冒险模式")
        .defaultValue(true)
        .build()
    );

    // --- 基础设置 ---
    private final Setting<SortPriority> priority = this.sgGeneral.add(new EnumSetting.Builder<SortPriority>().name("优先级").defaultValue(SortPriority.ClosestAngle).build());
    private final Setting<Double> range = this.sgGeneral.add(new DoubleSetting.Builder().name("检测范围").defaultValue(50.0).range(0.0, 192.0).build());
    private final Setting<Boolean> dynamic = this.sgGeneral.add(new BoolSetting.Builder().name("动态索敌").defaultValue(true).build());
    private final Setting<Boolean> onlyAir = this.sgGeneral.add(new BoolSetting.Builder().name("仅限空中").defaultValue(true).build());
    private final Setting<Boolean> preventGround = this.sgGeneral.add(new BoolSetting.Builder().name("防落地").defaultValue(true).build());
    
    // --- 渲染设置 ---
    private final Setting<Boolean> render = this.sgRender.add(new BoolSetting.Builder().name("渲染").defaultValue(true).build());
    private final Setting<ShapeMode> shapeMode = this.sgRender.add(new EnumSetting.Builder<ShapeMode>().name("渲染模式").defaultValue(ShapeMode.Both).visible(this.render::get).build());
    private final Setting<SettingColor> sideColor = this.sgRender.add(new ColorSetting.Builder().name("边颜色").defaultValue(new SettingColor(160, 0, 225, 35)).build());
    private final Setting<SettingColor> lineColor = this.sgRender.add(new ColorSetting.Builder().name("轮廓颜色").defaultValue(new SettingColor(255, 255, 255, 50)).build());
    
    private final Setting<Integer> fireworkTime = this.sgGeneral.add(new IntSetting.Builder().name("普通烟花间隔").min(0).sliderMax(200).defaultValue(50).build());
    private final Setting<Integer> waspSprint = this.sgGeneral.add(new IntSetting.Builder().name("追猎烟花间隔").min(0).sliderMax(200).defaultValue(20).build());

    private int timer;

    public Follower() {
       
        super(AddonTemplate.CATEGORY, "Follower", "追人，娱乐功能");
    }

    @Override
    public void onActivate() {
        this.targets.clear();
        this.timer = 0;
    }

    @Override
    public void onDeactivate() {
        this.targets.clear();
        CamUtils.rem(this);
        this.mc.options.sneakKey.setPressed(false);
        this.mc.options.jumpKey.setPressed(false);
    }

    private void findTarget() {
        TargetUtils.getList(this.targets, e -> {
            if (e == mc.player || !e.isAlive() || !(e instanceof LivingEntity)) return false;
            if (!entities.get().contains(e.getType())) return false;
            if (e instanceof PlayerEntity p) {
                if (!Friends.get().shouldAttack(p)) return false;
                GameMode gm = getGameMode(p);
                if (gm == GameMode.SURVIVAL && !attackSurvival.get()) return false;
                if (gm == GameMode.CREATIVE && !attackCreative.get()) return false;
                if (gm == GameMode.ADVENTURE && !attackAdventure.get()) return false;
                if (gm == GameMode.SPECTATOR) return false;
            }
            return this.mc.player.distanceTo(e) <= this.range.get();
        }, this.priority.get(), 1);
    }

    private GameMode getGameMode(PlayerEntity p) {
        if (mc.getNetworkHandler() == null) return GameMode.DEFAULT;
        PlayerListEntry entry = mc.getNetworkHandler().getPlayerListEntry(p.getUuid());
        if (entry == null) return GameMode.DEFAULT;
        return entry.getGameMode();
    }

    @EventHandler
    private void onRender3d(Render3DEvent event) {
        if (this.mc.player == null || !this.mc.player.isAlive()) return;
        if (this.dynamic.get() || this.targets.isEmpty()) this.findTarget();

        if (this.targets.isEmpty()) {
            CamUtils.rem(this);
            this.mc.options.sneakKey.setPressed(false);
            this.mc.options.jumpKey.setPressed(false);
        } else {
            CamUtils.add(this);
            // 修复错误 2: getFirst() 替换为 get(0)
            Entity primary = this.targets.get(0);

            if (!this.onlyAir.get() || !this.mc.player.isOnGround()) {
                if (!this.preventGround.get() || !primary.isOnGround()) {
                    this.mc.options.sneakKey.setPressed(CamUtils.pitch() > 0.0F);
                    this.mc.options.jumpKey.setPressed(CamUtils.pitch() <= 0.0F);

                    MeteorClient.mc.player.setYaw((float) Rotations.getYaw(primary));
                    MeteorClient.mc.player.setPitch(primary.isOnGround() && this.preventGround.get() ? -90.0F : (float) Rotations.getPitch(primary, Target.Body));
                }
            }
        }

        // 修复错误 3 & 4: getFirst() 替换为 get(0)
        if (this.render.get() && !this.targets.isEmpty() && this.targets.get(0) != null) {
            Entity target = this.targets.get(0);
            Vec3d lerped = target.getLerpedPos(event.tickDelta);
            double x = lerped.x - target.getX();
            double y = lerped.y - target.getY();
            double z = lerped.z - target.getZ();
            Box box = target.getBoundingBox();
            event.renderer.box(x + box.minX, y + box.minY, z + box.minZ, x + box.maxX, y + box.maxY, z + box.maxZ, this.sideColor.get(), this.lineColor.get(), this.shapeMode.get(), 0);
        }
    }

    @EventHandler
    private void onTick(Post event) {
        if (this.mc.player == null) return;
        
        int countdown = !this.targets.isEmpty() ? this.waspSprint.get() : this.fireworkTime.get();
        
        if (this.mc.player.isGliding() && this.mc.player.getEquippedStack(EquipmentSlot.CHEST).isOf(Items.ELYTRA)) {
            if (this.timer < 0 && this.mc.options.forwardKey.isPressed()) {
                this.quickUse(Items.FIREWORK_ROCKET);
                this.timer = countdown;
            }
            this.timer--;
        } else {
            this.timer = -1;
        }
    }

    @Override
    public String getInfoString() {
        // 修复错误 5: getFirst() 替换为 get(0)
        return !this.targets.isEmpty() ? EntityUtils.getName(this.targets.get(0)) : null;
    }

    void quickUse(Item item) {
        FindItemResult result = InvUtils.find(item);
        if (result.found()) {
        
            int selectedSlot = this.mc.player.getInventory().selectedSlot;
            int itemSlot = result.slot();
            boolean wasHeld = result.isMainHand();
            
            if (!wasHeld) InvUtils.quickSwap().fromId(selectedSlot).to(itemSlot);
            
            this.mc.interactionManager.interactItem(this.mc.player, Hand.MAIN_HAND);
            
            if (!wasHeld) InvUtils.quickSwap().fromId(selectedSlot).to(itemSlot);
        }
    }
}