package com.codigohasta.addon.utils.slimefunhelper.utils;
import net.minecraft.text.*;
import net.minecraft.util.Formatting;

public final class ChatUtils {public static net.minecraft.text.Text stringToText(String text){return net.minecraft.text.Text.literal(text.replace('&','§'));}public static String textToString(net.minecraft.text.Text text){return text==null?"":text.getString();}
public static String getDisplayedLocationDouble(net.minecraft.util.math.Vec3d p){return "%.2f, %.2f, %.2f".formatted(p.x,p.y,p.z);}
    public static Text getDisplayedLocation(double x, double z) {
        String suffixDirection = "";
        if (net.minecraft.client.MinecraftClient.getInstance().player != null) {
            int xsgn = (int) MathUtils.sgn(x - net.minecraft.client.MinecraftClient.getInstance().player.getX());
            int zsgn = (int) MathUtils.sgn(z - net.minecraft.client.MinecraftClient.getInstance().player.getZ());
            suffixDirection = "\n" + MathUtils.getDirectionName(xsgn, zsgn) + " " + "X" + (xsgn >= 0 ? "+" : "-") + "Z"
                    + (zsgn >= 0 ? "+" : "-");
        }
        return Text.literal("[%.2f,~,%.2f]".formatted(x, z))
                .setStyle(Style.EMPTY
                        .withClickEvent(new ClickEvent.CopyToClipboard("%.2f ~ %.2f".formatted(x, z)))
                        .withHoverEvent(new HoverEvent.ShowText(Text.literal("click to copy coord" + suffixDirection))))
                .formatted(Formatting.GREEN);
    }
}
