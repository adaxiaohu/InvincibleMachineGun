package com.codigohasta.addon.utils.gcore;

/**
 * 毫秒计时器。
 *
 * <p>移植自 {@code dev.godden.core.Utils.ClassUtil.MSTimer}（GCore / LiquidBounce）。
 * 保留原有的三个方法语义与 {@code System.currentTimeMillis()} 实现。
 *
 * <p><b>翻译说明</b>：源类是 Kotlin 的普通 class（有状态），所以按实例类移植，
 * 不走 static 化。方法体与源逐行对应。
 */
public final class GcoreMSTimer {

    /** 上次 {@link #reset()} 或构造的时刻。 */
    private long lastTime = System.currentTimeMillis();

    /**
     * 距上次 reset 是否已过 {@code time} 毫秒。
     *
     * <p>源类有两个重载（long / int），Java 里保留两个以免调用点改签名。
     */
    public boolean hasPassTime(long time) {
        return System.currentTimeMillis() - this.lastTime >= time;
    }

    /** 见 {@link #hasPassTime(long)}。 */
    public boolean hasPassTime(int time) {
        return System.currentTimeMillis() - this.lastTime >= (long) time;
    }

    /** 重置计时。 */
    public void reset() {
        this.lastTime = System.currentTimeMillis();
    }

    /** 距上次 reset 已过多少毫秒。 */
    public long getPassTime() {
        return System.currentTimeMillis() - this.lastTime;
    }
}
