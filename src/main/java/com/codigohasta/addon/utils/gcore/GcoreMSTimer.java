package com.codigohasta.addon.utils.gcore;

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
