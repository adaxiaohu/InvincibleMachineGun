package com.codigohasta.addon.modules;

import com.codigohasta.addon.AddonTemplate;
import com.codigohasta.addon.utils.alien.AlienMovementUtil;
import com.codigohasta.addon.utils.alien.AlienPlayerUtil;
import com.codigohasta.addon.utils.alien.AlienRotationUtil;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.util.PlayerInput;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec2f;


public class AlienSprint extends Module {
    public static AlienSprint INSTANCE;

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    public final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("Mode").description("Sprint mode").defaultValue(Mode.Legit).build());
    private final Setting<Boolean> inWaterPause = sgGeneral.add(new BoolSetting.Builder()
        .name("InWaterPause").description("Pause sprinting in water").defaultValue(true).build());
    private final Setting<Boolean> inWebPause = sgGeneral.add(new BoolSetting.Builder()
        .name("InWebPause").description("Pause sprinting in webs").defaultValue(true).build());
    private final Setting<Boolean> sneakingPause = sgGeneral.add(new BoolSetting.Builder()
        .name("SneakingPause").description("Pause sprinting when sneaking").defaultValue(false).build());
    private final Setting<Boolean> blindnessPause = sgGeneral.add(new BoolSetting.Builder()
        .name("BlindnessPause").description("Pause sprinting when blind").defaultValue(false).build());
    private final Setting<Boolean> usingPause = sgGeneral.add(new BoolSetting.Builder()
        .name("UsingPause").description("Pause sprinting when using items").defaultValue(false).build());
    private final Setting<Boolean> lagPause = sgGeneral.add(new BoolSetting.Builder()
        .name("LagPause").description("Pause sprinting after teleport").defaultValue(true).build());

    
    private final Setting<Boolean> pauseOnCollision = sgGeneral.add(new BoolSetting.Builder()
        .name("PauseOnCollision")
        .description("撞墙时停止疾跑（对应 Grim SprintE）。关掉会「靠墙跳被 SprintE 告警」，几乎必炸。")
        .defaultValue(true)
        .build());

    
    private final Setting<Boolean> pauseOnWater = sgGeneral.add(new BoolSetting.Builder()
        .name("PauseOnWater")
        .description("在水里停止疾跑（对应 Grim SprintG）。原版游泳时允许，但普通涉水不行。")
        .defaultValue(true)
        .build());

    
    private final Setting<Boolean> movementSync = sgGeneral.add(new BoolSetting.Builder()
        .name("MovementSync")
        .description("只在真的按前进时疾跑（对应 AlienV4 的 MovementSync）。关掉会出现「站着也疾跑」，可能被 Grim 判 Simulation。")
        .defaultValue(true)
        .build());

    
    private final Setting<Boolean> debug = sgGeneral.add(new BoolSetting.Builder()
        .name("Debug")
        .description("每 tick 打印 yaw / fixYaw / 补偿后输入，用于排查 Grim 告警。默认关闭。")
        .defaultValue(false)
        .build());

    boolean pause = false;

    public AlienSprint() {
        super(AddonTemplate.CATEGORY, "强制疾跑Alien",
            "AlienV4 的 Sprint 等价移植。Rotation 模式：任意方向疾跑 + silent 旋转，"
            + "已按 Grim Simulation 的输入判据做补偿（travel 期换 yaw + 输入反向旋转）。");
        INSTANCE = this;
    }

    private boolean isSprintPressed() {
        return mc.options.forwardKey.isPressed() && !mc.options.backKey.isPressed();
    }

    private boolean isBackPressed() {
        return mc.options.backKey.isPressed() && !mc.options.forwardKey.isPressed();
    }

    private boolean isLeftPressed() {
        return mc.options.leftKey.isPressed() && !mc.options.rightKey.isPressed();
    }

    private boolean isRightPressed() {
        return mc.options.rightKey.isPressed() && !mc.options.leftKey.isPressed();
    }

    
    public void onInputTick() {
        if (mc.player == null || mc.options == null) return;
        if (mode.get() != Mode.Rotation) return;

        if (!AlienRotationUtil.shouldRotate) return;
        if (AlienRotationUtil.inputCompensated) return;

        final var input = mc.player.input;
        if (input == null) return;
        final float mF = input.playerInput.forward() ? 1.0F : (input.playerInput.backward() ? -1.0F : 0.0F);
        final float mS = input.playerInput.left() ? 1.0F : (input.playerInput.right() ? -1.0F : 0.0F);

        if (mF == 0.0F && mS == 0.0F) return; 

        AlienRotationUtil.inputCompensated = true;

        final float realYaw = AlienRotationUtil.travelApplied
            ? AlienRotationUtil.travelPreYaw
            : mc.player.getYaw();

        final float delta = MathHelper.wrapDegrees(realYaw - AlienRotationUtil.fixYaw)
            * MathHelper.RADIANS_PER_DEGREE;
        final float cos = MathHelper.cos(delta);
        final float sin = MathHelper.sin(delta);
        final int nS = Math.round(mS * cos - mF * sin);
        final int nF = Math.round(mF * cos + mS * sin);

        input.playerInput = new PlayerInput(
            nF > 0,
            nF < 0,
            nS > 0,
            nS < 0,
            input.playerInput.jump(),
            input.playerInput.sneak(),
            input.playerInput.sprint()
        );

        // ★ 等价 Alien 改 movementForward/Sideways：
        //   1.21.11 的 applyInput() 读的是 movementVector，必须同步重算。
        float f = input.playerInput.forward() == input.playerInput.backward() ? 0.0F
            : (input.playerInput.forward() ? 1.0F : -1.0F);
        float g = input.playerInput.left() == input.playerInput.right() ? 0.0F
            : (input.playerInput.left() ? 1.0F : -1.0F);
        input.movementVector = new Vec2f(g, f).normalize();
    }

    
    public float getSprintYaw(float yaw) {
        // ★★★ 符号约定（已用日志实测纠正，勿再改）
        //
        //   【⛔ 撤回 2026-10-18 的错误修改】
        //   我曾按「yaw 增大 = 向左」把这里全部改反，结果：
        //     COMPENSATE: mS=-1(D) -> nF=-1（后退）
        //   ⇒ 疾跑不能后退 ⇒ 触发不了疾跑 + 全方向失效。
        //
        //   【✅ 正确原理】这套符号的设计目的不是「让 yaw 指向目标方向」，
        //   而是让 <b>输入补偿后恒为 forward=1</b>：
        //     delta = realYaw - fixYaw，输入绕 delta 旋转
        //     A  : fix=real-90, delta=+90  ⇒ nF=round(0*0+1*1)=+1
        //     D  : fix=real+90, delta=-90  ⇒ nF=round(0*0+(-1)*(-1))=+1
        //     S  : fix=real+180, delta=180 ⇒ nF=round((-1)*(-1))=+1
        //     W+A: fix=real-45, delta=+45  ⇒ nF=round(0.707+0.707)=+1
        //     W+D: fix=real+45, delta=-45  ⇒ nF=round(0.707+0.707)=+1
        //   全部 forward=1，而「实际朝向」由 fixYaw 承担。
        //   这才是 Alien Sprint 的核心机制：输入永远是前进，靠 yaw 转向。
        if (isSprintPressed()) {
            if (isLeftPressed()) {
                yaw -= 45.0F;
            } else if (isRightPressed()) {
                yaw += 45.0F;
            }
        } else if (isBackPressed()) {
            yaw += 180.0F;
            if (isLeftPressed()) {
                yaw += 45.0F;
            } else if (isRightPressed()) {
                yaw -= 45.0F;
            }
        } else if (isLeftPressed()) {
            yaw -= 90.0F;
        } else if (isRightPressed()) {
            yaw += 90.0F;
        }

        // ★ 不 wrap：保持与传入 yaw 同一圈，避免跨 360° 跳变。
        //   只在最终发给服务端前才可能需要对折（由 normalize 统一处理）。
        return yaw;
    }

    @Override
    public String getInfoString() {
        return mode.get().name();
    }

    @Override
    public void onDeactivate() {
        AlienRotationUtil.shouldRotate = false;
        AlienRotationUtil.inputCompensated = false;
        if (mc.player != null) {
            mc.player.bodyYaw = mc.player.getYaw();
            mc.player.headYaw = mc.player.getYaw();
        }
    }

    @EventHandler
    public void onPacket(PacketEvent.Receive event) {
        if (lagPause.get() && event.packet instanceof PlayerPositionLookS2CPacket) {
            pause = true;
        }
    }

    private boolean shouldRotateNow() {
        if (mode.get() != Mode.Rotation) return false;

        // Alien 那个条件的括号优先级要按源码读：
        //   (A && B && ... && !blindness) || !blindnessPause
        // ⇒ blindnessPause 关闭时，整段恒真（只要 mode==Rotation）。
        boolean mainClause = (mc.player.getHungerManager().getFoodLevel() > 6 || mc.player.isCreative())
            && AlienMovementUtil.isMoving()
            // Alien 的 !Freecam.isOn() / Freecam.isOff() —— 本项目没有 Freecam 依赖，视为 true
            // 1.21.11: isFallFlying() 已改名为 isGliding()
            && !mc.player.isGliding()
            && (!AlienPlayerUtil.isInWeb(mc.player) || !inWebPause.get())
            && (!mc.player.isSneaking() || !sneakingPause.get())
            && !mc.player.hasVehicle()
            && (!mc.player.isUsingItem() || !usingPause.get())
            && !mc.player.isInFluid()
            && !mc.player.hasStatusEffect(StatusEffects.BLINDNESS);

        return mainClause || !blindnessPause.get();
    }

    
    public void alienTickMovement() {
        if (mc.player == null || mc.world == null) return;

        // ★★★ 每个客户端 tick 只算一次。
        //
        //   {@code ClientPlayerEntity.tickMovement()} 在同一个游戏 tick 内会被调用多次
        //   （载具路径 travelControlled、骑乘、部分 mixin 的重复调用等）。
        //   若每次都重算 sendYaw，就会出现：
        //     travel()  用的是第 1 次的 sendYaw
        //     jump()    用的是第 2 次的 sendYaw
        //     sendMovementPackets 用的是第 3 次的 sendYaw
        //   ⇒ 三者不一致 ⇒ 客户端位移方向 ≠ 服务端预测方向 ⇒ Simulation offset。
        //
        //   日志实证（修复前）：
        //     TRAVEL: send=-162.02 yaw=17.977 mIn=(0,0,0.98)
        //     COMPENSATE: realYaw=-105.94 fix=74.06
        //   三个值互不相同，正是重复计算导致的。
        //
        //   ★ 注意：守卫必须在 resetTick() 【之前】——
        //     若先 reset 再 return，会把本 tick 已算好的值清掉。
        final int tick = mc.player.age;
        if (AlienRotationUtil.lastComputeTick == tick) {
            return;   // 本 tick 已算过，直接复用上次的 fixYaw / sendYaw（保持不被清）
        }
        AlienRotationUtil.lastComputeTick = tick;

        // 每 tick 复位（对应 Alien 每次 tickMovement 重算 fixYaw）
        AlienRotationUtil.resetTick();


        // ────────────────────────────────────────────────────────────────
        // 步骤 1：等价 RotationManager.update(TickMovementEvent) priority=999
        //
        //   UpdateRotateEvent e = UpdateRotateEvent.get(mc.player.getYaw(), mc.player.getPitch());
        //   EVENT_BUS.post(e);                       // ← Sprint.rotate 在这里改 e.yaw
        //   this.nextYaw = e.getYaw();   fixYaw  = this.nextYaw;
        //   this.nextPitch = e.getPitch(); fixPitch = this.nextPitch;
        //
        //   ⇒ fixYaw 的初值是「真实 yaw」，只有 Sprint.rotate 条件成立时才被
        //      改写成 getSprintYaw(真实yaw)。条件不成立 ⇒ fixYaw == 真实 yaw。
        // ────────────────────────────────────────────────────────────────
        final float realYaw = mc.player.getYaw();
        final float realPitch = mc.player.getPitch();

        // e.yaw 初值 = 真实 yaw
        float eventYaw = realYaw;

        // 步骤 1.1：Sprint.rotate()（priority=-100）—— 条件成立才改 e.yaw
        if (shouldRotateNow()) {
            eventYaw = getSprintYaw(realYaw);
            // 等价于 rotation()：同 tick 内三处 yaw 替换共用一个值
            AlienRotationUtil.shouldRotate = true;
        }

        // ────────────────────────────────────────────────────────────────
        // 步骤 1.2：RotateManager 把 e.yaw 存进 fixYaw
        //
        //   ★ 这里【不 wrap 到 ±180】，而是让 fixYaw 与 realYaw 同圈。
        //     1.21.11 的 getYaw() 是未归一化累积值（日志实测到 181、359.9 等）。
        //     若在此 wrap，fix 会从 359.9 突然跳成 -0.9 ⇒ 相邻包差 360°
        //     ⇒ Grim AimModulo360 + Simulation 回弹（这就是你看到的现象）。
        //     发包处用 continuous() 统一对齐到上一包，才是正确的收口点。
        AlienRotationUtil.fixYaw = AlienRotationUtil.normalize(eventYaw, realYaw);
        AlienRotationUtil.fixPitch = AlienRotationUtil.normalizePitch(realPitch);

        // ★★ 本 tick 的「最终发送值」—— 一次性算好，供三处共用：
        //   ① MixinPlayerEntity.travel  换 yaw
        //   ② MixinLivingEntitySprint.jump 换 yaw
        //   ③ MixinClientPlayerEntitySprint.sendMovementPackets 发包
        //
        //   三者必须是同一个数，否则「客户端算位移用的 yaw」与「服务端收到的 yaw」
        //   会差一个整圈 ⇒ 服务端预测方向错 ⇒ Simulation 回弹。
        //
        //   continuous() 保证与上一包差值最小（避免 ±180 边界跳 360°，
        //   这是 Grim AimModulo360 的判据）。
        if (AlienRotationUtil.shouldRotate) {
            AlienRotationUtil.sendYaw = AlienRotationUtil.continuous(
                AlienRotationUtil.fixYaw, AlienRotationUtil.lastSentYaw);
        } else {
            // 不旋转时发送值 = 真实 yaw（仍要 continuous 对齐，避免整圈累积）
            AlienRotationUtil.sendYaw = AlienRotationUtil.continuous(
                realYaw, AlienRotationUtil.lastSentYaw);
        }

        // 兼容字段
        AlienRotationUtil.sprintYaw = AlienRotationUtil.sendYaw;


        // ────────────────────────────────────────────────────────────────
        // 步骤 2：等价 Sprint.onMove(TickMovementEvent)
        // ────────────────────────────────────────────────────────────────
        if (mode.get() == Mode.PressKey) {
            if (!inWater()) {
                mc.options.sprintKey.setPressed(true);
            }
        } else {
            if (!inWater()) {
                mc.player.setSprinting(shouldSprint());
            }
        }
    }

    
    @EventHandler
    public void onTick(TickEvent.Pre event) {
        // 空：核心逻辑已迁移到 alienTickMovement()（tickMovement HEAD），
        // 与 Alien 的 TickMovementEvent 时机一致。留此方法是防止其它代码误用。
    }

    @EventHandler
    public void onTickPost(TickEvent.Post event) {
        pause = false;

        if (debug.get() && mc.player != null) {
            var in = mc.player.input.playerInput;
            info("yaw=%.1f fix=%.1f | in[F=%s B=%s L=%s R=%s] | sprinting=%s rotate=%s",
                mc.player.getYaw(), AlienRotationUtil.fixYaw,
                in.forward(), in.backward(), in.left(), in.right(),
                mc.player.isSprinting(), AlienRotationUtil.shouldRotate);
        }
    }

    private boolean inWater() {
        return inWaterPause.get() && mc.player.isInFluid();
    }

    
    private boolean shouldSprint() {
        if ((mc.player.getHungerManager().getFoodLevel() > 6 || mc.player.isCreative())
            && AlienMovementUtil.isMoving()
            && !pause
            && (!mc.player.isSneaking() || !sneakingPause.get())
            && (!AlienPlayerUtil.isInWeb(mc.player) || !inWebPause.get())
            && (!mc.player.isUsingItem() || !usingPause.get())
            && !mc.player.hasVehicle()
            && (!mc.player.hasStatusEffect(StatusEffects.BLINDNESS) || !blindnessPause.get())
            // ★ Grim SprintE（SprintE.java:34-42）：硬水平碰撞时不能疾跑
            && (!pauseOnCollision.get() || !(mc.player.horizontalCollision && !mc.player.collidedSoftly))
            // ★ Grim SprintG（SprintG.java:21-22）：水里不能疾跑（游泳除外）
            && (!pauseOnWater.get() || !mc.player.isTouchingWater() || mc.player.isSwimming())) {

            return switch (mode.get()) {
                case Legit -> movementSync.get()
                    ? mc.player.input.playerInput.forward()
                    : (mc.options.forwardKey.isPressed()
                        && MathHelper.angleBetween(mc.player.getYaw(), AlienRotationUtil.fixYaw) < 40.0F);
                case Rage -> true;
                // ★ Alien:128-132 逐字对应：
                //   if (movementSync) return mc.player.input.movementForward > 0.0F;
                //   return angleBetween(getSprintYaw(getYaw()), ROTATION.rotationYaw) < 40.0F;
                //
                // movementForward > 0  ⇔  playerInput.forward()（补偿后）
                // ROTATION.rotationYaw  ⇔  fixYaw（我们只有一个旋转源）
                case Rotation -> movementSync.get()
                    ? mc.player.input.playerInput.forward()
                    : MathHelper.angleBetween(getSprintYaw(mc.player.getYaw()), AlienRotationUtil.fixYaw) < 40.0F;
                default -> false;
            };
        }
        return false;
    }

    public enum Mode {
        PressKey,
        Legit,
        Rage,
        Rotation
    }
}
