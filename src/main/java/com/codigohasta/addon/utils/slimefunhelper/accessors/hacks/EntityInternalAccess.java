package com.codigohasta.addon.utils.slimefunhelper.accessors.hacks;

import net.minecraft.entity.Entity;
public interface EntityInternalAccess {
    static EntityInternalAccess of(Entity entity){return (EntityInternalAccess)entity;}
    void setDataFlag(int flag,boolean value);
    com.codigohasta.addon.utils.slimefunhelper.hacks.utils.entity.Predictor getPositionPredictor();
}
