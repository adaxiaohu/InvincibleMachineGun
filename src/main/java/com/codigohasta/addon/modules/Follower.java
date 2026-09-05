package com.codigohasta.addon.modules;

import com.codigohasta.addon.AddonTemplate;
import com.codigohasta.addon.mixin.InventoryAccessor;
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
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.GameType;

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
    private final Setting<Boolean> preventGround = this.sgGeneral.add(new BoolSetting.Builder().name("不追落地者").defaultValue(true).build());
    
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
        this.mc.options.keyShift.setDown(false);
        this.mc.options.keyJump.setDown(false);
    }

    // --- 索敌 ---
    private void findTarget() {
        this.targets.clear();
        Entity bestTarget = null;
        double closestDiff = Double.MAX_VALUE;

      
        for (Entity entity : mc.level.entitiesForRendering()) {
         
            if (entity == mc.player || !entity.isAlive() || !(entity instanceof LivingEntity)) continue;

         
            double dist = mc.player.distanceTo(entity);
            if (dist > range.get()) continue;

            
            if (!entities.get().contains(entity.getType())) continue;

            
            if (entity instanceof Player player) {
               
                if (!Friends.get().shouldAttack(player)) continue;

                
                GameType gm = getGameMode(player);
                
               
                if (gm == GameType.CREATIVE && !attackCreative.get()) continue;
                if (gm == GameType.SURVIVAL && !attackSurvival.get()) continue;
                if (gm == GameType.ADVENTURE && !attackAdventure.get()) continue;
                if (gm == GameType.SPECTATOR) continue; 
            }

            
            if (dist < closestDiff) {
                closestDiff = dist;
                bestTarget = entity;
            }
        }

        if (bestTarget != null) {
            this.targets.add(bestTarget);
        }
    }

    
    private GameType getGameMode(Player p) {
        if (mc.getConnection() == null) return GameType.DEFAULT_MODE;
        
        
        PlayerInfo entry = mc.getConnection().getPlayerInfo(p.getUUID());
        if (entry == null) return GameType.DEFAULT_MODE;
        
        GameType gm = entry.getGameMode();
      
        return (gm != null) ? gm : GameType.DEFAULT_MODE;
    }

    @EventHandler
    private void onRender3d(Render3DEvent event) {
        if (this.mc.player == null || !this.mc.player.isAlive()) return;
        if (this.dynamic.get() || this.targets.isEmpty()) this.findTarget();

        if (this.targets.isEmpty()) {
            CamUtils.rem(this);
            this.mc.options.keyShift.setDown(false);
            this.mc.options.keyJump.setDown(false);
        } else {
            CamUtils.add(this);
            Entity primary = this.targets.getFirst();

            if (!this.onlyAir.get() || !this.mc.player.onGround()) {
                if (!this.preventGround.get() || !primary.onGround()) {
                    
                    
                    // 低头 (Pitch > 0) -> 按下 Sneak (Shift) 
                    this.mc.options.keyShift.setDown(CamUtils.pitch() > 0.0F);
                    // 抬头 (Pitch <= 0) -> 按下 Jump (空格) 
                    this.mc.options.keyJump.setDown(CamUtils.pitch() <= 0.0F);

                    // 身体锁定目标
                    MeteorClient.mc.player.setYRot((float) Rotations.getYaw(primary));
                    MeteorClient.mc.player.setXRot(primary.onGround() && this.preventGround.get() ? -90.0F : (float) Rotations.getPitch(primary, Target.Body));
                }
            }
        }

        // ESP 渲染
        if (this.render.get() && !this.targets.isEmpty() && this.targets.getFirst() != null) {
            Entity target = this.targets.getFirst();
            Vec3 lerped = target.getPosition(event.tickDelta);
            double x = lerped.x - target.getX();
            double y = lerped.y - target.getY();
            double z = lerped.z - target.getZ();
            AABB box = target.getBoundingBox();
            event.renderer.box(x + box.minX, y + box.minY, z + box.minZ, x + box.maxX, y + box.maxY, z + box.maxZ, this.sideColor.get(), this.lineColor.get(), this.shapeMode.get(), 0);
        }
    }

    @EventHandler
    private void onTick(Post event) {
        if (this.mc.player == null) return;
        
        int countdown = !this.targets.isEmpty() ? this.waspSprint.get() : this.fireworkTime.get();
        
        
        // 1.21.11 这么写的: isGliding() + getEquippedStack
        if (this.mc.player.isFallFlying() && this.mc.player.getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA)) {
            if (this.timer < 0 && this.mc.options.keyUp.isDown()) {
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
        return !this.targets.isEmpty() ? EntityUtils.getName(this.targets.getFirst()) : null;
    }

    void quickUse(Item item) {
        FindItemResult result = InvUtils.find(item);
        if (result.found()) {
            int selectedSlot = ((InventoryAccessor) this.mc.player.getInventory()).getSelectedSlot();
            int itemSlot = result.slot();
            boolean wasHeld = result.isMainHand();
            if (!wasHeld) InvUtils.quickSwap().fromId(selectedSlot).to(itemSlot);
            this.mc.gameMode.useItem(this.mc.player, InteractionHand.MAIN_HAND);
            if (!wasHeld) InvUtils.quickSwap().fromId(selectedSlot).to(itemSlot);
        }
    }
}