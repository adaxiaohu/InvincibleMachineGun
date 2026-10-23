package com.codigohasta.addon.utils.alien;

/**
 * AlienV4 Sprint 的旋转状态 —— 对应 AlienV4 {@code RotationManager} 的
 * {@code fixYaw} / {@code fixPitch} / {@code rotation} 三件套。
 *
 * <h2>为什么必须成组维护</h2>
 * Grim 的 {@code Simulation} 用「包里的 yaw + knownInput」反推位移
 * （{@code PredictionEngine.java:847-849}、{@code :921-930}）。
 * 要让 offset ≈ 0，**同一 tick 内**必须同时满足：
 * <ol>
 *   <li>{@code travel()} 计算位移时用 {@link #fixYaw}（不是真实 yaw）</li>
 *   <li>WASD 输入反向旋转，使它在 {@code fixYaw} 坐标系下变成「纯前进」</li>
 *   <li>发出去的包用 {@link #fixYaw}</li>
 * </ol>
 * 三个量分开写、或跨 tick 不一致 ⇒ 永久错位 ⇒ 必 flag。
 * 所以统一放在本类，由 {@code AlienSprint.onTick(Pre)} 单点写入。
 */
public class AlienRotationUtil {

    // ═══════════ ① 由 AlienSprint 写入：本 tick 的「对服务端呈现的朝向」 ═══════════

    /** 本 tick 是否启用 silent 旋转。 */
    public static boolean shouldRotate = false;

    /**
     * 本 tick 对服务端呈现的 yaw —— 对应 AlienV4 的 {@code fixYaw}。
     *
     * <p>三个消费方都必须读它，不能各读各的 {@code mc.player.getYaw()}：
     * <ul>
     *   <li>{@code MixinPlayerEntity} —— {@code travel()} 期临时替换真实 yaw</li>
     *   <li>{@code MixinKeyboardInput} —— 输入反向补偿的 {@code delta} 基准</li>
     *   <li>{@code MixinClientPlayerEntitySprint} —— 发包 yaw</li>
     * </ul>
     */
    public static float fixYaw = 0.0F;
    public static float fixPitch = 0.0F;

    /** 兼容旧字段名（= {@link #fixYaw}）。发包 mixin 用。 */
    public static float sprintYaw = 0.0F;

    // ═══════════ ② travel 期替换的保存/还原 ═══════════

    /** {@code travel()} 期保存的真实 yaw/pitch（RETURN 还原用）。 */
    public static float travelPreYaw = 0.0F;
    public static float travelPrePitch = 0.0F;

    /**
     * ★ {@code travel()} 是否**真的替换过** yaw。
     *
     * <p>还原必须看这个标志，不能看 {@link #shouldRotate}：
     * 后者可能在 tick 中途被别的模块改掉，导致「替换过却没还原」⇒
     * yaw 永久停在 fixYaw ⇒ 玩家视角冻结 / 移动方向错乱。
     */
    public static boolean travelApplied = false;

    // ═══════════ ③ sendMovementPackets 期替换的保存/还原 ═══════════

    public static float preYaw = 0.0F;
    public static float preBodyYaw = 0.0F;
    public static float preHeadYaw = 0.0F;

    /** ★ 同 {@link #travelApplied}，发包期是否真的替换过。 */
    public static boolean packetApplied = false;

    /** 已发给服务端的 yaw/pitch（诊断用）。 */
    public static float rotationYaw = 0.0F;
    public static float rotationPitch = 0.0F;

    /**
     * ★ 上一包实际发出去的 yaw —— 用于 {@link #continuous(float, float)} 保持连续。
     *
     * <p>{@link Float#NaN} 表示还没发过。
     */
    public static float lastSentYaw = Float.NaN;

    /**
     * ★★ 本 tick 的最终发送值（{@link #continuous} 对齐后）。
     *
     * <p>由 {@code AlienSprint.alienTickMovement()} 每 tick 算一次，
     * 供三处共用 —— <b>三者必须是同一个数</b>：
     * <ol>
     *   <li>{@code MixinPlayerEntity} —— {@code travel()} 期换 yaw（算位移方向）</li>
     *   <li>{@code MixinLivingEntitySprint} —— {@code jump()} 期换 yaw（算跳跃加速方向）</li>
     *   <li>{@code MixinClientPlayerEntitySprint} —— 发包 yaw</li>
     * </ol>
     * 若各自算各自的，客户端位移方向会与服务端预测差一个整圈 ⇒ Simulation 回弹。
     */
    public static float sendYaw = 0.0F;

    /**
     * ★ 上次执行 {@code alienTickMovement()} 的客户端 tick 号（{@code player.age}）。
     *
     * <p>用于保证「一个 tick 内只算一次 fixYaw / sendYaw」。
     * {@code ClientPlayerEntity.tickMovement()} 在同一 tick 内可能被调用多次，
     * 若每次都重算，travel / jump / 发包三处会拿到不同的值
     * ⇒ 客户端位移方向与 Grim 预测差 90°/180° ⇒ Simulation。
     */
    public static int lastComputeTick = -1;

    // ═══════════ ④ 输入补偿的一次性闸门 ═══════════

    /**
     * 输入补偿是否已在本 tick 生效过一次。
     *
     * <p>防止 {@code KeyboardInput.tick()} 被同 tick 调用多次时重复旋转
     * （重复旋转会把已转成 W 的输入再转一次，等于没转甚至转到反方向）。
     */
    public static boolean inputCompensated = false;

    /** 每 tick 开头由 {@code AlienSprint} 复位。 */
    public static void resetTick() {
        shouldRotate = false;
        inputCompensated = false;
        travelApplied = false;
        packetApplied = false;
    }

