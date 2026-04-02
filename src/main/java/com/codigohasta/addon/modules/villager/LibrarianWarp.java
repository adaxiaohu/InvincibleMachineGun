package com.codigohasta.addon.modules.villager;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/**
 * 图书管理员专属的数据实体类 (Minecraft 1.21.11)
 * 记录了村民的坐标、冷却状态，以及他所售卖的所有附魔书数据。
 */
public class LibrarianWarp {
    private static final MinecraftClient mc = MinecraftClient.getInstance();
    
    // 基础实体与坐标数据
    private final UUID uuid;
    private final BlockPos operatePos;         // 工作台(讲台)方块坐标，用于寻路终点
    private final Vec3d operatePosCenter;      // 工作台中心点，用于计算距离
    
    // 交易状态与冷却数据
    private boolean discovered = false;        // 是否已经打开GUI查过他卖什么书了？
    private long lastTradeTime = 0L;           // 上次把他买空的时间戳 (System.currentTimeMillis)
    private long tradeTimes = 0L;              // 今天已经被买空的次数 (达到上限后不再打扰)

    // 附魔书商品清单 (一个村民可能卖多本不同的书，所以用 List)
    private final List<LibrarianOffer> offers = new ArrayList<>();

    public LibrarianWarp(UUID uuid, BlockPos operatePos) {
        this.uuid = uuid;
        this.operatePos = operatePos;
        this.operatePosCenter = operatePos.toCenterPos();
    }

    // ==========================================
    // 实体获取逻辑
    // ==========================================
    public VillagerEntity getVillager() {
        if (mc.world == null) return null;
        try {
            for (Entity entity : mc.world.getEntities()) {
                if (entity instanceof VillagerEntity && entity.getUuid().equals(this.uuid)) {
                    return (VillagerEntity) entity;
                }
            }
        } catch (Exception e) {
            ChatUtils.error("获取图书管理员状态异常 : " + e.getMessage());
        }
        return null;
    }

    // ==========================================
    // 交易清单管理
    // ==========================================
    public void addOffer(LibrarianOffer offer) {
        this.offers.add(offer);
    }

    public void clearOffers() {
        this.offers.clear();
    }

    public List<LibrarianOffer> getOffers() {
        return this.offers;
    }

    // ==========================================
    // 基础 Getter / Setter
    // ==========================================
    public UUID getUuid() {
        return uuid;
    }

    public BlockPos getOperatePos() {
        return operatePos;
    }

    public Vec3d getOperatePosCenter() {
        return operatePosCenter;
    }

    public boolean isDiscovered() {
        return discovered;
    }

    public void setDiscovered(boolean discovered) {
        this.discovered = discovered;
    }

    public long getLastTradeTime() {
        return lastTradeTime;
    }

    public void setLastTradeTime(long lastTradeTime) {
        this.lastTradeTime = lastTradeTime;
    }

    public long getTradeTimes() {
        return tradeTimes;
    }

    public void setTradeTimes(long tradeTimes) {
        this.tradeTimes = tradeTimes;
    }

    // ==========================================
    // 内部类：单项交易清单明细 (LibrarianOffer)
    // 必须使用普通类而不是 Record，因为缺货状态(outOfStock)是动态变化的
    // ==========================================
    public static class LibrarianOffer {
        private final int tradeIndex;                           // 该交易在村民GUI中的槽位索引(0, 1, 2...)，发包专用
        private final RegistryKey<Enchantment> enchantment;     // 附魔类型 (如 经验修补)
        private final int level;                                // 附魔等级 (如 锋利 5)
        private final int emeraldPrice;       // 当前售价 (打折后或涨价后的实际价格)
private final int originalPrice;      // 原始售价 (用于判断是否溢价)
private boolean outOfStock;           // 当前是否缺货

public LibrarianOffer(int tradeIndex, RegistryKey<Enchantment> enchantment, int level, int emeraldPrice, int originalPrice, boolean outOfStock) {
    this.tradeIndex = tradeIndex;
    this.enchantment = enchantment;
    this.level = level;
    this.emeraldPrice = emeraldPrice;
    this.originalPrice = originalPrice;
    this.outOfStock = outOfStock;
}

public int getOriginalPrice() {
    return originalPrice;
}

        public int getTradeIndex() {
            return tradeIndex;
        }

        public RegistryKey<Enchantment> getEnchantment() {
            return enchantment;
        }

        public int getLevel() {
            return level;
        }

        public int getEmeraldPrice() {
            return emeraldPrice;
        }

        public boolean isOutOfStock() {
            return outOfStock;
        }

        public void setOutOfStock(boolean outOfStock) {
            this.outOfStock = outOfStock;
        }
    }
}