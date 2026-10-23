package com.codigohasta.addon.utils.sakura.render;

import meteordevelopment.meteorclient.utils.render.color.Color;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.texture.GlTexture;
import net.minecraft.util.Identifier;

/**
 * 把「指定的贴图 UV 区域」裁剪成圆角矩形绘制头像。
 *
 * <p><b>为什么需要这个类</b>：不同生物的头在贴图上的位置并不相同。
 * 双足生物（僵尸/骷髅/苦力怕…）用 {@code (8,8)}，但蜘蛛的头在 {@code (40,12)}，
 * 若一律按 {@code (8,8)} 取，拿到的就是腿或身体的图案。因此 UV 由
 * {@link SakuraEntityPortrait} 从实体模型里读出来传进来。
 *
 * <p><b>实现方式（NanoVG → SDF 着色器）</b>：早期用 NanoVG 的
 * {@code nvgImagePattern + nvgRoundedRect + nvgFill}，由 NanoVG 完成圆角裁剪。
 * 现在改为 {@link Shader2DUtil#drawTexturedRoundedRect}：几何上是一个四边形，
 * 圆角由片元着色器的有符号距离场（SDF）算出并抗锯齿，贴图用 {@code texelFetch} 采样。
 *
 * <p><b>为什么 {@code texelFetch} 是关键</b>：MC 的皮肤贴图默认是 LINEAR 过滤 + mipmap，
 * 用普通 {@code texture()} 采样会被插值糊掉——这正是「头像模糊」的根因。
 * 以前必须额外用 {@code glTexParameteri} 把贴图改成 NEAREST（且因为
 * {@code NVG_IMAGE_NEAREST} 对 wrap 贴图无效，还得小心还原绑定以免 NanoVG 缓存脱节）。
 * {@code texelFetch} 直接取单个纹素，<b>完全绕过过滤与 sampler object</b>，
 * 既像素精确又不需要任何 GL 参数补丁。
 */
public final class SakuraSkinRenderer {

    private static final MinecraftClient mc = MinecraftClient.getInstance();

    /** 玩家皮肤：头部正面在 (8,8)，帽子/外层在 (40,8)。 */
    public static final float PLAYER_FACE_U = 8f;
    public static final float PLAYER_FACE_V = 8f;
    public static final float PLAYER_HAT_U = 40f;

    private SakuraSkinRenderer() {}

    /**
     * 画玩家头像（双层皮肤，按原版模型的装配关系叠加）。
     *
     * <p><b>与原版几何的对应</b>：原版头部是内层 8x8x8 的立方体，外层（帽子层）用
     * {@code dilation = 0.5} 膨胀成 9x9x9。两层贴图区域都是 8x8 纹素，但外层要铺满
     * 9 个单位，所以外层纹素在屏幕上比内层<b>大 9/8 倍</b>。因此这里让外层占满
     * {@code size}、内层缩到 {@code size * 8/9} 并居中，两层的相对大小才与游戏里一致。
     *
     * <p><b>绘制顺序必须是先内层后外层</b>：内层是被遮挡的一方，只在外层透明处透出来。
     *
     * @param radius 圆角半径；{@code <= 0} 表示不裁剪。不裁剪时可见轮廓完全由贴图 alpha
     *               决定（外层不透明处露出头发、透明处透出内层的脸，像把整个头颅抠出来）；
     *               裁剪时四角被切掉，回到圆角头像的观感。
     * @param alpha  整体不透明度 0~1（用于面板出入场淡入淡出）
     * @return 是否成功绘制
     */
    public static boolean drawPlayerHeadLayered(Identifier textureId,
                                                float x, float y, float size, float radius, float alpha) {
        if (textureId == null) return false;
        TextureInfo info = infoFor(textureId);
        if (info == null) return false;

        Color tint = withAlpha(alpha);

        // 老式 64x32 皮肤没有独立的外层贴图，只能画单层
        if (info.width() < 64 || info.height() < 64) {
            return drawFace(info, x, y, size, radius, tint,
                new SakuraEntityPortrait.HeadUV(PLAYER_FACE_U, PLAYER_FACE_V, 8f, 8f));
        }

        float innerSize = size * (8f / 9f);
        float inset = (size - innerSize) / 2f;
        // 内层圆角按同比例缩小，两层的圆弧才是同心的
        float innerRadius = radius * (innerSize / size);

        // 内层在下：被外层挡住的部分不外露，只在外层透明处透出来
        drawFace(info, x + inset, y + inset, innerSize, innerRadius, tint,
            new SakuraEntityPortrait.HeadUV(PLAYER_FACE_U, PLAYER_FACE_V, 8f, 8f));
        // 外层在上：铺满整个 size，纹素因此比内层大 9/8 倍（与原版膨胀一致）
        drawFace(info, x, y, size, radius, tint,
            new SakuraEntityPortrait.HeadUV(PLAYER_HAT_U, PLAYER_FACE_V, 8f, 8f));

        return true;
    }

