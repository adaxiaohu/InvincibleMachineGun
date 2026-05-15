package com.codigohasta.addon.utils.alien;

public class AlienAnimateUtil {

    public static double animate(double current, double endPoint, double speed) {
        if (speed >= 1.0) {
            return endPoint;
        } else if (speed == 0.0) {
            return current;
        } else {
            boolean shouldContinueAnimation = endPoint > current;
            double dif = Math.max(endPoint, current) - Math.min(endPoint, current);
            if (Math.abs(dif) <= 0.001) {
                return endPoint;
            } else {
                double factor = dif * speed;
                return current + (shouldContinueAnimation ? factor : -factor);
            }
        }
    }
}
