package com.codigohasta.addon.utils.sakura.render;

import com.mojang.blaze3d.opengl.GlStateManager;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL33;

/**
 * GL 状态快照 —— 用于「自己动过裸 GL 之后，把状态还原回去」。
 *
 * <p><b>为什么必须存在这个类</b>：1.21.11 的 {@code GlStateManager} 把 GL 状态**缓存在静态字段里**
 * （{@code BLEND}、{@code DEPTH}、{@code CULL}、{@code SCISSOR}、{@code activeTexture}、
 * {@code TEXTURES[]}、{@code COLOR_MASK}、{@code readFbo/writeFbo}）。
 * 这些 {@code _xxx()} 方法在写 GL 之前会先比对缓存，**值相同就直接跳过 GL 调用**。
 *
 * <p>而 NanoVG、以及本项目直接写 GL 的后处理代码，都是绕过缓存的裸 GL 调用。
 * 一旦它们改了状态而没更新缓存，缓存就与现实脱节，于是 Minecraft 之后
 * 「以为已经设好了」而跳过调用 —— 实际 GL 状态却是错的。
 * 表现就是：文字成片不显示 / 只显示一部分、贴图错乱、血条和头像时有时无。
 *
 * <p><b>为什么不能简单地「读出来再写回去」</b>：{@code _xxx()} 的守卫是按**缓存**比对的。
 * 若缓存本身就已是脏的，写回时可能被判定为「相同」而跳过，脏状态反而被固化。
 *
 * <p><b>本类的做法</b>：先把缓存**强行推到一个不同值**（迫使守卫失配、必然发出 GL 调用），
 * 再推到目标值。无论缓存此前是脏是对，两次调用后缓存与真实 GL 都等于目标值。
 * 即所谓「翻转再设置」，对守卫式缓存是可靠的再同步手段。
 */
public final class GlStateSnapshot {

    /** 与 {@code GlStateManager.TEXTURE_COUNT} 保持一致（1.21.11 为 12）。 */
    private static final int TEXTURE_UNIT_COUNT = 12;

    private final boolean blend;
    private final boolean cull;
    private final boolean depthTest;
    private final boolean scissorTest;
    private final boolean depthMask;

    private final int[] blendSrcRgb = new int[1];
    private final int[] blendDstRgb = new int[1];
    private final int[] blendSrcAlpha = new int[1];
    private final int[] blendDstAlpha = new int[1];
    private final int[] activeTexture = new int[1];
    private final int[] program = new int[1];
    private final int[] vertexArray = new int[1];
    private final int[] viewport = new int[4];
    private final int[] colorMask = new int[4];

    /**
     * 每个纹理单元上绑定的纹理。
     *
     * <p><b>为什么必须记录全部 12 个单元，而不是只记进入时活动的那一个</b>：
     * NanoVG 不管进来时活动的是哪个单元，帧结束都会把<b>单元 0</b>的绑定改掉
     * （离线探针实测：帧后单元 0 由原贴图变为 0），并把活动单元留在 0。
     * 而 {@code GlStateManager.TEXTURES[]} 是按单元缓存的，它并不知道这件事。
     * 于是之后 Minecraft 想绑定自己的字体图集时会「以为单元 0 上已经是它了」而跳过
     * {@code glBindTexture}，实际 GPU 上却是 NanoVG 留下的绑定 → 字形采到错误贴图
     * → <b>文字成片不显示</b>。只还原活动单元不足以覆盖这个错位。
     */
    private final int[] textureBindings = new int[TEXTURE_UNIT_COUNT];

    /** 读取绑定用的复用缓冲，避免每个单元各分配一次。 */
    private final int[] scratch = new int[1];

    /** 纹理单元 0 上原本绑定的 sampler object（0 表示没有）。 */
    private int sampler0;