    /**
     * 画生物头像。
     *
     * @param uv 头部正面的 UV 区域（贴图像素，宽高可能不相等——鱿鱼的脸是 12x16）
     * @return 是否成功绘制
     */
    public static boolean drawMobHead(Identifier textureId,
                                      float x, float y, float size, float radius,
                                      SakuraEntityPortrait.HeadUV uv, float alpha) {
        if (textureId == null || uv == null) return false;
        TextureInfo info = infoFor(textureId);
        if (info == null) return false;

        return drawFace(info, x, y, size, radius, withAlpha(alpha), uv);
    }

    /**
     * 用贴图指定区域填充一个圆角矩形。
     *
     * <p>脸部区域可能<b>不是正方形</b>（鱿鱼 12x16、蝙蝠 4x3、骆驼 7x8），
     * 而头像是正方形。这里按 {@code cover} 语义等比缩放并居中：取两个方向中较大的
     * 缩放系数，保证脸铺满整个头像且不变形，多余部分由 SDF 圆角自然裁掉。
     *
     * <p>换算必须用贴图自身的宽高，否则 64x32 的老式贴图会被纵向拉伸
     * （图案只能等比缩放，要按比例放大到足够覆盖目标区域）。
     */
    private static boolean drawFace(TextureInfo info,
                                    float x, float y, float size, float radius, Color tint,
                                    SakuraEntityPortrait.HeadUV uv) {
        if (uv.sizeX() <= 0f || uv.sizeY() <= 0f) return false;

        // cover：取较大缩放系数，让短边也能铺满，长边溢出部分被圆角裁掉
        float unit = Math.max(size / uv.sizeX(), size / uv.sizeY());

        Shader2DUtil.drawTexturedRoundedRect(
            x, y, size, size, radius,
            info.glId(), info.width(), info.height(),
            uv.u(), uv.v(), unit, tint);

        return true;
    }

    private static Color withAlpha(float alpha) {
        int a = Math.max(0, Math.min(255, (int) (alpha * 255f)));
        return new Color(255, 255, 255, a);
    }

    /** 一张贴图的 GL 纹理 id 及像素尺寸。 */
    private record TextureInfo(int glId, int width, int height) {}

    /**
     * 取贴图对应的 GL 纹理信息。
     *
     * <p>贴图未加载完成时安静返回 null，下一帧再试（不缓存失败结果）。
     */
    private static TextureInfo infoFor(Identifier textureId) {
        try {
            AbstractTexture texture = mc.getTextureManager().getTexture(textureId);
            if (texture == null) return null;
            if (!(texture.getGlTexture() instanceof GlTexture glTexture)) return null;

            int width = glTexture.getWidth(0);
            int height = glTexture.getHeight(0);
            int glId = glTexture.getGlId();
            if (width <= 0 || height <= 0 || glId <= 0) return null;

            return new TextureInfo(glId, width, height);
        } catch (Throwable ignored) {
            return null;
        }
    }
}
