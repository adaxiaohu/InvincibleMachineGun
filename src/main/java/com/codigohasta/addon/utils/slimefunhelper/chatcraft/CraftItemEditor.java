package com.codigohasta.addon.utils.slimefunhelper.chatcraft;
import com.codigohasta.addon.utils.slimefunhelper.SfTranslations;
import com.codigohasta.addon.utils.slimefunhelper.utils.ItemStackUtils;
import com.codigohasta.addon.modules.slimefunhelper.FastCraft;
import meteordevelopment.meteorclient.gui.*;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.*;
/** Meteor replacement for the source trade-slot item editor: editable complete SNBT and creative delivery. */
public final class CraftItemEditor extends WindowScreen {
    private static final net.minecraft.client.MinecraftClient mc = net.minecraft.client.MinecraftClient.getInstance();
    private final ItemStack item;
    private String contents;
    private static String text(String key,String fallback){return SfTranslations.text("ui.fast-craft.editor."+key,fallback);}
    public CraftItemEditor(ItemStack stack){
        super(GuiThemes.get(),text("title","交易物品编辑"));item=stack.copy();
        contents=ItemStack.CODEC.encodeStart(ItemStackUtils.registry().getOps(NbtOps.INSTANCE),item).getOrThrow().toString();
    }
    @Override public void initWidgets(){
        add(theme.label(item.getName().getString())).expandX();
        var field=add(theme.textBox(contents)).minWidth(450).expandX().widget();field.action=()->contents=field.get();
        var status=add(theme.label(text("hint","编辑完整物品 SNBT；创造模式可获取编辑后的物品"))).widget();
        var row=add(theme.horizontalList()).expandX().widget();
        row.add(theme.button(text("copy","复制"))).widget().action=()->mc.keyboard.setClipboard(contents);
        row.add(theme.button(text("validate","检查"))).widget().action=()->{
            try{parse();status.set(text("valid","物品数据有效"));}catch(Exception error){status.set(error.getMessage());}
        };
        row.add(theme.button(text("give","获取物品"))).widget().action=()->{
            if(FastCraft.INSTANCE==null||!FastCraft.INSTANCE.isActive()||mc.player==null||mc.interactionManager==null)return;
            if(!mc.interactionManager.getCurrentGameMode().isCreative()){status.set(text("creative-only","需要创造模式"));return;}
            try{var parsed=parse();CreativeGive.give(parsed,parsed.getCount());status.set(text("given","已获取物品"));}catch(Exception error){status.set(error.getMessage());}
        };
    }
    private ItemStack parse() throws Exception {
        return ItemStack.CODEC.parse(ItemStackUtils.registry().getOps(NbtOps.INSTANCE),StringNbtReader.readCompound(contents)).getOrThrow();
    }
}