    /**
     * 进入时 DRAW / READ 帧缓冲的绑定。
     *
     * <p><b>★ 2026-10-18 修复</b>：以前这里<b>不记录帧缓冲</b>，注释还写着
     * 「本类的使用场景都是渲染到当前已绑定的主帧缓冲，绑定它属于空操作」——
     * 这个前提是错的。{@code Shader2DUtil.drawFullscreen} 里会真的执行
     * <pre>GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, mainFramebufferId(fb));</pre>
     * 而 {@code mainFramebufferId} 内部走 {@code getOrCreateFramebuffer(...)}，
     * 拿到的 id <b>不一定等于</b> 进入前 MC 绑定的那个。
     *
     * <p>裸 GL 改完却不还原，会让 {@code GlStateManager} 缓存的
     * {@code readFbo/writeFbo} 与真实状态<b>脱节</b>：之后 Minecraft 以为
     * 「已经绑好了」而跳过 {@code glBindFramebuffer}，绘制就落到错误的 FBO 上
     * ——表现正是用户报的「整屏闪烁」+「图标位置出现另一个游戏画面」。
     */
    private final int[] drawFbo = new int[1];
    private final int[] readFbo = new int[1];

    private GlStateSnapshot() {
        blend = GL11.glIsEnabled(GL11.GL_BLEND);
        cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        depthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        scissorTest = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);

        GL11.glGetIntegerv(GL14.GL_BLEND_SRC_RGB, blendSrcRgb);
        GL11.glGetIntegerv(GL14.GL_BLEND_DST_RGB, blendDstRgb);
        GL11.glGetIntegerv(GL14.GL_BLEND_SRC_ALPHA, blendSrcAlpha);
        GL11.glGetIntegerv(GL14.GL_BLEND_DST_ALPHA, blendDstAlpha);
        GL11.glGetIntegerv(GL13.GL_ACTIVE_TEXTURE, activeTexture);
        captureTextureBindings(activeTexture[0]);
        GL11.glGetIntegerv(GL20.GL_CURRENT_PROGRAM, program);
        GL11.glGetIntegerv(GL30.GL_VERTEX_ARRAY_BINDING, vertexArray);
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
        GL11.glGetIntegerv(GL11.GL_COLOR_WRITEMASK, colorMask);

        // 记录真实帧缓冲绑定（见 drawFbo 字段上的说明）
        GL11.glGetIntegerv(GL30.GL_DRAW_FRAMEBUFFER_BINDING, drawFbo);
        GL11.glGetIntegerv(GL30.GL_READ_FRAMEBUFFER_BINDING, readFbo);

