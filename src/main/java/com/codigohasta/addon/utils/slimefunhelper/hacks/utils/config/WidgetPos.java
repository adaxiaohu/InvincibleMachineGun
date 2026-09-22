package com.codigohasta.addon.utils.slimefunhelper.hacks.utils.config;
import net.minecraft.client.util.Window;
/** Source percentage/absolute coordinates, edited and persisted as Meteor JSON. */
public record WidgetPos(int type, double percentageX, double percentageY, int lengthX, int lengthY) {
    public int getWindowX(Window window){return type==0?(int)(window.getScaledWidth()*percentageX):type==1?lengthX:0;}
    public int getWindowY(Window window){return type==0?(int)(window.getScaledHeight()*percentageY):type==1?lengthY:0;}
}
