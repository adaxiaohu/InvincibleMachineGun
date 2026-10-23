package com.codigohasta.addon.utils.sakura.render;

/**
 * 一套文字渲染用的字体组合（主字体 + 回退字体 + 图集尺寸）。
 *
 * <p><b>为什么需要这个概念：</b>{@link SakuraTextRenderer}
 * 原本把字体路径写死在类里。而不同 HUD 可能需要不同的字体组合 ——
 * 两套字体混在一个图集里
 * 是不行的：字形 UV、shelf 打包游标、度量缓存、甚至「图集写满就重建」的时机都会互相污染，
 * 一方把图集刷满就会把另一方已缓存（且已被写进 mesh）的字形挤掉。
 *
 * <p>所以把「字体 + 图集 + 度量缓存」整体抽成一个字体集：每个字体集各持一张图集纹理、
 * 各持一份度量/字形缓存，互不干扰。{@code SakuraTextRenderer} 的静态 API 默认走
 * {@link #SAKURA}。</p>
 *
 * <p><b>为什么用类而不是枚举：</b>字体集需要携带任意字体路径与图集尺寸，
 * 枚举的常量集合是封闭的，将来再加一套字体就得改枚举。这里做成值对象，
 * 需要新字体集时直接 {@code new} 一个常量即可，构造时校验参数。</p>
 *
 * @param primaryFont     主字体（拉丁/数字）；该字体没有的字形才回退
 * @param fallbackFont    回退字体（典型是中日韩）
 * @param atlasSize       字形图集边长（正方形）
 * @param padding         图集中字形之间的空隙（像素），避免 LINEAR 采样渗色
 */
public record CatFontSet(String primaryFont, String fallbackFont, int atlasSize, int padding) {

    /**
     * Sakura 原字体：{@code regular_medium.otf} + {@code kuriyama.ttf}。
     *
     * <p>这是 {@code SakuraTextRenderer} 默认使用的字体集，
     * <b>路径与图集参数必须与改造前逐字一致</b>，否则 SakuraTargetHud 的观感会变。
     * 图集 2048² 的 RED8 = 4MB，与 Meteor 自己的字体图集同量级。</p>
     */
    public static final CatFontSet SAKURA = new CatFontSet(
        "/assets/invincible_machine_gun/fonts/regular_medium.otf",
        "/assets/invincible_machine_gun/fonts/kuriyama.ttf",
        2048,
        1
    );

    public CatFontSet {
        if (primaryFont == null || primaryFont.isEmpty()) {
            throw new IllegalArgumentException("字体集缺少主字体路径");
        }
        if (fallbackFont == null || fallbackFont.isEmpty()) {
            throw new IllegalArgumentException("字体集缺少回退字体路径");
        }
        if (atlasSize <= 0) {
            throw new IllegalArgumentException("图集边长必须为正数: " + atlasSize);
        }
        if (padding < 0) {
            throw new IllegalArgumentException("字形间距不能为负: " + padding);
        }
    }
}
