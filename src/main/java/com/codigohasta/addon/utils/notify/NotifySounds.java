package com.codigohasta.addon.utils.notify;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

/**
 * 通知音效（移植 CatLean {@code d2} 的音效系统）。
 *
 * <h2>✅ 源码确证：音效只在 ENABLED / DISABLED 时播</h2>
 *
 * <p>CatLean {@code sg.q(...)} 里的 switch（{@code sw.java} 的映射表确证）：
 * <pre>
 *   nArray[ys.DISABLED.ordinal()] = 1;   // case 1 -&gt; d2.O.d()
 *   nArray[ys.ENABLED.ordinal()]  = 2;   // case 2 -&gt; d2.O.s()
 * </pre>
 * 即 <b>只有 ENABLED / DISABLED 两种通知类型有音效</b>
 * （正好对应「模块开/关」→ {@code enable.ogg} / {@code disable.ogg}）。
 *
 * <p>其余检测项的音效由 Notifier 各分支自己播（CatLean 里也是分散调用的）：
 * <pre>
 *   armor-alert   低甲
 *   chat-mention  被提及
 *   found-stash   物资点
 *   lag           服务器无响应
 *   lag-back      回弹
 *   range-in/out  进出视野
 * </pre>
 *
 * <h2>资源来源</h2>
 * <p>{@code assets/invincible_machine_gun/sounds/*.ogg}
 * —— 用户 2026-10-07 确认<b>从 CatLean 提取</b>这批发通知相关的音效。
 *
 * <p>播放方式照项目已有的 {@code TerminatorHudShader.playSound}
 * （{@code SoundEvent.of(id)} + {@code PositionedSoundInstance.ui}），
 * 走客户端 {@code SoundManager}，不需要服务端注册。
 */
public final class NotifySounds {

    private static final String NS = "invincible_machine_gun";

    public static final Identifier ENABLE = Identifier.of(NS, "enable");
    public static final Identifier DISABLE = Identifier.of(NS, "disable");
    public static final Identifier ARMOR_ALERT = Identifier.of(NS, "armor_alert");
    public static final Identifier CHAT_MENTION = Identifier.of(NS, "chat_mention");
    public static final Identifier FOUND_STASH = Identifier.of(NS, "found_stash");
    public static final Identifier LAG = Identifier.of(NS, "lag");
    public static final Identifier LAG_BACK = Identifier.of(NS, "lag_back");
    public static final Identifier RANGE_IN = Identifier.of(NS, "range_in");
    public static final Identifier RANGE_OUT = Identifier.of(NS, "range_out");
    /**
     * 图腾弹出。
     *
     * <p>用户 2026-10-07 指定用自己提供的 {@code he_poptotem.ogg}：
     * 已从构建目录复制进 {@code assets/invincible_machine_gun/sounds/}
     * 并注册到 {@code sounds.json}。
     */
    public static final Identifier HE_POPTOTEM = Identifier.of(NS, "he_poptotem");
    /**
     * CatLean 自带的图腾音 —— 用于【自己】弹图腾
     * （用户 2026-10-07：自己用 totem-pop，别人用 he_poptotem）。
     */
    public static final Identifier TOTEM_POP = Identifier.of(NS, "totem_pop");

    private NotifySounds() {}

    /**
     * 播放一个音效。
     *
     * <p>照 {@code TerminatorHudShader.playSound} 的写法：
     * {@code SoundEvent.of(id)} + {@code PositionedSoundInstance.ui(...)}。
     *
     * <h2>★★ 2026-10-11：必须派发回主线程，否则会崩端</h2>
     *
     * <p><b>实测崩溃</b>（{@code crash-reports\crash-2026-10-11_16.11.30-client.txt}）：
     * <pre>
     * java.util.ConcurrentModificationException
     *   at java.util.HashMap$HashIterator.nextNode
     *   at net.minecraft.class_1140.handler$zgl002$bephax$mixinTick(class_1140.java:648)
     *   at net.minecraft.class_1140.method_4857   // SoundEngine.tickInGameSound
     *   at net.minecraft.class_1144.method_18670  // SoundManager.tick
     * </pre>
     *
     * <p><b>机制</b>：{@code SoundEngine.tickInGameSound}（渲染线程）会迭代
     * {@code instanceToChannel}（{@code field_18950}，一个 {@code HashMap}）。
     * 而 {@code SoundEngine.play()} 会往同一个 Map 里 {@code put}。
     * <b>在另一个线程上 put，就是数据竞争</b> → {@code ConcurrentModificationException}。
     *
     * <p><b>我们为什么会踩到</b>：Meteor 的 {@code PacketEvent.Receive} 发在
     * <b>Netty IO 线程</b>上（{@code ClientConnectionMixin.onHandlePacket} 注入
     * {@code channelRead0}），而本类被 {@code Notifier} 的
     * 「图腾弹出」分支直接调用（源 {@code Notifier.java:538-632}）。
     * 「持续打带图腾的目标」会让这个包**连续密集**到达 ⇒ 连续在不同线程上 play
     * ⇒ 撞上渲染线程的迭代窗口 ⇒ 崩。
     *
     * <p><b>修法</b>：用 {@link MinecraftClient#execute(Runnable)} 把播放动作
     * 派发到**主线程**（渲染线程）执行，消除跨线程写入。
     * 若当前已在主线程，{@code execute} 会直接执行（{@code MinecraftClient}
     * 内部判断 {@code isOnThread()}），没有额外延迟。
     *
     * <p><b>为什么不抛异常</b>：音效资源缺失只会静默无声，
     * 不该影响通知本身（通知已经发出去了）。
     */
    public static void play(Identifier id, float volume) {
        if (id == null) return;
        try {
            final float vol = Math.max(0f, Math.min(1f, volume));
            final SoundEvent sound = SoundEvent.of(id);
            MinecraftClient mc = MinecraftClient.getInstance();
            // ★ 关键：派发到主线程。Netty 线程上直接 play 会与渲染线程的
            //   SoundEngine.tickInGameSound 迭代 instanceToChannel 冲突 → CME 崩端。
            mc.execute(() -> {
                try {
                    mc.getSoundManager().play(
                        PositionedSoundInstance.ui(sound, 1.0f, vol));
                } catch (Throwable ignoredElse) {
                    // 音效失败不影响通知
                }
            });
        } catch (Throwable ignored) {
            // 音效失败不影响通知
        }
    }

    public static void play(Identifier id) {
        play(id, 1.0f);
    }

    /**
     * 按通知类型自动选音效（照 CatLean：只有 ENABLED/DISABLED 有音效）。
     *
     * @return 是否播了音
     */
    public static boolean playForType(ImgNotificationType type, float volume) {
        switch (type) {
            case ENABLED -> { play(ENABLE, volume); return true; }
            case DISABLED -> { play(DISABLE, volume); return true; }
            default -> { return false; }
        }
    }
}
