package com.codigohasta.addon.utils.gcore;

import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.util.math.Box;

import java.util.List;

/**
 * 实体碰撞检测工具。
 *
 * <p>移植自 {@code dev.godden.core.Utils.EntityUtil}（GCore）。
 * 源是 Kotlin `object`，Java 里改为**静态方法**。
 *
 * <p>原方法签名是 {@code List} 参数但实际传 {@code ArrayList}；
 * 移植时放宽为 {@link List} 以兼容 {@link GcoreTickUpdate} 返回的列表。
 */
public final class GcoreEntityUtil {

    private GcoreEntityUtil() {
    }

    /**
     * 盒内是否有实体。
     *
     * <p>源里的判据写得很绕：
     * <pre>
     * if (entity instanceof EndCrystalEntity &amp;&amp; ignoreCrystal &amp;&amp; 中心相等
     *     || !entity.getBoundingBox().intersects(box)) continue;
     * return true;
     * </pre>
     * 即「末影水晶 + 要求忽略 + 中心完全一致」时跳过，或不碰撞时跳过；
     * 其余情况返回 true。**保持原判据与短路顺序。**
     */
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

    /**
     * 与 {@link #checkAnyLivingEntityCollie} 实现相同。
     *
     * <p>源里确实是两个一模一样的方法（GCore 自己重复了），移植时**保留两份**
     * 以免调用点改名。
     */
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
