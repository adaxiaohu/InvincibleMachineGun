package com.codigohasta.addon.utils.alien;

public class AlienAnimation {
    private final AlienFadeUtils fadeUtils = new AlienFadeUtils(0L);
    public double from = 0.0;
    public double to = 0.0;

    public double get(double target, long length, AlienEasing ease) {
        if (target != this.to) {
            this.from = this.from + (this.to - this.from) * this.fadeUtils.ease(ease);
            this.to = target;
            this.fadeUtils.reset();
        }
        this.fadeUtils.setLength(length);
        return this.from + (this.to - this.from) * this.fadeUtils.ease(ease);
    }
}
