package com.codigohasta.addon.utils.gcore;

import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;

public final class GcoreTpOption {

    /** VClip 搜索方向（源 {@code TpUtil.TpVClipFindType}）。 */
    public enum TpVClipFindType {
        /** 先往上、再往下找。 */
        ALL("先上后下"),
        /** 只往上找。 */
        OnlyUp("只往上"),
        /** 只往下找。 */
        OnlyDown("只往下");

        private final String choiceName;

        TpVClipFindType(String choiceName) {
            this.choiceName = choiceName;
        }

        public String getChoiceName() {
            return this.choiceName;
        }

        @Override
        public String toString() {
            return this.choiceName;
        }
    }

    private final Setting<GcoreTpOption.TpVClipFindType> searchVclipMode;
    private final Setting<Double> searchFindStep;
    private final Setting<Double> moveDistance;
    private final Setting<Boolean> allowIntoVoid;
    private final Setting<Integer> limitPacket;
    private final Setting<Boolean> printWhenTooManyPacket;
    private final Setting<Boolean> back;

    /** ★★ 服务端「单包位移倍率」（{@code moved-too-quickly-multiplier}）。 */
    private final Setting<Double> serverStepMult;

    public GcoreTpOption(SettingGroup group) {
        this(group, true);
    }

    public GcoreTpOption(SettingGroup group, boolean backDefault) {
        this.searchVclipMode = group.add(new EnumSetting.Builder<GcoreTpOption.TpVClipFindType>()
            .name("VClip 搜索方向")
            .description("VClip 搜索方向：ALL 先上后下，OnlyUp 只往上，OnlyDown 只往下。")
            .defaultValue(GcoreTpOption.TpVClipFindType.OnlyUp)
            .build());

        this.searchFindStep = group.add(new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
            .name("VClip 搜索步长")
            .description("VClip 搜索的步长。")
            .defaultValue(1.8)
            .range(0.1, 10.0)
            .sliderRange(0.1, 10.0)
            .build());

        this.moveDistance = group.add(new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
            .name("单包位移")
            .description("每个移动包的最大位移，决定需要多少个包。\n"
                + "★ 本值应当 ≈ 服务端的「单包位移倍率」（默认 10.0）。\n"
                + "  服务器放宽限制时，本值要跟着一起调大，包数才会相应减少。")
            .defaultValue(50.0)
            .range(0.1, Double.MAX_VALUE)
            .sliderRange(0.1, 50.0)
            .build());

        this.allowIntoVoid = group.add(new BoolSetting.Builder()
            .name("允许传入虚空")
            .description("允许传送到虚空下方。")
            .defaultValue(false)
            .build());

        this.limitPacket = group.add(new IntSetting.Builder()
            .name("移动包上限")
            .description("移动包数量上限，超过就放弃本次传送。")
            .defaultValue(20)
            .range(1, 20)
            .sliderRange(1, 20)
            .build());

        // ★★ 2026-10-11 新增：服务端「单包位移倍率」。
        //
        //   服务端判据（ServerGamePacketListenerImpl:1527）：
        //       位移 ≤ movedTooQuicklyMultiplier × i
        //   Spigot 默认 moved-too-quickly-multiplier = 10.0 ⇒ 「≤ 10·i」。
        //
        //   本项暴露给所有走 GcoreTpUtil.doTpMove 的 TP 模块，
        //   用于「服务器特意放宽/关闭该检查」的场景 —— 否则本类会在
        //   规划阶段误判「包数超限」而放弃传送。
        //
        //   默认 10.0 时行为与修复前完全一致。
        this.serverStepMult = group.add(new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
            .name("服务端单包位移倍率")
            .description("对应服务端 spigot.yml 的 moved-too-quickly-multiplier（默认 10.0）。\n"
                + "服务端判据：单包位移 ≤ 本值 × 本包下标。\n"
                + "★ 只有服务器**特意放宽/关闭**了该检查时才需要调大；\n"
                + "  调大后「单包位移」也要一起调大才能生效。\n"
                + "默认 10.0 = Spigot 默认值，行为与原版一致。")
            .defaultValue(com.codigohasta.addon.utils.gcore.GcoreMoveSequenceUtil.SERVER_STEP_MULT)
            .min(0.1)
            .sliderRange(1.0, 100.0)
            .max(Double.MAX_VALUE)
            .build());

        this.printWhenTooManyPacket = group.add(new BoolSetting.Builder()
            .name("超限时提示")
            .description("包数超限时在聊天栏提示。")
            .defaultValue(true)
            .build());

        this.back = group.add(new BoolSetting.Builder()
            .name("回传原位")
            .description("TP 之后是否传送回原位。关闭则会真的停在目标位置。")
            .defaultValue(backDefault)
            .build());
    }

    public GcoreTpOption.TpVClipFindType getSearchVclipMode() {
        return this.searchVclipMode.get();
    }

    public float getSearchFindStep() {
        return this.searchFindStep.get().floatValue();
    }

    public float getMoveDistance() {
        return this.moveDistance.get().floatValue();
    }

    public boolean getAllowIntoVoid() {
        return this.allowIntoVoid.get();
    }

    public int getLimitPacket() {
        return this.limitPacket.get();
    }

    public boolean getPrintWhenTooManyPacket() {
        return this.printWhenTooManyPacket.get();
    }

    public boolean getBack() {
        return this.back.get();
    }

    /** 见 {@link #serverStepMult}。 */
    public double getServerStepMult() {
        return this.serverStepMult.get();
    }

    /**
     * 见 {@link #getBack()}。
     *
     * <p>对应源 {@code TpOption.setBack(boolean)}（源第 114 行）。
     *
     * <p><b>★ 注意：不要在构造器里用它来改「出厂默认」</b>——
     * Meteor 每次加载配置都会 {@code Settings.fromTag → reset()}，
     * 把值打回 {@code defaultValue}，覆盖掉这里的设置。
     * 要改默认值请用 {@link #GcoreTpOption(SettingGroup, boolean)}。
     * 本方法只适合运行期（用户交互、模块逻辑）临时改值。
     */
    public void setBack(boolean value) {
        this.back.set(value);
    }
}
