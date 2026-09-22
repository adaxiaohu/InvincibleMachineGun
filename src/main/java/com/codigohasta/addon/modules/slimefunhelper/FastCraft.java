package com.codigohasta.addon.modules.slimefunhelper;

import com.codigohasta.addon.mixin.slimefunhelper.*;
import com.codigohasta.addon.utils.slimefunhelper.SfTranslations;
import com.codigohasta.addon.utils.slimefunhelper.chatcraft.*;
import com.codigohasta.addon.utils.slimefunhelper.events.Event;
import com.codigohasta.addon.utils.slimefunhelper.hacks.InvTasks;
import com.codigohasta.addon.utils.slimefunhelper.hacks.api.*;
import com.codigohasta.addon.utils.slimefunhelper.managers.config.FlagRef;
import com.codigohasta.addon.utils.slimefunhelper.utils.Debug;
import java.util.*;
import java.util.function.*;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.*;
import net.minecraft.client.gui.widget.*;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.item.*;
import net.minecraft.recipe.*;
import net.minecraft.screen.AbstractCraftingScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.village.TradeOffer;

/** Source recipe repeat/lock, drop craft and merchant controls adapted to host GUI widgets. */
public class FastCraft extends BaseModule {
    public static FastCraft INSTANCE;
    public final ModulePath fastCraft=makePath("inventory","fast-craft");
    public final FlagRef enable=flagBuilder(fastCraft.add("enable-fastcraft-buttons")).build();
    public final FlagRef dropCraft=flagBuilder(fastCraft.add("drop-craft")).build();
    private NetworkRecipeId lastCrafted;
    private boolean lock;
    private HandledScreen<?> lastScreen;
    private final List<Positioned> widgets=new ArrayList<>();
    private long generation;
    private record Positioned(ClickableWidget widget, IntSupplier x, IntSupplier y){}
    public FastCraft(){super("FastCraft");INSTANCE=this;bindFlag(enable);}
    @Override public void registerAll(){registerListener(SfChatCraftEvents.SCREEN_INIT,this::onCraftScreenInitialize);}
    @Override public void onEnableModule(){if(mc.currentScreen!=null&&!checkNull())onCraftScreenInitialize(new Event<>(mc.currentScreen,false,false));}
    @Override public void onDisableModule(){generation++;removeWidgets();}
    public void resetRuntimeState(){generation++;lastCrafted=null;lock=false;removeWidgets();}
    private void removeWidgets(){
        generation++;
        if(lastScreen!=null)for(var widget:widgets)((ChatCraftScreenAccessor)lastScreen).sfimg$remove(widget.widget());
        widgets.clear();lastScreen=null;
    }
    public NetworkRecipeId getLastCrafted(){return lastCrafted;}
    public boolean isLock(){return lock;}
    public void onRecipeClicked(NetworkRecipeId recipe){if(isActive()&&!lock)lastCrafted=recipe;}
    public void toggleRecipeLock(){if(isActive()){lock=!lock;Debug.chat(Text.literal(SfTranslations.text("ui.fast-craft.recipe-lock","配方锁定")+": "+lock));}}
    public void onCraftScreenInitialize(Event<Screen> event){
        removeWidgets();
        if(!enable.get())return;
        Screen screen=event.context();
        if(screen instanceof CraftingScreen crafting){lastScreen=crafting;addCrafting(crafting,false);}
        else if(screen instanceof InventoryScreen inventory && mc.interactionManager!=null&&!mc.interactionManager.getCurrentGameMode().isCreative()){
            lastScreen=inventory;addCrafting(inventory,true);
        }else if(screen instanceof MerchantScreen merchant){lastScreen=merchant;addMerchant(merchant);}
    }
    private boolean usable(HandledScreen<?> screen){return isActive()&&enable.get()&&!checkNull()&&mc.interactionManager!=null&&mc.currentScreen==screen&&mc.player.currentScreenHandler==screen.getScreenHandler();}
    public void placeLastCraftingRecipe(HandledScreen<? extends AbstractCraftingScreenHandler> screen,boolean doCraft){
        if(!usable(screen))return;
        NetworkRecipeId last=lastCrafted;
        if(last==null){Debug.chat(Text.literal(SfTranslations.text("ui.fast-craft.empty-history","合成历史为空")));return;}
        mc.interactionManager.clickRecipe(screen.getScreenHandler().syncId,last,true);
        if(doCraft){
            int maximum=64;
            for(Ingredient ingredient:CraftRecipeData.getIngredients(last))
                for(ItemStack stack:CraftRecipeData.streamIngredientOptions(ingredient).toList())maximum=Math.min(maximum,stack.getMaxCount());
            craftAtSlotIndex(screen,maximum,screen.getScreenHandler().getOutputSlot().getIndex());
        }
    }
    public void craftAtSlotIndex(HandledScreen<?> screen,int maximum,int slot){
        if(!usable(screen))return;
        final long ticket=generation;
        boolean drop=dropCraft.get();
        for(int i=0;i<(drop?maximum:1);i++)InvTasks.getClickExecutor().execute(()->{
            if(ticket==generation&&usable(screen))mc.interactionManager.clickSlot(screen.getScreenHandler().syncId,slot,drop?0:1,drop?SlotActionType.THROW:SlotActionType.QUICK_MOVE,mc.player);
        });
    }
    private void add(HandledScreen<?> screen,ClickableWidget widget,IntSupplier x,IntSupplier y){
        widgets.add(new Positioned(widget,x,y));widget.setX(x.getAsInt());widget.setY(y.getAsInt());((ChatCraftScreenAccessor)screen).sfimg$add(widget);
    }
    private void button(HandledScreen<?> screen,String key,String fallback,String tipKey,String tip,Runnable action,IntSupplier x,IntSupplier y,int width,int height){
        var button=ButtonWidget.builder(Text.literal(SfTranslations.text(key,fallback)),b->{if(usable(screen))action.run();}).dimensions(0,0,width,height).build();
        button.setTooltip(Tooltip.of(Text.literal(SfTranslations.text(tipKey,tip))));add(screen,button,x,y);
    }
    private void addCrafting(HandledScreen<? extends AbstractCraftingScreenHandler> screen,boolean inventory){
        var access=(ChatCraftHandledAccessor)screen;
        button(screen,"widget.fast-craft.craft","合成","widget.fast-craft.craft.tooltips","放入上次配方；Shift 点击直接合成",()->placeLastCraftingRecipe(screen,ChatCraftKeys.shift()),()->access.sfimg$x()+(inventory?150:120),()->screen.height/2-(inventory?38:25),24,12);
        button(screen,"widget.fast-craft.lock","锁定","widget.fast-craft.lock.tooltips","锁定记录的配方",this::toggleRecipeLock,()->access.sfimg$x()+(inventory?150:95),()->screen.height/2-25,24,12);
        button(screen,"widget.fast-craft.toggle-drop","丢弃","widget.fast-craft.toggle-drop.tooltips","切换合成后丢弃",dropCraft::toggle,()->access.sfimg$x()+(inventory?150:120),()->screen.height/2-72,24,12);
        var preview=new CraftItemWidget(()->lastCrafted==null?new ItemStack(Items.BARRIER):CraftRecipeData.getRecipeResult(lastCrafted),false,()->lock);
        add(screen,preview,()->access.sfimg$x()+(inventory?132:150),()->access.sfimg$y()+(inventory?55:56));
    }
    public TradeOffer currentTrade(MerchantScreen screen){
        int selected=((ChatCraftMerchantAccessor)screen).sfimg$selected();var offers=screen.getScreenHandler().getRecipes();
        return selected>=0&&selected<offers.size()?offers.get(selected):null;
    }
    private void trade(MerchantScreen screen){
        if(!usable(screen))return;
        ((ChatCraftMerchantAccessor)screen).sfimg$syncRecipe();
        if(ChatCraftKeys.shift()){
            TradeOffer offer=currentTrade(screen);
            if(offer!=null&&!offer.getSellItem().isEmpty()){
                ItemStack out=offer.getSellItem();int max=(int)Math.ceil((float)out.getMaxCount()/out.getCount());
                craftAtSlotIndex(screen,Math.min(max,offer.getMaxUses()-offer.getUses()),2);
            }
        }
    }
    private void addMerchant(MerchantScreen screen){
        var access=(ChatCraftHandledAccessor)screen;
        IntSupplier originX=()->access.sfimg$x()+168;IntSupplier originY=()->access.sfimg$y()+60;
        for(int index=0;index<3;index++){
            final int i=index;Supplier<ItemStack> stack=()->{var offer=currentTrade(screen);return offer==null?ItemStack.EMPTY:switch(i){case 0->offer.getDisplayedFirstBuyItem();case 1->offer.getDisplayedSecondBuyItem();default->offer.getSellItem();};};
            add(screen,new CraftItemWidget(stack,true,()->false),()->originX.getAsInt()+(i==2?51:i*20+1),()->originY.getAsInt()+1);
        }
        var info=new TradeInfoWidget(()->currentTrade(screen));add(screen,info,()->originX.getAsInt()+40,()->originY.getAsInt()+4);
        button(screen,"widget.fast-trade.trade","交易","widget.fast-trade.trade.tooltips","填充交易物品；Shift 点击直接交易",()->trade(screen),()->originX.getAsInt()+71,originY,18,8);
        button(screen,"widget.fast-trade.toggle-drop","丢弃","widget.fast-trade.toggle-drop.tooltips","切换交易后丢弃",dropCraft::toggle,()->originX.getAsInt()+71,()->originY.getAsInt()+12,18,8);
    }
    public void positionWidgets(Screen screen){
        if(!isActive()||screen!=lastScreen)return;
        for(var entry:widgets){entry.widget.setX(entry.x.getAsInt());entry.widget.setY(entry.y.getAsInt());entry.widget.visible=enable.get();entry.widget.active=enable.get();}
    }
}
