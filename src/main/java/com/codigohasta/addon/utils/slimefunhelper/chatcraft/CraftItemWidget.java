package com.codigohasta.addon.utils.slimefunhelper.chatcraft;
import com.codigohasta.addon.modules.slimefunhelper.FastCraft;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.input.MouseInput;
import net.minecraft.item.*;
import net.minecraft.text.Text;
import java.util.function.*;
public final class CraftItemWidget extends ClickableWidget {
    private final Supplier<ItemStack> item;
    private final BooleanSupplier locked;
    private final boolean editable;
    public CraftItemWidget(Supplier<ItemStack> item,boolean editable,BooleanSupplier locked){super(0,0,18,18,Text.empty());this.item=item;this.editable=editable;this.locked=locked;}
    @Override protected void renderWidget(DrawContext context,int mx,int my,float delta){
        ItemStack stack=item.get();if(stack==null||stack.isEmpty())return;
        var mc=MinecraftClient.getInstance();context.drawItem(stack,getX()+1,getY()+1);context.drawStackOverlay(mc.textRenderer,stack,getX()+1,getY()+1);
        if(locked.getAsBoolean()){
            context.getMatrices().pushMatrix();context.getMatrices().translate(getX()+10,getY()+10);context.getMatrices().scale(7f/16f,7f/16f);
            context.drawItem(new ItemStack(Items.BARRIER),0,0);context.getMatrices().popMatrix();
        }
        if(isHovered())context.drawItemTooltip(mc.textRenderer,stack,mx,my);
    }
    @Override protected boolean isValidClickButton(MouseInput input){return editable&&input.button()==1;}
    @Override public void onClick(Click click,boolean doubled){
        if(editable&&FastCraft.INSTANCE!=null&&FastCraft.INSTANCE.isActive()&&!item.get().isEmpty())
            MinecraftClient.getInstance().setScreen(new CraftItemEditor(item.get()));
    }
    @Override protected void appendClickableNarrations(NarrationMessageBuilder builder){appendDefaultNarrations(builder);}
}
