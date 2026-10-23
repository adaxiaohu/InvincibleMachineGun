package com.codigohasta.addon.utils.gcore;

/**
 * 枚举集合。
 *
 * <p>移植自 {@code dev.godden.core.Utils.ClassUtil.EnumType}（GCore）。
 *
 * <p><b>翻译说明</b>：源里的枚举实现了 LiquidBounce 的 {@code NamedChoice}
 * （提供一个 {@code choiceName} 用于设置界面显示）。Meteor 的 {@code EnumSetting}
 * 直接用枚举名，所以这里**去掉 NamedChoice 接口**，改为覆写 {@code toString()}
 * 返回同样的名字，保证设置界面显示一致。
 *
 * <p>目前已移植 {@link BlockPlaceVersion}（TP 锚）、
 * {@link CrystalPlaceVersion}（TP 水晶）与 {@link ItemMode}。
 */
public final class GcoreEnumType {

    private GcoreEnumType() {
    }

    /** 方块放置的版本行为。默认 {@code MC1_14}。 */
    public enum BlockPlaceVersion {
        MC1_14("1.14"),
        MC1_12("1.12");

        private final String choiceName;

        BlockPlaceVersion(String choiceName) {
            this.choiceName = choiceName;
        }

        /** 源里 {@code NamedChoice.getChoiceName()}。 */
        public String getChoiceName() {
            return this.choiceName;
        }

        @Override
        public String toString() {
            return this.choiceName;
        }
    }

    /**
     * 水晶放置的版本行为。
     *
     * <p>与 {@link BlockPlaceVersion} 是**两个不同的枚举**（源里也是这样）——
     * 前者只有 2 个值、choiceName 带点号（{@code "1.14"}）；
     * 本枚举有 **3 个值**、choiceName 是常量名本身（{@code "MC1_14"}）。
     *
     * <p><b>★ 不要合并成一个枚举</b>：源的 {@code CrystalPlaceVersion}
     * 多一个 {@code MC1_13}，且 {@code toString()} 的内容不同。
     * 合并会让设置界面显示错、也可能让旧存档的枚举值解析失败。
     *
     * <p><b>用在哪</b>：{@code GcoreCrystalUtil.canPlaceCheck} 里
     * {@code version == MC1_12} 时要求底座上方 <b>2 格</b>都是空气；
     * {@code GcoreTpCrystalUtil.findAndSetTpVec} 里
     * {@code version == MC1_14 && canTouchObs} 可以直接朝下放。
     */
    public enum CrystalPlaceVersion {
        MC1_12("MC1_12"),
        MC1_13("MC1_13"),
        MC1_14("MC1_14");

        private final String choiceName;

        CrystalPlaceVersion(String choiceName) {
            this.choiceName = choiceName;
        }

        /** 源里 {@code NamedChoice.getChoiceName()}。 */
        public String getChoiceName() {
            return this.choiceName;
        }

        @Override
        public String toString() {
            return this.choiceName;
        }
    }

    /**
     * 切物品的模式。
     *
     * <p>{@link #getFindIntRange(int)} 返回「在哪些槽位里找物品」，与源逐行对应：
     * <pre>
     * HotBar   -> 0..8
     * Inv      -> 0..35
     * SwapInv  -> 0..35
     * None/其它 -> selectedSlot..selectedSlot（只找手持槽）
     * </pre>
     */
    public enum ItemMode {
        None("不切"),
        HotBar("快捷栏"),
        Inv("背包"),
        SwapInv("背包交换");

        private final String choiceName;

        ItemMode(String choiceName) {
            this.choiceName = choiceName;
        }

        public String getChoiceName() {
            return this.choiceName;
        }

        /**
         * 查找范围。
         *
         * <p>注意源的 switch 只映射了 HotBar / Inv / SwapInv 三个分支，
         * 其余（含 {@code None}）走 {@code default} → 只返回选中槽。
         */
        public GcoreIntRange getFindIntRange(int selectedSlot) {
            return switch (this) {
                case HotBar -> new GcoreIntRange(0, 8);
                case Inv -> new GcoreIntRange(0, 35);
                case SwapInv -> new GcoreIntRange(0, 35);
                default -> new GcoreIntRange(selectedSlot, selectedSlot);
            };
        }

        @Override
        public String toString() {
            return this.choiceName;
        }
    }
}
