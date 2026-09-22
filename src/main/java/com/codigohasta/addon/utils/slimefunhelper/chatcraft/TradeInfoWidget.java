package com.codigohasta.addon.utils.slimefunhelper.chatcraft;
import com.codigohasta.addon.utils.slimefunhelper.SfTranslations;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.text.Text;
import net.minecraft.village.TradeOffer;
import java.util.*;
import java.util.function.Supplier;
/** Source merchant arrow, stock state and all six pricing/use details. */
public final class TradeInfoWidget extends ClickableWidget {
    private final Supplier<TradeOffer> trade;
    public TradeInfoWidget(Supplier<TradeOffer> trade){super(0,0,10,10,Text.empty());this.trade=trade;}
    private Text detail(String key,String fallback,Object value){return Text.literal(SfTranslations.text("widget.fast-trade.trade-info."+key,fallback).formatted(value));}
    @Override protected void renderWidget(DrawContext context,int mx,int my,float delta){
        var mc=MinecraftClient.getInstance();var offer=trade.get();
        if(offer==null){context.drawTextWithShadow(mc.textRenderer,SfTranslations.text("widget.fast-trade.no-select","未选中交易"),getX()-28,getY(),0xffff5555);return;}
        context.drawTextWithShadow(mc.textRenderer,offer.isDisabled()?"×":"→",getX(),getY(),offer.isDisabled()?0xffff5555:0xffffffff);
        if(isHovered())context.drawTooltip(mc.textRenderer,List.of(
            Text.literal(SfTranslations.text("widget.fast-trade.trade-info.0","交易信息")),
            Text.literal(SfTranslations.text("widget.fast-trade.trade-info.1","价格与使用次数")),
            detail("max-trade","最大交易次数: %s",offer.getMaxUses()),
            detail("current-trade","已用交易次数: %s",offer.getUses()),
            detail("default-count","原始数量: %s",offer.getFirstBuyItem().count()),
            detail("price-multiplier","价格倍率: %s","%.2f".formatted(offer.getPriceMultiplier())),
            detail("demand-bonus","需求加成: %s",offer.getDemandBonus()),
            detail("special-price","特殊价格: %s",offer.getSpecialPrice())),mx,my);
    }
    @Override protected void appendClickableNarrations(NarrationMessageBuilder builder){appendDefaultNarrations(builder);}
}