        sampler0 = captureAndUnbindSampler(activeTexture[0]);
    }

    /**
     * 记录全部纹理单元上绑定的纹理。
     *
     * <p>读取绑定必须逐个切换活动单元，因此结束后要把活动单元切回进入时的值。
     * 这里用裸 {@code glActiveTexture}：{@code GlStateManager} 的 activeTexture 缓存
     * 我们并未改动，走 {@code _activeTexture} 反而会让缓存与现实脱节。
     *
     * @param originalActive 进入时的活动纹理单元（GL 枚举，如 {@code GL_TEXTURE0}）
     */
    private void captureTextureBindings(int originalActive) {
        for (int unit = 0; unit < TEXTURE_UNIT_COUNT; unit++) {
            GL13.glActiveTexture(GL13.GL_TEXTURE0 + unit);
            GL11.glGetIntegerv(GL11.GL_TEXTURE_BINDING_2D, scratch);
            textureBindings[unit] = scratch[0];
        }
        GL13.glActiveTexture(originalActive);
    }


    /**
     * 记录并解绑纹理单元 0 上的 sampler object。
     *
     * <p><b>为什么必须做这件事</b>：Minecraft 1.21.9+ 引入了 GL sampler object
     * （{@code GlSampler} / {@code GlCommandEncoder} 里的 {@code GL33C.glBindSampler}），
     * 而 Sakura 原版所在的 1.21.4 根本没有这个东西。
     *
     * <p>sampler object 会<b>覆盖纹理自身的过滤与环绕参数</b>。Minecraft 是裸调用绑定的，
     * 之后从不解绑，并且不更新 {@code GlStateManager} 的缓存。于是单元 0 上长期残留
     * 一个默认带 mipmap 过滤（{@code LINEAR_MIPMAP_LINEAR}）的采样器，后果是：
     * <ul>
     *     <li>NanoVG 的字形图集只有 level 0，在 mipmap 过滤下纹理不完整 → 采样为空
     *         → <b>文字整片消失</b>（面板背景是纯色填充、不采样纹理，所以始终正常）</li>
     *     <li>图片纹理（MC 贴图）虽有 mipmap 能显示，但被强制 LINEAR 过滤
     *         → <b>头像发糊</b>，{@code NVG_IMAGE_NEAREST} 被无视</li>
     *     <li>本项目自己的后处理着色器同样采样单元 0 → 模糊/故障效果也会取到错误结果</li>
     * </ul>
     *
     * <p>{@code GL_SAMPLER_BINDING} 读取的是<b>当前活动单元</b>的绑定，因此需要先切到
     * 单元 0。这里只在确有必要时切换，并立刻切回原单元，避免引入额外状态变化。
     *
     * @param activeTexture 进入时的活动纹理单元
     * @return 单元 0 原本绑定的 sampler（0 表示无）
     */
    private static int captureAndUnbindSampler(int activeTexture) {
        try {
            boolean switched = activeTexture != GL13.GL_TEXTURE0;
            if (switched) GL13.glActiveTexture(GL13.GL_TEXTURE0);

            int sampler = GL11.glGetInteger(GL33.GL_SAMPLER_BINDING);
            if (sampler != 0) GL33.glBindSampler(0, 0);

            // 切回原单元时必须用裸调用：GlStateManager 的 activeTexture 缓存没被我们改过，
            // 走 _activeTexture 会被缓存守卫判定为「无变化」而跳过。
            if (switched) GL13.glActiveTexture(activeTexture);
            return sampler;
        } catch (Throwable ignored) {
            // 理论上 3.3 core 一定支持 sampler object；真出错也不该影响渲染
            return 0;
        }
    }

    /** 记录当前 GL 状态。 */
    public static GlStateSnapshot capture() {
        return new GlStateSnapshot();
    }

    /**
     * 还原状态，并同时修正 {@code GlStateManager} 的缓存。
     *
     * <p>只有被 GlStateManager 缓存的状态需要走 {@code _xxx()}（带翻转）；
     * 程序、顶点数组、视口没有缓存（其 {@code _xxx()} 每次都直接下发 GL），裸调用即可。
     *
     * <p>帧缓冲不在快照范围内：本类的使用场景都是「渲染到当前已绑定的主帧缓冲」，
     * 绑定它属于空操作，不需要也无法可靠地还原。
     */
    public void restore() {
        // ★ 先还原帧缓冲（2026-10-18 修复）：裸 GL 的 glBindFramebuffer 改过它，
        //   若不还原，GlStateManager 的 readFbo/writeFbo 缓存会与真实状态脱节，
        //   后续 MC 的绘制会落到错误的 FBO —— 表现为整屏闪烁 / 图标处出现旧画面。
        restoreFramebuffer();

        GlStateManager._glUseProgram(program[0]);
        GlStateManager._glBindVertexArray(vertexArray[0]);
        GlStateManager._viewport(viewport[0], viewport[1], viewport[2], viewport[3]);

        restoreTextureBindings();
        restoreSampler0();

        forceBlendFunc(blendSrcRgb[0], blendDstRgb[0], blendSrcAlpha[0], blendDstAlpha[0]);
        forceBlend(blend);
        forceCull(cull);
        forceDepthTest(depthTest);
        forceScissorTest(scissorTest);
        forceDepthMask(depthMask);
        forceColorMask(colorMask[0] != 0, colorMask[1] != 0, colorMask[2] != 0, colorMask[3] != 0);
    }

    /**
     * 还原 DRAW / READ 帧缓冲，并同步 {@code GlStateManager} 的缓存。
     *
     * <p>{@code _glBindFramebuffer} 自带缓存守卫
     * （{@code if ((i == 36008 || i == 36160) && readFbo != j)}），
     * 所以不能只调一次 —— 缓存里若「恰好已经是目标值」就会被跳过，
     * 而真实 GL 可能仍停在别处。这里用「先绑 0 再绑目标」迫使守卫失配，
     * 保证真正的 {@code glBindFramebuffer} 被下发，缓存也同步到正确值。
     */
    private void restoreFramebuffer() {
        // 强制下发：中间插一个不同值（0 = 默认帧缓冲）
        int targetDraw = drawFbo[0];
        if (targetDraw != 0) {
            GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, 0);
        }
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, targetDraw);

        int targetRead = readFbo[0];
        if (targetRead != 0) {
            GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, 0);
        }
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, targetRead);
    }

    // ─── 翻转再设置：先推到不同值迫使守卫失配，再推到目标值 ───

    private static void forceBlend(boolean on) {
        if (on) {
            GlStateManager._disableBlend();
            GlStateManager._enableBlend();
        } else {
            GlStateManager._enableBlend();
            GlStateManager._disableBlend();
        }
    }

    private static void forceCull(boolean on) {
        if (on) {
            GlStateManager._disableCull();
            GlStateManager._enableCull();
        } else {
            GlStateManager._enableCull();
            GlStateManager._disableCull();
        }
    }

    private static void forceDepthTest(boolean on) {
        if (on) {
            GlStateManager._disableDepthTest();
            GlStateManager._enableDepthTest();
        } else {
            GlStateManager._enableDepthTest();
            GlStateManager._disableDepthTest();
        }
    }

    private static void forceScissorTest(boolean on) {
        if (on) {
            GlStateManager._disableScissorTest();
            GlStateManager._enableScissorTest();
        } else {
            GlStateManager._enableScissorTest();
            GlStateManager._disableScissorTest();
        }
    }

    private static void forceDepthMask(boolean on) {
        GlStateManager._depthMask(!on);
        GlStateManager._depthMask(on);
    }

    private static void forceColorMask(boolean r, boolean g, boolean b, boolean a) {
        GlStateManager._colorMask(!r, !g, !b, !a);
        GlStateManager._colorMask(r, g, b, a);
    }

    private static void forceBlendFunc(int srcRgb, int dstRgb, int srcAlpha, int dstAlpha) {
        // 选一个必定不等于目标值的「翻转值」，迫使守卫失配
        int flipSrc = GL11.GL_ONE, flipDst = GL11.GL_ZERO;
        if (srcRgb == GL11.GL_ONE && dstRgb == GL11.GL_ZERO
            && srcAlpha == GL11.GL_ONE && dstAlpha == GL11.GL_ZERO) {
            flipSrc = GL11.GL_ZERO;
            flipDst = GL11.GL_ONE;
        }
        GlStateManager._blendFuncSeparate(flipSrc, flipDst, flipSrc, flipDst);
        GlStateManager._blendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha);
    }

    /**
     * 把纹理单元 0 的 sampler object 还原成进入前的样子。
     *
     * <p>sampler object 的绑定<b>不在 {@code GlStateManager} 的缓存里</b>
     * （Minecraft 是裸调用绑定的），所以不存在缓存脱节问题，直接用裸调用还原即可，
     * 也不需要「翻转再设置」。
     */
    private void restoreSampler0() {
        if (sampler0 == 0) return;
        try {
            boolean switched = activeTexture[0] != GL13.GL_TEXTURE0;
            if (switched) GL13.glActiveTexture(GL13.GL_TEXTURE0);
            GL33.glBindSampler(0, sampler0);
            if (switched) GL13.glActiveTexture(activeTexture[0]);
        } catch (Throwable ignored) {
            // 见 captureAndUnbindSampler
        }
    }

    /**
     * 校正活动纹理单元。
     *
     * <p>这里需要 <b>三次</b> 调用，而不是两次：布尔状态只有两个取值，翻转一次即可保证
     * 第二次必定生效；但纹理单元取值多于两个，若目标恰好等于翻转值，第二次会被跳过，
     * 而真实 GL 可能仍停在旧单元上。中间多插一个单元即可保证第三次必定下发。
     */
    private static void forceActiveTexture(int unit) {
        GlStateManager._activeTexture(GL13.GL_TEXTURE0);
        GlStateManager._activeTexture(GL13.GL_TEXTURE1);
        GlStateManager._activeTexture(unit);
    }

    /**
     * 把全部 12 个纹理单元的绑定还原，并同步 {@code GlStateManager.TEXTURES[]} 缓存。
     *
     * <p>这是「文字消失」的根因修复点。NanoVG 每帧都会把<b>单元 0</b>的绑定改掉，
     * 而快照以前只记录了进入时活动的那一个单元，于是 MC 的按单元缓存与实际 GL 脱节，
     * 之后 MC 想绑字体图集时会被守卫判定为「无变化」而跳过真正的 {@code glBindTexture}。
     *
     * <p>流程：先用三重调用把活动单元强制校正到单元 0（绕开守卫，确保缓存与现实一致），
     * 再逐个单元走 {@code _bindTexture}（带翻转，保证守卫失配）写回原绑定，
     * 最后把活动单元还原成进入时的值。
     */
    private void restoreTextureBindings() {
        // 必须先校正活动单元：_bindTexture 写入的是 TEXTURES[activeTexture]，
        // 若活动单元缓存本身就是脏的，后续所有写入都会落到错误的槽位。
        forceActiveTexture(GL13.GL_TEXTURE0);

        for (int unit = 0; unit < TEXTURE_UNIT_COUNT; unit++) {
            // 相邻两次的目标单元必然不同，因此守卫每次都会失配、真实下发，
            // 不需要像 forceActiveTexture 那样额外插一次翻转。
            GlStateManager._activeTexture(GL13.GL_TEXTURE0 + unit);
            forceBindTexture(textureBindings[unit]);
        }

        forceActiveTexture(activeTexture[0]);
    }

    /**
     * 探测用纹理，仅在「目标绑定为 0」时使用，懒创建后常驻。
     *
     * <p>翻转需要一个「合法的、且不等于目标值」的纹理名。0 是合法的默认值，
     * 但若目标本身就是 0 就不能拿 0 当翻转值（守卫会跳过，脱节残留）。
     * 非 0 的任意数字在 core profile 下绑定会报 GL_INVALID_OPERATION，
     * 因此这里真实地创建一个 1x1 纹理来充当翻转值。
     */
    private static int probeTexture;

    private static void forceBindTexture(int texture) {
        // 必须在 activeTexture 缓存已校正之后调用：_bindTexture 写入的是 TEXTURES[activeTexture]
        int probe;
        if (texture == 0) {
            if (probeTexture == 0) {
                try {
                    // 用 _genTexture 而非裸 glGenTextures，以便 GlStateManager 的计数保持一致
                    probeTexture = GlStateManager._genTexture();
                } catch (Throwable ignored) {
                    probeTexture = -1;  // 标记为不可用，避免每帧重试
                }
            }
            if (probeTexture <= 0) {
                // 无法取得翻转值：至少下发一次目标值（缓存若已正确则本就是空操作）
                GlStateManager._bindTexture(texture);
                return;
            }
            probe = probeTexture;
        } else {
            probe = 0;
        }

        GlStateManager._bindTexture(probe);
        GlStateManager._bindTexture(texture);
    }
}
