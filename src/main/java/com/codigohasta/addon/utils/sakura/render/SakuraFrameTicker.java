package com.codigohasta.addon.utils.sakura.render;

import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;

/**
 * 每帧推进 Sakura 2D 渲染层的帧号。
 *
 * <h2>为什么需要它</h2>
 *
 * <p>{@link Shader2DUtil} 的毛玻璃（{@code drawRoundedBlur}）需要一个
 * 「面板之前的画面」作为模糊源。若<b>每个面板各自拷贝一次整屏</b>
 * （1920×1080×4B ≈ <b>8.3MB</b>），30 个面板就是 <b>250MB/帧</b>、
 * 60fps 下约 <b>15GB/s</b> —— 纯浪费（性能审计已确证）。
 *
 * <p>正解：一帧内<b>只拷贝一次</b>，所有面板共用（它们采的本来就是同一时刻的画面）。
 * 这靠 {@code drawRoundedBlur(..., cacheSceneForFrame=true)} 实现，
 * 而该缓存以<b>帧号</b>为键 —— 所以必须有「每帧推进一次帧号」的地方。
 *
 * <h2>⚠️ 为什么必须是「中央、且最早执行」的</h2>
 *
 * <p>帧号推进<b>必须每帧恰好一次、且发生在所有面板绘制之前</b>：
 * <ul>
 *   <li>漏推进 → 帧号不变 → 缓存永远命中第一帧 → <b>模糊冻结</b>；</li>
 *   <li>推进多次 → 缓存提前失效 → 又退化成「每面板一次拷贝」；</li>
 *   <li>推进太晚（在面板之后）→ 本帧面板用的是上一帧的画面。</li>
 * </ul>
 *
 * <p>所以这里挂在 {@link Render2DEvent} 上并用
 * {@link EventPriority#HIGHEST}，保证在任何 HUD 模块之前执行。
 *
 * <h2>✅ 已确证的触发前提</h2>
 *
 * <p>Meteor 的 {@code InGameHudMixin}（{@code @Inject(method="render", at=TAIL)}）
 * 是 {@code Render2DEvent} 的<b>唯一</b>触发点 → 每帧恰好一次（已核对源码）。
 *
 * <p>本项目另有一条 {@code MixinScreenOverlay}（开 GUI 时）渲染路径，
 * 但它<b>只调 IMGTips</b>（纯文字、无模糊面板），不读取这里的帧号，
 * 因此不会出现「开了背包就把模糊冻结」的问题。
 */
public final class SakuraFrameTicker {

    /** 由 {@code AddonTemplate} 注册到事件总线。 */
    public SakuraFrameTicker() {}

    /**
     * 每帧最早执行：推进 Sakura 2D 层的帧号。
     *
     * <p>本类由 {@code AddonTemplate} 注册为事件监听器
     * （Meteor 的 {@code EventBus} 支持任意对象订阅）。
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    private void onRender2D(Render2DEvent event) {
        Shader2DUtil.beginFrame();
    }
}
