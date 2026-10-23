package com.codigohasta.addon.utils.gcore;

public final class GcoreMathUtils {

    private GcoreMathUtils() {
    }

    public static void forEachPoint(double xStep, double yStep, double zStep,
                                    GcoreTriConsumer<Double, Double, Double> block) {
        for (double xt = 0.0; xt <= 1.0; xt += xStep) {
            for (double yt = 0.0; yt <= 1.0; yt += yStep) {
                for (double zt = 0.0; zt <= 1.0; zt += zStep) {
                    block.accept(xt, yt, zt);
                }
            }
        }
    }
}
