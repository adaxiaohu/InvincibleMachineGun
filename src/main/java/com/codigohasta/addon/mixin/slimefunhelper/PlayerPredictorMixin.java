package com.codigohasta.addon.mixin.slimefunhelper;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.gen.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

import net.minecraft.entity.player.PlayerEntity;
import com.codigohasta.addon.utils.slimefunhelper.accessors.hacks.PlayerInternalAccess;
import com.codigohasta.addon.utils.slimefunhelper.hacks.utils.entity.PredictorImpl;
import com.codigohasta.addon.modules.slimefunhelper.PositionPredict;
@Mixin(PlayerEntity.class)
public abstract class PlayerPredictorMixin implements PlayerInternalAccess {
    @Unique private PredictorImpl sf$playerPredictor;
    public PredictorImpl getPredictorImpl(){
        if(sf$playerPredictor==null)sf$playerPredictor=new PredictorImpl((PlayerEntity)(Object)this);
        return sf$playerPredictor;
    }
    public com.codigohasta.addon.utils.slimefunhelper.hacks.utils.entity.Predictor getPositionPredictor(){return getPredictorImpl();}
    @Inject(method="tick",at=@At("RETURN"))
    private void sf$recordAfterTick(CallbackInfo ci){
        if(com.codigohasta.addon.utils.slimefunhelper.SlimefunRuntime.modifying())getPredictorImpl().tick();
    }
}
