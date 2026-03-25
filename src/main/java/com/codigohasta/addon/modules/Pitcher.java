//最开始抄袭了wurst，然后又抄袭了Trouser-Streak的ProjectileLauncher
package com.codigohasta.addon.modules;

import com.codigohasta.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import meteordevelopment.meteorclient.mixininterface.IPlayerMoveC2SPacket;
import net.minecraft.item.Items;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.util.Hand;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class Pitcher extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgTP = settings.createGroup("超远TP设置(copy in Trouser-Streak");
    public enum Mode { Vanilla, Paper }
    public enum TPMode { Reverse, Forward }
    private final SettingGroup sgAuto = settings.createGroup("连射");
     private final SettingGroup sgTotem = settings.createGroup("图腾绕过");
    private final SettingGroup sgAim = settings.createGroup("自瞄");
    private final SettingGroup sgRender = settings.createGroup("渲染");

    private final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("兼容模式")
        .description("Vanilla = 22格, Paper = 最高149格")
        .defaultValue(Mode.Paper)
        .build()
    );

    private final Setting<TPMode> tpmode = sgGeneral.add(new EnumSetting.Builder<TPMode>()
        .name("TP方向")
        .description("向后加速伤害更高，向前投掷距离更远")
        .defaultValue(TPMode.Reverse)
        .build()
    );

    private final Setting<List<net.minecraft.item.Item>> projectileItems = sgGeneral.add(new ItemListSetting.Builder()
        .name("适用物品")
        .defaultValue(Items.BOW, Items.TRIDENT, Items.ENDER_PEARL, Items.SPLASH_POTION, Items.EXPERIENCE_BOTTLE, Items.SNOWBALL)
        .build()
    );

    private final Setting<Double> paperDistance = sgTP.add(new DoubleSetting.Builder()
        .name("Paper最大距离")
        .defaultValue(149.0)
        .sliderMax(169.0)
        .visible(() -> mode.get() == Mode.Paper)
        .build()
    );

    private final Setting<Integer> paperPackets = sgTP.add(new IntSetting.Builder()
        .name("Paper堆叠包数")
        .defaultValue(15)
        .min(1)
        .sliderMax(20)
        .visible(() -> mode.get() == Mode.Paper)
        .build()
    );

    // ================= 基础高伤与环境检测设置 =================
    private final Setting<Double> strength = sgGeneral.add(new DoubleSetting.Builder()
        .name("力量")
        .description("理论上最大支持10，似乎在paper服可以拉更大")
        .defaultValue(10.0)
        .min(0.1)
        .sliderMax(20)
        .build()
    );

    private final Setting<Boolean> vertical = sgGeneral.add(new BoolSetting.Builder()
        .name("垂直修正")
        .description("开启后就可以自由角度射击")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> useOffset = sgGeneral.add(new BoolSetting.Builder()
        .name("防摔")
        .description("防止自己摔死。这样可以飞在天上随意射击，不会受到摔伤。")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> smartStrength = sgGeneral.add(new BoolSetting.Builder()
        .name("自动空间检测")
        .description("向后方发送射线，如果身后2格高的空间有方块阻挡，自动缩短发包距离，减少无效箭。")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> yeetTridents = sgGeneral.add(new BoolSetting.Builder()
        .name("三叉戟模式")
        .description("是否对三叉戟也应用高伤。")
        .defaultValue(false)
        .build()
    );

    // ================= 连射设置 =================
    private final Setting<Boolean> autoShoot = sgAuto.add(new BoolSetting.Builder()
        .name("开启连射")
        .description("自动蓄力并松开射击，没敌机关枪。")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> charge = sgAuto.add(new IntSetting.Builder()
        .name("蓄力时间")
        .description("蓄力多少Tick后自动发射。高伤模式下用4。")
        .defaultValue(4)
        .min(1)
        .sliderMax(20)
        .visible(autoShoot::get)
        .build()
    );

    private final Setting<Boolean> onlyWhenHoldingRightClick = sgAuto.add(new BoolSetting.Builder()
        .name("仅按右键时连射")
        .description("开启后只有长按右键才会连射。关闭则自动狂射。")
        .defaultValue(true)
        .visible(autoShoot::get)
        .build()
    );

    private final Setting<Boolean> totemBypass = sgTotem.add(new BoolSetting.Builder()
        .name("破图腾双发扳机")
        .description("释放时自动连射两箭。第一箭取基础力量破图腾，第二箭高伤在无敌帧内秒杀。")
        .defaultValue(false)
        .build()
    );

    private final Setting<Double> bypassStrength = sgTotem.add(new DoubleSetting.Builder()
        .name("第二箭力量")
        .description("第二箭的力量倍数（必须比基础力量高才能产生伤害差）,也就是第一箭力量要低于第二箭力量。")
        .defaultValue(20.0)
        .min(0.1)
        .sliderMax(30.0)
        .visible(totemBypass::get)
        .build()
    );

    private final Setting<Integer> bypassDelay = sgTotem.add(new IntSetting.Builder()
        .name("第二箭延迟")
        .description("第一箭射出后，自动蓄力多少Tick再射第二箭（推荐 4）。")
        .defaultValue(4)
        .min(1)
        .sliderMax(10)
        .visible(totemBypass::get)
        .build()
    );

    // ================= 自瞄设置 =================
    public enum TargetPriority { Angle, Distance, Health }

    private final Setting<Boolean> aimbot = sgAim.add(new BoolSetting.Builder()
        .name("开启自瞄")
        .description("直接修改客户端视角，自动锁定目标。")
        .defaultValue(false)
        .build()
    );

    private final Setting<TargetPriority> priority = sgAim.add(new EnumSetting.Builder<TargetPriority>()
        .name("优先度")
        .description("自瞄选择目标的优先逻辑。")
        .defaultValue(TargetPriority.Angle)
        .visible(aimbot::get)
        .build()
    );

    private final Setting<Double> aimRange = sgAim.add(new DoubleSetting.Builder()
        .name("自瞄范围")
        .description("自动锁定目标的最大距离。")
        .defaultValue(40.0)
        .min(1.0)
        .sliderMax(100.0)
        .visible(aimbot::get)
        .build()
    );

    private final Setting<Boolean> aimOnlyWhenHoldingRightClick = sgAim.add(new BoolSetting.Builder()
        .name("仅拉弓时自瞄")
        .description("只有在准备射击（按住右键或自动射击）时才锁定视角。")
        .defaultValue(true)
        .visible(aimbot::get)
        .build()
    );

    private final Setting<Boolean> ignoreWalls = sgAim.add(new BoolSetting.Builder()
        .name("忽略墙后目标")
        .description("开启后，只有当目标暴露在视线内时才会自瞄。")
        .defaultValue(true)
        .visible(aimbot::get)
        .build()
    );

    private final Setting<Set<EntityType<?>>> entities = sgAim.add(new EntityTypeListSetting.Builder()
        .name("自瞄目标")
        .description("选择你要自动瞄准的实体类型。")
        .defaultValue(EntityType.PLAYER)
        .visible(aimbot::get)
        .build()
    );

    // ================= 渲染设置 =================
    private final Setting<Boolean> doRender = sgRender.add(new BoolSetting.Builder()
        .name("开启渲染")
        .description("开启物理预测曲线和目标框渲染。")
        .defaultValue(true)
        .build()
    );

    private final Setting<SettingColor> boxColor = sgRender.add(new ColorSetting.Builder()
        .name("目标框颜色")
        .description("被锁定或瞄准目标的渲染框颜色。")
        .defaultValue(new SettingColor(255, 0, 0, 100))
        .visible(doRender::get)
        .build()
    );
    // ===================================================

    private boolean isShooting = false;
    private boolean forcedPressed = false;
    private Entity currentTarget = null; 
    private boolean isSecondShot = false;
  private int totemStep = 0; // 0: 空闲, 1: 正在蓄力第二箭
    private int bypassTimer = -1;
  

    public Pitcher() {
        super(AddonTemplate.CATEGORY, "大力投手", "大力投手，可以把自己后退或者前进距离，来丢东西。可以把没影珍珠丢出15000格。来自trouser-streak，被我抄袭并且做了修改。没那么好用，比较混乱，娱乐功能");
    }

    @Override
    public void onDeactivate() {
        if (forcedPressed) {
            mc.options.useKey.setPressed(false);
            forcedPressed = false;
        }
        totemStep = 0;
        bypassTimer = -1;
        isShooting = false;
    }

   @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        // 识别当前哪只手拿着远程武器
        boolean validMain = isValidItem(mc.player.getMainHandStack());
        boolean validOff = isValidItem(mc.player.getOffHandStack());
        boolean hasValidItem = validMain || validOff;
        Hand hand = validMain ? Hand.MAIN_HAND : Hand.OFF_HAND;

        // --- 图腾双发状态机修复 ---
        if (totemBypass.get() && totemStep == 1) {
            // 倒计时刚开始：强制触发右键拉弓动作
            if (bypassTimer == bypassDelay.get()) {
                mc.interactionManager.interactItem(mc.player, hand);
            }

            if (bypassTimer > 0) {
                // 蓄力期间：强制锁定右键按下状态
                mc.options.useKey.setPressed(true);
                bypassTimer--;
            } 
            else if (bypassTimer == 0) {
                // 倒计时结束：执行射击
                // 修复：移除 mc.player.isUsingItem() 判断，防止因为延迟导致的失效
                mc.interactionManager.stopUsingItem(mc.player);
                
                // 关键修复：强制松开按键并重置状态机，防止死循环拉弓
                mc.options.useKey.setPressed(false);
                totemStep = 0; 
                bypassTimer = -1;
            }
            return; // 补刀期间跳过其他逻辑
        }

        // --- 2. 自瞄逻辑 ---
        if (aimbot.get() && hasValidItem) {
            currentTarget = null; // 重置当前目标
            boolean isPressingRightClick = mc.options.useKey.isPressed() || forcedPressed;

            if (!aimOnlyWhenHoldingRightClick.get() || isPressingRightClick) {
                Entity bestTarget = null;
                double bestScore = Double.MAX_VALUE;

                for (Entity entity : mc.world.getEntities()) {
                    if (entity == mc.player) continue;
                    if (!(entity instanceof LivingEntity living) || living.isDead() || living.getHealth() <= 0) continue;
                    if (!entities.get().contains(entity.getType())) continue;
                    if (entity instanceof PlayerEntity player && (player.isCreative() || player.isSpectator() || Friends.get().isFriend(player))) continue;

                    double dist = mc.player.distanceTo(entity);
                    if (dist > aimRange.get()) continue;
                    if (ignoreWalls.get() && !mc.player.canSee(entity)) continue;

                    double score = 0;
                    switch (priority.get()) {
                        case Distance: score = dist; break;
                        case Health: score = living.getHealth(); break;
                        case Angle:
                            Vec3d targetPos = entity.getBoundingBox().getCenter();
                            double dX = targetPos.x - mc.player.getX();
                            double dY = targetPos.y - mc.player.getEyeY();
                            double dZ = targetPos.z - mc.player.getZ();
                            double yawDiff = Math.toDegrees(Math.atan2(dZ, dX)) - 90.0 - mc.player.getYaw();
                            double pitchDiff = -Math.toDegrees(Math.atan2(dY, Math.sqrt(dX * dX + dZ * dZ))) - mc.player.getPitch();
                            score = Math.abs(MathHelper.wrapDegrees((float)yawDiff)) + Math.abs(MathHelper.wrapDegrees((float)pitchDiff));
                            break;
                    }

                    if (score < bestScore) {
                        bestScore = score;
                        bestTarget = entity;
                    }
                }

                if (bestTarget != null) {
                    currentTarget = bestTarget;
                    Vec3d targetPos = bestTarget.getBoundingBox().getCenter();
                    double dX = targetPos.x - mc.player.getX();
                    double dY = targetPos.y - mc.player.getEyeY();
                    double dZ = targetPos.z - mc.player.getZ();
                    double distXZ = Math.sqrt(dX * dX + dZ * dZ);
                    mc.player.setYaw((float) Math.toDegrees(Math.atan2(dZ, dX)) - 90.0F);
                    mc.player.setPitch((float) -Math.toDegrees(Math.atan2(dY, distXZ)));
                }
            }
        }

        // --- 3. 连射控制 ---
        if (totemStep == 0) {
            handleNormalAutoShoot(hasValidItem);
        }
    }

    private void handleNormalAutoShoot(boolean hasValidItem) {
        if (!autoShoot.get() || !hasValidItem) return;

        ItemStack activeStack = mc.player.getActiveItem();
        // 进食/喝药保护 (1.21.11 字符串安全判断)
        if (mc.player.isUsingItem() && !isValidItem(activeStack)) {
            if (forcedPressed) {
                mc.options.useKey.setPressed(false);
                forcedPressed = false;
            }
            return;
        }

        // 第一箭拉弓逻辑
        if (!onlyWhenHoldingRightClick.get() && !mc.player.isUsingItem()) {
            mc.options.useKey.setPressed(true);
            forcedPressed = true;
        }

        if (mc.player.isUsingItem() && isValidItem(activeStack)) {
            if (mc.player.getItemUseTime() >= charge.get()) {
                mc.interactionManager.stopUsingItem(mc.player);
            }
        }
    }

    @EventHandler
    private void onSendPacket(PacketEvent.Send event) {
        if (isShooting || mc.player == null) return;

        // 拦截弓箭释放包
        if (event.packet instanceof PlayerActionC2SPacket p && p.getAction() == PlayerActionC2SPacket.Action.RELEASE_USE_ITEM) {
            if (isValidProjectile(mc.player.getActiveItem())) {
                event.cancel();
                processShoot(event.packet);
            }
        }
        // 拦截珍珠/药水等右键瞬发包
        else if (event.packet instanceof PlayerInteractItemC2SPacket p) {
            ItemStack stack = (p.getHand() == Hand.MAIN_HAND) ? mc.player.getMainHandStack() : mc.player.getOffHandStack();
            if (isValidProjectile(stack)) {
                event.cancel();
                processShoot(event.packet);
            }
        }
    }

    private void processShoot(net.minecraft.network.packet.Packet<?> originalPacket) {
        
        if (mc.player == null || mc.getNetworkHandler() == null) return;

        isShooting = true;
        
        // 判定力量：如果状态机是 1，说明这一下是补刀，用高伤
        double currentStr = (totemStep == 1) ? bypassStrength.get() : strength.get();
        // 1. 发送冲刺包（Wurst 高伤触发器）
        mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_SPRINTING));

        // 2. 获取基础坐标与方向 (1.21.11 安全坐标)
        double x = mc.player.getX(), y = mc.player.getY(), z = mc.player.getZ();
        Vec3d startPos = new Vec3d(x, y, z);
        Vec3d lookVec = mc.player.getRotationVector();

        // 3. 计算当前箭矢力量 (如果是第二箭则用破图腾力量)
        currentStr = (isSecondShot && totemBypass.get()) ? bypassStrength.get() : strength.get();
        // 如果开启了 Paper 模式，力量距离上限自动解锁至 149
        double maxDist = mode.get() == Mode.Vanilla ? 21.9 : paperDistance.get();
        double adjustedStrength = (currentStr / 10.0) * Math.sqrt(500.0);
        
        // 限制力量不超出当前模式的最大 TP 距离
        adjustedStrength = Math.min(adjustedStrength, maxDist);

        // 4. 计算位移方向 (根据 TP 模式决定向后还是向前)
        Vec3d dir = (tpmode.get() == TPMode.Reverse) ? lookVec.multiply(-1) : lookVec;
        Vec3d spoofOffset = new Vec3d(dir.x * adjustedStrength, (vertical.get() ? dir.y * adjustedStrength : 0), dir.z * adjustedStrength);

        // 5. 自动空间检测 (Smart Strength)
        if (smartStrength.get()) {
            double safeDist = getSafeSpoofDistance(startPos, spoofOffset);
            double adjustedDist = Math.max(0.01, safeDist - 0.5);
            if (adjustedDist < spoofOffset.length()) {
                spoofOffset = spoofOffset.normalize().multiply(adjustedDist);
            }
        }

        // 目标 TP 点
        Vec3d targetPos = startPos.add(spoofOffset);

        // 6. [核心绕过] 堆叠包预热 (Paper 模式发送 15+ 个包填充缓冲区)
        int spam = mode.get() == Mode.Vanilla ? 4 : paperPackets.get();
        for (int i = 0; i < spam; i++) {
            // 规则：1.21.11 必须包含 horizontalCollision
            mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.OnGroundOnly(true, mc.player.horizontalCollision));
        }

        // 7. [核心绕过] 瞬间位移序列
        // A. 飞到目标点
        sendMovePacket(targetPos);

        // B. 如果是向前 TP，则在远处发包（珍珠瞬移逻辑）；如果是向后 TP，则先回传
        if (tpmode.get() == TPMode.Forward) {
            mc.getNetworkHandler().sendPacket(originalPacket);
        }

        // C. 飞回起点
        sendMovePacket(startPos);

        // D. 如果是向后 TP（经典 32k 弓逻辑），则在回传后立刻发包，利用瞬间动量差
        if (tpmode.get() == TPMode.Reverse) {
            mc.getNetworkHandler().sendPacket(originalPacket);
        }

        // 8. [核心同步] 发送极小偏移包刷位置同步，防止被 Paper 判定为 Invalid Teleport 拉回
        // 在 y 轴增加 0.001 并随机水平偏移 0.05
        Vec3d syncPos = startPos.add((Math.random() - 0.5) * 0.05, 0.001, (Math.random() - 0.5) * 0.05);
        sendMovePacket(syncPos);
        mc.player.setPosition(syncPos.x, syncPos.y, syncPos.z);

        // 9. [防摔/防拉回] 垂直偏移补丁
        if (vertical.get() && useOffset.get() && spoofOffset.y > 0) {
            sendMovePacket(startPos.add(0, 0.01, 0));
        }

        isShooting = false;

        // 10. 破图腾状态切换逻辑
        if (totemBypass.get()) {
            // 如果刚刚完成的是正常射击（第一箭）
            if (totemStep == 0) {
                totemStep = 1;
                bypassTimer = bypassDelay.get(); 
            } else {
                // 如果刚刚完成的是补刀箭（第二箭），重置回到空闲
                totemStep = 0;
                bypassTimer = -1;
            }
        }
    }

     // ================= 轨迹与渲染 ================
    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (!doRender.get() || mc.player == null || mc.world == null) return;
        if (!isValidItem(mc.player.getMainHandStack()) && !isValidItem(mc.player.getOffHandStack())) return;

        float tickDelta = event.tickDelta;

        // 1. 平滑角度计算视线向量
        float pitchInterp = MathHelper.lerp(tickDelta, mc.player.lastPitch, mc.player.getPitch());
        float yawInterp = MathHelper.lerp(tickDelta, mc.player.lastYaw, mc.player.getYaw());
        
        float radPitch = pitchInterp * 0.017453292F;
        float radYaw = -yawInterp * 0.017453292F;
        float cosYaw = MathHelper.cos(radYaw);
        float sinYaw = MathHelper.sin(radYaw);
        float cosPitch = MathHelper.cos(radPitch);
        float sinPitch = MathHelper.sin(radPitch);
        
        // 这里的方向向量 lookVec 是渲染用的平滑向量
        Vec3d lookVec = new Vec3d((double)(sinYaw * cosPitch), (double)(-sinPitch), (double)(cosYaw * cosPitch));

        // 2. 动量计算与颜色警报
        double baseStr = (strength.get() / 10.0) * Math.sqrt(500.0);
        Vec3d spoofOffset = new Vec3d(-lookVec.x * baseStr, vertical.get() ? -lookVec.y * baseStr : 0, -lookVec.z * baseStr);
        
        double maxD = spoofOffset.length();
        double finalVelAdd = maxD;
        Color laserColor = new Color(0, 255, 0, 255); // 默认绿色
        
        if (smartStrength.get()) {
            // 空间检测使用当前时刻的真实坐标
            double sDist = getSafeSpoofDistance(new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ()), spoofOffset);
            double adjDist = Math.max(0.01, sDist - 0.5);
            if (adjDist < maxD) finalVelAdd = adjDist;
            
            float ratio = (float) MathHelper.clamp(sDist / maxD, 0.0, 1.0);
            laserColor = new Color((int) ((1.0f - ratio) * 255), (int) (ratio * 255), 0, 255);
        }

        
        double renderX = MathHelper.lerp(tickDelta, mc.player.lastX, mc.player.getX());
        double renderY = MathHelper.lerp(tickDelta, mc.player.lastY, mc.player.getY()) + (mc.player.getEyeY() - mc.player.getY());
        double renderZ = MathHelper.lerp(tickDelta, mc.player.lastZ, mc.player.getZ());
        
        
        Vec3d simPos = new Vec3d(renderX, renderY - 0.1, renderZ);
      
        Vec3d simVel = lookVec.normalize().multiply(3.0 + finalVelAdd);
        
        List<Vec3d> points = new ArrayList<>();
        points.add(simPos);
        Entity hitEnt = null;

       
        for (int step = 0; step < 150; step++) {
            Vec3d nextSimPos = simPos.add(simVel);
            
          
            RaycastContext bCtx = new RaycastContext(simPos, nextSimPos, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc.player);
            HitResult bHit = mc.world.raycast(bCtx);
            if (bHit != null && bHit.getType() == HitResult.Type.BLOCK) {
                nextSimPos = bHit.getPos();
            }

       
            Box segBox = new Box(simPos.x, simPos.y, simPos.z, nextSimPos.x, nextSimPos.y, nextSimPos.z).expand(0.5);
            double nearest = Double.MAX_VALUE;
            
            for (Entity e : mc.world.getOtherEntities(mc.player, segBox)) {
                if (!(e instanceof LivingEntity living) || !living.isAlive()) continue;
             
                if (e instanceof PlayerEntity p && (p.isCreative() || p.isSpectator() || Friends.get().isFriend(p))) continue;

                Optional<Vec3d> clip = e.getBoundingBox().expand(0.3).raycast(simPos, nextSimPos);
                if (clip.isPresent()) {
                    double d = simPos.squaredDistanceTo(clip.get());
                    if (d < nearest) {
                        nearest = d;
                        nextSimPos = clip.get();
                        hitEnt = e; 
                    }
                }
            }

            points.add(nextSimPos);
            if (hitEnt != null || (bHit != null && bHit.getType() == HitResult.Type.BLOCK)) break; 
            
            simPos = nextSimPos;
       
            simVel = simVel.multiply(0.99).subtract(0, 0.05, 0); 
        }

   
        if (points.size() >= 2) {
            Vec3d pStart = points.get(0);
            Vec3d pNext = points.get(1);
            if (pStart.distanceTo(pNext) > 8) {
                Vec3d dir = pNext.subtract(pStart).normalize();
                points.set(0, pStart.add(dir.multiply(1.5)));
            }
        }

      
        for (int renderIdx = 0; renderIdx < points.size() - 1; renderIdx++) {
            Vec3d p1 = points.get(renderIdx);
            Vec3d p2 = points.get(renderIdx + 1);
            event.renderer.line(p1.x, p1.y, p1.z, p2.x, p2.y, p2.z, laserColor);
        }

    
        if (hitEnt != null) {
            event.renderer.box(
                hitEnt.getBoundingBox(),
                boxColor.get(),
                boxColor.get(),
                meteordevelopment.meteorclient.renderer.ShapeMode.Lines,
                0
            );
        }
    }

    private double getSafeSpoofDistance(Vec3d start, Vec3d offset) {
        Vec3d end = start.add(offset);
        double maxDist = offset.length();

        RaycastContext footContext = new RaycastContext(start, end, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc.player);
        HitResult footHit = mc.world.raycast(footContext);

        Vec3d headOffsetVec = new Vec3d(0, 1.8, 0);
        Vec3d headStart = start.add(headOffsetVec);
        Vec3d headEnd = end.add(headOffsetVec);
        RaycastContext headContext = new RaycastContext(headStart, headEnd, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc.player);
        HitResult headHit = mc.world.raycast(headContext);

        double safeDist = maxDist;

        if (footHit != null && footHit.getType() == HitResult.Type.BLOCK) {
            safeDist = Math.min(safeDist, start.distanceTo(footHit.getPos()));
        }
        if (headHit != null && headHit.getType() == HitResult.Type.BLOCK) {
            safeDist = Math.min(safeDist, headStart.distanceTo(headHit.getPos()));
        }
        return safeDist;
    }

    private void sendPos(double x, double y, double z, boolean onGround) {
        mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(x, y, z, onGround, mc.player.horizontalCollision));
    }

    private boolean isValidItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        String name = stack.getItem().toString();
        return name.contains("bow") || (yeetTridents.get() && name.contains("trident"));
    }
    // 1.21.11 专用发包：必须带 horizontalCollision 参数，且使用 meteor$ 前缀
    private void sendMovePacket(Vec3d pos) {
        if (mc.getNetworkHandler() == null) return;
        PlayerMoveC2SPacket packet = new PlayerMoveC2SPacket.PositionAndOnGround(
            pos.x, pos.y, pos.z, false, mc.player.horizontalCollision
        );
        // 标记此包由模块发出，防止被自己的 AntiHunger 或 NoFall 拦截
        ((IPlayerMoveC2SPacket) packet).meteor$setTag(1337);
        mc.player.networkHandler.sendPacket(packet);
    }

    private boolean isValidProjectile(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        // 使用 ItemListSetting 的包含判定，避开 instanceof 坑
        return projectileItems.get().contains(stack.getItem());
    }

}