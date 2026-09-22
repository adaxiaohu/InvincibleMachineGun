package com.codigohasta.addon.mixin.slimefunhelper;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.gen.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

import net.minecraft.entity.Entity;
import com.codigohasta.addon.utils.slimefunhelper.accessors.hacks.EntityInternalAccess;
@Mixin(Entity.class)
public abstract class EntityAccessMixin implements EntityInternalAccess, com.codigohasta.addon.utils.slimefunhelper.accessors.interfaces.MetadataHolder {
    @Unique private com.codigohasta.addon.utils.slimefunhelper.utils.containers.MetaData sf$metadata;
    public com.codigohasta.addon.utils.slimefunhelper.utils.containers.MetaData getMetadata(){
        if(sf$metadata==null)sf$metadata=new com.codigohasta.addon.utils.slimefunhelper.utils.containers.MetaData();
        return sf$metadata;
    }
    public boolean isMetaEmpty(){return sf$metadata==null;}
    @Invoker("setFlag") public abstract void setDataFlag(int flag,boolean value);
    @Unique private com.codigohasta.addon.utils.slimefunhelper.hacks.utils.entity.Predictor sf$predictor;
    public com.codigohasta.addon.utils.slimefunhelper.hacks.utils.entity.Predictor getPositionPredictor(){
        if(sf$predictor==null)sf$predictor=new com.codigohasta.addon.utils.slimefunhelper.hacks.utils.entity.SimpleEntityPredictor((Entity)(Object)this);
        return sf$predictor;
    }
}