    /** 模块关闭时彻底复位，防止残留。 */
    public static void resetAll() {
        resetTick();
        fixYaw = 0.0F;
        fixPitch = 0.0F;
        sprintYaw = 0.0F;
        travelPreYaw = 0.0F;
        travelPrePitch = 0.0F;
        preYaw = 0.0F;
        preBodyYaw = 0.0F;
        preHeadYaw = 0.0F;
    }

    /**
     * 让 {@code fixYaw} 与 {@code prevSentYaw} 保持连续 —— 只对折 ±360，
     * 绝不在 ±180 处翻转符号。
     *
     * <h2>★ 这是 Grim {@code AimModulo360} 的唯一根治办法</h2>
     * {@code AimModulo360.java:31}：
     * <pre>
     *   if (player.yaw &lt; 360 &amp;&amp; player.yaw &gt; -360
     *       &amp;&amp; Math.abs(rotationUpdate.getDeltaXRot()) &gt; 320
     *       &amp;&amp; Math.abs(lastDeltaYaw) &lt; 30)  ⇒ flag
     * </pre>
     * {@code deltaXRot = player.yaw - player.lastYaw}（{@code CheckManagerListener.java:726}），
     * <b>不做 wrapDegrees</b>。
     *
     * <p>因此只要相邻两包落在 {@code +179.9} 与 {@code -179.9}（同一角度），
     * 差值就是 ~360 ⇒ 必 flag。
     *
     * <p><b>解法不是 wrap 到 ±180</b>（那样天天跳边界），而是
     * <b>追踪上一包已发的值，在本 tick 的等价候选中选离它最近的那个</b>：
     * 上一包 179.9 ⇒ 本 tick 选 180.1 而不是 -179.9，差值 0.2，完全合法。
     *
     * @param desired    本 tick 期望朝向（任意圈数）
     * @param lastSent   上一包实际发出去的 yaw（首次为 {@code null} 语义，传 {@link Float#NaN}）
     */
    public static float continuous(float desired, float lastSent) {
        // 与 Alien 完全一致：只折到 (-180, 180]，不做圈数对齐。
        return wrap180(desired);
    }

    /**
     * 把 {@code fixYaw} 拉回「离 {@code realYaw} 最近的等价角」。
     *
     * <h2>★ 关键约束：结果的圈数必须与 realYaw 一致</h2>
     * 1.21.11 的 {@code mc.player.getYaw()} 是<b>未归一化的累积值</b>
     * （日志实测出现过 {@code -360.0}、{@code -359.5}）。
     * {@code getSprintYaw} 只做 ±45/±90/±180 偏移，所以：
     * <pre>
     *   realYaw = -360, 后退(+180)  ⇒  fixYaw = -180
     *   realYaw = -360, 前进(0)     ⇒  fixYaw = -360
     * </pre>
     * 两者差 180，本身合法；但<b>如果在这里把 -180 再折成 +180 或 -540，
     * 就会与 realYaw 差出 360 的整圈</b>，导致：
     * <ul>
     *   <li>{@code delta = realYaw - fixYaw} 被放大 ⇒ 输入补偿 cos/sin 错</li>
     *   <li>相邻包 yaw 差 ~360 ⇒ Grim {@code AimModulo360} flag</li>
     * </ul>
     *
     * <h2>⛔ 已撤回的两次错误实现</h2>
     * <ol>
     *   <li>{@code return realYaw + wrap(fix - real)} —— 在 real=181 时推出 361</li>
     *   <li>{@code return wrap180(realYaw + delta)} —— 把 -180 折成 +180，
     *       跨边界跳变（日志 {@code fix=-180 → fix=-360}）</li>
     * </ol>
     *
     * <h2>✅ 现在的做法</h2>
     * <b>原样返回 {@code fixYaw}</b>，不做任何归一化。
     * 因为 {@code getSprintYaw} 的偏移本身就在 ±180 内，
     * 结果天然与 {@code realYaw} 同圈、差值 ≤ 180。
     * 跨包连续性由发包处的 {@link #continuous(float, float)} 负责。
     */
    public static float normalize(float fixYaw, float realYaw) {
        // 仅当差值真的超过 180 时才修正（防止 getSprintYaw 的 +180 后退在
        // 边界处造成 359.x 的荒谬差值），且修正后仍保持与 realYaw 同圈。
        float delta = fixYaw - realYaw;
        // 折到 (-180, 180] —— 注意：这是 delta，不是结果
        delta = delta % 360.0F;
        if (delta > 180.0F) delta -= 360.0F;
        if (delta <= -180.0F) delta += 360.0F;
        // 结果 = realYaw + delta：与 realYaw 同圈，且差值 ≤ 180
        return realYaw + delta;
    }

    /** 折到 {@code (-180, 180]}。仅用于显示/比较，不用于发包。 */
    public static float wrap180(float angle) {
        float a = angle % 360.0F;
        if (a > 180.0F) a -= 360.0F;
        if (a <= -180.0F) a += 360.0F;
        return a;
    }

    /**
     * pitch 无需「跨圈」处理，但要夹到 MC 的合法区间 {@code [-90, 90]}。
     *
     * <p>Alien 直接 {@code setPitch(fixPitch)}，其中 {@code fixPitch} 来自
     * {@code mc.player.getPitch()}（本就在合法区间），所以这里保持原值即可；
     * 加 clamp 只为防御异常值导致 {@code setPitch} 抛错或渲染异常。
     */
    public static float normalizePitch(float pitch) {
        if (pitch > 90.0F) return 90.0F;
        if (pitch < -90.0F) return -90.0F;
        return pitch;
    }
}
