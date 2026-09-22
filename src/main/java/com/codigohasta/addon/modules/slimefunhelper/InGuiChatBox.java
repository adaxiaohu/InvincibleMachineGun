package com.codigohasta.addon.modules.slimefunhelper;
import com.codigohasta.addon.mixin.slimefunhelper.ChatCraftHandledAccessor;
import com.codigohasta.addon.mixin.slimefunhelper.ChatCraftScreenAccessor;
import com.codigohasta.addon.utils.slimefunhelper.chatcraft.*;
import com.codigohasta.addon.utils.slimefunhelper.events.Event;
import com.codigohasta.addon.utils.slimefunhelper.hacks.api.*;
import com.codigohasta.addon.utils.slimefunhelper.hacks.utils.config.WidgetPos;
import com.codigohasta.addon.utils.slimefunhelper.managers.config.*;
import net.minecraft.client.gui.screen.*;
import net.minecraft.client.gui.screen.ingame.*;
import net.minecraft.client.gui.widget.TextFieldWidget;

/** Source in-container chat field, using vanilla widgets inside the active screen. */
public class InGuiChatBox extends BaseModule {
    public static InGuiChatBox INSTANCE;
    public final ModulePath chat=makePath("chat","chat-helper");
    public final FlagRef enableGui=flagBuilder(chat.add("chat-box-in-gui")).build();
    public final FlagRef enableOther=flagBuilder(chat.add("chat-box-in-any-screen")).build();
    public final NBTRef<WidgetPos> otherScreenInputPos=builder(chat.add("chat-box-other-pos"),WidgetPos.class)
        .defaultValue(new WidgetPos(0,0.5,1.0,0,0)).build();
    public final DoubleRef otherScreenInputLength=doubleBuilder(chat.add("chat-box-other-length")).defaultValue(100.0).build();
    private Screen owner;
    private ChatLikeInputWidget input;
    public InGuiChatBox(){super("InGuiChatBox");INSTANCE=this;}
    @Override public void registerAll(){registerListener(SfChatCraftEvents.SCREEN_INIT,this::onScreenInitialize);}
    @Override public void onEnableModule(){if(mc.currentScreen!=null&&!checkNull())onScreenInitialize(new Event<>(mc.currentScreen,false,false));}
    @Override public void onDisableModule(){removeWidget();}
    private void removeWidget(){if(owner!=null&&input!=null){if(owner.getFocused()==input)owner.setFocused(null);((ChatCraftScreenAccessor)owner).sfimg$remove(input);}owner=null;input=null;}
    public void onScreenInitialize(Event<Screen> event){
        removeWidget();Screen screen=event.context();
        if(screen instanceof HandledScreen<?>){if(!enableGui.get())return;}
        else if(!enableOther.get()||screen instanceof ChatScreen||screen.getFocused() instanceof TextFieldWidget)return;
        owner=screen;input=createInputWidget(0,0,100);positionWidget(screen);
        ((ChatCraftScreenAccessor)screen).sfimg$add(input);
    }
    public ChatLikeInputWidget createInputWidget(int x,int y,int width){
        return new ChatLikeInputWidget(mc.textRenderer,x,y,width,12,value->{
            if(!isActive()||checkNull()||value==null||value.isEmpty()||value.equals("/"))return;
            value=ChatExtra.INSTANCE.normalizeSendText(value);
            mc.inGameHud.getChatHud().addToMessageHistory(value);
            if(value.startsWith("/"))mc.player.networkHandler.sendChatCommand(value.substring(1));
            else mc.player.networkHandler.sendChatMessage(value);
        });
    }
    public void positionWidget(Screen screen){
        if(!isActive()||owner!=screen||input==null)return;
        if(screen instanceof HandledScreen<?>){
            var access=(ChatCraftHandledAccessor)screen;
            input.setX(access.sfimg$x()+2);input.setY(access.sfimg$y()+access.sfimg$height()+(screen instanceof CreativeInventoryScreen?40:10));input.setWidth(access.sfimg$width()-4);
            input.visible=enableGui.get();input.active=enableGui.get();
        }else{
            WidgetPos pos=otherScreenInputPos.get();int width=Math.max(1,(int) otherScreenInputLength.get());
            input.setX(pos.getWindowX(mc.getWindow())-width/2);input.setY(pos.getWindowY(mc.getWindow())-12);input.setWidth(width);
            input.visible=enableOther.get();input.active=enableOther.get();
        }
    }
}
