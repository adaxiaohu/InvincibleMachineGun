package com.codigohasta.addon.utils.slimefunhelper.utils;
import java.awt.Color;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.*;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
public final class RenderUtils {
    public static Vec3d getCameraPos(){return net.minecraft.client.MinecraftClient.getInstance().gameRenderer.getCamera().getCameraPos();}
    public static Vec3d getTracerOrigin(float partialTicks){
        var camera=net.minecraft.client.MinecraftClient.getInstance().gameRenderer.getCamera();
        var direction=camera.getHorizontalPlane();
        return new Vec3d(direction.x(),direction.y(),direction.z()).multiply(10);
    }
    public static void drawLineVirtual(MatrixStack matrix,Vec3d from,Vec3d to,Color color){if(current!=null)current.renderer.line(from.x,from.y,from.z,to.x,to.y,to.z,new meteordevelopment.meteorclient.utils.render.color.Color(color.getRGB()));}
    public static void drawLineVirtualCameraCoord(MatrixStack matrix,Vec3d from,Vec3d to,Color color){Vec3d camera=getCameraPos();drawLineVirtual(matrix,from.add(camera),to.add(camera),color);}
    public static Render3DEvent current;
    public static void startDrawVirtual(MatrixStack matrix){}
    public static void stopDrawVirtual(MatrixStack matrix){}
    public static void drawOutlinedBox(MatrixStack matrix,Vec3d from,Vec3d to,Color color){if(current!=null)current.renderer.box(new Box(from,to),new meteordevelopment.meteorclient.utils.render.color.Color(color.getRGB()),new meteordevelopment.meteorclient.utils.render.color.Color(color.getRGB()),ShapeMode.Lines,0);}
public static Box getLerpedBox(net.minecraft.entity.Entity e,float delta){return e.getBoundingBox().offset(e.getLerpedPos(delta).subtract(e.getEntityPos()));}
public static void drawSolidBox(MatrixStack m,Vec3d from,Vec3d to,Color c){if(current!=null)current.renderer.box(new Box(from,to),new meteordevelopment.meteorclient.utils.render.color.Color(c.getRGB()),new meteordevelopment.meteorclient.utils.render.color.Color(c.getRGB()),ShapeMode.Sides,0);}

}
