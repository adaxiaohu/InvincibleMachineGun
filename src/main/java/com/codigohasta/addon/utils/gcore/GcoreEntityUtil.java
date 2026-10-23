package com.codigohasta.addon.utils.gcore;

import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.util.math.Box;

import java.util.List;

public final class GcoreEntityUtil {

    private GcoreEntityUtil() {
    }

    public static boolean checkAnyEntitiesCollie(Box box, boolean ignoreCrystal, List<Entity> list) {
        for (Entity entity : list) {
            if (entity instanceof EndCrystalEntity
                && ignoreCrystal
                && box.getCenter().equals(entity.getBoundingBox().getCenter())
                || !entity.getBoundingBox().intersects(box)) {
                continue;
            }
            return true;
        }
        return false;
    }

    /** 见 {@link #checkAnyEntitiesCollie}，用世界实体列表。 */
    public static boolean checkAnyEntitiesCollie(Box box, boolean ignoreCrystal) {
        return checkAnyEntitiesCollie(box, ignoreCrystal, GcoreTickUpdate.getWorldEntitiesList());
    }

    /** 盒内是否有「可交互实体」（{@code intersectionChecked}）。 */
    public static boolean checkAnyLivingEntityCollie(Box box, List<Entity> list) {
        for (Entity entity : list) {
            if (!entity.intersectionChecked || !entity.getBoundingBox().intersects(box)) {
                continue;
            }
            return true;
        }
        return false;
    }

    /** 见 {@link #checkAnyLivingEntityCollie}，用世界实体列表。 */
    public static boolean checkAnyLivingEntityCollie(Box box) {
        return checkAnyLivingEntityCollie(box, GcoreTickUpdate.getWorldEntitiesList());
    }

    public static boolean checkAnyLivingEntityCollieInEntities(Box box, List<Entity> list) {
        for (Entity entity : list) {
            if (!entity.intersectionChecked || !entity.getBoundingBox().intersects(box)) {
                continue;
            }
            return true;
        }
        return false;
    }
}
